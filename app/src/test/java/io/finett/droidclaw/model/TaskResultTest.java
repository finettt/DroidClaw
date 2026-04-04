package io.finett.droidclaw.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskResult model.
 * Tests unified result model for heartbeat and cron job executions.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskResultTest {
    
    @Test
    public void testDefaultConstructor() {
        TaskResult result = new TaskResult();
        
        assertNotNull(result.getId());
        assertTrue(result.getExecutedAt() > 0);
        assertFalse(result.wasNotified());
        assertFalse(result.isUserViewed());
        assertFalse(result.isUserChatted());
    }
    
    @Test
    public void testFromCronJob() {
        CronJob job = CronJob.create("Test Job", "Test Prompt", 60);
        TaskRecord record = TaskRecord.create(job.getId(), job.getName(), "Execute task");
        record.setSessionId("session-123");
        record.setIterationCount(5);
        record.setToolCallsCount(10);
        record.setTokensUsed(1500);
        record.setNotificationTitle("Task Alert");
        record.setNotificationSummary("Found issues");
        record.markSuccess("Task completed");  // This sets response and status
        record.setWasNotified(true);
        record.setUserViewed(true);
        
        TaskResult result = TaskResult.fromCronJob(job, record);
        
        assertEquals("cronjob", result.getTaskType());
        assertEquals(job.getId(), result.getTaskId());
        assertEquals(job.getName(), result.getTaskName());
        assertEquals("session-123", result.getSessionId());
        assertEquals("Execute task", result.getPrompt());
        assertEquals("Task completed", result.getResponse());
        assertEquals(5, result.getIterationCount());
        assertEquals(10, result.getToolCallsCount());
        assertEquals(1500, result.getTokensUsed());
        assertEquals("success", result.getStatus());
        assertEquals("Task Alert", result.getNotificationTitle());
        assertEquals("Found issues", result.getNotificationSummary());
        assertTrue(result.wasNotified());
        assertTrue(result.isUserViewed());
        assertTrue(result.isCronJob());
        assertFalse(result.isHeartbeat());
    }
    
    @Test
    public void testFromHeartbeat() {
        String sessionId = "main-session";
        String response = "HEARTBEAT_ALERT: Critical error detected";
        long executedAt = System.currentTimeMillis();
        
        TaskResult result = TaskResult.fromHeartbeat(sessionId, response, executedAt);
        
        assertEquals("heartbeat", result.getTaskType());
        assertEquals("heartbeat", result.getTaskId());
        assertEquals("Heartbeat Alert", result.getTaskName());
        assertEquals(sessionId, result.getSessionId());
        assertEquals("Heartbeat check", result.getPrompt());
        assertEquals(response, result.getResponse());
        assertEquals(executedAt, result.getExecutedAt());
        assertEquals("success", result.getStatus());
        assertTrue(result.isHeartbeat());
        assertFalse(result.isCronJob());
    }
    
    @Test
    public void testSettersAndGetters() {
        TaskResult result = new TaskResult();
        
        result.setTaskType("cronjob");
        result.setTaskId("task-123");
        result.setTaskName("My Task");
        result.setSessionId("session-456");
        result.setPrompt("Test prompt");
        result.setResponse("Test response");
        result.setNotificationTitle("Title");
        result.setNotificationSummary("Summary");
        result.setExecutedAt(12345L);
        result.setDurationMs(5000L);
        result.setIterationCount(3);
        result.setToolCallsCount(7);
        result.setTokensUsed(2000);
        result.setStatus("success");
        result.setErrorMessage("No error");
        result.setWasNotified(true);
        result.setUserViewed(true);
        result.setUserChatted(true);
        result.setContinuedInSessionId("new-session-789");
        
        assertEquals("cronjob", result.getTaskType());
        assertEquals("task-123", result.getTaskId());
        assertEquals("My Task", result.getTaskName());
        assertEquals("session-456", result.getSessionId());
        assertEquals("Test prompt", result.getPrompt());
        assertEquals("Test response", result.getResponse());
        assertEquals("Title", result.getNotificationTitle());
        assertEquals("Summary", result.getNotificationSummary());
        assertEquals(12345L, result.getExecutedAt());
        assertEquals(5000L, result.getDurationMs());
        assertEquals(3, result.getIterationCount());
        assertEquals(7, result.getToolCallsCount());
        assertEquals(2000, result.getTokensUsed());
        assertEquals("success", result.getStatus());
        assertEquals("No error", result.getErrorMessage());
        assertTrue(result.wasNotified());
        assertTrue(result.isUserViewed());
        assertTrue(result.isUserChatted());
        assertEquals("new-session-789", result.getContinuedInSessionId());
    }
    
    @Test
    public void testIsCronJob() {
        TaskResult result = new TaskResult();
        
        result.setTaskType("cronjob");
        assertTrue(result.isCronJob());
        
        result.setTaskType("heartbeat");
        assertFalse(result.isCronJob());
    }
    
    @Test
    public void testIsHeartbeat() {
        TaskResult result = new TaskResult();
        
        result.setTaskType("heartbeat");
        assertTrue(result.isHeartbeat());
        
        result.setTaskType("cronjob");
        assertFalse(result.isHeartbeat());
    }
    
    @Test
    public void testIsSuccess() {
        TaskResult result = new TaskResult();
        
        result.setStatus("success");
        assertTrue(result.isSuccess());
        
        result.setStatus("failure");
        assertFalse(result.isSuccess());
        
        result.setStatus("silent");
        assertFalse(result.isSuccess());
    }
    
    @Test
    public void testIsSilent() {
        TaskResult result = new TaskResult();
        
        result.setStatus("silent");
        assertTrue(result.isSilent());
        
        result.setStatus("success");
        assertFalse(result.isSilent());
        
        result.setStatus("failure");
        assertFalse(result.isSilent());
    }
    
    @Test
    public void testMarkViewed() {
        TaskResult result = new TaskResult();
        assertFalse(result.isUserViewed());
        
        result.markViewed();
        assertTrue(result.isUserViewed());
    }
    
    @Test
    public void testMarkChatted() {
        TaskResult result = new TaskResult();
        assertFalse(result.isUserChatted());
        assertNull(result.getContinuedInSessionId());
        
        result.markChatted("chat-session-123");
        
        assertTrue(result.isUserChatted());
        assertEquals("chat-session-123", result.getContinuedInSessionId());
    }
    
    // ========== NOTIFICATION FALLBACK TESTS ==========
    
    @Test
    public void testGetNotificationTitleOrDefault_WithTitle() {
        TaskResult result = new TaskResult();
        result.setNotificationTitle("Custom Title");
        result.setTaskName("Default Task");
        
        assertEquals("Custom Title", result.getNotificationTitleOrDefault());
    }
    
    @Test
    public void testGetNotificationTitleOrDefault_Fallback() {
        TaskResult result = new TaskResult();
        result.setTaskName("Fallback Task Name");
        
        assertEquals("Fallback Task Name", result.getNotificationTitleOrDefault());
    }
    
    @Test
    public void testGetNotificationTitleOrDefault_EmptyTitle() {
        TaskResult result = new TaskResult();
        result.setNotificationTitle("");
        result.setTaskName("Task Name");
        
        assertEquals("Task Name", result.getNotificationTitleOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_WithSummary() {
        TaskResult result = new TaskResult();
        result.setNotificationSummary("Custom summary");
        
        assertEquals("Custom summary", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_FromResponse() {
        TaskResult result = new TaskResult();
        result.setResponse("First line of response\nSecond line");
        
        assertEquals("First line of response", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_SkipHeaders() {
        TaskResult result = new TaskResult();
        result.setResponse("# Header\n## Subheader\nActual content here");
        
        assertEquals("Actual content here", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_LongLine() {
        TaskResult result = new TaskResult();
        String longLine = "This is a very long line that exceeds 100 characters and should be truncated to avoid displaying too much text in the notification summary area";
        result.setResponse(longLine);
        
        String summary = result.getNotificationSummaryOrDefault();
        assertEquals(100, summary.length());
        assertTrue(summary.endsWith("..."));
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_EmptyResponse() {
        TaskResult result = new TaskResult();
        result.setResponse("");
        
        assertEquals("Task completed", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_NullResponse() {
        TaskResult result = new TaskResult();
        result.setResponse(null);
        
        assertEquals("Task completed", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_OnlyHeaders() {
        TaskResult result = new TaskResult();
        result.setResponse("# Header 1\n## Header 2\n### Header 3");
        
        assertEquals("Task completed", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testGetNotificationSummaryOrDefault_EmptySummary() {
        TaskResult result = new TaskResult();
        result.setNotificationSummary("");
        result.setResponse("Response text");
        
        assertEquals("Response text", result.getNotificationSummaryOrDefault());
    }
    
    @Test
    public void testUniqueIds() {
        TaskResult result1 = new TaskResult();
        TaskResult result2 = new TaskResult();
        
        assertNotEquals(result1.getId(), result2.getId());
    }
    
    @Test
    public void testFromCronJobWithFailure() {
        CronJob job = CronJob.create("Failed Job", "Test", 60);
        TaskRecord record = TaskRecord.create(job.getId(), job.getName(), "Prompt");
        record.markFailure("Connection timeout");
        
        TaskResult result = TaskResult.fromCronJob(job, record);
        
        assertEquals("failure", result.getStatus());
        assertEquals("Connection timeout", result.getErrorMessage());
    }
}