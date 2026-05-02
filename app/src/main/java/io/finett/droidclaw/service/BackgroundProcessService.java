package io.finett.droidclaw.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import io.finett.droidclaw.R;

/**
 * Foreground service that keeps background tool executions alive.
 *
 * <p>Started by {@link BackgroundProcessManager} when the first background process is submitted
 * and stopped automatically when all processes finish or are killed.
 *
 * <p>Holds a {@link PowerManager.WakeLock} to prevent the CPU from sleeping mid-execution.
 */
public class BackgroundProcessService extends Service {

    private static final String TAG = "BackgroundProcessService";

    static final String CHANNEL_ID = "droidclaw_bg_exec";
    private static final int NOTIFICATION_ID = 8801;

    static final String ACTION_UPDATE_NOTIFICATION = "io.finett.droidclaw.BG_UPDATE";
    static final String ACTION_STOP = "io.finett.droidclaw.BG_STOP";
    static final String EXTRA_RUNNING_COUNT = "running_count";

    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;

    // ==================== Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        acquireWakeLock();
        Log.d(TAG, "BackgroundProcessService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "Stop action received — killing all processes and stopping service");
            BackgroundProcessManager.getInstance(this).killAll();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        int runningCount = (intent != null)
                ? intent.getIntExtra(EXTRA_RUNNING_COUNT, 0)
                : 0;

        startForeground(NOTIFICATION_ID, buildNotification(runningCount));

        if (intent != null && ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
            // Just a notification refresh — no new work to start
            return START_STICKY;
        }

        Log.d(TAG, "BackgroundProcessService started — " + runningCount + " process(es) running");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Safety net: kill any remaining processes if the OS tears down the service
        BackgroundProcessManager.getInstance(this).killAll();
        releaseWakeLock();
        Log.d(TAG, "BackgroundProcessService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== Static helpers called by BackgroundProcessManager ====================

    /**
     * Start (or no-op if already running) the foreground service.
     * Called when the first background process is submitted.
     */
    public static void ensureRunning(Context context) {
        Intent intent = new Intent(context, BackgroundProcessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Push an updated notification to reflect the current running count.
     * Safe to call even when the service is not running (it will start it).
     */
    public static void updateNotification(Context context, int runningCount) {
        Intent intent = new Intent(context, BackgroundProcessService.class);
        intent.setAction(ACTION_UPDATE_NOTIFICATION);
        intent.putExtra(EXTRA_RUNNING_COUNT, runningCount);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stop the service if there are no running processes left.
     * Called by {@link BackgroundProcessManager} after each process finishes.
     */
    public static void stopIfIdle(Context context) {
        Intent intent = new Intent(context, BackgroundProcessService.class);
        intent.setAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    // ==================== Notification ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DroidClaw Background Tasks",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active background tool executions");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int runningCount) {
        String title = runningCount == 1
                ? "DroidClaw — 1 task running in background"
                : "DroidClaw — " + runningCount + " tasks running in background";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText("Tap to open DroidClaw")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }

    // ==================== WakeLock ====================

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DroidClaw:BgExec");
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}