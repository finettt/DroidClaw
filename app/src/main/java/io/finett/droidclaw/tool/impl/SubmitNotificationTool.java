package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

public class SubmitNotificationTool implements Tool {

    private static final String TAG = "SubmitNotificationTool";
    private static final String NAME = "submit_notification";

    private final ToolDefinition definition;
    private final Context context;

    // Last submitted notification, read by workers after agent loop completes
    private static volatile JsonObject lastNotification;

    public SubmitNotificationTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        ParametersBuilder builder = new ParametersBuilder()
                .addString("title", "Short title for the notification (e.g., 'Daily Summary Complete', 'Issues Detected')", true)
                .addString("summary", "Brief 1-2 sentence summary of what happened or what needs attention", true)
                .addString("status", "Task status: 'success', 'warning', or 'error'", false);

        return new ToolDefinition(
                NAME,
                "Submit a structured notification at the end of a background task. Call this when the task " +
                        "completes to provide the user with a clear title and summary. " +
                        "Status should be 'success' if everything went well, 'warning' if there are minor concerns, " +
                        "or 'error' if something failed.",
                builder.build()
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
    public ToolResult execute(JsonObject arguments) {
        try {
            if (arguments == null || !arguments.has("title") || !arguments.has("summary")) {
                return ToolResult.error("Missing required parameters: title and summary are required");
            }

            String title = arguments.get("title").getAsString().trim();
            String summary = arguments.get("summary").getAsString().trim();
            String status = "success";

            if (arguments.has("status")) {
                status = arguments.get("status").getAsString().trim().toLowerCase();
                if (!status.equals("success") && !status.equals("warning") && !status.equals("error")) {
                    status = "success";
                }
            }

            if (title.isEmpty() || summary.isEmpty()) {
                return ToolResult.error("Title and summary cannot be empty");
            }

            JsonObject notification = new JsonObject();
            notification.addProperty("title", title);
            notification.addProperty("summary", summary);
            notification.addProperty("status", status);
            lastNotification = notification;

            Log.d(TAG, "Notification submitted: " + title);

            JsonObject result = new JsonObject();
            result.addProperty("status", "recorded");
            result.addProperty("title", title);
            result.addProperty("message", "Notification recorded successfully");

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to submit notification", e);
            return ToolResult.error("Failed to submit notification: " + e.getMessage());
        }
    }

    public static JsonObject getLastNotification() {
        return lastNotification;
    }

    /** Should be called after the notification is consumed to prevent stale data. */
    public static void clearLastNotification() {
        lastNotification = null;
    }
}
