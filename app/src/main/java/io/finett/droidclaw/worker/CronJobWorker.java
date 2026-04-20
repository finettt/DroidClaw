package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.util.NotificationManager;

/**
 * Worker that executes cron jobs in the background.
 * Loads the cron job from repository, executes its prompt in an isolated session,
 * and saves the execution record with retry logic.
 */
public class CronJobWorker extends BaseTaskWorker {

    private static final String TAG = "CronJobWorker";

    private NotificationManager notificationManager;

    public CronJobWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.notificationManager = new NotificationManager(appContext);
    }

    @NonNull
    @Override
    public Result doWork() {

        String jobId = getInputData().getString("job_id");

        if (jobId == null || jobId.isEmpty()) {
            Log.w(TAG, "No job ID provided, skipping");
            return Result.failure();
        }

        Log.d(TAG, "Starting cron job worker: " + jobId);

        try {

            CronJob job = taskRepository.getCronJob(jobId);
            if (job == null) {
                Log.w(TAG, "Cron job not found: " + jobId);
                return Result.failure();
            }


            if (!job.isEnabled() || job.isPaused()) {
                Log.d(TAG, "Cron job is disabled or paused, skipping: " + jobId);
                return Result.success();
            }


            String prompt = job.getPrompt();
            if (prompt == null || prompt.trim().isEmpty()) {
                Log.w(TAG, "Cron job has empty prompt: " + jobId);
                return Result.failure();
            }

            long startTime = System.currentTimeMillis();


            ChatSession session = createIsolatedSession(SessionType.HIDDEN_CRON);
            session.setParentTaskId(job.getId());


            TaskResult result = executeWithSandbox(session, prompt);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;


            if (result.isSuccess()) {
                job.recordSuccess(duration);
                job.setLastRunTimestamp(System.currentTimeMillis());
            } else {
                job.recordFailure(result.getContent());
                job.setLastRunTimestamp(System.currentTimeMillis());


                if (job.canRetry()) {
                    Log.d(TAG, "Scheduling retry for job: " + jobId + " (attempt " + job.getRetryCount() + ")");
                    CronJobScheduler scheduler = new CronJobScheduler(appContext);
                    scheduler.executeJobNow(jobId);
                } else {
                    Log.w(TAG, "Job exceeded max retries: " + jobId);
                }
            }


            taskRepository.updateCronJob(job);


            taskRepository.saveTaskResult(result);


            notificationManager.sendTaskNotification(result);
            Log.d(TAG, "Cron job completed: " + job.getName());


            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Cron job worker failed: " + jobId, e);


            notificationManager.showErrorNotification("Cron Job Failed", "Job " + jobId + ": " + e.getMessage());


            try {
                CronJob job = taskRepository.getCronJob(jobId);
                if (job != null) {
                    job.recordFailure(e.getMessage());
                    job.setLastRunTimestamp(System.currentTimeMillis());
                    taskRepository.updateCronJob(job);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to update job error state", ex);
            }

            return Result.failure();
        }
    }

    @Override
    protected String getTaskTitle(int sessionType) {
        return "Cron Job Execution";
    }

    @Override
    protected String getParentTaskId() {

        String jobId = getInputData().getString("job_id");
        return jobId != null ? jobId : "cron";
    }

    @Override
    protected int getTaskType() {
        return TaskResult.TYPE_CRON_JOB;
    }

    @Override
    protected Result executeTask() {


        return Result.success();
    }
}
