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

    public void scheduleHeartbeat(HeartbeatConfig config) {
        long intervalMillis = Math.max(config.getIntervalMillis(), 15 * 60 * 1000L);
        long intervalMinutes = intervalMillis / (60 * 1000L);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
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

    public void cancelHeartbeat() {
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME);
    }

    public void scheduleCronJob(CronJob job) {
        if (!job.isEnabled()) {
            return;
        }

        long intervalMillis;
        try {
            intervalMillis = Long.parseLong(job.getSchedule());
        } catch (NumberFormatException e) {
            intervalMillis = 60 * 60 * 1000L;
        }

        intervalMillis = Math.max(intervalMillis, 15 * 60 * 1000L);
        long intervalMinutes = intervalMillis / (60 * 1000L);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
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

    public void cancelCronJob(String jobId) {
        String workName = CRON_WORK_PREFIX + jobId;
        workManager.cancelUniqueWork(workName);
    }

    public void cancelAllCronJobs() {
    }

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

    public boolean isHeartbeatScheduled() {
        return true;
    }

    public boolean isCronJobScheduled(String jobId) {
        return true;
    }

    public void cancelAll() {
        workManager.cancelAllWork();
    }
}
