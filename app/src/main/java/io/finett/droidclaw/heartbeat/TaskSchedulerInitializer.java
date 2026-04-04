package io.finett.droidclaw.heartbeat;

import android.content.Context;
import android.util.Log;

import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Initializes and schedules all background tasks (Heartbeat and Cron Jobs).
 *
 * This class should be called during app initialization to ensure
 * all enabled tasks are properly scheduled.
 */
public class TaskSchedulerInitializer {
    private static final String TAG = "TaskSchedulerInitializer";

    /**
     * Initialize and schedule all background tasks.
     *
     * @param context Application context
     */
    public static void initialize(Context context) {
        Log.d(TAG, "Initializing background tasks...");

        try {
            SettingsManager settingsManager = new SettingsManager(context);
            TaskRepository taskRepository = new TaskRepository(context);
            TaskScheduler scheduler = new TaskScheduler(context, taskRepository);

            // 1. Schedule Heartbeat with persisted config
            HeartbeatConfig heartbeatConfig = settingsManager.getHeartbeatConfig();
            scheduler.scheduleHeartbeat(heartbeatConfig);
            Log.d(TAG, "Heartbeat scheduled: enabled=" + heartbeatConfig.isEnabled() +
                    ", interval=" + heartbeatConfig.getIntervalMinutes() + "min");

            // 2. Schedule all enabled cron jobs
            scheduler.scheduleAllEnabledCronJobs();

            Log.d(TAG, "Background tasks initialization completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize background tasks", e);
        }
    }

    /**
     * Initialize with custom heartbeat config.
     *
     * @param context Application context
     * @param heartbeatConfig Custom heartbeat configuration
     */
    public static void initializeWithConfig(Context context, HeartbeatConfig heartbeatConfig) {
        Log.d(TAG, "Initializing background tasks with custom heartbeat config...");

        try {
            SettingsManager settingsManager = new SettingsManager(context);
            TaskRepository taskRepository = new TaskRepository(context);
            TaskScheduler scheduler = new TaskScheduler(context, taskRepository);

            // Save and schedule Heartbeat with custom config
            settingsManager.setHeartbeatConfig(heartbeatConfig);
            scheduler.scheduleHeartbeat(heartbeatConfig);
            Log.d(TAG, "Heartbeat scheduled with custom config: " + heartbeatConfig.getIntervalMinutes() + "min");

            // 2. Schedule all enabled cron jobs
            scheduler.scheduleAllEnabledCronJobs();

            Log.d(TAG, "Background tasks initialization completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize background tasks", e);
        }
    }

    /**
     * Cancel all background tasks.
     *
     * @param context Application context
     */
    public static void cancelAll(Context context) {
        Log.d(TAG, "Cancelling all background tasks...");

        try {
            TaskRepository taskRepository = new TaskRepository(context);
            TaskScheduler scheduler = new TaskScheduler(context, taskRepository);

            scheduler.cancelHeartbeat();
            scheduler.cancelAllCronJobs();

            Log.d(TAG, "All background tasks cancelled");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel background tasks", e);
        }
    }
}
