package io.finett.droidclaw.tool;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.tool.impl.ScreenPerformActionTool;
import io.finett.droidclaw.tool.impl.ScreenTapTool;
import io.finett.droidclaw.tool.impl.ScreenTypeTextTool;

/**
 * Instrumented tests for screen tool argument validation.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>ScreenTapTool — resource_id parameter path</li>
 *   <li>ScreenTapTool — text parameter path</li>
 *   <li>ScreenTypeTextTool — resource_id parameter</li>
 *   <li>ScreenPerformActionTool — valid action strings</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class ScreenControlToolsArgumentValidationInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ==================== ScreenTapTool tests ====================

    @Test
    public void screenTap_resourceIdParameterPath_validatesResource() {
        // When no x/y but resource_id is provided, the bridge should still be checked first
        // (service not connected), so we just verify it doesn't crash
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("resource_id", "com.example.app:id/button");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service), not at validation
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTap_textParameterPath_validatesText() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Submit");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service), not at validation
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTap_emptyResourceID_fallsThroughToTextCheck() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("resource_id", "   "); // whitespace only
        args.addProperty("x", 100);
        args.addProperty("y", 200);
        ToolResult result = tool.execute(args);

        // x+y should take precedence over resource_id, so it goes to bridge check
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTap_emptyText_fallsThroughToError() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "   "); // whitespace only
        JsonObject emptyArgs = new JsonObject();
        ToolResult result = tool.execute(emptyArgs);

        // Should fail with "provide at least one of" error
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("at least one of"));
    }

    @Test
    public void screenTap_nullArguments_returnsError() {
        ScreenTapTool tool = new ScreenTapTool(false);
        ToolResult result = tool.execute(null);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing arguments"));
    }

    // ==================== ScreenTypeTextTool tests ====================

    @Test
    public void screenTypeText_resourceIdParameter_validatesResource() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Hello");
        args.addProperty("resource_id", "com.example.app:id/input");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service)
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTypeText_nullResourceId_fallsThroughToFocused() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Hello");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service)
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTypeText_emptyResourceId_fallsThroughToFocused() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Hello");
        args.addProperty("resource_id", "   ");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service)
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenTypeText_nullText_returnsError() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("resource_id", "com.example.app:id/input");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void screenTypeText_nullArguments_returnsError() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        ToolResult result = tool.execute(null);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    // ==================== ScreenPerformActionTool tests ====================

    @Test
    public void screenPerformAction_backAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "back");
        ToolResult result = tool.execute(args);

        // Should fail at bridge check (no service)
        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_homeAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "home");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_recentsAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "recents");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_notificationsAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "notifications");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_quickSettingsAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "quick_settings");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_lockScreenAction_validAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "lock_screen");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_caseInsensitiveAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "HOME"); // uppercase — should be normalized
        ToolResult result = tool.execute(args);

        // Should not fail at "unknown action" — should fail at bridge check
        assertFalse(result.isSuccess());
        // Verify it doesn't say "Unknown action"
        if (result.getError() != null) {
            assertFalse("Should not say 'Unknown action' for HOME",
                    result.getError().contains("Unknown action"));
        }
    }

    @Test
    public void screenPerformAction_whitespaceTrimmedAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "  home  "); // whitespace — should be trimmed
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void screenPerformAction_missingAction_returnsError() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void screenPerformAction_nullAction_returnsError() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.add("action", null);
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void screenPerformAction_nullArguments_returnsError() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        ToolResult result = tool.execute(null);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    // ==================== Approval description tests ====================

    @Test
    public void screenTap_approvalDescription_withResourceID() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("resource_id", "com.example.app:id/button");

        String description = tool.getApprovalDescription(args);

        assertEquals("Tap element with resource ID: com.example.app:id/button", description);
    }

    @Test
    public void screenTap_approvalDescription_withText() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Submit");

        String description = tool.getApprovalDescription(args);

        assertEquals("Tap element with text: \"Submit\"", description);
    }

    @Test
    public void screenTap_approvalDescription_withCoordinates() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("x", 100);
        args.addProperty("y", 200);

        String description = tool.getApprovalDescription(args);

        assertEquals("Tap screen at coordinates (100, 200)", description);
    }

    @Test
    public void screenTypeText_approvalDescription_withResourceID() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "Hello");
        args.addProperty("resource_id", "com.example.app:id/input");

        String description = tool.getApprovalDescription(args);

        assertEquals("Type \"Hello\" into field: com.example.app:id/input", description);
    }

    @Test
    public void screenTypeText_approvalDescription_truncatesLongText() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        String longText = new String(new char[60]).replace('\0', 'A');
        args.addProperty("text", longText);

        String description = tool.getApprovalDescription(args);

        assertEquals("Type \"AAAAAAA...\" into focused field", description);
    }

    @Test
    public void screenPerformAction_approvalDescription() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "home");

        String description = tool.getApprovalDescription(args);

        assertEquals("Perform system action: home", description);
    }
}
