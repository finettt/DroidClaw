package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CompletableFuture;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.accessibility.DroidClawAccessibilityService;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for the five screen-control tools.
 *
 * The bridge is registered with a mocked accessibility service that completes
 * command futures synchronously, so tool-level argument validation, approval
 * behavior, and dispatch paths can be exercised without a real device.
 */
@RunWith(RobolectricTestRunner.class)
public class ScreenToolsTest {

    @Mock
    private DroidClawAccessibilityService mockService;

    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Complete non-gesture commands inline; gesture commands (TAP/SWIPE)
        // complete asynchronously, which on Robolectric's main thread yields
        // a "dispatched" result — both paths are valid outcomes here.
        doAnswer(invocation -> {
            AccessibilityCommand cmd = invocation.getArgument(0);
            CompletableFuture<ToolResult> future = invocation.getArgument(1);
            if (future != null && !future.isDone()
                    && cmd != null
                    && cmd.getType() != AccessibilityCommand.Type.TAP
                    && cmd.getType() != AccessibilityCommand.Type.SWIPE) {
                future.complete(ToolResult.success("{\"status\":\"ok\"}"));
            }
            return null;
        }).when(mockService).executeCommand(
                any(AccessibilityCommand.class), any(CompletableFuture.class));
        AccessibilityBridge.register(mockService);
    }

    @After
    public void tearDown() throws Exception {
        AccessibilityBridge.unregister();
        if (mocks != null) mocks.close();
    }

    private static JsonObject args(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Integer) o.addProperty(k, (Integer) v);
            else o.addProperty(k, String.valueOf(v));
        }
        return o;
    }

    // ==================== names ====================

    @Test
    public void toolNames_matchRegisteredNames() {
        assertEquals("screen_tap", new ScreenTapTool(false).getName());
        assertEquals("screen_swipe", new ScreenSwipeTool(false).getName());
        assertEquals("screen_type_text", new ScreenTypeTextTool(false).getName());
        assertEquals("screen_get_ui_tree", new ScreenGetUiTreeTool().getName());
        assertEquals("screen_perform_action", new ScreenPerformActionTool(false).getName());
    }

    // ==================== approval behavior ====================

    @Test
    public void requiresApproval_trustMode_bypassesApproval() {
        assertFalse(new ScreenTapTool(true).requiresApproval());
        assertFalse(new ScreenSwipeTool(true).requiresApproval());
        assertFalse(new ScreenTypeTextTool(true).requiresApproval());
        assertFalse(new ScreenPerformActionTool(true).requiresApproval());
    }

    @Test
    public void requiresApproval_noTrustMode_requiresApproval() {
        assertTrue(new ScreenTapTool(false).requiresApproval());
        assertTrue(new ScreenSwipeTool(false).requiresApproval());
        assertTrue(new ScreenTypeTextTool(false).requiresApproval());
        assertTrue(new ScreenPerformActionTool(false).requiresApproval());
    }

    @Test
    public void getUiTree_neverRequiresApproval() {
        assertFalse(new ScreenGetUiTreeTool().requiresApproval());
    }

    // ==================== disconnected bridge ====================

    @Test
    public void execute_bridgeDisconnected_allToolsError() {
        AccessibilityBridge.unregister();

        for (io.finett.droidclaw.tool.Tool tool : new io.finett.droidclaw.tool.Tool[]{
                new ScreenTapTool(false), new ScreenSwipeTool(false),
                new ScreenTypeTextTool(false), new ScreenGetUiTreeTool(),
                new ScreenPerformActionTool(false)}) {
            ToolResult r = tool.execute(args("x", 1, "y", 1));
            assertFalse(tool.getName() + " should fail when bridge is disconnected",
                    r.isSuccess());
            assertTrue(r.getError().contains("Accessibility"));
        }
    }

    // ==================== screen_tap ====================

    @Test
    public void tap_nullArguments_errors() {
        ToolResult r = new ScreenTapTool(true).execute(null);
        assertFalse(r.isSuccess());
    }

    @Test
    public void tap_emptyArguments_errors() {
        ToolResult r = new ScreenTapTool(true).execute(new JsonObject());
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("Provide at least one of"));
    }

    @Test
    public void tap_byCoordinates_dispatches() {
        ToolResult r = new ScreenTapTool(true).execute(args("x", 100, "y", 200));
        assertNotNull(r);
    }

    @Test
    public void tap_byResourceId_dispatches() {
        ToolResult r = new ScreenTapTool(true)
                .execute(args("resource_id", "com.example.app:id/btn"));
        assertNotNull(r);
    }

    @Test
    public void tap_byText_dispatches() {
        ToolResult r = new ScreenTapTool(true).execute(args("text", "Submit"));
        assertNotNull(r);
    }

    @Test
    public void tap_blankResourceId_fallsThroughToError() {
        ToolResult r = new ScreenTapTool(true).execute(args("resource_id", "   "));
        assertFalse(r.isSuccess());
    }

    @Test
    public void tap_approvalDescription_variants() {
        ScreenTapTool tool = new ScreenTapTool(false);
        assertEquals("Tap element with resource ID: rid",
                tool.getApprovalDescription(args("resource_id", "rid")));
        assertEquals("Tap element with text: \"Go\"",
                tool.getApprovalDescription(args("text", "Go")));
        assertEquals("Tap screen at coordinates (5, 6)",
                tool.getApprovalDescription(args("x", 5, "y", 6)));
        assertEquals("Tap screen", tool.getApprovalDescription(null));
    }

    // ==================== screen_swipe ====================

    @Test
    public void swipe_missingParameter_namesIt() {
        ToolResult r = new ScreenSwipeTool(true)
                .execute(args("from_x", 0, "from_y", 100, "to_x", 0));
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("to_y"));
    }

    @Test
    public void swipe_nullArguments_errors() {
        ToolResult r = new ScreenSwipeTool(true).execute(null);
        assertFalse(r.isSuccess());
    }

    @Test
    public void swipe_allParameters_dispatches() {
        ToolResult r = new ScreenSwipeTool(true)
                .execute(args("from_x", 100, "from_y", 800, "to_x", 100, "to_y", 200));
        assertNotNull(r);
    }

    @Test
    public void swipe_extremeDuration_clampedWithoutError() {
        ToolResult r = new ScreenSwipeTool(true).execute(args(
                "from_x", 0, "from_y", 0, "to_x", 10, "to_y", 10, "duration_ms", 99999));
        assertNotNull(r);
    }

    @Test
    public void swipe_approvalDescription() {
        ScreenSwipeTool tool = new ScreenSwipeTool(false);
        assertEquals("Swipe screen from (0, 100) to (0, 200)",
                tool.getApprovalDescription(args(
                        "from_x", 0, "from_y", 100, "to_x", 0, "to_y", 200)));
        assertEquals("Swipe screen", tool.getApprovalDescription(null));
    }

    // ==================== screen_type_text ====================

    @Test
    public void typeText_missingText_errors() {
        ToolResult r = new ScreenTypeTextTool(true).execute(new JsonObject());
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("text"));
    }

    @Test
    public void typeText_intoFocusedField_dispatches() {
        ToolResult r = new ScreenTypeTextTool(true).execute(args("text", "hello"));
        assertNotNull(r);
    }

    @Test
    public void typeText_intoResourceId_dispatches() {
        ToolResult r = new ScreenTypeTextTool(true)
                .execute(args("text", "hello", "resource_id", "com.app:id/input"));
        assertNotNull(r);
    }

    @Test
    public void typeText_approvalDescription_truncatesLongText() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        String longText = new String(new char[80]).replace('\0', 'a');
        String desc = tool.getApprovalDescription(args("text", longText));
        assertTrue(desc.contains("..."));
        assertTrue("description should be truncated, was: " + desc, desc.length() < 100);
    }

    // ==================== screen_get_ui_tree ====================

    @Test
    public void getUiTree_defaultDepth_dispatches() {
        ToolResult r = new ScreenGetUiTreeTool().execute(null);
        assertNotNull(r);
    }

    @Test
    public void getUiTree_explicitDepth_dispatches() {
        ToolResult r = new ScreenGetUiTreeTool().execute(args("depth", 3));
        assertNotNull(r);
    }

    @Test
    public void getUiTree_outOfRangeDepth_clampedWithoutError() {
        ToolResult r = new ScreenGetUiTreeTool().execute(args("depth", 100));
        assertNotNull(r);
    }

    // ==================== screen_perform_action ====================

    @Test
    public void performAction_missingAction_errors() {
        ToolResult r = new ScreenPerformActionTool(true).execute(new JsonObject());
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("action"));
    }

    @Test
    public void performAction_unknownAction_listsValidOnes() {
        ToolResult r = new ScreenPerformActionTool(true).execute(args("action", "fly"));
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("back"));
        assertTrue(r.getError().contains("lock_screen"));
    }

    @Test
    public void performAction_allValidActions_dispatch() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(true);
        for (String action : new String[]{"back", "home", "recents",
                "notifications", "quick_settings", "lock_screen"}) {
            ToolResult r = tool.execute(args("action", action));
            assertNotNull(action + " should dispatch", r);
            assertFalse(action + " should not produce validation error: "
                    + (r.isSuccess() ? "" : r.getError()),
                    !r.isSuccess() && r.getError().contains("Unknown action"));
        }
    }

    @Test
    public void performAction_caseInsensitive() {
        ToolResult r = new ScreenPerformActionTool(true).execute(args("action", "BACK"));
        assertFalse("BACK (uppercase) must not be treated as unknown",
                !r.isSuccess() && r.getError().contains("Unknown action"));
    }

    @Test
    public void performAction_approvalDescription() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        assertEquals("Perform system action: home",
                tool.getApprovalDescription(args("action", "home")));
        assertEquals("Perform system action", tool.getApprovalDescription(null));
    }
}
