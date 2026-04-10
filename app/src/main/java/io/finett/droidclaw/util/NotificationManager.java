package io.finett.droidclaw.util;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.fragment.ZenResultFragment;

/**
 * Unified notification manager for all task-related notifications.
 * Handles notification channels, agent-generated content, and deep linking.
 */
public class NotificationManager {

    // Channel IDs
    public static final String CHANNEL_ID_HEARTBEAT_ALERTS = "droidclaw_heartbeat_alerts";
    public static final String CHANNEL_ID_TASK_RESULTS = "droidclaw_task_results";
    public static final String CHANNEL_ID_TASK_ERRORS = "droidclaw_task_errors";

    // Channel names
    private static final String CHANNEL_NAME_HEARTBEAT_ALERTS = "droidclaw_heartbeat_alerts";
    private static final String CHANNEL_NAME_TASK_RESULTS = "droidclaw_task_results";
    private static final String CHANNEL_NAME_TASK_ERRORS = "droidclaw_task_errors";

    // Channel descriptions
    private static final String CHANNEL_DESC_HEARTBEAT_ALERTS = "droidclaw_heartbeat_alerts_desc";
    private static final String CHANNEL_DESC_TASK_RESULTS = "droidclaw_task_results_desc";
    private static final String CHANNEL_DESC_TASK_ERRORS = "droidclaw_task_errors_desc";

    // Notification IDs (unique for each notification type)
    private static final int NOTIFICATION_ID_HEARTBEAT = 1001;
    private static final int NOTIFICATION_ID_ERROR = 1002;
    private static final int NOTIFICATION_ID_CRON_JOB = 1003;
    private static final int NOTIFICATION_ID_MANUAL_TASK = 1004;

    // PendingIntent request codes (must be unique for each pending intent)
    private static final int PENDING_INTENT_HEARTBEAT = 2001;
    private static final int PENDING_INTENT_ERROR = 2002;
    private static final int PENDING_INTENT_CRON_JOB = 2003;
    private static final int PENDING_INTENT_MANUAL_TASK = 2004;

    private final Context context;
    private final android.app.NotificationManager systemNotificationManager;

    /**
     * Represents notification content with title and summary.
     */
    public static class NotificationContent {
        private final String title;
        private final String summary;
        private final String fullContent;

        public NotificationContent(String title, String summary, String fullContent) {
            this.title = title;
            this.summary = summary;
            this.fullContent = fullContent;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public String getFullContent() {
            return fullContent;
        }
    }

    public NotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemNotificationManager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    /**
     * Create all notification channels (required for Android 8.0+).
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Load channel names and descriptions from string resources
            String heartbeatChannelName = context.getString(R.string.notification_channel_heartbeat_alerts);
            String heartbeatChannelDesc = context.getString(R.string.notification_channel_heartbeat_alerts_desc);
            String taskResultsChannelName = context.getString(R.string.notification_channel_task_results);
            String taskResultsChannelDesc = context.getString(R.string.notification_channel_task_results_desc);
            String errorsChannelName = context.getString(R.string.notification_channel_task_errors);
            String errorsChannelDesc = context.getString(R.string.notification_channel_task_errors_desc);

            // Heartbeat Alerts channel - High priority
            NotificationChannel heartbeatChannel = new NotificationChannel(
                    CHANNEL_ID_HEARTBEAT_ALERTS,
                    heartbeatChannelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            heartbeatChannel.setDescription(heartbeatChannelDesc);
            heartbeatChannel.enableVibration(true);
            systemNotificationManager.createNotificationChannel(heartbeatChannel);

            // Task Results channel - Default priority
            NotificationChannel taskResultsChannel = new NotificationChannel(
                    CHANNEL_ID_TASK_RESULTS,
                    taskResultsChannelName,
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            taskResultsChannel.setDescription(taskResultsChannelDesc);
            taskResultsChannel.enableVibration(false);
            taskResultsChannel.setSound(null, null);
            systemNotificationManager.createNotificationChannel(taskResultsChannel);

            // Task Errors channel - High priority
            NotificationChannel errorsChannel = new NotificationChannel(
                    CHANNEL_ID_TASK_ERRORS,
                    errorsChannelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            errorsChannel.setDescription(errorsChannelDesc);
            errorsChannel.enableVibration(true);
            systemNotificationManager.createNotificationChannel(errorsChannel);
        }
    }

    /**
     * Send a notification for a task result.
     * Routes to the appropriate channel based on task type and success status.
     *
     * @param result The task result to notify about
     */
    public void sendTaskNotification(TaskResult result) {
        if (result == null) {
            return;
        }

        NotificationContent content = generateNotificationContent(result);

        switch (result.getType()) {
            case TaskResult.TYPE_HEARTBEAT:
                showHeartbeatNotification(content, result);
                break;
            case TaskResult.TYPE_CRON_JOB:
                showCronJobNotification(content, result);
                break;
            case TaskResult.TYPE_MANUAL:
                showManualTaskNotification(content, result);
                break;
            default:
                showGenericTaskNotification(content, result);
                break;
        }
    }

