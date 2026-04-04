package io.finett.droidclaw.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for CronJob model.
 * Tests cron job creation, execution tracking, and utility methods.
 */
@RunWith(RobolectricTestRunner.class)
public class CronJobTest {
    
    @Test
    public void testCreateCronJob() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        
        assertNotNull(job);
        assertNotNull(job.getId());
        assertEquals("Test Job", job.getName());
        assertEquals("Test prompt", job.getPrompt());
        assertEquals(60, job.getIntervalMinutes());
        assertTrue(job.isEnabled());
    }
    
    @Test
    public void testDefaultConstructor() {
        CronJob job = new CronJob();
        
        assertNotNull(job.getId());
        assertTrue(job.isEnabled());
        assertNotNull(job.getMemoryTags());
        assertEquals(0, job.getMemoryTags().size());
    }
    
    @Test
    public void testParameterizedConstructor() {
        CronJob job = new CronJob("My Job", "My Prompt", 120);
        
        assertEquals("My Job", job.getName());
        assertEquals("My Prompt", job.getPrompt());
        assertEquals(120, job.getIntervalMinutes());
    }
    
    @Test
    public void testDefaultValues() {
        CronJob job = new CronJob();
        
        assertTrue(job.isRequireNetwork());
        assertFalse(job.isRequireCharging());
        assertTrue(job.isBatteryNotLow());
        assertEquals(10, job.getMaxIterations());
        assertTrue(job.isLoadMemories());
        assertEquals(0, job.getRunCount());
        assertEquals(0, job.getSuccessCount());
        assertEquals(0, job.getFailureCount());
        assertTrue(job.isNotifyOnSuccess());
        assertTrue(job.isNotifyOnFailure());
    }
    
    @Test
    public void testSettersAndGetters() {
        CronJob job = new CronJob();
        
        job.setName("New Name");
        job.setPrompt("New Prompt");
        job.setIntervalMinutes(30);
        job.setEnabled(false);
        job.setRequireNetwork(false);
        job.setRequireCharging(true);
        job.setBatteryNotLow(false);
        job.setModelOverride("gpt-4");
        job.setMaxIterations(20);
        job.setLoadMemories(false);
        
        assertEquals("New Name", job.getName());
        assertEquals("New Prompt", job.getPrompt());
        assertEquals(30, job.getIntervalMinutes());
        assertFalse(job.isEnabled());
        assertFalse(job.isRequireNetwork());
        assertTrue(job.isRequireCharging());
        assertFalse(job.isBatteryNotLow());
        assertEquals("gpt-4", job.getModelOverride());
        assertEquals(20, job.getMaxIterations());
        assertFalse(job.isLoadMemories());
    }
    
    @Test
    public void testMemoryTags() {
        CronJob job = new CronJob();
        
        List<String> tags = new ArrayList<>();
        tags.add("server");
        tags.add("monitoring");
        
        job.setMemoryTags(tags);
        
        assertEquals(2, job.getMemoryTags().size());
        assertTrue(job.getMemoryTags().contains("server"));
        assertTrue(job.getMemoryTags().contains("monitoring"));
    }
    
    @Test
    public void testRecordSuccess() {
        CronJob job = new CronJob();
        long beforeTime = System.currentTimeMillis();
        
        job.recordSuccess();
        
        long afterTime = System.currentTimeMillis();
        assertEquals(1, job.getRunCount());
        assertEquals(1, job.getSuccessCount());
        assertEquals(0, job.getFailureCount());
        assertTrue(job.getLastRunAt() >= beforeTime && job.getLastRunAt() <= afterTime);
    }
    
    @Test
    public void testRecordFailure() {
        CronJob job = new CronJob();
        long beforeTime = System.currentTimeMillis();
        
        job.recordFailure();
        
        long afterTime = System.currentTimeMillis();
        assertEquals(1, job.getRunCount());
        assertEquals(0, job.getSuccessCount());
        assertEquals(1, job.getFailureCount());
        assertTrue(job.getLastRunAt() >= beforeTime && job.getLastRunAt() <= afterTime);
    }
    
    @Test
    public void testMultipleExecutions() {
        CronJob job = new CronJob();
        
        job.recordSuccess();
        job.recordSuccess();
        job.recordFailure();
        job.recordSuccess();
        
        assertEquals(4, job.getRunCount());
        assertEquals(3, job.getSuccessCount());
        assertEquals(1, job.getFailureCount());
    }
    
    @Test
    public void testGetSuccessRate() {
        CronJob job = new CronJob();
        
        assertEquals(0, job.getSuccessRate()); // No runs yet
        
        job.recordSuccess();
        job.recordSuccess();
        job.recordFailure();
        
        assertEquals(66, job.getSuccessRate()); // 2/3 = 66%
    }
    
    @Test
    public void testGetSuccessRate100Percent() {
        CronJob job = new CronJob();
        
        job.recordSuccess();
        job.recordSuccess();
        job.recordSuccess();
        
        assertEquals(100, job.getSuccessRate());
    }
    
    @Test
    public void testGetSuccessRate0Percent() {
        CronJob job = new CronJob();
        
        job.recordFailure();
        job.recordFailure();
        
        assertEquals(0, job.getSuccessRate());
    }
    
    @Test
    public void testCalculateNextRun() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(60);
        job.setEnabled(true);
        
        long beforeTime = System.currentTimeMillis();
        job.recordSuccess();
        long afterTime = System.currentTimeMillis();
        
        long expectedNextRun = job.getLastRunAt() + (60 * 60 * 1000);
        assertEquals(expectedNextRun, job.getNextRunAt());
    }
    
    @Test
    public void testCalculateNextRunDisabled() {
        CronJob job = new CronJob();
        job.setEnabled(false);
        job.setIntervalMinutes(60);
        
        job.recordSuccess();
        
        // When disabled, nextRunAt is not calculated
        assertEquals(0, job.getNextRunAt());
    }
    
    @Test
    public void testToggle() {
        CronJob job = new CronJob();
        assertTrue(job.isEnabled());
        
        job.toggle();
        assertFalse(job.isEnabled());
        
        job.toggle();
        assertTrue(job.isEnabled());
    }
    
    @Test
    public void testToggleUpdatesTimestamp() {
        CronJob job = new CronJob();
        long initialUpdatedAt = job.getUpdatedAt();
        
        try {
            Thread.sleep(10); // Small delay to ensure timestamp changes
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        job.toggle();
        
        assertTrue(job.getUpdatedAt() > initialUpdatedAt);
    }
    
    // ========== INTERVAL DISPLAY STRING TESTS ==========
    
    @Test
    public void testGetIntervalDisplayString_Minutes() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(15);
        assertEquals("15 minutes", job.getIntervalDisplayString());
        
        job.setIntervalMinutes(45);
        assertEquals("45 minutes", job.getIntervalDisplayString());
    }
    
    @Test
    public void testGetIntervalDisplayString_OneHour() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(60);
        assertEquals("1 hour", job.getIntervalDisplayString());
    }
    
    @Test
    public void testGetIntervalDisplayString_Hours() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(120);
        assertEquals("2 hours", job.getIntervalDisplayString());
        
        job.setIntervalMinutes(360);
        assertEquals("6 hours", job.getIntervalDisplayString());
    }
    
    @Test
    public void testGetIntervalDisplayString_OneDay() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(1440);
        assertEquals("1 day", job.getIntervalDisplayString());
    }
    
    @Test
    public void testGetIntervalDisplayString_Days() {
        CronJob job = new CronJob();
        job.setIntervalMinutes(2880);
        assertEquals("2 days", job.getIntervalDisplayString());
        
        job.setIntervalMinutes(10080);
        assertEquals("7 days", job.getIntervalDisplayString());
    }
    
    @Test
    public void testNotificationSettings() {
        CronJob job = new CronJob();
        
        job.setNotifyOnSuccess(false);
        job.setNotifyOnFailure(false);
        
        assertFalse(job.isNotifyOnSuccess());
        assertFalse(job.isNotifyOnFailure());
    }
    
    @Test
    public void testCreatedInSessionId() {
        CronJob job = new CronJob();
        
        job.setCreatedInSessionId("session-123");
        assertEquals("session-123", job.getCreatedInSessionId());
    }
    
    @Test
    public void testTimestamps() {
        CronJob job = new CronJob();
        
        long createdAt = job.getCreatedAt();
        long updatedAt = job.getUpdatedAt();
        
        assertTrue(createdAt > 0);
        assertTrue(updatedAt > 0);
        assertEquals(createdAt, updatedAt); // Should be same at creation
    }
}