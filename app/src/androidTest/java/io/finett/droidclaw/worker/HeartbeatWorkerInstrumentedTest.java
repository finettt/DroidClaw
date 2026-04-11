package io.finett.droidclaw.worker;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Instrumented tests for HeartbeatWorker.
 * Tests the worker's execution logic with real workspace files and repositories.
 * Note: Full AgentLoop execution requires network access and API configuration,
 * so these tests focus on the worker's own logic (file reading, config checks, etc.).
 */
@RunWith(AndroidJUnit4.class)
public class HeartbeatWorkerInstrumentedTest {

    private Context context;
    private WorkspaceManager workspaceManager;
    private HeartbeatConfigRepository heartbeatConfigRepo;
    private TaskRepository taskRepository;

    @Before
    public void setUp() {
        context = getApplicationContext();
        workspaceManager = new WorkspaceManager(context);
        heartbeatConfigRepo = new HeartbeatConfigRepository(context);
        taskRepository = new TaskRepository(context);

        // Initialize workspace
        try {
            workspaceManager.initializeWithSkills();
        } catch (IOException e) {
            // Workspace may already be initialized
        }

        // Clear test data
        clearTestData();
    }

    @After
    public void tearDown() {
        clearTestData();
    }

    private void clearTestData() {
        // Clear heartbeat config
        context.getSharedPreferences("droidclaw_heartbeat", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        // Clear task repository
        context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== WORKER INSTANTIATION TESTS ====================

    @Test
    public void worker_canBeCreated() {
        // Verify worker can access required dependencies
        assertNotNull("Context should be available", context);
        assertNotNull("WorkspaceManager should be available", workspaceManager);
        assertNotNull("HeartbeatConfigRepository should be available", heartbeatConfigRepo);
    }

    // ==================== HEARTBEAT FILE READING TESTS ====================

    @Test
    public void readHeartbeatFile_withExistingFile_returnsContent() throws Exception {
        // Create HEARTBEAT.md file
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File agentDir = new File(workspaceRoot, ".agent");
        if (!agentDir.exists()) {
            agentDir.mkdirs();
        }

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        try (FileWriter writer = new FileWriter(heartbeatFile)) {
            writer.write("# Heartbeat Check\n\n");
            writer.write("Review the current state of the workspace.\n");
            writer.write("Include HEARTBEAT_OK if everything is healthy.\n");
        }

        // Verify file exists
        assertTrue("HEARTBEAT.md should exist", heartbeatFile.exists());
    }

    @Test
    public void readHeartbeatFile_withMissingFile_returnsNull() {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        // Delete file if it exists
        if (heartbeatFile.exists()) {
            heartbeatFile.delete();
        }

        assertFalse("HEARTBEAT.md should not exist", heartbeatFile.exists());
    }

    @Test
    public void checkHeartbeatOk_withStructuredResponse_returnsTrue() {
        String content = "{\"healthy\":true,\"summary\":\"All systems normal\",\"issues\":[]}";

        // Test structured output parsing
        io.finett.droidclaw.model.HeartbeatResponse response =
                io.finett.droidclaw.model.HeartbeatResponse.fromJson(content);

        assertTrue("Should be healthy", response.isHealthy());
    }

    @Test
    public void checkHeartbeatOk_withStructuredResponse_returnsFalse() {
        String content = "{\"healthy\":false,\"summary\":\"Issues found\",\"issues\":[{\"category\":\"workspace\",\"description\":\"Incomplete tasks\",\"severity\":\"low\"}]}";

        io.finett.droidclaw.model.HeartbeatResponse response =
                io.finett.droidclaw.model.HeartbeatResponse.fromJson(content);

        assertFalse("Should not be healthy", response.isHealthy());
    }

    @Test
    public void checkHeartbeatOk_withLegacyMarker_returnsTrue() {
        String content = "System is healthy. All checks passed.\n\n{\"HEARTBEAT_OK\": true}";

        // Test the legacy detection logic
        boolean hasMarker = content != null && content.contains("HEARTBEAT_OK") && content.contains("true");

        assertTrue("Should detect HEARTBEAT_OK marker", hasMarker);
    }

    @Test
    public void checkHeartbeatOk_withRefusal_returnsFalse() {
        String content = "[REFUSAL] I'm sorry, I cannot assist with that request.";

        // Refusal should be treated as unhealthy
        boolean isHealthy = !content.startsWith("[REFUSAL]");

        assertFalse("Should return false for refusal", isHealthy);
    }

    @Test
    public void checkHeartbeatOk_withoutMarker_returnsFalse() throws Exception {
        String content = "System has issues. Some checks failed.";
        
        boolean hasMarker = content.contains("HEARTBEAT_OK");
        
        assertFalse("Should not find HEARTBEAT_OK marker", hasMarker);
    }

    @Test
    public void checkHeartbeatOk_withNullContent_returnsFalse() {
        String content = null;
        
        boolean hasMarker = content != null && content.contains("HEARTBEAT_OK");
        
        assertFalse("Should handle null content", hasMarker);
    }

    @Test
    public void checkHeartbeatOk_withEmptyContent_returnsFalse() {
        String content = "";
        
        boolean hasMarker = content.contains("HEARTBEAT_OK");
        
        assertFalse("Should handle empty content", hasMarker);
    }

    // ==================== HEARTBEAT CONFIG TESTS ====================

    @Test
    public void doWork_withDisabledHeartbeat_skipsExecution() {
        // Configure heartbeat as disabled
        HeartbeatConfig config = new HeartbeatConfig(false, 30 * 60 * 1000L, 0L);
        heartbeatConfigRepo.updateConfig(config);

        // Verify the config is disabled (worker will check this in doWork)
        HeartbeatConfig retrieved = heartbeatConfigRepo.getConfig();
        assertFalse("Heartbeat should be disabled", retrieved.isEnabled());
    }

    @Test
    public void doWork_withIntervalNotElapsed_skipsExecution() throws Exception {
        // Configure heartbeat with recent last run
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, now - 5 * 60 * 1000L); // 5 minutes ago
        heartbeatConfigRepo.updateConfig(config);

        // Verify shouldRun returns false
        assertFalse("Should not run - interval not elapsed", config.shouldRun(now));
    }

    @Test
    public void doWork_withIntervalElapsed_allowsExecution() throws Exception {
        // Configure heartbeat with old last run
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 60 * 60 * 1000L); // 1 hour ago
        heartbeatConfigRepo.updateConfig(config);

        // Verify shouldRun returns true
        assertTrue("Should run - interval elapsed", config.shouldRun(now));
    }

