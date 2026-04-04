package io.finett.droidclaw.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskRecord model.
 * Tests task record creation, status tracking, and utility methods.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskRecordTest {
    
    @Test
    public void testCreateTaskRecord() {
        TaskRecord record = TaskRecord.create("cron-123", "Test Job", "Test prompt");
        
        assertNotNull(record);
        assertNotNull(record.getId());
        assertEquals("cron-123", record.getCronJobId());
        assertEquals("Test Job", record.getCronJobName());
        assertEquals("Test prompt", record.getPrompt());
        assertTrue(record.getStartedAt() > 0);
    }
    
    @Test
    public void testDefaultConstructor() {
        TaskRecord record = new TaskRecord();
        
        assertNotNull(record.getId());
        assertTrue(record.getStartedAt() > 0);
    }
    
    @Test
    public void testParameterizedConstructor() {
        TaskRecord record = new TaskRecord("cron-abc", "My Job", "My Prompt");
        
        assertEquals("cron-abc", record.getCronJobId());
        assertEquals("My Job", record.getCronJobName());
        assertEquals("My Prompt", record.getPrompt());
    }
    
    @Test
    public void testSettersAndGetters() {
        TaskRecord record = new TaskRecord();
        
        record.setCronJobId("cron-456");
        record.setCronJobName("Updated Job");
        record.setPrompt("Updated Prompt");
        record.setSessionId("session-789");
        record.setResponse("Test response");
        record.setIterationCount(5);
        record.setToolCallsCount(10);
        record.setTokensUsed(1500);
        
        assertEquals("cron-456", record.getCronJobId());
        assertEquals("Updated Job", record.getCronJobName());
        assertEquals("Updated Prompt", record.getPrompt());
        assertEquals("session-789", record.getSessionId());
        assertEquals("Test response", record.getResponse());
        assertEquals(5, record.getIterationCount());
        assertEquals(10, record.getToolCallsCount());
        assertEquals(1500, record.getTokensUsed());
    }
    
    @Test
    public void testMarkSuccess() {
        TaskRecord record = TaskRecord.create("cron-123", "Job", "Prompt");
        long beforeTime = System.currentTimeMillis();
        
        record.markSuccess("Success response");
        
        long afterTime = System.currentTimeMillis();
        assertEquals("success", record.getStatus());
        assertEquals("Success response", record.getResponse());
        assertTrue(record.getCompletedAt() >= beforeTime && record.getCompletedAt() <= afterTime);
        assertTrue(record.getDurationMs() >= 0);
        assertTrue(record.isSuccess());
        assertFalse(record.isFailed());
    }
    
    @Test
    public void testMarkFailure() {
        TaskRecord record = TaskRecord.create("cron-123", "Job", "Prompt");
        long beforeTime = System.currentTimeMillis();
        
        record.markFailure("Error: Connection failed");
        
        long afterTime = System.currentTimeMillis();
        assertEquals("failure", record.getStatus());
        assertEquals("Error: Connection failed", record.getErrorMessage());
        assertTrue(record.getCompletedAt() >= beforeTime && record.getCompletedAt() <= afterTime);
        assertTrue(record.getDurationMs() >= 0);
        assertFalse(record.isSuccess());
        assertTrue(record.isFailed());
    }
    
    @Test
    public void testMarkTimeout() {
        TaskRecord record = TaskRecord.create("cron-123", "Job", "Prompt");
        long beforeTime = System.currentTimeMillis();
        
        record.markTimeout();
        
        long afterTime = System.currentTimeMillis();
        assertEquals("timeout", record.getStatus());
        assertEquals("Execution exceeded time limit", record.getErrorMessage());
        assertTrue(record.getCompletedAt() >= beforeTime && record.getCompletedAt() <= afterTime);
        assertTrue(record.getDurationMs() >= 0);
        assertFalse(record.isSuccess());
        assertTrue(record.isFailed());
    }
    
    @Test
    public void testIsSuccess() {
        TaskRecord record = new TaskRecord();
        
        record.setStatus("success");
        assertTrue(record.isSuccess());
        
        record.setStatus("failure");
        assertFalse(record.isSuccess());
        
        record.setStatus("timeout");
        assertFalse(record.isSuccess());
    }
    
    @Test
    public void testIsFailed() {
        TaskRecord record = new TaskRecord();
        
        record.setStatus("failure");
        assertTrue(record.isFailed());
        
        record.setStatus("timeout");
        assertTrue(record.isFailed());
        
        record.setStatus("success");
        assertFalse(record.isFailed());
        
        record.setStatus("cancelled");
        assertFalse(record.isFailed()); // Cancelled is not failed
    }
    
    @Test
    public void testDurationCalculation() {
        TaskRecord record = new TaskRecord();
        long startedAt = System.currentTimeMillis();
        record.setStartedAt(startedAt);
        
        try {
            Thread.sleep(50); // 50ms delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        record.markSuccess("Done");
        
        assertTrue(record.getDurationMs() >= 50);
    }
    
    @Test
    public void testSetCompletedAtCalculatesDuration() {
        TaskRecord record = new TaskRecord();
        record.setStartedAt(1000);
        
        record.setCompletedAt(2500);
        
        assertEquals(1500, record.getDurationMs());
    }
    
    // ========== NOTIFICATION TESTS ==========
    
    @Test
    public void testNotificationTracking() {
        TaskRecord record = new TaskRecord();
        
        record.setNotificationTitle("Alert Title");
        record.setNotificationSummary("Alert summary");
        record.setWasNotified(true);
        record.setUserViewed(true);
        
        assertEquals("Alert Title", record.getNotificationTitle());
        assertEquals("Alert summary", record.getNotificationSummary());
        assertTrue(record.wasNotified());
        assertTrue(record.isUserViewed());
    }
    
    @Test
    public void testResultIdLink() {
        TaskRecord record = new TaskRecord();
        
        record.setResultId("result-123");
        assertEquals("result-123", record.getResultId());
    }
    
    // ========== DURATION DISPLAY STRING TESTS ==========
    
    @Test
    public void testGetDurationDisplayString_Milliseconds() {
        TaskRecord record = new TaskRecord();
        record.setDurationMs(500);
        assertEquals("500ms", record.getDurationDisplayString());
    }
    
    @Test
    public void testGetDurationDisplayString_Seconds() {
        TaskRecord record = new TaskRecord();
        record.setDurationMs(2500);
        assertEquals("2.5s", record.getDurationDisplayString());
        
        record.setDurationMs(15000);
        assertEquals("15.0s", record.getDurationDisplayString());
    }
    
    @Test
    public void testGetDurationDisplayString_Minutes() {
        TaskRecord record = new TaskRecord();
        record.setDurationMs(90000); // 1m 30s
        assertEquals("1m 30s", record.getDurationDisplayString());
        
        record.setDurationMs(180000); // 3m 0s
        assertEquals("3m 0s", record.getDurationDisplayString());
    }
    
    @Test
    public void testGetDurationDisplayString_EdgeCases() {
        TaskRecord record = new TaskRecord();
        
        record.setDurationMs(999);
        assertEquals("999ms", record.getDurationDisplayString());
        
        record.setDurationMs(1000);
        assertEquals("1.0s", record.getDurationDisplayString());
        
        record.setDurationMs(59999);
        assertEquals("60.0s", record.getDurationDisplayString());
        
        record.setDurationMs(60000);
        assertEquals("1m 0s", record.getDurationDisplayString());
    }
    
    // ========== STATUS DISPLAY TESTS ==========
    
    @Test
    public void testGetStatusBadgeColor() {
        TaskRecord record = new TaskRecord();
        
        record.setStatus("success");
        assertEquals("#4CAF50", record.getStatusBadgeColor());
        
        record.setStatus("failure");
        assertEquals("#F44336", record.getStatusBadgeColor());
        
        record.setStatus("timeout");
        assertEquals("#F44336", record.getStatusBadgeColor());
        
        record.setStatus("cancelled");
        assertEquals("#9E9E9E", record.getStatusBadgeColor());
        
        record.setStatus("unknown");
        assertEquals("#2196F3", record.getStatusBadgeColor());
    }
    
    @Test
    public void testGetStatusDisplayText() {
        TaskRecord record = new TaskRecord();
        
        record.setStatus("success");
        assertEquals("Success", record.getStatusDisplayText());
        
        record.setStatus("failure");
        assertEquals("Failed", record.getStatusDisplayText());
        
        record.setStatus("timeout");
        assertEquals("Timed Out", record.getStatusDisplayText());
        
        record.setStatus("cancelled");
        assertEquals("Cancelled", record.getStatusDisplayText());
        
        record.setStatus("running");
        assertEquals("Running", record.getStatusDisplayText());
    }
    
    @Test(expected = NullPointerException.class)
    public void testGetStatusDisplayTextNull() {
        TaskRecord record = new TaskRecord();
        record.setStatus(null);
        record.getStatusDisplayText(); // Current implementation throws NPE
    }
    
    @Test
    public void testUniqueIds() {
        TaskRecord record1 = TaskRecord.create("cron-1", "Job", "Prompt");
        TaskRecord record2 = TaskRecord.create("cron-1", "Job", "Prompt");
        
        assertNotEquals(record1.getId(), record2.getId());
    }
}