    /**
     * Generate notification content from a task result.
     * Uses agent-generated title/summary if available, otherwise generates generic content.
     *
     * @param result The task result
     * @return NotificationContent with title, summary, and full content
     */
    public NotificationContent generateNotificationContent(TaskResult result) {
        String title;
        String summary;
        String fullContent = result.getContent();

        // Check if agent-generated content is available in metadata
        String agentTitle = result.getMetadataValue("notification_title");
        String agentSummary = result.getMetadataValue("notification_summary");

        if (agentTitle != null && !agentTitle.isEmpty()) {
            title = agentTitle;
        } else {
            title = generateDefaultTitle(result);
        }

        if (agentSummary != null && !agentSummary.isEmpty()) {
            summary = agentSummary;
        } else {
            summary = generateDefaultSummary(result);
        }

        // Truncate full content for notification display if needed
        if (fullContent != null && fullContent.length() > 1000) {
            fullContent = fullContent.substring(0, 1000) + "...";
        }

        return new NotificationContent(title, summary, fullContent);
    }

    /**
     * Show a heartbeat alert notification (high priority).
     */
    private void showHeartbeatNotification(NotificationContent content, TaskResult result) {
        Intent intent = createDeepLinkIntent(result);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_HEARTBEAT,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_HEARTBEAT_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_heartbeat)
                .setContentTitle(content.getTitle())
                .setContentText(content.getSummary())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.getFullContent()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "View", pendingIntent);

        systemNotificationManager.notify(NOTIFICATION_ID_HEARTBEAT, builder.build());
    }

    /**
     * Show a cron job result notification (default priority).
     */
    private void showCronJobNotification(NotificationContent content, TaskResult result) {
        Intent intent = createDeepLinkIntent(result);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_CRON_JOB,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASK_RESULTS)
                .setSmallIcon(R.drawable.ic_notification_cron)
                .setContentTitle(content.getTitle())
                .setContentText(content.getSummary())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.getFullContent()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "View", pendingIntent);

        systemNotificationManager.notify(NOTIFICATION_ID_CRON_JOB, builder.build());
    }

    /**
     * Show a manual task result notification (default priority).
     */
    private void showManualTaskNotification(NotificationContent content, TaskResult result) {
        Intent intent = createDeepLinkIntent(result);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_MANUAL_TASK,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASK_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(content.getTitle())
                .setContentText(content.getSummary())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.getFullContent()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "View", pendingIntent);

        systemNotificationManager.notify(NOTIFICATION_ID_MANUAL_TASK, builder.build());
    }

    /**
     * Show a generic task notification (fallback).
     */
    private void showGenericTaskNotification(NotificationContent content, TaskResult result) {
        Intent intent = createDeepLinkIntent(result);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_MANUAL_TASK,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASK_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(content.getTitle())
                .setContentText(content.getSummary())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.getFullContent()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "View", pendingIntent);

        systemNotificationManager.notify(NOTIFICATION_ID_MANUAL_TASK, builder.build());
    }

    /**
     * Show an error notification for failed tasks (high priority).
     */
    public void showErrorNotification(String title, String errorMessage) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_ERROR,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASK_ERRORS)
                .setSmallIcon(R.drawable.ic_notification_error)
                .setContentTitle(title)
                .setContentText(truncate(errorMessage, 100))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(errorMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        systemNotificationManager.notify(NOTIFICATION_ID_ERROR, builder.build());
    }

    /**
     * Create an intent with deep link to ZenResultFragment.
     * Passes the TaskResult as a serializable extra.
     */
    private Intent createDeepLinkIntent(TaskResult result) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("deep_link_destination", "zen_result");
        intent.putExtra("task_result", result);
        return intent;
    }

    /**
     * Cancel all notifications.
     */
    public void cancelAllNotifications() {
        systemNotificationManager.cancel(NOTIFICATION_ID_HEARTBEAT);
        systemNotificationManager.cancel(NOTIFICATION_ID_ERROR);
        systemNotificationManager.cancel(NOTIFICATION_ID_CRON_JOB);
        systemNotificationManager.cancel(NOTIFICATION_ID_MANUAL_TASK);
    }

    /**
     * Cancel a specific notification by type.
     */
    public void cancelNotification(int taskType) {
        switch (taskType) {
            case TaskResult.TYPE_HEARTBEAT:
                systemNotificationManager.cancel(NOTIFICATION_ID_HEARTBEAT);
                break;
            case TaskResult.TYPE_CRON_JOB:
                systemNotificationManager.cancel(NOTIFICATION_ID_CRON_JOB);
                break;
            case TaskResult.TYPE_MANUAL:
                systemNotificationManager.cancel(NOTIFICATION_ID_MANUAL_TASK);
                break;
        }
    }

    /**
     * Generate a default title for a task result.
     */
    private String generateDefaultTitle(TaskResult result) {
        boolean isSuccess = result.isSuccess();

        switch (result.getType()) {
            case TaskResult.TYPE_HEARTBEAT:
                return isSuccess ? "Heartbeat Check - OK" : "Heartbeat Alert - Attention Needed";
            case TaskResult.TYPE_CRON_JOB:
                return isSuccess ? "Cron Job Completed" : "Cron Job Failed";
            case TaskResult.TYPE_MANUAL:
                return isSuccess ? "Task Completed" : "Task Failed";
            default:
                return isSuccess ? "Task Completed" : "Task Failed";
        }
    }

    /**
     * Generate a default summary for a task result.
     */
    private String generateDefaultSummary(TaskResult result) {
        String content = result.getContent();
        if (content == null || content.isEmpty()) {
            return "Task completed with no output";
        }

        // Extract first sentence or first 200 chars as summary
        String summary = content;
        int sentenceEnd = summary.indexOf('.');
        if (sentenceEnd > 0 && sentenceEnd < 200) {
            summary = summary.substring(0, sentenceEnd + 1);
        } else if (summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }

        return summary;
    }

    /**
     * Truncate text to maximum length.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
