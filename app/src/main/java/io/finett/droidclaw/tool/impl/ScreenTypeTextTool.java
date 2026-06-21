package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Types text into an editable field using Android's {@code ACTION_SET_TEXT} accessibility action.
 *
 * <p>Optionally targets a specific field by resource ID. When no resource ID is provided,
 * the currently focused editable field is used — call {@code screen_tap} on the field first.
 * Trust-mode aware: when {@code trustMode} is true, approval is bypassed.
 */
public class ScreenTypeTextTool implements Tool {

    private static final String NAME = "screen_type_text";

    private final boolean trustMode;
    private final ToolDefinition definition;

    public ScreenTypeTextTool(boolean trustMode) {
        this.trustMode = trustMode;
        this.definition = buildDefinition();
    }

    private ToolDefinition buildDefinition() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
                .addString("text",
                        "The text to type into the field. Replaces any existing content.",
                        true)
                .addString("resource_id",
                        "Optional: the resource ID of the target text field "
                        + "(e.g. 'com.example.app:id/search_input'). "
                        + "If omitted, the currently focused editable field is used — "
                        + "tap the field first with screen_tap.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Type text into an editable field on the screen using the accessibility API. "
                + "This replaces the field's current content with the provided text. "
                + "If resource_id is not specified, the currently focused editable field is used. "
                + "Tip: use screen_tap to focus a field before calling this tool without resource_id.",
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
        if (arguments == null) return "Type text into field";
        String text = arguments.has("text") && !arguments.get("text").isJsonNull()
                ? arguments.get("text").getAsString() : "";
        String rid = arguments.has("resource_id") && !arguments.get("resource_id").isJsonNull()
                ? arguments.get("resource_id").getAsString() : null;

        // Truncate long text for display
        String displayText = text.length() > 50 ? text.substring(0, 47) + "..." : text;

        if (rid != null && !rid.isEmpty()) {
            return "Type \"" + displayText + "\" into field: " + rid;
        }
        return "Type \"" + displayText + "\" into focused field";
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!AccessibilityBridge.isConnected()) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Enable DroidClaw in Settings → Accessibility.");
        }

        if (arguments == null || !arguments.has("text") || arguments.get("text").isJsonNull()) {
            return ToolResult.error("Missing required parameter: text");
        }

        String text = arguments.get("text").getAsString();

        String resourceId = null;
        if (arguments.has("resource_id") && !arguments.get("resource_id").isJsonNull()) {
            String rid = arguments.get("resource_id").getAsString().trim();
            if (!rid.isEmpty()) {
                resourceId = rid;
            }
        }

        AccessibilityCommand command = resourceId != null
                ? AccessibilityCommand.setTextOnNode(resourceId, text)
                : AccessibilityCommand.setTextOnFocused(text);

        return AccessibilityBridge.execute(command);
    }
}