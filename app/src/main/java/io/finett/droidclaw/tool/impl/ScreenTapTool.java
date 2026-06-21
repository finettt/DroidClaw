package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Taps a point on the screen either by coordinates or by locating a node via
 * its resource ID or visible text.
 *
 * <p>When {@code trustMode} is {@code true} (set at registration time by
 * {@link io.finett.droidclaw.tool.ToolRegistry}), approval is skipped so the
 * agent can operate uninterrupted across apps. When {@code false}, the existing
 * {@link io.finett.droidclaw.agent.AgentLoop} approval dialog fires before execution.
 */
public class ScreenTapTool implements Tool {

    private static final String NAME = "screen_tap";

    private final boolean trustMode;
    private final ToolDefinition definition;

    public ScreenTapTool(boolean trustMode) {
        this.trustMode = trustMode;
        this.definition = buildDefinition();
    }

    private ToolDefinition buildDefinition() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
                .addInteger("x",
                        "X coordinate to tap (in screen pixels). Provide either x+y OR resource_id OR text.",
                        false)
                .addInteger("y",
                        "Y coordinate to tap (in screen pixels). Provide either x+y OR resource_id OR text.",
                        false)
                .addString("resource_id",
                        "Tap the first node with this Android resource ID (e.g. 'com.example.app:id/button'). "
                        + "Use screen_get_ui_tree to discover resource IDs.",
                        false)
                .addString("text",
                        "Tap the first node whose visible text exactly or partially matches this string. "
                        + "Use screen_get_ui_tree to discover text values.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Tap an element on the phone screen. Provide either:\n"
                + "  • x + y — tap by screen coordinates (use centerX/centerY from screen_get_ui_tree)\n"
                + "  • resource_id — tap by Android view resource ID\n"
                + "  • text — tap the first element whose text matches\n"
                + "Always call screen_get_ui_tree first to verify what is visible and get coordinates.",
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
        if (arguments == null) return "Tap screen";

        if (arguments.has("resource_id") && !arguments.get("resource_id").isJsonNull()) {
            String rid = arguments.get("resource_id").getAsString();
            return "Tap element with resource ID: " + rid;
        }
        if (arguments.has("text") && !arguments.get("text").isJsonNull()) {
            String t = arguments.get("text").getAsString();
            return "Tap element with text: \"" + t + "\"";
        }
        if (arguments.has("x") && arguments.has("y")) {
            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            return "Tap screen at coordinates (" + x + ", " + y + ")";
        }
        return "Tap screen";
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!AccessibilityBridge.isConnected()) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Enable DroidClaw in Settings → Accessibility.");
        }

        if (arguments == null) {
            return ToolResult.error("Missing arguments: provide x+y, resource_id, or text");
        }

        // Prefer coordinate tap
        boolean hasX = arguments.has("x") && !arguments.get("x").isJsonNull();
        boolean hasY = arguments.has("y") && !arguments.get("y").isJsonNull();
        if (hasX && hasY) {
            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            return AccessibilityBridge.execute(AccessibilityCommand.tapAt(x, y));
        }

        // Fall back to resource_id
        if (arguments.has("resource_id") && !arguments.get("resource_id").isJsonNull()) {
            String rid = arguments.get("resource_id").getAsString().trim();
            if (!rid.isEmpty()) {
                return AccessibilityBridge.execute(AccessibilityCommand.tapByResourceId(rid));
            }
        }

        // Fall back to text match
        if (arguments.has("text") && !arguments.get("text").isJsonNull()) {
            String text = arguments.get("text").getAsString().trim();
            if (!text.isEmpty()) {
                return AccessibilityBridge.execute(AccessibilityCommand.tapByText(text));
            }
        }

        return ToolResult.error("Provide at least one of: x+y, resource_id, or text");
    }
}