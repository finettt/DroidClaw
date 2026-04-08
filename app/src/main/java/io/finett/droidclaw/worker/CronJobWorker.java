package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskResult;

/**
 * Worker that executes cron jobs in the background.
 * Loads the cron job from repository, executes its prompt in an isolated session,
 * and saves the execution record.
 */
public class CronJobWorker extends BaseTaskWorker {

    private static final String TAG = "CronJobWorker";

    public CronJobWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Get job ID from input data
        String jobId = getInputData().getString("job_id");
        
        if (jobId == null || jobId.isEmpty()) {
            Log.w(TAG, "No job ID provided, skipping");
            return Result.failure();
        }

        Log.d(TAG, "Starting cron job worker: " + jobId);

        try {
            // Load cron job from repository
            CronJob job = taskRepository.getCronJob(jobId);
            if (job == null) {
                Log.w(TAG, "Cron job not found: " + jobId);
                return Result.failure();
            }

            // Check if job is enabled
            if (!job.isEnabled()) {
                Log.d(TAG, "Cron job is disabled, skipping: " + jobId);
                return Result.success();
            }

            // Check if job should run based on schedule
            long currentTime = System.currentTimeMillis();
            if (!job.shouldRun(currentTime)) {
                Log.d(TAG, "Cron job interval not elapsed, skipping: " + jobId);
                return Result.success();
            }

            // Get the prompt to execute
            String prompt = job.getPrompt();
            if (prompt == null || prompt.trim().isEmpty()) {
                Log.w(TAG, "Cron job has empty prompt: " + jobId);
                return Result.failure();
            }

            // Create isolated session for this cron job
            ChatSession session = createIsolatedSession(SessionType.HIDDEN_CRON);
            session.setParentTaskId(job.getId());

            // Execute the job prompt in sandbox
            TaskResult result = executeWithSandbox(session, prompt);

            // Save task result
            taskRepository.saveTaskResult(result);

            // Update job's last run timestamp
            job.setLastRunTimestamp(currentTime);
            taskRepository.updateCronJob(job);

            // Generate notification content
            String notificationContent = generateNotificationContent(result);
            Log.d(TAG, "Cron job completed: " + job.getName() + " - " + notificationContent);

            // Cron jobs always return success (job continues)
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Cron job worker failed: " + jobId, e);
            return Result.failure();
        }
    }

    @Override
    protected String getTaskTitle(int sessionType) {
        return "Cron Job Execution";
    }

    @Override
    protected String getParentTaskId() {
        // Will be set from the actual job ID in doWork()
        String jobId = getInputData().getString("job_id");
        return jobId != null ? jobId : "cron";
    }

    @Override
    protected int getTaskType() {
        return TaskResult.TYPE_CRON_JOB;
    }

    @Override
    protected Result executeTask() {
        // This method is not used for CronJobWorker as doWork() is overridden directly
        // The abstract method is required by BaseTaskWorker but cron job has custom logic
        return Result.success();
    }
}
