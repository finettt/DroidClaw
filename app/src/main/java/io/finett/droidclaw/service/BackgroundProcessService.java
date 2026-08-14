package io.finett.droidclaw.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    /**
     * Grace period before an idle service actually tears itself down. Prevents a
     * stop/start race: if new work is submitted right after the last process
     * finished, the delayed stop is cancelled and the same service record keeps
     * serving — avoiding a {@code startForegroundService()} that lands on a
     * dying record, which Android 15 punishes with
     * {@code ForegroundServiceDidNotStartInTimeException} (process death).
     */
    private static final long STOP_GRACE_MS = 3_000;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private final Handler stopHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingStop;

    // ==================== Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        acquireWakeLock();
        // startForeground() must be called within 5 seconds on Android 15+.
        // Doing it here in onCreate() is the earliest possible point and gives
        // us the full window before onStartCommand() runs.
        // buildNotification(0) is a placeholder — onStartCommand() immediately
        // overwrites it with the actual running count via notificationManager.notify().
        startForeground(NOTIFICATION_ID, buildNotification(0));
        Log.d(TAG, "BackgroundProcessService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(this);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            int running = manager.runningCount();
            if (running > 0) {
                // Stale stop intent: new work was submitted after this intent was
                // queued (stopIfIdle sends it when the count hits 0, but submit()
                // can race ahead). Killing here would destroy the new process.
                Log.d(TAG, "Stale stop intent ignored — " + running + " process(es) running");
                return START_STICKY;
            }
            Log.d(TAG, "Stop action received — scheduling idle shutdown");
            scheduleStop();
            return START_NOT_STICKY;
        }

        // New work or a refresh arrived — cancel any pending idle shutdown.
        cancelPendingStop();

        int runningCount = (intent != null)
                ? intent.getIntExtra(EXTRA_RUNNING_COUNT, 0)
                : 0;

        // Re-assert foreground state: every startForegroundService() call
        // (ensureRunning / updateNotification) must be paired with a
        // startForeground() call, even when the service is already running —
        // otherwise Android 12+ may kill the process with
        // ForegroundServiceDidNotStartInTimeException.
        startForeground(NOTIFICATION_ID, buildNotification(runningCount));

        if (intent != null && ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
            // Just a notification refresh — no new work to start
            return START_STICKY;
        }

        Log.d(TAG, "BackgroundProcessService started — " + runningCount + " process(es) running");
        return START_STICKY;
    }

    /**
     * Schedule the actual teardown after {@link #STOP_GRACE_MS}. The runnable
     * re-checks the running count, so work submitted during the grace window
     * keeps the service alive even if no new intent cancels the stop.
     */
    private void scheduleStop() {
        if (pendingStop != null) {
            return; // already scheduled
        }
        pendingStop = () -> {
            pendingStop = null;
            if (BackgroundProcessManager.getInstance(this).runningCount() > 0) {
                Log.d(TAG, "Pending stop expired but work is running — staying alive");
                return;
            }
            Log.d(TAG, "Grace period elapsed — stopping service");
            stopForeground(true);
            stopSelf();
        };
        stopHandler.postDelayed(pendingStop, STOP_GRACE_MS);
    }

    private void cancelPendingStop() {
        if (pendingStop != null) {
            stopHandler.removeCallbacks(pendingStop);
            pendingStop = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelPendingStop();
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
     *
     * <p>Uses {@link Context#startForegroundService} when the service is not running
     * to avoid {@link android.app.PendingIntent.CanceledException} on API 26+ when
     * the app is backgrounded and the service is dead (exactly when cron/heartbeat
     * tasks update the count).</p>
     */
    public static void updateNotification(Context context, int runningCount) {
        Intent intent = new Intent(context, BackgroundProcessService.class);
        intent.setAction(ACTION_UPDATE_NOTIFICATION);
        intent.putExtra(EXTRA_RUNNING_COUNT, runningCount);
        if (isServiceRunning(context)) {
            context.startService(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static boolean isServiceRunning(Context context) {
        @SuppressWarnings("deprecation")
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        try {
            java.util.List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
            if (services == null) return false;
            for (ActivityManager.RunningServiceInfo info : services) {
                if (info.service.getClassName().equals(BackgroundProcessService.class.getName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stop the service if there are no running processes left.
     * Called by {@link BackgroundProcessManager} after each process finishes.
     */
    public static void stopIfIdle(Context context) {
        if (!isServiceRunning(context)) {
            // Service is dead — sending ACTION_STOP to a dead service would start it
            // just to stop it, with the same crash risk as updateNotification.
            Log.d(TAG, "Service not running — skipping stopIfIdle");
            return;
        }
        Intent intent = new Intent(context, BackgroundProcessService.class);
        intent.setAction(ACTION_STOP);
        // Use startService (not startForegroundService) because this intent
        // immediately stops the service — no need for foreground guarantees.
        context.startService(intent);
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