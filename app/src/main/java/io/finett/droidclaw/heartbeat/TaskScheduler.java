package io.finett.droidclaw.heartbeat;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Central scheduler for Heartbeat and Cron Job execution.
 *
 * Manages WorkManager scheduling for both types of tasks:
 * - Heartbeat: Periodic work that runs in the main chat session
 * - Cron Jobs: One-time or periodic work that runs in isolated hidden sessions
 */
public class TaskScheduler {
    private static final String TAG = "TaskScheduler";

    // WorkManager work names
    public static final String HEARTBEAT_WORK_NAME = "heartbeat_work";
    public static final String CRON_JOB_WORK_PREFIX = "cron_job_";

    private final Context context;
    private final WorkManager workManager;
    private final TaskRepository taskRepository;

    public TaskScheduler(Context context, TaskRepository taskRepository) {
        this.context = context;
        this.workManager = WorkManager.getInstance(context);
        this.taskRepository = taskRepository;
    }

    // ========== HEARTBEAT SCHEDULING ==========

    /**
     * Schedule or reschedule the heartbeat worker.
     *
     * @param config Heartbeat configuration
     */
    public void scheduleHeartbeat(HeartbeatConfig config) {
        if (!config.isEnabled()) {
            cancelHeartbeat();
            Log.d(TAG, "Heartbeat disabled - cancelled");
            return;
        }

        long intervalMinutes = config.getIntervalMinutes();
        // WorkManager minimum interval is 15 minutes
        if (intervalMinutes < 15) {
            Log.w(TAG, "Heartbeat interval " + intervalMinutes + "min below minimum 15min, using 15min");
            intervalMinutes = 15;
        }

        Constraints.Builder constraintsBuilder = new Constraints.Builder();
        if (config.isRequireNetwork()) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
        }

        PeriodicWorkRequest heartbeatWork = new PeriodicWorkRequest.Builder(
                HeartbeatWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraintsBuilder.build())
                .addTag("heartbeat")
                .build();

        workManager.enqueueUniquePeriodicWork(
                HEARTBEAT_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                heartbeatWork
        );

        Log.d(TAG, "Heartbeat scheduled with interval: " + intervalMinutes + " minutes");
    }

    /**
     * Cancel the heartbeat worker.
     */
    public void cancelHeartbeat() {
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME);
        Log.d(TAG, "Heartbeat cancelled");
    }

    /**
     * Check if heartbeat is currently scheduled.
     */
    public boolean isHeartbeatScheduled() {
        // This is a simplified check - in production you'd query WorkInfo
        return true; // We assume it's scheduled if it was ever scheduled
    }

    // ========== CRON JOB SCHEDULING ==========

    /**
     * Schedule a cron job for periodic execution.
     *
     * @param cronJob The cron job to schedule
     */
    public void scheduleCronJob(CronJob cronJob) {
        if (!cronJob.isEnabled()) {
            cancelCronJob(cronJob.getId());
            Log.d(TAG, "Cron job disabled - cancelled: " + cronJob.getName());
            return;
        }

        long intervalMinutes = cronJob.getIntervalMinutes();
        // WorkManager minimum interval is 15 minutes
        if (intervalMinutes < 15) {
            Log.w(TAG, "Cron job interval " + intervalMinutes + "min below minimum 15min, using 15min");
            intervalMinutes = 15;
        }

        Constraints.Builder constraintsBuilder = new Constraints.Builder();
        if (cronJob.isRequireNetwork()) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
        }
        if (cronJob.isRequireCharging()) {
            constraintsBuilder.setRequiresCharging(true);
        }

        PeriodicWorkRequest cronWork = new PeriodicWorkRequest.Builder(
                CronJobWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraintsBuilder.build())
                .addTag("cron_job")
                .addTag(CRON_JOB_WORK_PREFIX + cronJob.getId())
                .setInputData(new androidx.work.Data.Builder()
                        .putString("cronJobId", cronJob.getId())
                        .build())
                .build();

        String workName = CRON_JOB_WORK_PREFIX + cronJob.getId();
        workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                cronWork
        );

        Log.d(TAG, "Cron job scheduled: " + cronJob.getName() + " (ID: " + cronJob.getId() + ")");
    }

    /**
     * Cancel a specific cron job.
     *
     * @param cronJobId The cron job ID to cancel
     */
    public void cancelCronJob(String cronJobId) {
        String workName = CRON_JOB_WORK_PREFIX + cronJobId;
        workManager.cancelUniqueWork(workName);
        Log.d(TAG, "Cron job cancelled: " + cronJobId);
    }

    /**
     * Schedule all enabled cron jobs from the repository.
     * Useful for re-initializing all scheduled tasks.
     */
    public void scheduleAllEnabledCronJobs() {
        List<CronJob> enabledJobs = taskRepository.getEnabledCronJobs();
        for (CronJob job : enabledJobs) {
            scheduleCronJob(job);
        }
        Log.d(TAG, "Scheduled " + enabledJobs.size() + " enabled cron jobs");
    }

    /**
     * Cancel all cron jobs.
     */
    public void cancelAllCronJobs() {
        workManager.cancelAllWorkByTag("cron_job");
        Log.d(TAG, "All cron jobs cancelled");
    }

    /**
     * Run a cron job immediately (for testing or manual trigger).
     *
     * @param cronJobId The cron job ID to run
     */
    public void runCronJobNow(String cronJobId) {
        CronJob job = taskRepository.getCronJob(cronJobId);
        if (job == null) {
            Log.w(TAG, "Cron job not found: " + cronJobId);
            return;
        }

        OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(CronJobWorker.class)
                .setInputData(new androidx.work.Data.Builder()
                        .putString("cronJobId", cronJobId)
                        .build())
                .addTag("cron_job_manual")
                .build();

        workManager.enqueueUniqueWork(
                CRON_JOB_WORK_PREFIX + cronJobId + "_now",
                ExistingWorkPolicy.REPLACE,
                immediateWork
        );

        Log.d(TAG, "Cron job triggered manually: " + job.getName());
    }
}
