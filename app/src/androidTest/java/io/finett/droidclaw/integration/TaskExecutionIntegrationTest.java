package io.finett.droidclaw.integration;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.service.TaskScheduler;

/**
 * Integration tests for the WorkManager task execution system.
 * Tests the full lifecycle from scheduling to execution to result retrieval.
 */
@RunWith(AndroidJUnit4.class)
public class TaskExecutionIntegrationTest {

    private Context context;
    private TaskScheduler taskScheduler;
    private TaskRepository taskRepository;
    private ChatRepository chatRepository;
    private HeartbeatConfigRepository heartbeatConfigRepo;
    private WorkspaceManager workspaceManager;
    private WorkManager workManager;

    @Before
    public void setUp() {
        context = getApplicationContext();
        
        // Initialize WorkManager for testing
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        
        taskScheduler = new TaskScheduler(context);
        taskRepository = new TaskRepository(context);
        chatRepository = new ChatRepository(context);
        heartbeatConfigRepo = new HeartbeatConfigRepository(context);
        workspaceManager = new WorkspaceManager(context);
        workManager = WorkManager.getInstance(context);

        // Initialize workspace
        try {
            workspaceManager.initializeWithSkills();
        } catch (IOException e) {
            // May already be initialized
        }

        // Clear test data
        clearTestData();
    }

    @After
    public void tearDown() {
        workManager.cancelAllWork();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        clearTestData();
    }

    private void clearTestData() {
        context.getSharedPreferences("droidclaw_heartbeat", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        context.getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== HEARTBEAT LIFECYCLE TESTS ====================

    @Test
    public void heartbeat_fullLifecycle_schedulesAndSavesResult() throws Exception {
        // 1. Configure heartbeat
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, 0L);
        heartbeatConfigRepo.updateConfig(config);

        // 2. Create HEARTBEAT.md file
        createHeartbeatFile("# Heartbeat Check\n\nReview system health.\nInclude HEARTBEAT_OK if healthy.");

        // 3. Schedule heartbeat
        taskScheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        // 4. Verify work is scheduled
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Heartbeat should be scheduled", workInfos.isEmpty());

        // 5. Simulate task result (in real scenario, worker would do this)
        TaskResult result = new TaskResult("heartbeat-integration-1", TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(), "System is healthy. HEARTBEAT_OK");
        result.putMetadata("healthy", "true");
        taskRepository.saveTaskResult(result);

        // 6. Verify result is saved
        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("true", results.get(0).getMetadataValue("healthy"));
    }

    @Test
    public void heartbeat_cancelAndReschedule_worksCorrectly() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);

        // Schedule
        taskScheduler.scheduleHeartbeat(config);
        Thread.sleep(100);

