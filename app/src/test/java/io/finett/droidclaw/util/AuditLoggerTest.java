package io.finett.droidclaw.util;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Unit tests for AuditLogger.
 * Tests audit logging, retrieval, filtering, and pruning.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuditLoggerTest {

    private AuditLogger auditLogger;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        auditLogger = new AuditLogger(context);
    }

    // ========== AUDIT ENTRY CREATION TESTS ==========

    @Test
    public void testAuditEntryCreation() {
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry(
                "task-1",
                "cronjob",
                AuditLogger.AuditEntry.EVENT_EXECUTION,
                "start",
                "Task started: Test Job"
        );

        assertNotNull(entry);
        assertEquals("task-1", entry.getTaskId());
        assertEquals("cronjob", entry.getTaskType());
        assertEquals("execution", entry.getEventType());
        assertEquals("start", entry.getAction());
        assertEquals("Task started: Test Job", entry.getDetails());
        assertTrue(entry.getTimestamp() > 0);
    }

    @Test
    public void testAuditEntryDefaultConstructor() {
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry();

        assertNotNull(entry);
        assertTrue(entry.getTimestamp() > 0);
        assertNull(entry.getTaskId());
        assertNull(entry.getTaskType());
        assertNull(entry.getEventType());
        assertNull(entry.getAction());
        assertNull(entry.getDetails());
    }

    // ========== FACTORY METHODS TESTS ==========

    @Test
    public void testExecutionStartFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.executionStart(
                "task-1", "cronjob", "Test Job"
        );

        assertEquals("task-1", entry.getTaskId());
        assertEquals("cronjob", entry.getTaskType());
        assertEquals("execution", entry.getEventType());
        assertEquals("start", entry.getAction());
        assertTrue(entry.getDetails().contains("Task started"));
        assertTrue(entry.getDetails().contains("Test Job"));
    }

    @Test
    public void testExecutionSuccessFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.executionSuccess(
                "task-1", "heartbeat", "Heartbeat Check", 1500
        );

        assertEquals("task-1", entry.getTaskId());
        assertEquals("heartbeat", entry.getTaskType());
        assertEquals("execution", entry.getEventType());
        assertEquals("success", entry.getAction());
        assertTrue(entry.getDetails().contains("Task completed"));
        assertTrue(entry.getDetails().contains("1500ms"));
    }

    @Test
    public void testExecutionFailureFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.executionFailure(
                "task-1", "cronjob", "Server Log Checker", "Timeout exceeded"
        );

        assertEquals("task-1", entry.getTaskId());
        assertEquals("cronjob", entry.getTaskType());
        assertEquals("execution", entry.getEventType());
        assertEquals("failure", entry.getAction());
        assertTrue(entry.getDetails().contains("Task failed"));
        assertTrue(entry.getDetails().contains("Timeout exceeded"));
    }

    @Test
    public void testToolBlockedFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.toolBlocked(
                "task-1", "cronjob", "shell_exec"
        );

        assertEquals("task-1", entry.getTaskId());
        assertEquals("cronjob", entry.getTaskType());
        assertEquals("security", entry.getEventType());
        assertEquals("tool_blocked", entry.getAction());
        assertTrue(entry.getDetails().contains("shell_exec"));
    }

    @Test
    public void testResourceLimitExceededFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.resourceLimitExceeded(
                "task-1", "heartbeat", "maxTokenUsage", "75000"
        );

        assertEquals("task-1", entry.getTaskId());
        assertEquals("heartbeat", entry.getTaskType());
        assertEquals("resource_limit", entry.getEventType());
        assertEquals("limit_exceeded", entry.getAction());
        assertTrue(entry.getDetails().contains("maxTokenUsage"));
        assertTrue(entry.getDetails().contains("75000"));
    }

    @Test
    public void testEmergencyDisableFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.emergencyDisable(
                "Detected infinite loop"
        );

        assertNull(entry.getTaskId());
        assertEquals("system", entry.getTaskType());
        assertEquals("emergency", entry.getEventType());
        assertEquals("emergency_disable", entry.getAction());
        assertTrue(entry.getDetails().contains("Detected infinite loop"));
    }

    @Test
    public void testEmergencyEnableFactory() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.emergencyEnable(
                "Issue resolved by user"
        );

        assertNull(entry.getTaskId());
        assertEquals("system", entry.getTaskType());
        assertEquals("emergency", entry.getEventType());
        assertEquals("emergency_enable", entry.getAction());
        assertTrue(entry.getDetails().contains("Issue resolved by user"));
    }

    // ========== LOGGING TESTS ==========

    @Test
    public void testLogExecution() {
        AuditLogger.AuditEntry entry = AuditLogger.AuditEntry.executionStart(
                "task-1", "cronjob", "Test Job"
        );

        auditLogger.logExecution(entry);

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(1, entries.size());
        assertEquals("task-1", entries.get(0).getTaskId());
    }

    @Test
    public void testLogSecurityEvent() {
        auditLogger.logSecurityEvent(
                "task-1", "cronjob", "unauthorized_access", "Attempted to access /etc/passwd"
        );

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(1, entries.size());
        assertEquals("security", entries.get(0).getEventType());
        assertEquals("unauthorized_access", entries.get(0).getAction());
    }

    @Test
    public void testLogToolBlocked() {
        auditLogger.logToolBlocked("task-1", "cronjob", "shell_exec");

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(1, entries.size());
        assertEquals("security", entries.get(0).getEventType());
        assertTrue(entries.get(0).getDetails().contains("shell_exec"));
    }

    @Test
    public void testLogEmergencyEvent() {
        auditLogger.logEmergencyEvent("disable", "Critical security breach detected");

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(1, entries.size());
        assertEquals("emergency", entries.get(0).getEventType());
        assertEquals("disable", entries.get(0).getAction());
        assertTrue(entries.get(0).getDetails().contains("Critical security breach"));
    }

    // ========== RETRIEVAL TESTS ==========

    @Test
    public void testGetAllEntries() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-2", "heartbeat", "Heartbeat"));

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(2, entries.size());
    }

    @Test
    public void testGetAllEntriesWhenEmpty() {
        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertNotNull(entries);
        assertEquals(0, entries.size());
    }

    @Test
    public void testGetEntriesForTask() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));
        auditLogger.logExecution(AuditLogger.AuditEntry.executionSuccess("task-1", "cronjob", "Job 1", 1000));
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-2", "heartbeat", "Heartbeat"));

        List<AuditLogger.AuditEntry> task1Entries = auditLogger.getEntriesForTask("task-1");
        assertEquals(2, task1Entries.size());

        List<AuditLogger.AuditEntry> task2Entries = auditLogger.getEntriesForTask("task-2");
        assertEquals(1, task2Entries.size());
    }

    @Test
    public void testGetEntriesForTaskNotFound() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));

        List<AuditLogger.AuditEntry> entries = auditLogger.getEntriesForTask("non-existent");
        assertEquals(0, entries.size());
    }

    @Test
    public void testGetRecentEntries() {
        for (int i = 0; i < 10; i++) {
            auditLogger.logExecution(AuditLogger.AuditEntry.executionStart(
                    "task-" + i, "cronjob", "Job " + i
            ));
        }

        List<AuditLogger.AuditEntry> recent5 = auditLogger.getRecentEntries(5);
        assertEquals(5, recent5.size());

        List<AuditLogger.AuditEntry> recent20 = auditLogger.getRecentEntries(20);
        assertEquals(10, recent20.size()); // Only 10 entries exist
    }

    @Test
    public void testGetSecurityEvents() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));
        auditLogger.logToolBlocked("task-1", "cronjob", "shell_exec");
        auditLogger.logEmergencyEvent("disable", "Security breach");

        List<AuditLogger.AuditEntry> securityEvents = auditLogger.getSecurityEvents();
        assertEquals(2, securityEvents.size());

        // All should be security or emergency events
        for (AuditLogger.AuditEntry entry : securityEvents) {
            assertTrue(
                    "security".equals(entry.getEventType()) ||
                    "emergency".equals(entry.getEventType())
            );
        }
    }

    @Test
    public void testGetSecurityEventsWhenNoneExist() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));

        List<AuditLogger.AuditEntry> securityEvents = auditLogger.getSecurityEvents();
        assertEquals(0, securityEvents.size());
    }

    // ========== PRUNING TESTS ==========

    @Test
    public void testMaxEntriesPruning() {
        // Log more than MAX_ENTRIES (500)
        for (int i = 0; i < 550; i++) {
            auditLogger.logExecution(AuditLogger.AuditEntry.executionStart(
                    "task-" + i, "cronjob", "Job " + i
            ));
        }

        List<AuditLogger.AuditEntry> entries = auditLogger.getAllEntries();
        assertEquals(500, entries.size()); // Should be pruned to 500
    }

    // ========== CLEAR TESTS ==========

    @Test
    public void testClearAuditLog() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-2", "heartbeat", "Heartbeat"));

        assertEquals(2, auditLogger.getAllEntries().size());

        auditLogger.clearAuditLog();

        assertEquals(0, auditLogger.getAllEntries().size());
    }

    @Test
    public void testClearAuditLogWhenEmpty() {
        auditLogger.clearAuditLog();

        assertEquals(0, auditLogger.getAllEntries().size());
    }

    // ========== EVENT TYPE CONSTANTS TESTS ==========

    @Test
    public void testEventTypeConstants() {
        assertEquals("execution", AuditLogger.AuditEntry.EVENT_EXECUTION);
        assertEquals("security", AuditLogger.AuditEntry.EVENT_SECURITY);
        assertEquals("emergency", AuditLogger.AuditEntry.EVENT_EMERGENCY);
        assertEquals("resource_limit", AuditLogger.AuditEntry.EVENT_RESOURCE_LIMIT);
    }

    // ========== SETTERS TESTS ==========

    @Test
    public void testAuditEntrySetters() {
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry();

        entry.setTaskId("new-task");
        entry.setTaskType("heartbeat");
        entry.setEventType("execution");
        entry.setAction("success");
        entry.setDetails("Task completed successfully");
        entry.setTimestamp(1234567890L);

        assertEquals("new-task", entry.getTaskId());
        assertEquals("heartbeat", entry.getTaskType());
        assertEquals("execution", entry.getEventType());
        assertEquals("success", entry.getAction());
        assertEquals("Task completed successfully", entry.getDetails());
        assertEquals(1234567890L, entry.getTimestamp());
    }

    // ========== PERSISTENCE TESTS ==========

    @Test
    public void testEntriesPersistAcrossInstances() {
        auditLogger.logExecution(AuditLogger.AuditEntry.executionStart("task-1", "cronjob", "Job 1"));

        AuditLogger newLogger = new AuditLogger(context);
        List<AuditLogger.AuditEntry> entries = newLogger.getAllEntries();

        assertEquals(1, entries.size());
        assertEquals("task-1", entries.get(0).getTaskId());
    }

    // ========== SORTING TESTS ==========

    @Test
    public void testRecentEntriesSortedByTimestamp() {
        // Add entries with different timestamps
        AuditLogger.AuditEntry entry1 = new AuditLogger.AuditEntry(
                "task-1", "cronjob", "execution", "start", "Job 1"
        );
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AuditLogger.AuditEntry entry2 = new AuditLogger.AuditEntry(
                "task-2", "heartbeat", "execution", "start", "Heartbeat"
        );

        auditLogger.logExecution(entry1);
        auditLogger.logExecution(entry2);

        List<AuditLogger.AuditEntry> recent = auditLogger.getRecentEntries(10);
        assertEquals(2, recent.size());

        // Most recent should be first
        assertTrue(recent.get(0).getTimestamp() >= recent.get(1).getTimestamp());
    }

    @Test
    public void testEntriesForTaskSortedByTimestamp() {
        AuditLogger.AuditEntry entry1 = new AuditLogger.AuditEntry(
                "task-1", "cronjob", "execution", "start", "Start"
        );
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AuditLogger.AuditEntry entry2 = new AuditLogger.AuditEntry(
                "task-1", "cronjob", "execution", "success", "Success"
        );

        auditLogger.logExecution(entry1);
        auditLogger.logExecution(entry2);

        List<AuditLogger.AuditEntry> taskEntries = auditLogger.getEntriesForTask("task-1");
        assertEquals(2, taskEntries.size());

        // Most recent should be first
        assertTrue(taskEntries.get(0).getTimestamp() >= taskEntries.get(1).getTimestamp());
    }
}
