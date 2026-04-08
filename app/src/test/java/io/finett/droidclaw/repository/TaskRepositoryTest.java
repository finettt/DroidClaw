package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;

@RunWith(RobolectricTestRunner.class)
public class TaskRepositoryTest {

    private TaskRepository repository;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        repository = new TaskRepository(context);
        sharedPreferences = context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
    }

    // ==================== TASK RESULT TESTS ====================

    @Test
    public void saveTaskResult_andGetTaskResults_persistsAndReturnsResults() {
        TaskResult result1 = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat OK");
        TaskResult result2 = new TaskResult("result-2", TaskResult.TYPE_HEARTBEAT, 2000L, "Heartbeat 2");

        repository.saveTaskResult(result1);
        repository.saveTaskResult(result2);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals(2, results.size());
        assertEquals("result-2", results.get(0).getId()); // Sorted by timestamp desc
        assertEquals("result-1", results.get(1).getId());
    }

    @Test
    public void getTaskResults_filtersByType() {
        TaskResult heartbeat = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat");
        TaskResult cronJob = new TaskResult("result-2", TaskResult.TYPE_CRON_JOB, 2000L, "Cron");

        repository.saveTaskResult(heartbeat);
        repository.saveTaskResult(cronJob);

        List<TaskResult> heartbeats = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        List<TaskResult> cronJobs = repository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);

        assertEquals(1, heartbeats.size());
        assertEquals("result-1", heartbeats.get(0).getId());
        assertEquals(1, cronJobs.size());
        assertEquals("result-2", cronJobs.get(0).getId());
    }

    @Test
    public void getTaskResults_appliesLimit() {
        for (int i = 0; i < 5; i++) {
            TaskResult result = new TaskResult("result-" + i, TaskResult.TYPE_HEARTBEAT, (i + 1) * 1000L, "Content");
            repository.saveTaskResult(result);
        }

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 3);

        assertEquals(3, results.size());
        assertEquals("result-4", results.get(0).getId());
    }

    @Test
    public void saveTaskResult_updatesExistingResult() {
        TaskResult result = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Initial");
        repository.saveTaskResult(result);

        result.setContent("Updated");
        repository.saveTaskResult(result);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals(1, results.size());
        assertEquals("Updated", results.get(0).getContent());
    }

    @Test
    public void deleteTaskResult_removesResult() {
        TaskResult result = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Content");
        repository.saveTaskResult(result);

        repository.deleteTaskResult("result-1");

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertTrue(results.isEmpty());
    }

    @Test
    public void getAllTaskResults_returnsAllTypes() {
        TaskResult heartbeat = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat");
        TaskResult cronJob = new TaskResult("result-2", TaskResult.TYPE_CRON_JOB, 2000L, "Cron");
        TaskResult manual = new TaskResult("result-3", TaskResult.TYPE_MANUAL, 3000L, "Manual");

        repository.saveTaskResult(heartbeat);
        repository.saveTaskResult(cronJob);
        repository.saveTaskResult(manual);

        List<TaskResult> all = repository.getAllTaskResults();

        assertEquals(3, all.size());
    }

    @Test
    public void taskResult_persistsMetadata() {
        TaskResult result = new TaskResult("result-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Content");
        result.putMetadata("key1", "value1");
        result.putMetadata("key2", "value2");

        repository.saveTaskResult(result);

        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals(1, results.size());
        assertEquals("value1", results.get(0).getMetadataValue("key1"));
        assertEquals("value2", results.get(0).getMetadataValue("key2"));
    }

    @Test
    public void getTaskResults_returnsEmptyList_whenNoResultsExist() {
        List<TaskResult> results = repository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertTrue(results.isEmpty());
    }

    // ==================== CRON JOB TESTS ====================

    @Test
    public void saveCronJob_andGetCronJobs_persistsAndReturnsJobs() {
        CronJob job1 = new CronJob("job-1", "Daily Summary", "Summarize", "86400000");
        CronJob job2 = new CronJob("job-2", "Weekly Report", "Report", "604800000");

        repository.saveCronJob(job1);
        repository.saveCronJob(job2);

        List<CronJob> jobs = repository.getCronJobs();

        assertEquals(2, jobs.size());
    }

    @Test
    public void saveCronJob_updatesExistingJob() {
        CronJob job = new CronJob("job-1", "Original", "Prompt", "3600000");
        repository.saveCronJob(job);

        job.setName("Updated");
        job.setPrompt("New prompt");
        repository.saveCronJob(job);

        CronJob loaded = repository.getCronJob("job-1");

        assertEquals("Updated", loaded.getName());
        assertEquals("New prompt", loaded.getPrompt());
    }

    @Test
    public void updateCronJob_persistsChanges() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "3600000");
        repository.saveCronJob(job);

        job.setEnabled(false);
        repository.updateCronJob(job);

        CronJob loaded = repository.getCronJob("job-1");

        assertFalse(loaded.isEnabled());
    }

    @Test
    public void deleteCronJob_removesJob() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "3600000");
        repository.saveCronJob(job);

        repository.deleteCronJob("job-1");

        List<CronJob> jobs = repository.getCronJobs();

        assertTrue(jobs.isEmpty());
    }

    @Test
    public void getCronJob_returnsSpecificJob() {
        CronJob job1 = new CronJob("job-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("job-2", "Job 2", "Prompt 2", "7200000");

        repository.saveCronJob(job1);
        repository.saveCronJob(job2);

        CronJob loaded = repository.getCronJob("job-1");

        assertNotNull(loaded);
        assertEquals("job-1", loaded.getId());
        assertEquals("Job 1", loaded.getName());
    }

    @Test
    public void getCronJob_returnsNull_whenNotFound() {
        CronJob loaded = repository.getCronJob("nonexistent");

        assertNull(loaded);
    }

    @Test
    public void getCronJobs_returnsEmptyList_whenNoJobsExist() {
        List<CronJob> jobs = repository.getCronJobs();

        assertTrue(jobs.isEmpty());
    }

    @Test
    public void cronJob_persistsAllFields() {
        CronJob job = new CronJob("job-1", "Test Job", "Test prompt", "3600000");
        job.setEnabled(false);
        job.setLastRunTimestamp(5000L);
        job.setModelReference("provider/model");

        repository.saveCronJob(job);

        CronJob loaded = repository.getCronJob("job-1");

        assertEquals("job-1", loaded.getId());
        assertEquals("Test Job", loaded.getName());
        assertEquals("Test prompt", loaded.getPrompt());
        assertEquals("3600000", loaded.getSchedule());
        assertFalse(loaded.isEnabled());
        assertEquals(5000L, loaded.getLastRunTimestamp());
        assertEquals("provider/model", loaded.getModelReference());
    }

    // ==================== EXECUTION RECORD TESTS ====================

    @Test
    public void saveExecutionRecord_andGetExecutionHistory_persistsAndReturnsRecords() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        record1.complete(2000L);
        record1.setTokensUsed(100);

        TaskExecutionRecord record2 = new TaskExecutionRecord("task-1", "session-2", TaskResult.TYPE_HEARTBEAT, 3000L);
        record2.complete(4000L);
        record2.setTokensUsed(200);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        List<TaskExecutionRecord> history = repository.getExecutionHistory("task-1");

        assertEquals(2, history.size());
        assertEquals("session-2", history.get(0).getSessionId()); // Sorted by startTime desc
        assertEquals("session-1", history.get(1).getSessionId());
    }

    @Test
    public void getExecutionHistory_filtersByTaskId() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_CRON_JOB, 2000L);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        List<TaskExecutionRecord> task1History = repository.getExecutionHistory("task-1");
        List<TaskExecutionRecord> task2History = repository.getExecutionHistory("task-2");

        assertEquals(1, task1History.size());
        assertEquals("session-1", task1History.get(0).getSessionId());
        assertEquals(1, task2History.size());
        assertEquals("session-2", task2History.get(0).getSessionId());
    }

    @Test
    public void saveExecutionRecord_persistsAllFields() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        record.setEndTime(3000L);
        record.setDurationMillis(2000L);
        record.setTokensUsed(500);
        record.setIterations(5);
        record.setSuccess(true);

        repository.saveExecutionRecord(record);

        List<TaskExecutionRecord> history = repository.getExecutionHistory("task-1");

        assertEquals(1, history.size());
        TaskExecutionRecord loaded = history.get(0);
        assertEquals("task-1", loaded.getTaskId());
        assertEquals("session-1", loaded.getSessionId());
        assertEquals(TaskResult.TYPE_HEARTBEAT, loaded.getTaskType());
        assertEquals(1000L, loaded.getStartTime());
        assertEquals(3000L, loaded.getEndTime());
        assertEquals(2000L, loaded.getDurationMillis());
        assertEquals(500, loaded.getTokensUsed());
        assertEquals(5, loaded.getIterations());
        assertTrue(loaded.isSuccess());
        // null errorMessage is serialized as "" in JSON
        assertEquals("", loaded.getErrorMessage());
    }

    @Test
    public void saveExecutionRecord_persistsErrorMessage() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        record.fail(2000L, "Timeout error");

        repository.saveExecutionRecord(record);

        List<TaskExecutionRecord> history = repository.getExecutionHistory("task-1");

        assertEquals(1, history.size());
        assertEquals("Timeout error", history.get(0).getErrorMessage());
        assertFalse(history.get(0).isSuccess());
    }

    @Test
    public void deleteExecutionRecords_removesRecordsForTask() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        TaskExecutionRecord record2 = new TaskExecutionRecord("task-1", "session-2", TaskResult.TYPE_HEARTBEAT, 2000L);
        TaskExecutionRecord record3 = new TaskExecutionRecord("task-2", "session-3", TaskResult.TYPE_CRON_JOB, 3000L);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);
        repository.saveExecutionRecord(record3);

        repository.deleteExecutionRecords("task-1");

        List<TaskExecutionRecord> task1History = repository.getExecutionHistory("task-1");
        List<TaskExecutionRecord> task2History = repository.getExecutionHistory("task-2");

        assertTrue(task1History.isEmpty());
        assertEquals(1, task2History.size());
    }

    @Test
    public void clearAllExecutionRecords_removesAllRecords() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_CRON_JOB, 2000L);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        repository.clearAllExecutionRecords();

        List<TaskExecutionRecord> allRecords = repository.getAllExecutionRecords();

        assertTrue(allRecords.isEmpty());
    }

    @Test
    public void getExecutionHistory_returnsEmptyList_whenNoRecordsExist() {
        List<TaskExecutionRecord> history = repository.getExecutionHistory("nonexistent");

        assertTrue(history.isEmpty());
    }

    @Test
    public void getAllExecutionRecords_returnsAllRecords() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);
        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", TaskResult.TYPE_CRON_JOB, 2000L);
        TaskExecutionRecord record3 = new TaskExecutionRecord("task-1", "session-3", TaskResult.TYPE_HEARTBEAT, 3000L);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);
        repository.saveExecutionRecord(record3);

        List<TaskExecutionRecord> all = repository.getAllExecutionRecords();

        assertEquals(3, all.size());
    }
}
