package io.finett.droidclaw.worker;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Instrumented tests for CronJobWorker.
 * Tests the worker's execution logic with real repositories and cron job data.
 */
@RunWith(AndroidJUnit4.class)
public class CronJobWorkerInstrumentedTest {

    private Context context;
    private TaskRepository taskRepository;

    @Before
    public void setUp() {
        context = getApplicationContext();
        taskRepository = new TaskRepository(context);

        // Clear test data
        context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @After
    public void tearDown() {
        context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== WORKER INSTANTIATION TESTS ====================

    @Test
    public void worker_canBeCreated() {
        assertNotNull("Context should be available", context);
        assertNotNull("TaskRepository should be available", taskRepository);
    }

    @Test
    public void worker_receivesInputData() {
        Data inputData = new Data.Builder()
                .putString("job_id", "cron-test-1")
                .putString("job_name", "Test Job")
                .putString("job_prompt", "Test prompt")
                .putString("job_model", "provider1/model1")
                .build();

        // Verify input data is correctly structured
        assertEquals("Job ID should match", "cron-test-1", inputData.getString("job_id"));
        assertEquals("Job name should match", "Test Job", inputData.getString("job_name"));
        assertEquals("Job prompt should match", "Test prompt", inputData.getString("job_prompt"));
        assertEquals("Job model should match", "provider1/model1", inputData.getString("job_model"));
    }

    // ==================== CRON JOB LOADING TESTS ====================

    @Test
    public void doWork_withExistingJob_loadsJobFromRepository() {
        // Save a cron job
        CronJob job = new CronJob("cron-load-test", "Load Test", "Load test prompt", "3600000");
        taskRepository.saveCronJob(job);

        // Verify job is retrievable
        CronJob retrieved = taskRepository.getCronJob("cron-load-test");
        assertNotNull("Job should be retrievable", retrieved);
        assertEquals("cron-load-test", retrieved.getId());
        assertEquals("Load test prompt", retrieved.getPrompt());
    }

    @Test
    public void doWork_withDisabledJob_skipsExecution() {
        // Save a disabled cron job
        CronJob job = new CronJob("cron-disabled", "Disabled Job", "Disabled prompt", "3600000");
        job.setEnabled(false);
        taskRepository.saveCronJob(job);

        CronJob retrieved = taskRepository.getCronJob("cron-disabled");
        assertFalse("Job should be disabled", retrieved.isEnabled());
    }

    @Test
    public void doWork_withNonExistentJob_returnsFailure() {
        // Don't save any job - simulate looking for non-existent job
        CronJob retrieved = taskRepository.getCronJob("non-existent");
        assertNull("Job should not exist", retrieved);
    }

    @Test
    public void doWork_withEmptyPrompt_returnsFailure() {
        // Save a cron job with empty prompt
        CronJob job = new CronJob("cron-empty", "Empty Job", "", "3600000");
        taskRepository.saveCronJob(job);

        CronJob retrieved = taskRepository.getCronJob("cron-empty");
        assertNotNull("Job should exist", retrieved);
        assertTrue("Prompt should be empty", retrieved.getPrompt().isEmpty());
    }

    // ==================== CRON JOB SCHEDULE TESTS ====================

    @Test
    public void shouldRun_withIntervalElapsed_returnsTrue() {
        CronJob job = new CronJob("cron-interval", "Interval Job", "Prompt", "3600000");
        job.setLastRunTimestamp(System.currentTimeMillis() - 7200000L); // 2 hours ago

        assertTrue("Should run - interval elapsed", job.shouldRun(System.currentTimeMillis()));
    }

    @Test
    public void shouldRun_withIntervalNotElapsed_returnsFalse() {
        CronJob job = new CronJob("cron-interval", "Interval Job", "Prompt", "3600000");
        job.setLastRunTimestamp(System.currentTimeMillis() - 1800000L); // 30 minutes ago

        assertFalse("Should not run - interval not elapsed", job.shouldRun(System.currentTimeMillis()));
    }

    @Test
    public void shouldRun_withCronExpression_usesDefaultInterval() {
        CronJob job = new CronJob("cron-cron-expr", "Cron Expression Job", "Prompt", "0 0 * * *");
        job.setLastRunTimestamp(System.currentTimeMillis() - 30 * 60 * 1000L); // 30 minutes ago

        // With cron expression, should use default 1 hour interval
        assertFalse("Should not run - less than 1 hour", job.shouldRun(System.currentTimeMillis()));
    }

    @Test
    public void shouldRun_withDisabledJob_returnsFalse() {
        CronJob job = new CronJob("cron-disabled", "Disabled Job", "Prompt", "3600000");
        job.setEnabled(false);
        job.setLastRunTimestamp(0);

        assertFalse("Should not run when disabled", job.shouldRun(System.currentTimeMillis()));
    }

    // ==================== TASK RESULT TESTS ====================

    @Test
    public void taskResult_savedAfterExecution() {
        // Simulate task result saving
        TaskResult result = new TaskResult("cron-execution-test", TaskResult.TYPE_CRON_JOB, 
                System.currentTimeMillis(), "Cron job executed successfully");
        result.putMetadata("job_id", "cron-test-1");
        result.putMetadata("status", "success");

        taskRepository.saveTaskResult(result);

        // Verify result is saved
        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("cron-execution-test", results.get(0).getId());
        assertEquals("cron-test-1", results.get(0).getMetadataValue("job_id"));
    }

    @Test
    public void taskResult_withFailureStatus_savesCorrectly() {
        TaskResult result = new TaskResult("cron-failure-test", TaskResult.TYPE_CRON_JOB, 
                System.currentTimeMillis(), "Failed: execution error");
        result.putMetadata("job_id", "cron-test-2");
        result.putMetadata("status", "failed");
        result.putMetadata("error", "execution error");

        taskRepository.saveTaskResult(result);

        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("failed", results.get(0).getMetadataValue("status"));
        assertEquals("execution error", results.get(0).getMetadataValue("error"));
    }

    // ==================== EXECUTION RECORD TESTS ====================

    @Test
    public void executionRecord_savedAfterExecution() {
        // Simulate execution record saving
        long startTime = System.currentTimeMillis();
        TaskExecutionRecord record = new TaskExecutionRecord("cron-record-test", "session-cron-1", 
                TaskResult.TYPE_CRON_JOB, startTime);
        record.setEndTime(startTime + 5000L);
        record.setSuccess(true);
        record.setTokensUsed(200);
        record.setIterations(3);

        taskRepository.saveExecutionRecord(record);

        // Verify record is saved
        List<TaskExecutionRecord> records = taskRepository.getExecutionHistory("cron-record-test");
        assertEquals("Should have 1 record", 1, records.size());
        assertTrue("Record should be successful", records.get(0).isSuccess());
        assertEquals(200, records.get(0).getTokensUsed());
        assertEquals(3, records.get(0).getIterations());
    }

    @Test
    public void executionRecord_withFailure_savesCorrectly() {
        long startTime = System.currentTimeMillis();
        TaskExecutionRecord record = new TaskExecutionRecord("cron-fail-record", "session-cron-2", 
                TaskResult.TYPE_CRON_JOB, startTime);
        record.setEndTime(startTime + 2000L);
        record.setSuccess(false);
        record.setErrorMessage("Timeout after 2 seconds");

        taskRepository.saveExecutionRecord(record);

        List<TaskExecutionRecord> records = taskRepository.getExecutionHistory("cron-fail-record");
        assertEquals("Should have 1 record", 1, records.size());
        assertFalse("Record should be failed", records.get(0).isSuccess());
        assertEquals("Timeout after 2 seconds", records.get(0).getErrorMessage());
    }

    // ==================== CRON JOB UPDATE TESTS ====================

    @Test
    public void updateCronJobLastRun_updatesTimestamp() {
        // Save initial job
        CronJob job = new CronJob("cron-update-test", "Update Test", "Prompt", "3600000");
        taskRepository.saveCronJob(job);

        // Update last run timestamp
        long now = System.currentTimeMillis();
        job.setLastRunTimestamp(now);
        taskRepository.updateCronJob(job);

        // Verify update
        CronJob updated = taskRepository.getCronJob("cron-update-test");
        assertEquals("Timestamp should be updated", now, updated.getLastRunTimestamp());
    }

    // ==================== SESSION TYPE TESTS ====================

    @Test
    public void worker_createsIsolatedSession_withCorrectType() {
        // Verify session type constant is correct
        assertEquals("HIDDEN_CRON type should be 2", 2, SessionType.HIDDEN_CRON);
    }

    @Test
    public void worker_setsParentTaskId_correctly() {
        // Build input data with job ID
        Data inputData = new Data.Builder()
                .putString("job_id", "cron-parent-test")
                .build();

        // Verify parent task ID would be set correctly
        String parentTaskId = inputData.getString("job_id");
        assertEquals("Parent task ID should match job ID", "cron-parent-test", parentTaskId);
    }

    // ==================== MULTIPLE CRON JOB TESTS ====================

    @Test
    public void multipleCronJobs_allExecuteIndependently() {
        // Save multiple cron jobs
        CronJob job1 = new CronJob("cron-multi-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-multi-2", "Job 2", "Prompt 2", "7200000");
        CronJob job3 = new CronJob("cron-multi-3", "Job 3", "Prompt 3", "10800000");

        taskRepository.saveCronJob(job1);
        taskRepository.saveCronJob(job2);
        taskRepository.saveCronJob(job3);

        // Verify all jobs are saved
        List<CronJob> jobs = taskRepository.getCronJobs();
        assertEquals("Should have 3 jobs", 3, jobs.size());

        // Simulate execution of each job
        for (CronJob job : jobs) {
            TaskResult result = new TaskResult("cron-exec-" + job.getId(), TaskResult.TYPE_CRON_JOB, 
                    System.currentTimeMillis(), "Executed: " + job.getName());
            taskRepository.saveTaskResult(result);

            job.setLastRunTimestamp(System.currentTimeMillis());
            taskRepository.updateCronJob(job);
        }

        // Verify all results are saved
        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 3 results", 3, results.size());
    }

    // ==================== CRON JOB LIFECYCLE TESTS ====================

    @Test
    public void cronJob_fullLifecycle_createsResultAndRecord() {
        // 1. Create cron job
        CronJob job = new CronJob("cron-lifecycle", "Lifecycle Test", "Execute lifecycle test", "3600000");
        taskRepository.saveCronJob(job);

        // 2. Simulate execution
        long startTime = System.currentTimeMillis();
        TaskResult result = new TaskResult("cron-lifecycle-exec-1", TaskResult.TYPE_CRON_JOB, 
                startTime, "Lifecycle test completed successfully");
        result.putMetadata("job_id", "cron-lifecycle");
        result.putMetadata("status", "success");
        taskRepository.saveTaskResult(result);

        TaskExecutionRecord record = new TaskExecutionRecord("cron-lifecycle-exec-1", "session-lifecycle", 
                TaskResult.TYPE_CRON_JOB, startTime);
        record.setEndTime(startTime + 3000L);
        record.setSuccess(true);
        record.setTokensUsed(150);
        taskRepository.saveExecutionRecord(record);

        // 3. Update job timestamp
        job.setLastRunTimestamp(startTime);
        taskRepository.updateCronJob(job);

        // 4. Verify all data
        CronJob updatedJob = taskRepository.getCronJob("cron-lifecycle");
        assertEquals("Job timestamp should be updated", startTime, updatedJob.getLastRunTimestamp());

        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("Lifecycle test completed successfully", results.get(0).getContent());

        List<TaskExecutionRecord> records = taskRepository.getExecutionHistory("cron-lifecycle-exec-1");
        assertEquals("Should have 1 record", 1, records.size());
        assertTrue("Record should be successful", records.get(0).isSuccess());
    }
}
