package io.finett.droidclaw.service;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.worker.CronJobWorker;
import io.finett.droidclaw.worker.HeartbeatWorker;

/**
 * Service for scheduling background tasks using WorkManager.
 * Manages heartbeat and cron job scheduling with unique work names.
 */
public class TaskScheduler {

    private static final String HEARTBEAT_WORK_NAME = "heartbeat_task";
    private static final String CRON_WORK_PREFIX = "cron_task_";
    private static final String TASK_NOW_PREFIX = "task_now_";

    private final Context context;
    private final WorkManager workManager;

    public TaskScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(this.context);
    }

    /**
     * Schedule a periodic heartbeat task.
     * Note: Android WorkManager has a minimum interval of 15 minutes.
     * If the requested interval is less than 15 minutes, it will be clamped.
     *
     * @param config Heartbeat configuration
     */
    public void scheduleHeartbeat(HeartbeatConfig config) {
        // Enforce minimum 15-minute interval (WorkManager limitation)
        long intervalMillis = Math.max(config.getIntervalMillis(), 15 * 60 * 1000L);
        long intervalMinutes = intervalMillis / (60 * 1000L);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean("enabled", config.isEnabled())
                .build();

        PeriodicWorkRequest heartbeatWork = new PeriodicWorkRequest.Builder(
                HeartbeatWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        workManager.enqueueUniquePeriodicWork(
                HEARTBEAT_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                heartbeatWork
        );
    }

    /**
     * Cancel the heartbeat task.
     */
    public void cancelHeartbeat() {
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME);
    }

    /**
     * Schedule a periodic cron job.
     * Note: Android WorkManager has a minimum interval of 15 minutes.
     *
     * @param job CronJob to schedule
     */
    public void scheduleCronJob(CronJob job) {
        if (!job.isEnabled()) {
            return;
        }

        // Parse schedule - if it's a number, treat as milliseconds
        long intervalMillis;
        try {
            intervalMillis = Long.parseLong(job.getSchedule());
        } catch (NumberFormatException e) {
            // For cron expressions, default to 1 hour
            intervalMillis = 60 * 60 * 1000L;
        }

        // Enforce minimum 15-minute interval
        intervalMillis = Math.max(intervalMillis, 15 * 60 * 1000L);
        long intervalMinutes = intervalMillis / (60 * 1000L);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putString("job_id", job.getId())
                .putString("job_name", job.getName())
                .putString("job_prompt", job.getPrompt())
                .putString("job_model", job.getModelReference())
                .build();

        PeriodicWorkRequest cronWork = new PeriodicWorkRequest.Builder(
                CronJobWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        String workName = CRON_WORK_PREFIX + job.getId();
        workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                cronWork
        );
    }

    /**
     * Cancel a specific cron job.
     *
     * @param jobId ID of the cron job to cancel
     */
    public void cancelCronJob(String jobId) {
        String workName = CRON_WORK_PREFIX + jobId;
        workManager.cancelUniqueWork(workName);
    }

    /**
     * Cancel all cron jobs.
     */
    public void cancelAllCronJobs() {
        // Note: WorkManager doesn't provide a way to list all unique work names
        // The caller should track cron job IDs and cancel them individually
        // This is a placeholder that does nothing - caller must manage IDs
    }

    /**
     * Run a task immediately (for testing or manual execution).
     *
     * @param taskId ID of the task to run
     * @param taskType Type of task ("heartbeat" or "cron")
     */
    public void runTaskNow(String taskId, String taskType) {
        String workName = TASK_NOW_PREFIX + taskId + "_" + System.currentTimeMillis();

        Data inputData = new Data.Builder()
                .putString("task_id", taskId)
                .putString("task_type", taskType)
                .build();

        OneTimeWorkRequest oneTimeWork = new OneTimeWorkRequest.Builder(
                "heartbeat".equals(taskType) ? HeartbeatWorker.class : CronJobWorker.class
        )
                .setInputData(inputData)
                .build();

        workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.APPEND,
                oneTimeWork
        );
    }

    /**
     * Check if heartbeat is currently scheduled.
     * Note: WorkManager doesn't provide a direct way to check unique work status.
     * This always returns true if heartbeat was ever scheduled.
     * For accurate status, check HeartbeatConfigRepository.
     */
    public boolean isHeartbeatScheduled() {
        // WorkManager limitation: we can't easily query unique work status
        // Rely on HeartbeatConfigRepository for accurate state
        return true;
    }

    /**
     * Check if a specific cron job is scheduled.
     * Note: WorkManager doesn't provide a direct way to check unique work status.
     *
     * @param jobId ID of the cron job
     */
    public boolean isCronJobScheduled(String jobId) {
        // WorkManager limitation: we can't easily query unique work status
        // Rely on CronJob.enabled field for accurate state
        return true;
    }

    /**
     * Cancel all scheduled work.
     */
    public void cancelAll() {
        workManager.cancelAllWork();
    }
}