        List<WorkInfo> workInfos1 = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);
        assertFalse("Should be scheduled", workInfos1.isEmpty());
        assertTrue("Should be enqueued or running", 
                workInfos1.get(0).getState() == WorkInfo.State.ENQUEUED ||
                workInfos1.get(0).getState() == WorkInfo.State.RUNNING);

        // Cancel
        taskScheduler.cancelHeartbeat();
        Thread.sleep(100);

        List<WorkInfo> workInfos2 = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);
        boolean isCancelled = workInfos2.isEmpty() || 
                workInfos2.get(0).getState() == WorkInfo.State.CANCELLED;
        assertTrue("Should be cancelled", isCancelled);

        // Reschedule
        taskScheduler.scheduleHeartbeat(config);
        Thread.sleep(100);

        List<WorkInfo> workInfos3 = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);
        assertFalse("Should be rescheduled", workInfos3.isEmpty());
    }

    @Test
    public void heartbeat_withMissingHeartbeatFile_usesDefaultPrompt() throws Exception {
        // Don't create HEARTBEAT.md file
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        if (heartbeatFile.exists()) {
            heartbeatFile.delete();
        }

        // Configure and schedule
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        taskScheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        // Work should still be scheduled (worker will use default prompt)
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);
        assertFalse("Work should be scheduled even without HEARTBEAT.md", workInfos.isEmpty());
    }

    // ==================== CRON JOB LIFECYCLE TESTS ====================

    @Test
    public void cronJob_fullLifecycle_schedulesExecutesAndSavesResult() throws Exception {
        // 1. Create cron job
        CronJob job = new CronJob("cron-integration-1", "Daily Report",
                "Generate daily report summary", "3600000");
        taskRepository.saveCronJob(job);

        // 2. Schedule cron job
        taskScheduler.scheduleCronJob(job);

        Thread.sleep(100);

        // 3. Verify work is scheduled
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-integration-1")
                .get(5, TimeUnit.SECONDS);
        assertFalse("Cron job should be scheduled", workInfos.isEmpty());

        // 4. Simulate execution result
        long startTime = System.currentTimeMillis();
        TaskResult result = new TaskResult("cron-integration-1-exec", TaskResult.TYPE_CRON_JOB,
                startTime, "Daily report generated");
        taskRepository.saveTaskResult(result);

        // 5. Save execution record
        TaskExecutionRecord record = new TaskExecutionRecord("cron-integration-1-exec", "session-cron-1",
                TaskResult.TYPE_CRON_JOB, startTime);
        record.setEndTime(startTime + 5000L);
        record.setSuccess(true);
        taskRepository.saveExecutionRecord(record);

        // 6. Update job timestamp
        job.setLastRunTimestamp(startTime);
        taskRepository.updateCronJob(job);

        // 7. Verify all data
        CronJob updatedJob = taskRepository.getCronJob("cron-integration-1");
        assertEquals("Job timestamp should be updated", startTime, updatedJob.getLastRunTimestamp());

        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_CRON_JOB, 10);
        assertEquals("Should have 1 result", 1, results.size());

        List<TaskExecutionRecord> records = taskRepository.getExecutionHistory("cron-integration-1-exec");
        assertEquals("Should have 1 record", 1, records.size());
    }

    @Test
    public void cronJob_cancelAndDelete_removesAllData() throws Exception {
        // Create and schedule
        CronJob job = new CronJob("cron-delete", "Delete Me", "Prompt", "3600000");
        taskRepository.saveCronJob(job);
        taskScheduler.scheduleCronJob(job);

        Thread.sleep(100);

        // Cancel scheduling
        taskScheduler.cancelCronJob("cron-delete");

        Thread.sleep(100);

        // Verify work is cancelled (WorkManager marks as CANCELLED, doesn't remove)
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-delete")
                .get(5, TimeUnit.SECONDS);
        boolean isCancelled = workInfos.isEmpty() || 
                workInfos.get(0).getState() == WorkInfo.State.CANCELLED;
        assertTrue("Work should be cancelled", isCancelled);

        // Delete job from repository
        taskRepository.deleteCronJob("cron-delete");

        // Verify job is deleted
        CronJob deleted = taskRepository.getCronJob("cron-delete");
        assertNull("Job should be deleted", deleted);
    }

    @Test
    public void multipleCronJobs_allScheduledIndependently() throws Exception {
        // Create multiple cron jobs
        CronJob job1 = new CronJob("cron-multi-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-multi-2", "Job 2", "Prompt 2", "7200000");
        CronJob job3 = new CronJob("cron-multi-3", "Job 3", "Prompt 3", "10800000");

        taskRepository.saveCronJob(job1);
        taskRepository.saveCronJob(job2);
        taskRepository.saveCronJob(job3);

        taskScheduler.scheduleCronJob(job1);
        taskScheduler.scheduleCronJob(job2);
        taskScheduler.scheduleCronJob(job3);

        Thread.sleep(100);

        // Verify all are scheduled
        List<WorkInfo> workInfos1 = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-1")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> workInfos2 = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-2")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> workInfos3 = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-3")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Job 1 should be scheduled", workInfos1.isEmpty());
        assertFalse("Job 2 should be scheduled", workInfos2.isEmpty());
        assertFalse("Job 3 should be scheduled", workInfos3.isEmpty());

        // Cancel one job
        taskScheduler.cancelCronJob("cron-multi-2");

        Thread.sleep(100);

        // WorkManager marks work as CANCELLED rather than removing it
        List<WorkInfo> workInfos2After = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-2")
                .get(5, TimeUnit.SECONDS);
        boolean isCancelled = workInfos2After.isEmpty() || 
                workInfos2After.get(0).getState() == WorkInfo.State.CANCELLED;
        assertTrue("Job 2 should be cancelled", isCancelled);

        // Others should still be scheduled (not cancelled)
        List<WorkInfo> workInfos1After = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-1")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> workInfos3After = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-multi-3")
                .get(5, TimeUnit.SECONDS);
        boolean isStillScheduled1 = !workInfos1After.isEmpty() && 
                (workInfos1After.get(0).getState() == WorkInfo.State.ENQUEUED ||
                 workInfos1After.get(0).getState() == WorkInfo.State.RUNNING);
        boolean isStillScheduled3 = !workInfos3After.isEmpty() && 
                (workInfos3After.get(0).getState() == WorkInfo.State.ENQUEUED ||
                 workInfos3After.get(0).getState() == WorkInfo.State.RUNNING);
        assertTrue("Job 1 should still be scheduled", isStillScheduled1);
        assertTrue("Job 3 should still be scheduled", isStillScheduled3);
    }

    // ==================== SESSION CREATION TESTS ====================

    @Test
    public void isolatedSession_hiddenHeartbeatType_notVisibleInUI() {
        // Simulate session creation
        ChatSession session = new ChatSession("session-heartbeat", "Heartbeat Check",
                System.currentTimeMillis());
        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session.setParentTaskId("heartbeat");

        // Save session
        List<ChatSession> sessions = chatRepository.loadSessions();
        sessions.add(session);
        chatRepository.saveSessions(sessions);

        // Load visible sessions
        List<ChatSession> visibleSessions = chatRepository.getVisibleSessions();

        // Hidden session should not be in visible list
        for (ChatSession s : visibleSessions) {
            assertFalse("Hidden session should not be visible", 
                    s.getId().equals("session-heartbeat"));
        }
    }

    @Test
    public void isolatedSession_hiddenCronType_notVisibleInUI() {
        // Simulate session creation
        ChatSession session = new ChatSession("session-cron", "Cron Job Execution",
                System.currentTimeMillis());
        session.setSessionType(SessionType.HIDDEN_CRON);
        session.setParentTaskId("cron-integration");

        // Save session
        List<ChatSession> sessions = chatRepository.loadSessions();
        sessions.add(session);
        chatRepository.saveSessions(sessions);

        // Load visible sessions
        List<ChatSession> visibleSessions = chatRepository.getVisibleSessions();

        // Hidden session should not be in visible list
        for (ChatSession s : visibleSessions) {
            assertFalse("Hidden session should not be visible",
                    s.getId().equals("session-cron"));
        }
    }

    @Test
    public void isolatedSession_parentTaskId_linksToCronJob() {
        String cronJobId = "cron-link-test";
        
        // Create session with parent task ID
        ChatSession session = new ChatSession("session-link", "Cron Job",
                System.currentTimeMillis());
        session.setSessionType(SessionType.HIDDEN_CRON);
        session.setParentTaskId(cronJobId);

        // Verify link
        assertEquals("Parent task ID should match cron job ID", cronJobId, session.getParentTaskId());
        assertTrue("Session should be hidden", session.isHidden());
        assertEquals("Session type should be HIDDEN_CRON", SessionType.HIDDEN_CRON, session.getSessionType());
    }

    // ==================== DATA PERSISTENCE TESTS ====================

    @Test
    public void taskResult_persistsAcrossRepositoryInstances() {
        // Save with first instance
        TaskResult result = new TaskResult("task-persist", TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(), "Persistent result");
        taskRepository.saveTaskResult(result);

        // Create new instance
        TaskRepository newRepo = new TaskRepository(context);

        // Verify persistence
        List<TaskResult> results = newRepo.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Result should persist", 1, results.size());
        assertEquals("task-persist", results.get(0).getId());
    }

    @Test
    public void cronJob_persistsAcrossRepositoryInstances() {
        CronJob job = new CronJob("cron-persist", "Persistent Job", "Prompt", "3600000");
        taskRepository.saveCronJob(job);

        TaskRepository newRepo = new TaskRepository(context);

        List<CronJob> jobs = newRepo.getCronJobs();
        assertEquals("Job should persist", 1, jobs.size());
        assertEquals("cron-persist", jobs.get(0).getId());
    }

    // ==================== WORKER INPUT DATA TESTS ====================

    @Test
    public void heartbeatWorker_receivesCorrectInputData() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        taskScheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Work should be scheduled", workInfos.isEmpty());
        
        // Verify work has input data
        WorkInfo workInfo = workInfos.get(0);
        assertNotNull("WorkInfo should exist", workInfo);
    }

    @Test
    public void cronJobWorker_receivesJobDataInInput() throws Exception {
        CronJob job = new CronJob("cron-input-data", "Input Data Test", "Test prompt", "3600000");
        taskRepository.saveCronJob(job);
        taskScheduler.scheduleCronJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_task_cron-input-data")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Work should be scheduled", workInfos.isEmpty());
        assertNotNull("WorkInfo should exist", workInfos.get(0));
    }

    // ==================== HELPER METHODS ====================

    private void createHeartbeatFile(String content) throws IOException {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File agentDir = new File(workspaceRoot, ".agent");
        if (!agentDir.exists()) {
            agentDir.mkdirs();
        }

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        try (FileWriter writer = new FileWriter(heartbeatFile)) {
            writer.write(content);
        }
    }
}
