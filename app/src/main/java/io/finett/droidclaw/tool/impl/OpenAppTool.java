package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.google.gson.JsonObject;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool that opens an installed Android app by its package name.
 *
 * <p>Uses {@link PackageManager#getLaunchIntentForPackage(String)} to construct
 * and launch the app's launcher intent. Returns an error if the app is not
 * installed or has no launcher activity.
 */
public class OpenAppTool implements Tool {

    private static final String TOOL_NAME = "open_app";

    private final Context context;

    public OpenAppTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("package_name",
                        "The package name of the app to open (e.g., 'com.android.chrome'). "
                        + "Use list_apps to find available apps.",
                        true)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Open an installed Android app by its package name. "
                + "Launches the app's main/launcher activity. Use list_apps to find "
                + "the package name of installed apps. Requires user approval before execution.",
                parameters
        );
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        String packageName = arguments != null && arguments.has("package_name")
                ? arguments.get("package_name").getAsString()
                : "";
        return "Open app: " + packageName;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("package_name")
                || arguments.get("package_name").isJsonNull()) {
            return ToolResult.error("Missing required parameter: package_name");
        }

        String packageName = arguments.get("package_name").getAsString().trim();
        if (packageName.isEmpty()) {
            return ToolResult.error("package_name must not be empty");
        }

        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);

        if (launchIntent == null) {
            return ToolResult.error(
                    "Cannot open app: package '" + packageName + "' is not installed "
                    + "or has no launcher activity. Use list_apps to find available apps."
            );
        }

        // Add FLAG_ACTIVITY_NEW_TASK because startActivity() requires it when not
        // called from an Activity context (e.g., from a background service).
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(launchIntent);
        } catch (Exception e) {
            return ToolResult.error(
                    "Failed to open app: " + (e.getMessage() != null
                            ? e.getMessage()
                            : e.getClass().getSimpleName())
            );
        }

        return ToolResult.success(
                "App '" + packageName + "' opened successfully"
        );
    }
}
