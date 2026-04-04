package io.finett.droidclaw.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for HeartbeatConfig model.
 * Tests configuration options and active hours logic.
 */
@RunWith(RobolectricTestRunner.class)
public class HeartbeatConfigTest {
    
    @Test
    public void testDefaultValues() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        assertTrue(config.isEnabled());
        assertEquals(30, config.getIntervalMinutes());
        assertFalse(config.isShowOkMessages());
        assertTrue(config.isShowAlerts());
        assertTrue(config.isSendNotifications());
        assertEquals(300, config.getAckMaxChars());
        assertFalse(config.isRespectActiveHours());
        assertFalse(config.isRequireNetwork());
        assertTrue(config.isBatteryNotLow());
        assertEquals(0, config.getLastRunAt());
        assertEquals("", config.getLastStatus());
    }
    
    @Test
    public void testSetEnabled() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setEnabled(false);
        assertFalse(config.isEnabled());
        
        config.setEnabled(true);
        assertTrue(config.isEnabled());
    }
    
    @Test
    public void testSetIntervalMinutes() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setIntervalMinutes(15);
        assertEquals(15, config.getIntervalMinutes());
        
        config.setIntervalMinutes(120);
        assertEquals(120, config.getIntervalMinutes());
    }
    
    @Test
    public void testSetShowOkMessages() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setShowOkMessages(true);
        assertTrue(config.isShowOkMessages());
        
        config.setShowOkMessages(false);
        assertFalse(config.isShowOkMessages());
    }
    
    @Test
    public void testSetShowAlerts() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setShowAlerts(false);
        assertFalse(config.isShowAlerts());
        
        config.setShowAlerts(true);
        assertTrue(config.isShowAlerts());
    }
    
    @Test
    public void testSetSendNotifications() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setSendNotifications(false);
        assertFalse(config.isSendNotifications());
        
        config.setSendNotifications(true);
        assertTrue(config.isSendNotifications());
    }
    
    @Test
    public void testSetAckMaxChars() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setAckMaxChars(500);
        assertEquals(500, config.getAckMaxChars());
        
        config.setAckMaxChars(100);
        assertEquals(100, config.getAckMaxChars());
    }
    
    @Test
    public void testSetActiveHours() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setActiveHoursStart("08:00");
        config.setActiveHoursEnd("22:00");
        
        assertEquals("08:00", config.getActiveHoursStart());
        assertEquals("22:00", config.getActiveHoursEnd());
    }
    
    @Test
    public void testSetRespectActiveHours() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setRespectActiveHours(true);
        assertTrue(config.isRespectActiveHours());
        
        config.setRespectActiveHours(false);
        assertFalse(config.isRespectActiveHours());
    }
    
    @Test
    public void testSetConstraints() {
        HeartbeatConfig config = new HeartbeatConfig();
        
        config.setRequireNetwork(true);
        assertTrue(config.isRequireNetwork());
        
        config.setBatteryNotLow(false);
        assertFalse(config.isBatteryNotLow());
    }
    
    @Test
    public void testSetLastRunTracking() {
        HeartbeatConfig config = new HeartbeatConfig();
        long timestamp = System.currentTimeMillis();
        
        config.setLastRunAt(timestamp);
        config.setLastStatus("ok");
        
        assertEquals(timestamp, config.getLastRunAt());
        assertEquals("ok", config.getLastStatus());
    }
    
    // ========== ACTIVE HOURS TESTS ==========
    
    @Test
    public void testIsWithinActiveHours_NotRespected() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(false);
        config.setActiveHoursStart("08:00");
        config.setActiveHoursEnd("22:00");
        
        // Should always return true when not respecting active hours
        assertTrue(config.isWithinActiveHours(0));
        assertTrue(config.isWithinActiveHours(12));
        assertTrue(config.isWithinActiveHours(23));
    }
    
    @Test
    public void testIsWithinActiveHours_NoConfig() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        // No start/end configured
        
        assertTrue(config.isWithinActiveHours(0));
        assertTrue(config.isWithinActiveHours(12));
        assertTrue(config.isWithinActiveHours(23));
    }
    
    @Test
    public void testIsWithinActiveHours_NormalRange() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart("08:00");
        config.setActiveHoursEnd("22:00");
        
        assertFalse(config.isWithinActiveHours(7));  // Before start
        assertTrue(config.isWithinActiveHours(8));   // At start
        assertTrue(config.isWithinActiveHours(12));  // Middle
        assertTrue(config.isWithinActiveHours(21));  // Before end
        assertFalse(config.isWithinActiveHours(22)); // At end
        assertFalse(config.isWithinActiveHours(23)); // After end
    }
    
    @Test
    public void testIsWithinActiveHours_NightShift() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart("22:00"); // Start at 10 PM
        config.setActiveHoursEnd("08:00");   // End at 8 AM (wraparound)
        
        assertTrue(config.isWithinActiveHours(22));  // At start
        assertTrue(config.isWithinActiveHours(23));  // Late night
        assertTrue(config.isWithinActiveHours(0));   // Midnight
        assertTrue(config.isWithinActiveHours(5));   // Early morning
        assertFalse(config.isWithinActiveHours(8));  // At end
        assertFalse(config.isWithinActiveHours(12)); // Midday
        assertFalse(config.isWithinActiveHours(18)); // Evening
    }
    
    @Test
    public void testIsWithinActiveHours_FullDay() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart("00:00");
        config.setActiveHoursEnd("24:00");  // End at 24 to include hour 23
        
        assertTrue(config.isWithinActiveHours(0));
        assertTrue(config.isWithinActiveHours(12));
        assertTrue(config.isWithinActiveHours(23));
    }
    
    @Test
    public void testIsWithinActiveHours_InvalidFormat() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart("invalid");
        config.setActiveHoursEnd("also invalid");
        
        // Should default to true on parse error
        assertTrue(config.isWithinActiveHours(12));
    }
    
    @Test
    public void testIsWithinActiveHours_NullStart() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart(null);
        config.setActiveHoursEnd("22:00");
        
        assertTrue(config.isWithinActiveHours(12));
    }
    
    @Test
    public void testIsWithinActiveHours_NullEnd() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setRespectActiveHours(true);
        config.setActiveHoursStart("08:00");
        config.setActiveHoursEnd(null);
        
        assertTrue(config.isWithinActiveHours(12));
    }
}