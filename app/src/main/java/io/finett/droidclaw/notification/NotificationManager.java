package io.finett.droidclaw.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskResult;

/**
 * Manages Android push notifications for background tasks (Heartbeat & Cron Jobs).
 *
 * Responsibilities:
 * - Create notification channels (heartbeat, cronjob)
 * - Send heartbeat alert notifications
 * - Send cron job result notifications
 * - Handle notification tap intents (open ZenResultFragment)
 * - Request runtime permission for notifications (Android 13+)
 */
public class NotificationManager {
    private static final String TAG = "NotificationManager";

    // Notification channels
    public static final String CHANNEL_HEARTBEAT = "heartbeat_channel";
    public static final String CHANNEL_CRONJOB = "cronjob_channel";

    // Notification IDs
    public static final int NOTIFICATION_HEARTBEAT = 1001;
    public static final int NOTIFICATION_CRONJOB_BASE = 2000;

    // Permission
    public static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 100;

    private final Context context;
    private final android.app.NotificationManager systemNotificationManager;

    public NotificationManager(Context context) {
        this.context = context;
        this.systemNotificationManager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannels();
    }

    /**
     * Create notification channels for heartbeat and cron jobs.
     * Must be called before posting notifications.
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Heartbeat channel
            android.app.NotificationChannel heartbeatChannel = new android.app.NotificationChannel(
                    CHANNEL_HEARTBEAT,
                    "Heartbeat Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            heartbeatChannel.setDescription("Notifications for periodic system checks and alerts");
            heartbeatChannel.enableVibration(true);
            systemNotificationManager.createNotificationChannel(heartbeatChannel);

            // Cron job channel
            android.app.NotificationChannel cronjobChannel = new android.app.NotificationChannel(
                    CHANNEL_CRONJOB,
                    "Cron Job Results",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            cronjobChannel.setDescription("Notifications for scheduled task results");
            cronjobChannel.enableVibration(false);
            systemNotificationManager.createNotificationChannel(cronjobChannel);
        }
    }

    /**
     * Check if notification permission is granted.
     * Returns true on Android < 13 (no permission needed).
     */
    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permission not required on older versions
    }

    /**
     * Request notification permission (Android 13+).
     * Should be called from Activity.
     */
    public void requestPermission(androidx.activity.ComponentActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_NOTIFICATION_PERMISSION
            );
        }
    }

    /**
     * Send a heartbeat alert notification.
     *
     * @param result The TaskResult containing alert information
     */
    public void sendHeartbeatAlert(TaskResult result) {
        if (!hasPermission()) {
            android.util.Log.w(TAG, "Cannot send heartbeat alert - notification permission not granted");
            return;
        }

        String title = result.getNotificationTitle() != null ? result.getNotificationTitle() : "Heartbeat Alert";
        String summary = result.getNotificationSummary() != null ? result.getNotificationSummary() : "Alert from system check";

        Intent intent = createZenResultIntent(result.getId());
        int requestCode = result.getId().hashCode();
        PendingIntent pendingIntent = createPendingIntent(intent, requestCode);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_HEARTBEAT)
                .setSmallIcon(R.drawable.ic_notification_heartbeat)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        systemNotificationManager.notify(NOTIFICATION_HEARTBEAT, builder.build());

        android.util.Log.d(TAG, "Heartbeat alert notification sent: " + result.getId());
    }

    /**
     * Send a cron job result notification.
     *
     * @param result The TaskResult containing cron job result
     * @param cronJobId The ID of the parent cron job (for notification ID uniqueness)
     */
    public void sendCronJobResult(TaskResult result, String cronJobId) {
        if (!hasPermission()) {
            android.util.Log.w(TAG, "Cannot send cron job result - notification permission not granted");
            return;
        }

        String title = result.getNotificationTitle() != null ? result.getNotificationTitle() : "Task Complete";
        String summary = result.getNotificationSummary() != null ? result.getNotificationSummary() : "Scheduled task completed";

        Intent intent = createZenResultIntent(result.getId());
        // Use unique notification ID per cron job execution
        int notificationId = NOTIFICATION_CRONJOB_BASE + (cronJobId.hashCode() % 1000);

        PendingIntent pendingIntent = createPendingIntent(intent, result.getId().hashCode());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CRONJOB)
                .setSmallIcon(R.drawable.ic_notification_cronjob)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        systemNotificationManager.notify(notificationId, builder.build());

        android.util.Log.d(TAG, "Cron job result notification sent: " + result.getId());
    }

    /**
     * Create an intent to open ZenResultFragment with the given result.
     */
    private Intent createZenResultIntent(String resultId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("ACTION_VIEW_RESULT");
        intent.putExtra("EXTRA_RESULT_ID", resultId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Create a PendingIntent for the notification tap action.
     */
    private PendingIntent createPendingIntent(Intent intent, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                flags
        );
    }

    /**
     * Cancel a specific notification.
     */
    public void cancelNotification(int notificationId) {
        systemNotificationManager.cancel(notificationId);
    }

    /**
     * Cancel all notifications from this app.
     */
    public void cancelAllNotifications() {
        systemNotificationManager.cancelAll();
    }
}
