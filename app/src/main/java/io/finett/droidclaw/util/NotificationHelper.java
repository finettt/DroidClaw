package io.finett.droidclaw.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;

/**
 * Helper class for managing Android system notifications.
 * Handles notification channels and shows notifications for heartbeat alerts.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "droidclaw_heartbeat";
    private static final String CHANNEL_NAME = "Heartbeat Monitoring";
    private static final String CHANNEL_DESCRIPTION = "Notifications for system health checks";
    private static final int NOTIFICATION_ID_HEARTBEAT = 1001;
    private static final int NOTIFICATION_ID_ERROR = 1002;

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    /**
     * Create notification channel (required for Android 8.0+).
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(false);
            channel.setSound(null, null);

            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showHeartbeatNotification(String content) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Heartbeat Alert - Attention Needed")
                .setContentText(truncate(content, 100))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID_HEARTBEAT, builder.build());
    }

    public void showHeartbeatError(String errorMessage) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Heartbeat Failed")
                .setContentText(truncate(errorMessage, 100))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(errorMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID_ERROR, builder.build());
    }

    public void cancelAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_HEARTBEAT);
        notificationManager.cancel(NOTIFICATION_ID_ERROR);
    }

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
