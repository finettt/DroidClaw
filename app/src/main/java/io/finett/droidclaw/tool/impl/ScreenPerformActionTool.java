package io.finett.droidclaw.tool.impl;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;

import com.google.gson.JsonObject;

import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.accessibility.AccessibilityCommand;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Performs Android system-level global actions: back, home, recents, notifications,
 * quick settings, and lock screen.
 *
 * <p>Trust-mode aware: when {@code trustMode} is true, approval is bypassed so the agent
 * can navigate across apps uninterrupted.
 */
public class ScreenPerformActionTool implements Tool {

    private static final String NAME = "screen_perform_action";

    private final boolean trustMode;
    private final ToolDefinition definition;

    public ScreenPerformActionTool(boolean trustMode) {
        this.trustMode = trustMode;
        this.definition = buildDefinition();
    }

    private ToolDefinition buildDefinition() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
                .addString("action",
                        "The global action to perform. One of:\n"
                        + "  • back — press the Back button\n"
                        + "  • home — press the Home button\n"
                        + "  • recents — open the Recents/Overview screen\n"
                        + "  • notifications — pull down the notification shade\n"
                        + "  • quick_settings — open Quick Settings panel\n"
                        + "  • lock_screen — lock the device screen",
                        true)
                .build();

        return new ToolDefinition(
                NAME,
                "Perform a system-level navigation action on the Android device. "
                + "Use 'back' to go back, 'home' to go to the home screen, "
                + "'recents' to see recent apps, 'notifications' to open the notification shade, "
                + "'quick_settings' for the quick-settings tiles, or 'lock_screen' to lock the device.",
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
        if (arguments == null) return "Perform system action";
        String action = arguments.has("action") && !arguments.get("action").isJsonNull()
                ? arguments.get("action").getAsString() : "unknown";
        return "Perform system action: " + action;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!AccessibilityBridge.isConnected()) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Enable DroidClaw in Settings → Accessibility.");
        }

        if (arguments == null || !arguments.has("action") || arguments.get("action").isJsonNull()) {
            return ToolResult.error("Missing required parameter: action");
        }

        String action = arguments.get("action").getAsString().trim().toLowerCase();
        int globalActionId;

        switch (action) {
            case "back":
                globalActionId = AccessibilityService.GLOBAL_ACTION_BACK;
                break;
            case "home":
                globalActionId = AccessibilityService.GLOBAL_ACTION_HOME;
                break;
            case "recents":
                globalActionId = AccessibilityService.GLOBAL_ACTION_RECENTS;
                break;
            case "notifications":
                globalActionId = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
                break;
            case "quick_settings":
                globalActionId = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;
                break;
            case "lock_screen":
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    return ToolResult.error("lock_screen requires Android 9 (API 28) or newer; "
                            + "this device runs API " + Build.VERSION.SDK_INT);
                }
                globalActionId = AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN;
                break;
            default:
                return ToolResult.error("Unknown action: \"" + action + "\". "
                        + "Valid actions: back, home, recents, notifications, quick_settings, lock_screen");
        }

        AccessibilityCommand command = AccessibilityCommand.globalAction(globalActionId);
        return AccessibilityBridge.execute(command);
    }
}