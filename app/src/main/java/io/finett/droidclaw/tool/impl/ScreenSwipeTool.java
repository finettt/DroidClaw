package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Performs a swipe gesture between two screen coordinates.
 *
 * <p>Useful for scrolling lists, swiping between pages, or dismissing notifications.
 * Trust-mode aware: when {@code trustMode} is true, approval is bypassed.
 */
public class ScreenSwipeTool implements Tool {

    private static final String NAME = "screen_swipe";

    private final boolean trustMode;
    private final ToolDefinition definition;

    public ScreenSwipeTool(boolean trustMode) {
        this.trustMode = trustMode;
        this.definition = buildDefinition();
    }

    private ToolDefinition buildDefinition() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
                .addInteger("from_x", "Starting X coordinate of the swipe (in screen pixels)", true)
                .addInteger("from_y", "Starting Y coordinate of the swipe (in screen pixels)", true)
                .addInteger("to_x",   "Ending X coordinate of the swipe (in screen pixels)", true)
                .addInteger("to_y",   "Ending Y coordinate of the swipe (in screen pixels)", true)
                .addInteger("duration_ms",
                        "Duration of the swipe gesture in milliseconds (50–3000). Default is 300. "
                        + "Slower swipes (e.g. 800ms) work better for scrolling; faster for flings.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Perform a swipe gesture on the screen between two coordinates. "
                + "Use this for scrolling (swipe up to scroll down), pulling down the notification "
                + "shade, swiping between pages, or dismissing items. "
                + "Get valid coordinates from screen_get_ui_tree bounds first.",
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
        return !trustMode;
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        if (arguments == null) return "Swipe screen";
        try {
            int fx = arguments.get("from_x").getAsInt();
            int fy = arguments.get("from_y").getAsInt();
            int tx = arguments.get("to_x").getAsInt();
            int ty = arguments.get("to_y").getAsInt();
            return "Swipe screen from (" + fx + ", " + fy + ") to (" + tx + ", " + ty + ")";
        } catch (Exception e) {
            return "Swipe screen";
        }
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!AccessibilityBridge.isConnected()) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Enable DroidClaw in Settings → Accessibility.");
        }

        if (arguments == null) {
            return ToolResult.error("Missing required arguments: from_x, from_y, to_x, to_y");
        }

        String[] required = {"from_x", "from_y", "to_x", "to_y"};
        for (String key : required) {
            if (!arguments.has(key) || arguments.get(key).isJsonNull()) {
                return ToolResult.error("Missing required parameter: " + key);
            }
        }

        try {
            int fromX = arguments.get("from_x").getAsInt();
            int fromY = arguments.get("from_y").getAsInt();
            int toX   = arguments.get("to_x").getAsInt();
            int toY   = arguments.get("to_y").getAsInt();

            int duration = 300;
            if (arguments.has("duration_ms") && !arguments.get("duration_ms").isJsonNull()) {
                duration = arguments.get("duration_ms").getAsInt();
                duration = Math.max(50, Math.min(3000, duration));
            }

            AccessibilityCommand command = AccessibilityCommand.swipe(fromX, fromY, toX, toY, duration);
            return AccessibilityBridge.execute(command);
        } catch (Exception e) {
            return ToolResult.error("Invalid argument values: " + e.getMessage());
        }
    }
}