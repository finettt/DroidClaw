package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.*;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for TaskManagementTool.
 * Tests task management operations via the tool interface.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TaskManagementToolTest {

    private TaskManagementTool taskManagementTool;
    private Context context;
    private TaskRepository taskRepository;
    private Gson gson;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        taskManagementTool = new TaskManagementTool(context);
        taskRepository = new TaskRepository(context);
        gson = new Gson();
    }

    // ========== TOOL DEFINITION TESTS ==========

    @Test
    public void testToolName() {
        assertEquals("manage_tasks", taskManagementTool.getName());
    }

    @Test
    public void testToolDefinition() {
        assertNotNull(taskManagementTool.getDefinition());
        assertEquals("manage_tasks", taskManagementTool.getDefinition().getFunction().getName());
        assertNotNull(taskManagementTool.getDefinition().getFunction().getDescription());
        assertNotNull(taskManagementTool.getDefinition().getFunction().getParameters());
    }

    // ========== ACTION VALIDATION TESTS ==========

    @Test
    public void testExecuteWithNullAction() {
        JsonObject arguments = new JsonObject();

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter: action"));
    }

    @Test
    public void testExecuteWithUnknownAction() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "unknown_action");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Unknown action"));
    }

    // ========== CREATE TASK TESTS ==========

    @Test
    public void testCreateTaskWithValidArguments() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("name", "Test Job");
        arguments.addProperty("prompt", "Test prompt");
        arguments.addProperty("interval", "1h");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Test Job"));
        assertTrue(result.getContent().contains("1 hour"));

        // Verify task was created
        assertEquals(1, taskRepository.getTotalCronJobs());
    }

    @Test
    public void testCreateTaskMissingName() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("prompt", "Test prompt");
        arguments.addProperty("interval", "1h");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("name"));
    }

    @Test
    public void testCreateTaskMissingPrompt() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("name", "Test Job");
        arguments.addProperty("interval", "1h");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("prompt"));
    }

    @Test
    public void testCreateTaskMissingInterval() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("name", "Test Job");
        arguments.addProperty("prompt", "Test prompt");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("interval"));
    }

    @Test
    public void testCreateTaskInvalidInterval() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("name", "Test Job");
        arguments.addProperty("prompt", "Test prompt");
        arguments.addProperty("interval", "invalid");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid interval"));
    }

    @Test
    public void testCreateTaskDuplicateName() {
        // Create first task
        JsonObject arguments1 = new JsonObject();
        arguments1.addProperty("action", "create");
        arguments1.addProperty("name", "Duplicate Job");
        arguments1.addProperty("prompt", "First prompt");
        arguments1.addProperty("interval", "1h");
        taskManagementTool.execute(arguments1);

        // Try to create second task with same name
        JsonObject arguments2 = new JsonObject();
        arguments2.addProperty("action", "create");
        arguments2.addProperty("name", "Duplicate Job");
        arguments2.addProperty("prompt", "Second prompt");
        arguments2.addProperty("interval", "2h");

        ToolResult result = taskManagementTool.execute(arguments2);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("already exists"));
    }

    @Test
    public void testCreateTaskWithOptionalSettings() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "create");
        arguments.addProperty("name", "Custom Job");
        arguments.addProperty("prompt", "Custom prompt");
        arguments.addProperty("interval", "30min");
        arguments.addProperty("require_network", false);
        arguments.addProperty("notify_on_success", false);
        arguments.addProperty("notify_on_failure", false);

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());

        // Verify settings
        CronJob job = taskRepository.getAllCronJobs().get(0);
        assertFalse(job.isRequireNetwork());
        assertFalse(job.isNotifyOnSuccess());
        assertFalse(job.isNotifyOnFailure());
    }

    // ========== INTERVAL PARSING TESTS ==========

    @Test
    public void testCreateTaskWithAllValidIntervals() {
        String[] validIntervals = {"15min", "30min", "1h", "2h", "4h", "6h", "12h", "1d"};
        long[] expectedMinutes = {15, 30, 60, 120, 240, 360, 720, 1440};

        for (int i = 0; i < validIntervals.length; i++) {
            JsonObject arguments = new JsonObject();
            arguments.addProperty("action", "create");
            arguments.addProperty("name", "Job " + i);
            arguments.addProperty("prompt", "Prompt " + i);
            arguments.addProperty("interval", validIntervals[i]);

            ToolResult result = taskManagementTool.execute(arguments);

            assertTrue("Failed for interval: " + validIntervals[i], result.isSuccess());

            CronJob job = taskRepository.getAllCronJobs().get(i);
            assertEquals("Wrong minutes for: " + validIntervals[i],
                    expectedMinutes[i], job.getIntervalMinutes());
        }
    }

    @Test
    public void testCreateTaskWithAlternativeIntervalFormats() {
        String[][] intervals = {
                {"15m", "15min"},
                {"30m", "30min"},
                {"1hour", "1h"},
                {"60min", "1h"},
                {"2hours", "2h"},
                {"4hours", "4h"},
                {"6hours", "6h"},
                {"12hours", "12h"},
                {"1day", "1d"},
                {"24h", "1d"}
        };

        for (String[] interval : intervals) {
            JsonObject arguments = new JsonObject();
            arguments.addProperty("action", "create");
            arguments.addProperty("name", "Job " + interval[0]);
            arguments.addProperty("prompt", "Prompt");
            arguments.addProperty("interval", interval[0]);

            ToolResult result = taskManagementTool.execute(arguments);
            assertTrue("Failed for: " + interval[0], result.isSuccess());
        }
    }

    // ========== LIST TASKS TESTS ==========

    @Test
    public void testListTasksWhenEmpty() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "list");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("No scheduled tasks"));
    }

    @Test
    public void testListTasksWithOneJob() {
        // Create a task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Test Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        // List tasks
        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("action", "list");

        ToolResult result = taskManagementTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Test Job"));
        assertTrue(result.getContent().contains("Scheduled Tasks (1)"));
    }

    @Test
    public void testListTasksWithMultipleJobs() {
        // Create multiple tasks
        String[] names = {"Job 1", "Job 2", "Job 3"};
        for (String name : names) {
            JsonObject createArgs = new JsonObject();
            createArgs.addProperty("action", "create");
            createArgs.addProperty("name", name);
            createArgs.addProperty("prompt", "Prompt for " + name);
            createArgs.addProperty("interval", "1h");
            taskManagementTool.execute(createArgs);
        }

        // List tasks
        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("action", "list");

        ToolResult result = taskManagementTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Scheduled Tasks (3)"));
        assertTrue(result.getContent().contains("Job 1"));
        assertTrue(result.getContent().contains("Job 2"));
        assertTrue(result.getContent().contains("Job 3"));
    }

    // ========== PAUSE TASK TESTS ==========

    @Test
    public void testPauseTask() {
        // Create task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Pause Test Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        // Pause task
        JsonObject pauseArgs = new JsonObject();
        pauseArgs.addProperty("action", "pause");
        pauseArgs.addProperty("name", "Pause Test Job");

        ToolResult result = taskManagementTool.execute(pauseArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("paused"));

        // Verify task is disabled
        CronJob job = taskRepository.getAllCronJobs().get(0);
        assertFalse(job.isEnabled());
    }

    @Test
    public void testPauseTaskAlreadyPaused() {
        // Create and pause task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Already Paused Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        JsonObject pauseArgs1 = new JsonObject();
        pauseArgs1.addProperty("action", "pause");
        pauseArgs1.addProperty("name", "Already Paused Job");
        taskManagementTool.execute(pauseArgs1);

        // Try to pause again
        JsonObject pauseArgs2 = new JsonObject();
        pauseArgs2.addProperty("action", "pause");
        pauseArgs2.addProperty("name", "Already Paused Job");

        ToolResult result = taskManagementTool.execute(pauseArgs2);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("already paused"));
    }

    @Test
    public void testPauseTaskMissingName() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "pause");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("name"));
    }

    @Test
    public void testPauseTaskNotFound() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "pause");
        arguments.addProperty("name", "Non-existent Job");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    // ========== RESUME TASK TESTS ==========

    @Test
    public void testResumeTask() {
        // Create and pause task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Resume Test Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        JsonObject pauseArgs = new JsonObject();
        pauseArgs.addProperty("action", "pause");
        pauseArgs.addProperty("name", "Resume Test Job");
        taskManagementTool.execute(pauseArgs);

        // Resume task
        JsonObject resumeArgs = new JsonObject();
        resumeArgs.addProperty("action", "resume");
        resumeArgs.addProperty("name", "Resume Test Job");

        ToolResult result = taskManagementTool.execute(resumeArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("resumed"));

        // Verify task is enabled
        CronJob job = taskRepository.getAllCronJobs().get(0);
        assertTrue(job.isEnabled());
    }

    @Test
    public void testResumeTaskAlreadyActive() {
        // Create task (active by default)
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Already Active Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        // Try to resume
        JsonObject resumeArgs = new JsonObject();
        resumeArgs.addProperty("action", "resume");
        resumeArgs.addProperty("name", "Already Active Job");

        ToolResult result = taskManagementTool.execute(resumeArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("already active"));
    }

    @Test
    public void testResumeTaskMissingName() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "resume");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("name"));
    }

    @Test
    public void testResumeTaskNotFound() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "resume");
        arguments.addProperty("name", "Non-existent Job");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    // ========== DELETE TASK TESTS ==========

    @Test
    public void testDeleteTask() {
        // Create task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "Delete Test Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        assertEquals(1, taskRepository.getTotalCronJobs());

        // Delete task
        JsonObject deleteArgs = new JsonObject();
        deleteArgs.addProperty("action", "delete");
        deleteArgs.addProperty("name", "Delete Test Job");

        ToolResult result = taskManagementTool.execute(deleteArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("deleted"));

        // Verify task was deleted
        assertEquals(0, taskRepository.getTotalCronJobs());
    }

    @Test
    public void testDeleteTaskMissingName() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "delete");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("name"));
    }

    @Test
    public void testDeleteTaskNotFound() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "delete");
        arguments.addProperty("name", "Non-existent Job");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    // ========== TASK STATUS TESTS ==========

    @Test
    public void testTaskStatusWhenEmpty() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "status");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Task Manager Status"));
        assertTrue(result.getContent().contains("Total tasks:** 0"));
    }

    @Test
    public void testTaskStatusWithTasks() {
        // Create active task
        JsonObject createArgs1 = new JsonObject();
        createArgs1.addProperty("action", "create");
        createArgs1.addProperty("name", "Active Job");
        createArgs1.addProperty("prompt", "Test prompt");
        createArgs1.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs1);

        // Create and pause task
        JsonObject createArgs2 = new JsonObject();
        createArgs2.addProperty("action", "create");
        createArgs2.addProperty("name", "Paused Job");
        createArgs2.addProperty("prompt", "Test prompt");
        createArgs2.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs2);

        JsonObject pauseArgs = new JsonObject();
        pauseArgs.addProperty("action", "pause");
        pauseArgs.addProperty("name", "Paused Job");
        taskManagementTool.execute(pauseArgs);

        // Check status
        JsonObject statusArgs = new JsonObject();
        statusArgs.addProperty("action", "status");

        ToolResult result = taskManagementTool.execute(statusArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Total tasks:** 2"));
        assertTrue(result.getContent().contains("Active:** 1"));
        assertTrue(result.getContent().contains("Paused:** 1"));
    }

    // ========== EMERGENCY CONTROLS TESTS ==========

    @Test
    public void testEmergencyDisable() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "emergency_disable");
        arguments.addProperty("reason", "Security breach detected");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Emergency Disable Activated"));
        assertTrue(result.getContent().contains("Security breach detected"));
    }

    @Test
    public void testEmergencyDisableMissingReason() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "emergency_disable");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("reason"));
    }

    @Test
    public void testEmergencyEnable() {
        // First disable
        JsonObject disableArgs = new JsonObject();
        disableArgs.addProperty("action", "emergency_disable");
        disableArgs.addProperty("reason", "Test reason");
        taskManagementTool.execute(disableArgs);

        // Then enable
        JsonObject enableArgs = new JsonObject();
        enableArgs.addProperty("action", "emergency_enable");

        ToolResult result = taskManagementTool.execute(enableArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Emergency Disable Deactivated"));
    }

    @Test
    public void testEmergencyEnableWhenNotDisabled() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "emergency_enable");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("not currently active"));
    }

    // ========== AUDIT LOG TESTS ==========

    @Test
    public void testViewAuditLogWhenEmpty() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "audit_log");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("No audit log entries"));
    }

    @Test
    public void testViewAuditLogWithEntries() {
        // Create a task (generates audit entries via emergency disable)
        JsonObject disableArgs = new JsonObject();
        disableArgs.addProperty("action", "emergency_disable");
        disableArgs.addProperty("reason", "Test");
        taskManagementTool.execute(disableArgs);

        JsonObject enableArgs = new JsonObject();
        enableArgs.addProperty("action", "emergency_enable");
        taskManagementTool.execute(enableArgs);

        // View audit log
        JsonObject auditArgs = new JsonObject();
        auditArgs.addProperty("action", "audit_log");

        ToolResult result = taskManagementTool.execute(auditArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Audit Log"));
    }

    @Test
    public void testViewAuditLogWithMaxEntries() {
        // Generate multiple entries
        for (int i = 0; i < 5; i++) {
            JsonObject disableArgs = new JsonObject();
            disableArgs.addProperty("action", "emergency_disable");
            disableArgs.addProperty("reason", "Reason " + i);
            taskManagementTool.execute(disableArgs);

            JsonObject enableArgs = new JsonObject();
            enableArgs.addProperty("action", "emergency_enable");
            taskManagementTool.execute(enableArgs);
        }

        // View only 3 entries
        JsonObject auditArgs = new JsonObject();
        auditArgs.addProperty("action", "audit_log");
        auditArgs.addProperty("max_entries", 3);

        ToolResult result = taskManagementTool.execute(auditArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Last 3 entries"));
    }

    // ========== SECURITY CONFIG TESTS ==========

    @Test
    public void testViewSecurityConfig() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "security_config");

        ToolResult result = taskManagementTool.execute(arguments);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Task Security Configuration"));
        assertTrue(result.getContent().contains("Sandbox Settings"));
        assertTrue(result.getContent().contains("Resource Limits"));
    }

    @Test
    public void testViewSecurityConfigWithEmergencyActive() {
        // Activate emergency disable
        JsonObject disableArgs = new JsonObject();
        disableArgs.addProperty("action", "emergency_disable");
        disableArgs.addProperty("reason", "Test emergency");
        taskManagementTool.execute(disableArgs);

        // View security config
        JsonObject configArgs = new JsonObject();
        configArgs.addProperty("action", "security_config");

        ToolResult result = taskManagementTool.execute(configArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("EMERGENCY DISABLE ACTIVE"));
        assertTrue(result.getContent().contains("Test emergency"));
    }

    // ========== HISTORY TESTS ==========

    @Test
    public void testViewHistoryWhenEmpty() {
        // Create task
        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("action", "create");
        createArgs.addProperty("name", "History Test Job");
        createArgs.addProperty("prompt", "Test prompt");
        createArgs.addProperty("interval", "1h");
        taskManagementTool.execute(createArgs);

        // View history
        JsonObject historyArgs = new JsonObject();
        historyArgs.addProperty("action", "history");
        historyArgs.addProperty("name", "History Test Job");

        ToolResult result = taskManagementTool.execute(historyArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("No execution history"));
    }

    @Test
    public void testViewHistoryMissingName() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "history");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("name"));
    }

    @Test
    public void testViewHistoryNotFound() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("action", "history");
        arguments.addProperty("name", "Non-existent Job");

        ToolResult result = taskManagementTool.execute(arguments);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }
}
