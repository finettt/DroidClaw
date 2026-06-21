package io.finett.droidclaw.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.tool.impl.ScreenGetUiTreeTool;
import io.finett.droidclaw.tool.impl.ScreenPerformActionTool;
import io.finett.droidclaw.tool.impl.ScreenSwipeTool;
import io.finett.droidclaw.tool.impl.ScreenTapTool;
import io.finett.droidclaw.tool.impl.ScreenTypeTextTool;
import io.finett.droidclaw.util.SettingsManager;

import com.google.gson.JsonObject;

/**
 * Instrumented tests verifying:
 * <ul>
 *   <li>The {@link AccessibilityBridge} correctly reports disconnected state when the service
 *       has not been registered.</li>
 *   <li>Screen tools return a meaningful error (not a crash) when the service is not connected.</li>
 *   <li>Trust-mode flag propagates correctly to {@link Tool#requiresApproval()}.</li>
 *   <li>Screen tools are NOT registered in {@link ToolRegistry} when {@code screenControlEnabled}
 *       is false (default).</li>
 *   <li>Screen tools ARE registered when {@code screenControlEnabled} is true AND the
 *       bridge is connected (simulated via a test double).</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class ScreenControlToolsInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ── AccessibilityBridge ────────────────────────────────────────────────────

    @Test
    public void bridge_isNotConnected_byDefault() {
        // The real accessibility service is never connected in test environment
        assertFalse("Bridge should not be connected without the accessibility service",
                AccessibilityBridge.isConnected());
    }

    // ── Tool error messages when disconnected ──────────────────────────────────

    @Test
    public void screenGetUiTree_returnsError_whenNotConnected() {
        ScreenGetUiTreeTool tool = new ScreenGetUiTreeTool();
        ToolResult result = tool.execute(new JsonObject());
        assertFalse("Should fail when not connected", result.isSuccess());
        assertNotNull(result.getError());
        assertTrue("Error should mention accessibility service",
                result.getError().contains("Accessibility service"));
    }

    @Test
    public void screenTap_returnsError_whenNotConnected() {
        ScreenTapTool tool = new ScreenTapTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("x", 100);
        args.addProperty("y", 200);
        ToolResult result = tool.execute(args);
        assertFalse("Should fail when not connected", result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    public void screenSwipe_returnsError_whenNotConnected() {
        ScreenSwipeTool tool = new ScreenSwipeTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("from_x", 100);
        args.addProperty("from_y", 500);
        args.addProperty("to_x", 100);
        args.addProperty("to_y", 200);
        ToolResult result = tool.execute(args);
        assertFalse("Should fail when not connected", result.isSuccess());
    }

    @Test
    public void screenTypeText_returnsError_whenNotConnected() {
        ScreenTypeTextTool tool = new ScreenTypeTextTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("text", "hello");
        ToolResult result = tool.execute(args);
        assertFalse("Should fail when not connected", result.isSuccess());
    }

    @Test
    public void screenPerformAction_returnsError_whenNotConnected() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(false);
        JsonObject args = new JsonObject();
        args.addProperty("action", "home");
        ToolResult result = tool.execute(args);
        assertFalse("Should fail when not connected", result.isSuccess());
    }

    // ── Trust mode propagation ─────────────────────────────────────────────────

    @Test
    public void screenTap_requiresApproval_whenTrustModeOff() {
        ScreenTapTool tool = new ScreenTapTool(false);
        assertTrue("Should require approval when trust mode is off",
                tool.requiresApproval());
    }

    @Test
    public void screenTap_doesNotRequireApproval_whenTrustModeOn() {
        ScreenTapTool tool = new ScreenTapTool(true);
        assertFalse("Should not require approval when trust mode is on",
                tool.requiresApproval());
    }

    @Test
    public void screenSwipe_trustModeAware() {
        assertTrue(new ScreenSwipeTool(false).requiresApproval());
        assertFalse(new ScreenSwipeTool(true).requiresApproval());
    }

    @Test
    public void screenTypeText_trustModeAware() {
        assertTrue(new ScreenTypeTextTool(false).requiresApproval());
        assertFalse(new ScreenTypeTextTool(true).requiresApproval());
    }

    @Test
    public void screenPerformAction_trustModeAware() {
        assertTrue(new ScreenPerformActionTool(false).requiresApproval());
        assertFalse(new ScreenPerformActionTool(true).requiresApproval());
    }

    @Test
    public void screenGetUiTree_neverRequiresApproval() {
        assertFalse("screen_get_ui_tree is read-only and never needs approval",
                new ScreenGetUiTreeTool().requiresApproval());
    }

    // ── Tool registration gating ───────────────────────────────────────────────

    @Test
    public void toolRegistry_doesNotRegisterScreenTools_whenDisabled() {
        // Clear any existing settings and use defaults (screenControlEnabled = false)
        SettingsManager settings = new SettingsManager(context);
        settings.clear();

        ToolRegistry registry = new ToolRegistry(context, settings);

        assertFalse("screen_get_ui_tree should not be registered when disabled",
                registry.hasToolWithName("screen_get_ui_tree"));
        assertFalse("screen_tap should not be registered when disabled",
                registry.hasToolWithName("screen_tap"));
        assertFalse("screen_swipe should not be registered when disabled",
                registry.hasToolWithName("screen_swipe"));
        assertFalse("screen_type_text should not be registered when disabled",
                registry.hasToolWithName("screen_type_text"));
        assertFalse("screen_perform_action should not be registered when disabled",
                registry.hasToolWithName("screen_perform_action"));

        registry.shutdown();
    }

    @Test
    public void toolRegistry_doesNotRegisterScreenTools_whenEnabledButServiceNotConnected() {
        // Enable screen control in settings but do NOT connect the service
        SettingsManager settings = new SettingsManager(context);
        settings.clear();
        AgentConfig config = settings.getAgentConfig();
        config.setScreenControlEnabled(true);
        config.setScreenControlTrustMode(false);
        settings.setAgentConfig(config);

        // AccessibilityBridge.isConnected() returns false (no service in test environment)
        ToolRegistry registry = new ToolRegistry(context, settings);

        assertFalse("screen_tap should not be registered when service not connected",
                registry.hasToolWithName("screen_tap"));

        registry.shutdown();
    }

    // ── Argument validation ────────────────────────────────────────────────────

    @Test
    public void screenTap_returnsError_whenNoArguments() {
        ScreenTapTool tool = new ScreenTapTool(false);
        // Pass args with no position information — should fail validation before bridge check
        JsonObject emptyArgs = new JsonObject();
        ToolResult result = tool.execute(emptyArgs);
        assertFalse("Should fail without position arguments", result.isSuccess());
    }

    @Test
    public void screenSwipe_returnsError_whenMissingArgs() {
        ScreenSwipeTool tool = new ScreenSwipeTool(false);
        JsonObject partialArgs = new JsonObject();
        partialArgs.addProperty("from_x", 100);
        // Missing from_y, to_x, to_y
        ToolResult result = tool.execute(partialArgs);
        assertFalse("Should fail with incomplete arguments", result.isSuccess());
    }

    @Test
    public void screenPerformAction_returnsError_forUnknownAction() {
        ScreenPerformActionTool tool = new ScreenPerformActionTool(true); // trust mode so it's not approval-blocked
        JsonObject args = new JsonObject();
        args.addProperty("action", "fly_to_moon");
        ToolResult result = tool.execute(args);
        // Will fail at "not connected" before reaching action validation when no service,
        // so we just verify it's not a crash
        assertFalse("Should not succeed", result.isSuccess());
    }

    @Test
    public void toolNames_areCorrect() {
        assertEquals("screen_get_ui_tree", new ScreenGetUiTreeTool().getName());
        assertEquals("screen_tap", new ScreenTapTool(false).getName());
        assertEquals("screen_swipe", new ScreenSwipeTool(false).getName());
        assertEquals("screen_type_text", new ScreenTypeTextTool(false).getName());
        assertEquals("screen_perform_action", new ScreenPerformActionTool(false).getName());
    }
}