package io.finett.droidclaw.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.model.TaskResult;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskRepository.
 * Tests CRON JOBS, task records, and task results persistence.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskRepositoryTest {
    
    private TaskRepository repository;
    private Context context;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        repository = new TaskRepository(context);
        
        // Clear existing data by saving empty lists
        for (CronJob job : repository.getAllCronJobs()) {
            repository.deleteCronJob(job.getId());
        }
    }
    
    // ========== CRON JOBS TESTS ==========
    
    @Test
    public void testSaveCronJob() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        
        repository.saveCronJob(job);
        
        CronJob retrieved = repository.getCronJob(job.getId());
        assertNotNull(retrieved);
        assertEquals("Test Job", retrieved.getName());
        assertEquals("Test prompt", retrieved.getPrompt());
        assertEquals(60, retrieved.getIntervalMinutes());
    }
    
    @Test
    public void testGetAllCronJobs() {
        CronJob job1 = CronJob.create("Job 1", "Prompt 1", 30);
        CronJob job2 = CronJob.create("Job 2", "Prompt 2", 60);
        
        repository.saveCronJob(job1);
        repository.saveCronJob(job2);
        
        List<CronJob> jobs = repository.getAllCronJobs();
        assertEquals(2, jobs.size());
    }
    
    @Test
    public void testGetEnabledCronJobs() {
        CronJob job1 = CronJob.create("Job 1", "Prompt 1", 30);
        CronJob job2 = CronJob.create("Job 2", "Prompt 2", 60);
        job2.setEnabled(false);
        
        repository.saveCronJob(job1);
        repository.saveCronJob(job2);
        
        List<CronJob> enabled = repository.getEnabledCronJobs();
        assertEquals(1, enabled.size());
        assertTrue(enabled.get(0).isEnabled());
    }
    
    @Test
    public void testToggleCronJob() {
        CronJob job = CronJob.create("Test Job", "Prompt", 30);
        assertTrue(job.isEnabled());
        
        repository.saveCronJob(job);
        repository.toggleCronJob(job.getId());
        
        CronJob toggled = repository.getCronJob(job.getId());
        assertFalse(toggled.isEnabled());
        
        repository.toggleCronJob(job.getId());
        CronJob toggledBack = repository.getCronJob(job.getId());
        assertTrue(toggledBack.isEnabled());
    }
    
    @Test
    public void testDeleteCronJob() {
        CronJob job = CronJob.create("Test Job", "Prompt", 30);
        repository.saveCronJob(job);
        
        assertNotNull(repository.getCronJob(job.getId()));
        
        repository.deleteCronJob(job.getId());
        
        assertNull(repository.getCronJob(job.getId()));
    }
    
    @Test
    public void testDeleteCronJobDeletesTaskRecords() {
        CronJob job = CronJob.create("Test Job", "Prompt", 30);
        repository.saveCronJob(job);
        
        TaskRecord record = TaskRecord.create(job.getId(), job.getName(), "Prompt");
        record.markSuccess("Success");
        repository.saveTaskRecord(record);
        
        assertEquals(1, repository.getTaskRecordsForJob(job.getId()).size());
        
        repository.deleteCronJob(job.getId());
        
        assertEquals(0, repository.getTaskRecordsForJob(job.getId()).size());
    }
    
    @Test
    public void testUpdateCronJob() {
        CronJob job = CronJob.create("Original Name", "Original Prompt", 30);
        repository.saveCronJob(job);
        
        job.setName("Updated Name");
        job.setPrompt("Updated Prompt");
        job.setIntervalMinutes(120);
        repository.saveCronJob(job);
        
        CronJob retrieved = repository.getCronJob(job.getId());
        assertEquals("Updated Name", retrieved.getName());
        assertEquals("Updated Prompt", retrieved.getPrompt());
        assertEquals(120, retrieved.getIntervalMinutes());
    }
    
    // ========== TASK RECORDS TESTS ==========
    
    @Test
    public void testSaveTaskRecord() {
        TaskRecord record = TaskRecord.create("cron-123", "Test Job", "Test prompt");
        record.markSuccess("Success response");
        
        repository.saveTaskRecord(record);
        
        TaskRecord retrieved = repository.getTaskRecord(record.getId());
        assertNotNull(retrieved);
        assertEquals("cron-123", retrieved.getCronJobId());
        assertEquals("Test Job", retrieved.getCronJobName());
        assertTrue(retrieved.isSuccess());
    }
    
    @Test
    public void testGetAllTaskRecords() {
        TaskRecord record1 = TaskRecord.create("cron-1", "Job 1", "Prompt 1");
        TaskRecord record2 = TaskRecord.create("cron-2", "Job 2", "Prompt 2");
        record1.markSuccess("Success");
        record2.markFailure("Error");
        
        repository.saveTaskRecord(record1);
        repository.saveTaskRecord(record2);
        
        List<TaskRecord> records = repository.getAllTaskRecords();
        assertEquals(2, records.size());
    }
    
    @Test
    public void testGetTaskRecordsForJob() {
        TaskRecord record1 = TaskRecord.create("cron-1", "Job 1", "Prompt");
        TaskRecord record2 = TaskRecord.create("cron-1", "Job 1", "Prompt");
        TaskRecord record3 = TaskRecord.create("cron-2", "Job 2", "Prompt");
        record1.markSuccess("Success");
        record2.markSuccess("Success");
        record3.markSuccess("Success");
        
        repository.saveTaskRecord(record1);
        repository.saveTaskRecord(record2);
        repository.saveTaskRecord(record3);
        
        List<TaskRecord> job1Records = repository.getTaskRecordsForJob("cron-1");
        assertEquals(2, job1Records.size());
        
        List<TaskRecord> job2Records = repository.getTaskRecordsForJob("cron-2");
        assertEquals(1, job2Records.size());
    }
    
    @Test
    public void testGetRecentTaskRecords() {
        for (int i = 0; i < 10; i++) {
            TaskRecord record = TaskRecord.create("cron-1", "Job", "Prompt");
            record.markSuccess("Success " + i);
            repository.saveTaskRecord(record);
        }
        
        List<TaskRecord> recent = repository.getRecentTaskRecords(5);
        assertEquals(5, recent.size());
    }
    
    // ========== TASK RESULTS TESTS ==========
    
    @Test
    public void testSaveTaskResult() {
        TaskResult result = new TaskResult();
        result.setTaskType("cronjob");
        result.setTaskId("cron-123");
        result.setTaskName("Test Job");
        result.setResponse("Test response");
        result.setStatus("success");
        
        repository.saveTaskResult(result);
        
        TaskResult retrieved = repository.getTaskResult(result.getId());
        assertNotNull(retrieved);
        assertEquals("cronjob", retrieved.getTaskType());
        assertEquals("cron-123", retrieved.getTaskId());
        assertEquals("success", retrieved.getStatus());
    }
    
    @Test
    public void testGetAllTaskResults() {
        TaskResult result1 = TaskResult.fromHeartbeat("session-1", "Response 1", System.currentTimeMillis());
        TaskResult result2 = TaskResult.fromHeartbeat("session-2", "Response 2", System.currentTimeMillis());
        
        repository.saveTaskResult(result1);
        repository.saveTaskResult(result2);
        
        List<TaskResult> results = repository.getAllTaskResults();
        assertEquals(2, results.size());
    }
    
    @Test
    public void testGetUnviewedResults() {
        TaskResult result1 = new TaskResult();
        result1.setTaskType("heartbeat");
        result1.setUserViewed(false);
        
        TaskResult result2 = new TaskResult();
        result2.setTaskType("cronjob");
        result2.setUserViewed(true);
        
        repository.saveTaskResult(result1);
        repository.saveTaskResult(result2);
        
        List<TaskResult> unviewed = repository.getUnviewedResults();
        assertEquals(1, unviewed.size());
        assertFalse(unviewed.get(0).isUserViewed());
    }
    
    @Test
    public void testMarkResultViewed() {
        TaskResult result = new TaskResult();
        result.setTaskType("heartbeat");
        assertFalse(result.isUserViewed());
        
        repository.saveTaskResult(result);
        repository.markResultViewed(result.getId());
        
        TaskResult retrieved = repository.getTaskResult(result.getId());
        assertTrue(retrieved.isUserViewed());
    }
    
    // ========== STATISTICS TESTS ==========
    
    @Test
    public void testGetTotalCronJobs() {
        assertEquals(0, repository.getTotalCronJobs());
        
        repository.saveCronJob(CronJob.create("Job 1", "Prompt", 30));
        assertEquals(1, repository.getTotalCronJobs());
        
        repository.saveCronJob(CronJob.create("Job 2", "Prompt", 60));
        assertEquals(2, repository.getTotalCronJobs());
    }
    
    @Test
    public void testGetEnabledCronJobsCount() {
        CronJob job1 = CronJob.create("Job 1", "Prompt", 30);
        CronJob job2 = CronJob.create("Job 2", "Prompt", 60);
        job2.setEnabled(false);
        
        repository.saveCronJob(job1);
        repository.saveCronJob(job2);
        
        assertEquals(1, repository.getEnabledCronJobsCount());
    }
    
    @Test
    public void testGetTotalExecutions() {
        TaskRecord record1 = TaskRecord.create("cron-1", "Job", "Prompt");
        TaskRecord record2 = TaskRecord.create("cron-1", "Job", "Prompt");
        record1.markSuccess("Success");
        record2.markSuccess("Success");
        
        repository.saveTaskRecord(record1);
        repository.saveTaskRecord(record2);
        
        assertEquals(2, repository.getTotalExecutions());
    }
    
    @Test
    public void testGetOverallSuccessRate() {
        TaskRecord success1 = TaskRecord.create("cron-1", "Job", "Prompt");
        TaskRecord success2 = TaskRecord.create("cron-1", "Job", "Prompt");
        TaskRecord failure = TaskRecord.create("cron-1", "Job", "Prompt");
        success1.markSuccess("Success");
        success2.markSuccess("Success");
        failure.markFailure("Error");
        
        repository.saveTaskRecord(success1);
        repository.saveTaskRecord(success2);
        repository.saveTaskRecord(failure);
        
        int rate = repository.getOverallSuccessRate();
        assertEquals(66, rate); // 2/3 = 66%
    }
    
    @Test
    public void testGetOverallSuccessRateEmpty() {
        assertEquals(0, repository.getOverallSuccessRate());
    }
}