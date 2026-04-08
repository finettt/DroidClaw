package io.finett.droidclaw.repository;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;

/**
 * Instrumented tests for TaskRepository.
 * Tests the persistence layer for background tasks using real SharedPreferences.
 */
@RunWith(AndroidJUnit4.class)
public class TaskRepositoryInstrumentedTest {

    private TaskRepository repository;

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        getApplicationContext()
                .getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        repository = new TaskRepository(getApplicationContext());
    }

    @After
    public void tearDown() {
        // Clean up after tests
        getApplicationContext()
                .getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== TASK RESULTS TESTS ====================

    @Test
    public void saveTaskResult_savesAndRetrievesResult() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat OK");
        repository.saveTaskResult(result);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should have 1 result", 1, results.size());
        TaskResult retrieved = results.get(0);
        assertEquals("task-1", retrieved.getId());
        assertEquals(TaskResult.TYPE_HEARTBEAT, retrieved.getType());
        assertEquals("Heartbeat OK", retrieved.getContent());
    }

    @Test
    public void saveTaskResult_overwritesExistingResultWithSameId() {
        TaskResult result1 = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Initial");
        repository.saveTaskResult(result1);

        TaskResult result2 = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 2000L, "Updated");
        repository.saveTaskResult(result2);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should still have 1 result", 1, results.size());
        assertEquals("Updated", results.get(0).getContent());
        assertEquals(2000L, results.get(0).getTimestamp());
    }

    @Test
    public void getTaskResults_filtersByType() {
        // Save different task types
        repository.saveTaskResult(new TaskResult("heartbeat-1", TaskResult.TYPE_HEARTBEAT, 1000L, "HB1"));
        repository.saveTaskResult(new TaskResult("cron-1", TaskResult.TYPE_CRON_JOB, 2000L, "CJ1"));
        repository.saveTaskResult(new TaskResult("heartbeat-2", TaskResult.TYPE_HEARTBEAT, 3000L, "HB2"));
        repository.saveTaskResult(new TaskResult("manual-1", TaskResult.TYPE_MANUAL, 4000L, "MT1"));

        List<TaskResult> heartbeatResults = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should have 2 heartbeat results", 2, heartbeatResults.size());
        for (TaskResult result : heartbeatResults) {
            assertEquals(TaskResult.TYPE_HEARTBEAT, result.getType());
        }
    }

    @Test
    public void getTaskResults_sortedByTimestampDescending() {
        repository.saveTaskResult(new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "First"));
        repository.saveTaskResult(new TaskResult("task-2", TaskResult.TYPE_HEARTBEAT, 3000L, "Third"));
        repository.saveTaskResult(new TaskResult("task-3", TaskResult.TYPE_HEARTBEAT, 2000L, "Second"));

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals(3, results.size());
        assertEquals("Third", results.get(0).getContent());
        assertEquals("Second", results.get(1).getContent());
        assertEquals("First", results.get(2).getContent());
    }

    @Test
    public void getTaskResults_respectsLimit() {
        // Save 5 results
        for (int i = 0; i < 5; i++) {
            repository.saveTaskResult(new TaskResult("task-" + i, TaskResult.TYPE_HEARTBEAT, i * 1000L, "Content " + i));
        }

        List<TaskResult> limitedResults = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 3);

        assertEquals("Should return only 3 results", 3, limitedResults.size());
        // Should be the 3 most recent
        assertEquals("Content 4", limitedResults.get(0).getContent());
        assertEquals("Content 3", limitedResults.get(1).getContent());
        assertEquals("Content 2", limitedResults.get(2).getContent());
    }

    @Test
    public void getAllTaskResults_returnsAllTypes() {
        repository.saveTaskResult(new TaskResult("heartbeat-1", TaskResult.TYPE_HEARTBEAT, 1000L, "HB1"));
        repository.saveTaskResult(new TaskResult("cron-1", TaskResult.TYPE_CRON_JOB, 2000L, "CJ1"));
        repository.saveTaskResult(new TaskResult("manual-1", TaskResult.TYPE_MANUAL, 3000L, "MT1"));

        List<TaskResult> allResults = repository.getAllTaskResults();

        assertEquals("Should have 3 results", 3, allResults.size());
    }

    @Test
    public void deleteTaskResult_removesResult() {
        TaskResult result = new TaskResult("task-to-delete", TaskResult.TYPE_HEARTBEAT, 1000L, "Delete me");
        repository.saveTaskResult(result);

        repository.deleteTaskResult("task-to-delete");

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Should have 0 results after deletion", 0, results.size());
    }

    @Test
    public void deleteTaskResult_nonExistentId_doesNotCrash() {
        // Should not crash when deleting non-existent task
        repository.deleteTaskResult("non-existent");
        List<TaskResult> results = repository.getAllTaskResults();
        assertEquals("Should have 0 results", 0, results.size());
    }

    @Test
    public void taskResult_withMetadata_savesAndRetrievesMetadata() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Content");
        result.putMetadata("key1", "value1");
        result.putMetadata("key2", "value2");
        repository.saveTaskResult(result);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        TaskResult retrieved = results.get(0);

        assertEquals("value1", retrieved.getMetadataValue("key1"));
        assertEquals("value2", retrieved.getMetadataValue("key2"));
    }

    // ==================== CRON JOBS TESTS ====================

    @Test
    public void saveCronJob_savesAndRetrievesJob() {
        CronJob job = new CronJob("cron-1", "Daily Report", "Generate daily report", "86400000");
        job.setModelReference("provider1/model1");
        repository.saveCronJob(job);

        List<CronJob> jobs = repository.getCronJobs();

        assertEquals("Should have 1 job", 1, jobs.size());
        CronJob retrieved = jobs.get(0);
        assertEquals("cron-1", retrieved.getId());
        assertEquals("Daily Report", retrieved.getName());
        assertEquals("Generate daily report", retrieved.getPrompt());
        assertEquals("provider1/model1", retrieved.getModelReference());
    }

    @Test
    public void saveCronJob_overwritesExistingJobWithSameId() {
        CronJob job1 = new CronJob("cron-1", "Old Name", "Old Prompt", "3600000");
        repository.saveCronJob(job1);

        CronJob job2 = new CronJob("cron-1", "New Name", "New Prompt", "7200000");
        repository.saveCronJob(job2);

        List<CronJob> jobs = repository.getCronJobs();
        assertEquals("Should still have 1 job", 1, jobs.size());
        assertEquals("New Name", jobs.get(0).getName());
        assertEquals("New Prompt", jobs.get(0).getPrompt());
        assertEquals("7200000", jobs.get(0).getSchedule());
    }

    @Test
    public void saveCronJob_multipleJobs_allPersisted() {
        CronJob job1 = new CronJob("cron-1", "Job 1", "Prompt 1", "3600000");
        repository.saveCronJob(job1);
        
        CronJob job2 = new CronJob("cron-2", "Job 2", "Prompt 2", "7200000");
        repository.saveCronJob(job2);
        
        CronJob job3 = new CronJob("cron-3", "Job 3", "Prompt 3", "10800000");
        repository.saveCronJob(job3);

        List<CronJob> jobs = repository.getCronJobs();

        assertEquals("Should have 3 jobs", 3, jobs.size());
    }

    @Test
    public void getCronJob_returnsSpecificJob() {
        repository.saveCronJob(new CronJob("cron-1", "Job 1", "Prompt 1", "3600000"));
        repository.saveCronJob(new CronJob("cron-2", "Job 2", "Prompt 2", "7200000"));

        CronJob job = repository.getCronJob("cron-2");

        assertNotNull("Should find the job", job);
        assertEquals("cron-2", job.getId());
        assertEquals("Job 2", job.getName());
    }

    @Test
    public void getCronJob_nonExistentId_returnsNull() {
        CronJob job = repository.getCronJob("non-existent");
        assertNull("Should return null for non-existent job", job);
    }

    @Test
    public void updateCronJobLastRun_updatesTimestamp() {
        CronJob job = new CronJob("cron-1", "Job 1", "Prompt 1", "3600000");
        repository.saveCronJob(job);

        // Update the job's last run timestamp
        job.setLastRunTimestamp(5000L);
        repository.updateCronJob(job);

        CronJob updated = repository.getCronJob("cron-1");
        assertEquals(5000L, updated.getLastRunTimestamp());
    }

    @Test
    public void updateCronJobLastRun_nonExistentJob_doesNotCrash() {
        // Create a new job and update it (which saves it)
        CronJob job = new CronJob("non-existent", "Test", "Test", "3600000");
        job.setLastRunTimestamp(5000L);
        repository.updateCronJob(job);
        
        // Verify it was saved
        CronJob retrieved = repository.getCronJob("non-existent");
        assertNotNull("Job should exist", retrieved);
    }

    @Test
    public void deleteCronJob_removesJob() {
        repository.saveCronJob(new CronJob("cron-1", "Job 1", "Prompt 1", "3600000"));
        repository.saveCronJob(new CronJob("cron-2", "Job 2", "Prompt 2", "7200000"));

        repository.deleteCronJob("cron-1");

        List<CronJob> jobs = repository.getCronJobs();
        assertEquals("Should have 1 job remaining", 1, jobs.size());
        assertNull("Deleted job should not exist", repository.getCronJob("cron-1"));
    }

    @Test
    public void deleteCronJob_nonExistentId_doesNotCrash() {
        repository.deleteCronJob("non-existent");
    }

    @Test
    public void getEnabledCronJobs_returnsOnlyEnabledJobs() {
        CronJob job1 = new CronJob("cron-1", "Enabled Job", "Prompt 1", "3600000");
        job1.setEnabled(true);
        repository.saveCronJob(job1);

        CronJob job2 = new CronJob("cron-2", "Disabled Job", "Prompt 2", "7200000");
        job2.setEnabled(false);
        repository.saveCronJob(job2);

        List<CronJob> allJobs = repository.getCronJobs();
        List<CronJob> enabledJobs = new java.util.ArrayList<>();
        for (CronJob job : allJobs) {
            if (job.isEnabled()) {
                enabledJobs.add(job);
            }
        }
        
        assertEquals("Should have 1 enabled job", 1, enabledJobs.size());
        assertEquals("cron-1", enabledJobs.get(0).getId());
    }

    // ==================== TASK EXECUTION RECORDS TESTS ====================

    @Test
    public void saveExecutionRecord_savesAndRetrievesRecord() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(150);
        record.setIterations(3);
        repository.saveExecutionRecord(record);

        List<TaskExecutionRecord> records = repository.getExecutionHistory("task-1");

        assertEquals("Should have 1 record", 1, records.size());
        TaskExecutionRecord retrieved = records.get(0);
        assertEquals("task-1", retrieved.getTaskId());
        assertEquals("session-1", retrieved.getSessionId());
        assertTrue(retrieved.isSuccess());
        assertEquals(150, retrieved.getTokensUsed());
        assertEquals(3, retrieved.getIterations());
    }

    @Test
    public void getExecutionRecords_sortedByStartTimeDescending() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        record1.setEndTime(2000L);
        repository.saveExecutionRecord(record1);

        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_HEARTBEAT, 3000L);
        record2.setEndTime(4000L);
        repository.saveExecutionRecord(record2);

        TaskExecutionRecord record3 = new TaskExecutionRecord("task-3", "session-3", TaskResult.TYPE_HEARTBEAT, 2000L);
        record3.setEndTime(3000L);
        repository.saveExecutionRecord(record3);

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();
        List<TaskExecutionRecord> records = new java.util.ArrayList<>();
        for (TaskExecutionRecord r : allRecords) {
            if (r.getTaskType() == TaskResult.TYPE_HEARTBEAT) {
                records.add(r);
            }
        }
        Collections.sort(records, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));

        assertEquals(3, records.size());
        assertEquals("task-2", records.get(0).getTaskId());
        assertEquals("task-3", records.get(1).getTaskId());
        assertEquals("task-1", records.get(2).getTaskId());
    }

    @Test
    public void getExecutionRecords_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            TaskExecutionRecord record = new TaskExecutionRecord("task-" + i, "session-" + i, TaskResult.TYPE_HEARTBEAT, i * 1000L);
            record.setEndTime((i + 1) * 1000L);
            repository.saveExecutionRecord(record);
        }

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();
        List<TaskExecutionRecord> heartbeatRecords = new java.util.ArrayList<>();
        for (TaskExecutionRecord r : allRecords) {
            if (r.getTaskType() == TaskResult.TYPE_HEARTBEAT) {
                heartbeatRecords.add(r);
            }
        }
        Collections.sort(heartbeatRecords, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        List<TaskExecutionRecord> limitedRecords = heartbeatRecords.subList(0, Math.min(3, heartbeatRecords.size()));

        assertEquals("Should return only 3 records", 3, limitedRecords.size());
        assertEquals("task-4", limitedRecords.get(0).getTaskId());
        assertEquals("task-3", limitedRecords.get(1).getTaskId());
        assertEquals("task-2", limitedRecords.get(2).getTaskId());
    }

    @Test
    public void getRecentExecutionRecords_returnsAcrossAllTypes() {
        TaskExecutionRecord hbRecord = new TaskExecutionRecord("hb-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        hbRecord.setEndTime(2000L);
        repository.saveExecutionRecord(hbRecord);

        TaskExecutionRecord cronRecord = new TaskExecutionRecord("cron-1", "session-2", TaskResult.TYPE_CRON_JOB, 3000L);
        cronRecord.setEndTime(4000L);
        repository.saveExecutionRecord(cronRecord);

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();
        List<TaskExecutionRecord> recentRecords = new java.util.ArrayList<>(allRecords);
        Collections.sort(recentRecords, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));

        assertEquals("Should have 2 records", 2, recentRecords.size());
        // Should be sorted by start time descending
        assertEquals("cron-1", recentRecords.get(0).getTaskId());
        assertEquals("hb-1", recentRecords.get(1).getTaskId());
    }

    @Test
    public void getFailedExecutionRecords_returnsOnlyFailedRecords() {
        TaskExecutionRecord successRecord = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        successRecord.setEndTime(2000L);
        successRecord.setSuccess(true);
        repository.saveExecutionRecord(successRecord);

        TaskExecutionRecord failRecord = new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_HEARTBEAT, 3000L);
        failRecord.setEndTime(4000L);
        failRecord.setSuccess(false);
        failRecord.setErrorMessage("Timeout");
        repository.saveExecutionRecord(failRecord);

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();
        List<TaskExecutionRecord> failedRecords = new java.util.ArrayList<>();
        for (TaskExecutionRecord r : allRecords) {
            if (!r.isSuccess() && r.getTaskType() == TaskResult.TYPE_HEARTBEAT) {
                failedRecords.add(r);
            }
        }
        Collections.sort(failedRecords, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));

        assertEquals("Should have 1 failed record", 1, failedRecords.size());
        assertEquals("task-2", failedRecords.get(0).getTaskId());
        assertEquals("Timeout", failedRecords.get(0).getErrorMessage());
    }

    @Test
    public void clearExecutionRecords_clearsAllRecords() {
        repository.saveExecutionRecord(new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L));
        repository.saveExecutionRecord(new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_CRON_JOB, 2000L));

        repository.clearAllExecutionRecords();

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();

        assertEquals("Should have 0 records", 0, allRecords.size());
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void taskResultAndExecutionRecord_fullLifecycle() {
        // Create and save task result
        TaskResult result = new TaskResult("task-lifecycle", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat check completed");
        result.putMetadata("duration", "1500");
        result.putMetadata("status", "success");
        repository.saveTaskResult(result);

        // Create and save execution record
        TaskExecutionRecord record = new TaskExecutionRecord("task-lifecycle", "session-lifecycle", TaskResult.TYPE_HEARTBEAT, 1000L);
        record.setEndTime(2500L);
        record.setSuccess(true);
        record.setTokensUsed(100);
        record.setIterations(2);
        repository.saveExecutionRecord(record);

        // Verify both are retrievable
        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        List<TaskExecutionRecord> records = repository.getExecutionHistory("task-lifecycle");

        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("Should have 1 record", 1, records.size());

        TaskResult retrievedResult = results.get(0);
        TaskExecutionRecord retrievedRecord = records.get(0);

        assertEquals("task-lifecycle", retrievedResult.getId());
        assertEquals("task-lifecycle", retrievedRecord.getTaskId());
        assertEquals("success", retrievedResult.getMetadataValue("status"));
        assertTrue(retrievedRecord.isSuccess());
    }

    @Test
    public void cronJobAndTaskResult_cronJobExecution() {
        // Create cron job
        CronJob cronJob = new CronJob("cron-daily", "Daily Summary", "Generate daily summary", "86400000");
        repository.saveCronJob(cronJob);

        // Simulate execution
        TaskResult result = new TaskResult("cron-execution-1", TaskResult.TYPE_CRON_JOB, 5000L, "Daily summary generated");
        repository.saveTaskResult(result);

        TaskExecutionRecord record = new TaskExecutionRecord("cron-execution-1", "session-cron", TaskResult.TYPE_CRON_JOB, 5000L);
        record.setEndTime(6000L);
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        // Update cron job last run
        CronJob jobToUpdate = repository.getCronJob("cron-daily");
        jobToUpdate.setLastRunTimestamp(5000L);
        repository.updateCronJob(jobToUpdate);

        // Verify all data
        CronJob updatedJob = repository.getCronJob("cron-daily");
        assertEquals(5000L, updatedJob.getLastRunTimestamp());

        List<TaskResult> cronResults = repository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 1 cron result", 1, cronResults.size());

        List<TaskExecutionRecord> cronRecords = repository.getExecutionHistory("cron-execution-1");
        assertEquals("Should have 1 execution record", 1, cronRecords.size());
    }

    @Test
    public void persistence_acrossRepositoryInstances() {
        // Save data with first repository instance
        repository.saveTaskResult(new TaskResult("persistent-task", TaskResult.TYPE_HEARTBEAT, 1000L, "Persistent data"));
        repository.saveCronJob(new CronJob("persistent-cron", "Persistent Job", "Persistent prompt", "3600000"));

        // Create new repository instance
        TaskRepository newRepository = new TaskRepository(getApplicationContext());

        // Verify data persisted
        List<TaskResult> results = newRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Task result should persist", 1, results.size());

        List<CronJob> jobs = newRepository.getCronJobs();
        assertEquals("Cron job should persist", 1, jobs.size());
    }
}
