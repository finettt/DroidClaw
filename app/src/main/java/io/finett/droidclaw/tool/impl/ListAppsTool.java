package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool that lists all installed Android apps.
 *
 * <p>Uses {@link PackageManager#getInstalledApplications(int)} to enumerate
 * installed applications. Returns name, package name, and a flag indicating
 * whether the app is a system app.
 *
 * <p>Optional filter: {@code show_system} — if false, excludes system apps
 * (default: true, includes all apps).
 */
public class ListAppsTool implements Tool {

    private static final String TOOL_NAME = "list_apps";

    private final Context context;

    public ListAppsTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addBoolean("show_system",
                        "Include system apps in the results. Default: true (show all apps). "
                        + "Set to false to show only user-installed apps.",
                        false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "List all installed Android apps on the device. Returns each app's "
                + "name, package name, and whether it is a system app. Optionally "
                + "filter out system apps with show_system=false.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        PackageManager pm = context.getPackageManager();
        boolean showSystem = true;

        if (arguments != null && arguments.has("show_system")
                && !arguments.get("show_system").isJsonNull()) {
            showSystem = arguments.get("show_system").getAsBoolean();
        }

        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);

        JsonArray array = new JsonArray();
        int userApps = 0;
        int systemApps = 0;

        for (ApplicationInfo app : installedApps) {
            // Skip if it's a system app and we're not showing system apps
            if (!showSystem && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemApps++;
                continue;
            }

            JsonObject obj = new JsonObject();
            try {
                CharSequence name = app.loadLabel(pm);
                obj.addProperty("name", name != null ? name.toString() : app.packageName);
            } catch (Exception e) {
                // Fall back to package name if label loading fails
                obj.addProperty("name", app.packageName);
            }
            obj.addProperty("package_name", app.packageName);
            obj.addProperty("is_system_app", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            array.add(obj);

            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemApps++;
            } else {
                userApps++;
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("total", array.size());
        result.addProperty("user_apps", userApps);
        result.addProperty("system_apps", systemApps);
        result.addProperty("show_system", showSystem);
        result.add("apps", array);

        return ToolResult.success(result);
    }
}
