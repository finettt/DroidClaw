package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Read-only tool that returns a compact JSON representation of the currently visible UI.
 *
 * <p>The agent can use this to understand what is on screen before deciding which element
 * to interact with. Does NOT require approval (read-only, no side effects).
 */
public class ScreenGetUiTreeTool implements Tool {

    private static final String NAME = "screen_get_ui_tree";

    private final ToolDefinition definition;

    public ScreenGetUiTreeTool() {
        this.definition = buildDefinition();
    }

    private ToolDefinition buildDefinition() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
                .addInteger("depth",
                        "Maximum depth of the UI tree to retrieve (1–10). Default is 6. "
                        + "Lower values return faster but may miss deeply nested elements.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Read the current screen's UI hierarchy as JSON. Returns all visible elements "
                + "with their text, resource IDs, content descriptions, clickability flags, "
                + "and screen coordinates (bounds with centerX/centerY). Use this before "
                + "calling any screen interaction tool so you know what is visible and where "
                + "each element is located. Works on any app currently in the foreground.",
                params
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public boolean requiresApproval() {
        return false; // Read-only
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!AccessibilityBridge.isConnected()) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Enable DroidClaw in Settings → Accessibility and turn on "
                    + "Screen Control in Agent Settings.");
        }

        int depth = 6;
        if (arguments != null && arguments.has("depth")
                && !arguments.get("depth").isJsonNull()) {
            try {
                depth = arguments.get("depth").getAsInt();
                depth = Math.max(1, Math.min(10, depth));
            } catch (Exception e) {
                // Use default
            }
        }

        AccessibilityCommand command = AccessibilityCommand.getUiTree(depth);
        return AccessibilityBridge.execute(command);
    }
}