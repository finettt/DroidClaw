package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TaskModelTest {

    // ==================== SessionType TESTS ====================

    @Test
    public void sessionType_constants_haveCorrectValues() {
        assertEquals(0, SessionType.NORMAL);
        assertEquals(1, SessionType.HIDDEN_HEARTBEAT);
        assertEquals(2, SessionType.HIDDEN_CRON);
    }

    @Test
    public void sessionType_toString_returnsCorrectNames() {
        assertEquals("NORMAL", SessionType.toString(SessionType.NORMAL));
        assertEquals("HIDDEN_HEARTBEAT", SessionType.toString(SessionType.HIDDEN_HEARTBEAT));
        assertEquals("HIDDEN_CRON", SessionType.toString(SessionType.HIDDEN_CRON));
    }

    @Test
    public void sessionType_toString_handlesUnknownValue() {
        assertEquals("UNKNOWN(99)", SessionType.toString(99));
    }

    // ==================== TaskResult TESTS ====================

    @Test
    public void taskResult_constructor_setsAllProperties() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat OK");

        assertEquals("task-1", result.getId());
        assertEquals(TaskResult.TYPE_HEARTBEAT, result.getType());
        assertEquals(1000L, result.getTimestamp());
        assertEquals("Heartbeat OK", result.getContent());
        assertNotNull("Metadata should be initialized", result.getMetadata());
        assertTrue("Metadata should be empty initially", result.getMetadata().isEmpty());
    }

    @Test
    public void taskResult_setters_updateAllProperties() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Initial");

        result.setId("task-2");
        result.setType(TaskResult.TYPE_CRON_JOB);
        result.setTimestamp(2000L);
        result.setContent("Updated content");

        assertEquals("task-2", result.getId());
        assertEquals(TaskResult.TYPE_CRON_JOB, result.getType());
        assertEquals(2000L, result.getTimestamp());
        assertEquals("Updated content", result.getContent());
    }

    @Test
    public void taskResult_putMetadata_addsKeyValue() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Content");

        result.putMetadata("key1", "value1");

        assertEquals("value1", result.getMetadataValue("key1"));
        assertEquals(1, result.getMetadata().size());
    }

    @Test
    public void taskResult_getMetadataValue_returnsNull_forMissingKey() {
        TaskResult result = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Content");

        assertNull(result.getMetadataValue("nonexistent"));
    }

    @Test
    public void taskResult_typeToString_returnsCorrectNames() {
        assertEquals("HEARTBEAT", TaskResult.typeToString(TaskResult.TYPE_HEARTBEAT));
        assertEquals("CRON_JOB", TaskResult.typeToString(TaskResult.TYPE_CRON_JOB));
        assertEquals("MANUAL", TaskResult.typeToString(TaskResult.TYPE_MANUAL));
    }

    @Test
    public void taskResult_typeToString_handlesUnknownValue() {
        assertEquals("UNKNOWN(99)", TaskResult.typeToString(99));
    }

    // ==================== HeartbeatConfig TESTS ====================

    @Test
    public void heartbeatConfig_defaultConstructor_hasCorrectDefaults() {
        HeartbeatConfig config = new HeartbeatConfig();

        assertFalse("Should be disabled by default", config.isEnabled());
        assertEquals(30 * 60 * 1000L, config.getIntervalMillis());
        assertEquals(0, config.getLastRunTimestamp());
    }

    @Test
    public void heartbeatConfig_getDefaults_returnsDisabledConfig() {
        HeartbeatConfig config = HeartbeatConfig.getDefaults();

        assertFalse(config.isEnabled());
    }

    @Test
    public void heartbeatConfig_constructor_setsAllProperties() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 5000L);

        assertTrue(config.isEnabled());
        assertEquals(60000L, config.getIntervalMillis());
        assertEquals(5000L, config.getLastRunTimestamp());
    }

    @Test
    public void heartbeatConfig_setters_updateAllProperties() {
        HeartbeatConfig config = new HeartbeatConfig();

        config.setEnabled(true);
        config.setIntervalMillis(120000L);
        config.setLastRunTimestamp(10000L);

        assertTrue(config.isEnabled());
        assertEquals(120000L, config.getIntervalMillis());
        assertEquals(10000L, config.getLastRunTimestamp());
    }

    @Test
    public void heartbeatConfig_shouldRun_returnsTrue_whenEnabledAndIntervalPassed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 0L);

        assertTrue(config.shouldRun(120000L));
    }

    @Test
    public void heartbeatConfig_shouldRun_returnsFalse_whenDisabled() {
        HeartbeatConfig config = new HeartbeatConfig(false, 60000L, 0L);

        assertFalse(config.shouldRun(120000L));
    }

    @Test
    public void heartbeatConfig_shouldRun_returnsFalse_whenIntervalNotPassed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 50000L);

        assertFalse(config.shouldRun(60000L));
    }

    @Test
    public void heartbeatConfig_shouldRun_returnsTrue_whenExactIntervalReached() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 0L);

        assertTrue(config.shouldRun(60000L));
    }

    // ==================== CronJob TESTS ====================

    @Test
    public void cronJob_defaultConstructor_hasEmptyFields() {
        CronJob job = new CronJob();

        assertEquals("", job.getId());
        assertEquals("", job.getName());
        assertEquals("", job.getPrompt());
        assertEquals("", job.getSchedule());
        assertFalse(job.isEnabled());
        assertEquals(0, job.getLastRunTimestamp());
        assertEquals("", job.getModelReference());
    }

    @Test
    public void cronJob_constructor_setsRequiredFields() {
        CronJob job = new CronJob("job-1", "Daily Summary", "Summarize today's work", "86400000");

        assertEquals("job-1", job.getId());
        assertEquals("Daily Summary", job.getName());
        assertEquals("Summarize today's work", job.getPrompt());
        assertEquals("86400000", job.getSchedule());
        assertTrue("Should be enabled by default", job.isEnabled());
        assertEquals(0, job.getLastRunTimestamp());
        assertEquals("", job.getModelReference());
    }

    @Test
    public void cronJob_setters_updateAllProperties() {
        CronJob job = new CronJob();

        job.setId("job-2");
        job.setName("Updated Job");
        job.setPrompt("New prompt");
        job.setSchedule("3600000");
        job.setEnabled(false);
        job.setLastRunTimestamp(1000L);
        job.setModelReference("provider/model");

        assertEquals("job-2", job.getId());
        assertEquals("Updated Job", job.getName());
        assertEquals("New prompt", job.getPrompt());
        assertEquals("3600000", job.getSchedule());
        assertFalse(job.isEnabled());
        assertEquals(1000L, job.getLastRunTimestamp());
        assertEquals("provider/model", job.getModelReference());
    }

    @Test
    public void cronJob_shouldRun_returnsTrue_whenEnabledAndIntervalPassed() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "60000");
        job.setLastRunTimestamp(0L);

        assertTrue(job.shouldRun(120000L));
    }

    @Test
    public void cronJob_shouldRun_returnsFalse_whenDisabled() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "60000");
        job.setEnabled(false);
        job.setLastRunTimestamp(0L);

        assertFalse(job.shouldRun(120000L));
    }

    @Test
    public void cronJob_shouldRun_returnsFalse_whenIntervalNotPassed() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "60000");
        job.setLastRunTimestamp(50000L);

        assertFalse(job.shouldRun(60000L));
    }

    @Test
    public void cronJob_shouldRun_handlesCronExpression_withDefaultInterval() {
        CronJob job = new CronJob("job-1", "Test", "Prompt", "0 * * * *");
        job.setLastRunTimestamp(0L);

        // Should use 1 hour default interval for cron expressions
        // At exactly 1 hour, shouldRun returns true (>= comparison)
        assertTrue(job.shouldRun(60 * 60 * 1000L));
        // Before 1 hour, should return false
        assertFalse(job.shouldRun(60 * 60 * 1000L - 1));
    }

    // ==================== TaskExecutionRecord TESTS ====================

    @Test
    public void taskExecutionRecord_defaultConstructor_hasCorrectDefaults() {
        TaskExecutionRecord record = new TaskExecutionRecord();

        assertEquals("", record.getTaskId());
        assertEquals("", record.getSessionId());
        assertEquals(0, record.getTaskType());
        assertEquals(0, record.getStartTime());
        assertEquals(0, record.getEndTime());
        assertEquals(0, record.getDurationMillis());
        assertEquals(0, record.getTokensUsed());
        assertEquals(0, record.getIterations());
        assertFalse(record.isSuccess());
        assertNull(record.getErrorMessage());
    }

    @Test
    public void taskExecutionRecord_constructor_setsInitialFields() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);

        assertEquals("task-1", record.getTaskId());
        assertEquals("session-1", record.getSessionId());
        assertEquals(TaskResult.TYPE_HEARTBEAT, record.getTaskType());
        assertEquals(1000L, record.getStartTime());
        assertEquals(0, record.getEndTime());
        assertEquals(0, record.getDurationMillis());
        assertFalse(record.isSuccess());
        assertNull(record.getErrorMessage());
    }

    @Test
    public void taskExecutionRecord_setters_updateAllProperties() {
        TaskExecutionRecord record = new TaskExecutionRecord();

        record.setTaskId("task-2");
        record.setSessionId("session-2");
        record.setTaskType(TaskResult.TYPE_CRON_JOB);
        record.setStartTime(1000L);
        record.setEndTime(2000L);
        record.setDurationMillis(1000L);
        record.setTokensUsed(500);
        record.setIterations(3);
        record.setSuccess(true);
        record.setErrorMessage(null);

        assertEquals("task-2", record.getTaskId());
        assertEquals("session-2", record.getSessionId());
        assertEquals(TaskResult.TYPE_CRON_JOB, record.getTaskType());
        assertEquals(1000L, record.getStartTime());
        assertEquals(2000L, record.getEndTime());
        assertEquals(1000L, record.getDurationMillis());
        assertEquals(500, record.getTokensUsed());
        assertEquals(3, record.getIterations());
        assertTrue(record.isSuccess());
        assertNull(record.getErrorMessage());
    }

    @Test
    public void taskExecutionRecord_complete_setsEndTimeAndSuccess() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);

        record.complete(3000L);

        assertEquals(3000L, record.getEndTime());
        assertEquals(2000L, record.getDurationMillis());
        assertTrue(record.isSuccess());
        assertNull(record.getErrorMessage());
    }

    @Test
    public void taskExecutionRecord_fail_setsEndTimeAndError() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);

        record.fail(2500L, "Timeout error");

        assertEquals(2500L, record.getEndTime());
        assertEquals(1500L, record.getDurationMillis());
        assertFalse(record.isSuccess());
        assertEquals("Timeout error", record.getErrorMessage());
    }

    @Test
    public void taskExecutionRecord_setEndTime_calculatesDuration() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", TaskResult.TYPE_HEARTBEAT, 1000L);

        record.setEndTime(5000L);

        assertEquals(4000L, record.getDurationMillis());
    }

    // ==================== ChatSession EXTENSION TESTS ====================

    @Test
    public void chatSession_defaultHasNormalType() {
        ChatSession session = new ChatSession("session-1", "Test", 100L);

        assertEquals(SessionType.NORMAL, session.getSessionType());
        assertFalse(session.isHidden());
        assertNull(session.getParentTaskId());
    }

    @Test
    public void chatSession_setSessionType_changesVisibility() {
        ChatSession session = new ChatSession("session-1", "Test", 100L);

        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        assertEquals(SessionType.HIDDEN_HEARTBEAT, session.getSessionType());
        assertTrue(session.isHidden());
    }

    @Test
    public void chatSession_hiddenCron_isHidden() {
        ChatSession session = new ChatSession("session-1", "Test", 100L);
        session.setSessionType(SessionType.HIDDEN_CRON);

        assertTrue(session.isHidden());
    }

    @Test
    public void chatSession_normal_isNotHidden() {
        ChatSession session = new ChatSession("session-1", "Test", 100L);

        assertFalse(session.isHidden());
    }

    @Test
    public void chatSession_parentTaskId_canBeSet() {
        ChatSession session = new ChatSession("session-1", "Test", 100L);

        session.setParentTaskId("cron-job-1");

        assertEquals("cron-job-1", session.getParentTaskId());
    }
}