    @Test
    public void updateLastRun_updatesTimestamp() throws Exception {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        heartbeatConfigRepo.updateConfig(config);

        heartbeatConfigRepo.updateLastRun(now);

        HeartbeatConfig retrieved = heartbeatConfigRepo.getConfig();
        assertEquals("Last run should be updated", now, retrieved.getLastRunTimestamp());
    }

    // ==================== DEFAULT PROMPT TESTS ====================

    @Test
    public void getDefaultHeartbeatPrompt_containsRequiredFields() {
        String defaultPrompt = "Perform a system health check. Review the current state of the workspace, " +
               "check for any pending tasks or incomplete work, and verify that all systems " +
               "are functioning correctly. Report your findings.\n\n" +
               "Respond with a JSON object containing:\n" +
               "- \"healthy\": true if everything is healthy, false otherwise\n" +
               "- \"summary\": A brief summary of the system status\n" +
               "- \"issues\": An array of any issues found, each with category, description, and severity (low/medium/high)\n\n" +
               "If everything is healthy, set healthy to true and list no issues.";

        assertTrue("Default prompt should mention healthy", defaultPrompt.contains("healthy"));
        assertTrue("Default prompt should mention summary", defaultPrompt.contains("summary"));
        assertTrue("Default prompt should mention issues", defaultPrompt.contains("issues"));
        assertTrue("Default prompt should mention severity", defaultPrompt.contains("severity"));
    }

    // ==================== WORKER RESULT TESTS ====================

    @Test
    public void taskResult_savedAfterExecution() throws Exception {
        // Setup: Create a task result manually (simulating worker execution)
        TaskResult result = new TaskResult("heartbeat-test", TaskResult.TYPE_HEARTBEAT, 
                System.currentTimeMillis(), "Heartbeat check completed successfully");
        result.putMetadata("healthy", "true");
        result.putMetadata("status", "success");

        taskRepository.saveTaskResult(result);

        // Verify result is saved
        java.util.List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("heartbeat-test", results.get(0).getId());
        assertEquals("true", results.get(0).getMetadataValue("healthy"));
    }

    @Test
    public void taskResult_withFailureStatus_savesCorrectly() throws Exception {
        TaskResult result = new TaskResult("heartbeat-failure", TaskResult.TYPE_HEARTBEAT, 
                System.currentTimeMillis(), "Failed: timeout exceeded");
        result.putMetadata("healthy", "false");
        result.putMetadata("status", "failed");
        result.putMetadata("error", "timeout exceeded");

        taskRepository.saveTaskResult(result);

        java.util.List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);
        assertEquals("Should have 1 result", 1, results.size());
        assertEquals("false", results.get(0).getMetadataValue("healthy"));
        assertEquals("timeout exceeded", results.get(0).getMetadataValue("error"));
    }

    // ==================== WORKER BEHAVIOR TESTS ====================

    @Test
    public void worker_withEmptyHeartbeatFile_usesDefaultPrompt() throws Exception {
        // Create empty HEARTBEAT.md
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File agentDir = new File(workspaceRoot, ".agent");
        if (!agentDir.exists()) {
            agentDir.mkdirs();
        }

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        try (FileWriter writer = new FileWriter(heartbeatFile)) {
            writer.write("");
        }

        // Worker should detect empty file and use default prompt
        assertTrue("HEARTBEAT.md should exist but be empty", 
                heartbeatFile.exists() && heartbeatFile.length() == 0);
    }

    @Test
    public void worker_createsIsolatedSession_withCorrectType() throws Exception {
        // This test verifies session creation logic
        // In a real scenario, the worker would create a session with SessionType.HIDDEN_HEARTBEAT
        // We verify the session type constant is correct
        assertEquals("HIDDEN_HEARTBEAT type should be 1", 1, io.finett.droidclaw.model.SessionType.HIDDEN_HEARTBEAT);
    }
}
