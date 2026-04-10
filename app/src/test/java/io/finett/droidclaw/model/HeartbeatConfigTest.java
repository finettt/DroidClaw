package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for HeartbeatConfig model.
 */
public class HeartbeatConfigTest {

    private HeartbeatConfig config;

    @Before
    public void setUp() {
        config = new HeartbeatConfig();
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void defaultConstructor_createsDisabledConfig() {
        assertFalse("Should be disabled by default", config.isEnabled());
        assertEquals("Default interval should be 30 minutes",
                30 * 60 * 1000L, config.getIntervalMillis());
        assertEquals("Default last run should be 0", 0, config.getLastRunTimestamp());
    }

    @Test
    public void parameterizedConstructor_setsAllFields() {
        HeartbeatConfig customConfig = new HeartbeatConfig(true, 60000L, 12345L);

        assertTrue("Should be enabled", customConfig.isEnabled());
        assertEquals("Interval should be 60000", 60000L, customConfig.getIntervalMillis());
        assertEquals("Last run should be 12345", 12345L, customConfig.getLastRunTimestamp());
    }

    // ==================== GETTER/SETTER TESTS ====================

    @Test
    public void setEnabled_updatesState() {
        config.setEnabled(true);
        assertTrue("Should be enabled after setEnabled(true)", config.isEnabled());

        config.setEnabled(false);
        assertFalse("Should be disabled after setEnabled(false)", config.isEnabled());
    }

    @Test
    public void setIntervalMillis_updatesInterval() {
        config.setIntervalMillis(15 * 60 * 1000L);
        assertEquals("Interval should be 15 minutes", 15 * 60 * 1000L, config.getIntervalMillis());

        config.setIntervalMillis(120 * 60 * 1000L);
        assertEquals("Interval should be 120 minutes", 120 * 60 * 1000L, config.getIntervalMillis());
    }

    @Test
    public void setLastRunTimestamp_updatesTimestamp() {
        long timestamp = System.currentTimeMillis();
        config.setLastRunTimestamp(timestamp);
        assertEquals("Timestamp should match", timestamp, config.getLastRunTimestamp());
    }

    // ==================== shouldRun() LOGIC TESTS ====================

    @Test
    public void shouldRun_returnsFalse_whenDisabled() {
        config.setEnabled(false);
        config.setIntervalMillis(60000L);
        config.setLastRunTimestamp(0L);

        assertFalse("Should not run when disabled", config.shouldRun(120000L));
    }

    @Test
    public void shouldRun_returnsTrue_whenEnabledAndIntervalElapsed() {
        config.setEnabled(true);
        config.setIntervalMillis(60000L); // 1 minute
        config.setLastRunTimestamp(0L);

        assertTrue("Should run when interval elapsed", config.shouldRun(120000L)); // 2 minutes
    }

    @Test
    public void shouldRun_returnsFalse_whenIntervalNotElapsed() {
        config.setEnabled(true);
        config.setIntervalMillis(60000L); // 1 minute
        config.setLastRunTimestamp(50000L); // 50 seconds

        assertFalse("Should not run when interval not elapsed", config.shouldRun(60000L)); // 60 seconds
    }

    @Test
    public void shouldRun_returnsTrue_whenExactlyAtInterval() {
        config.setEnabled(true);
        config.setIntervalMillis(60000L);
        config.setLastRunTimestamp(0L);

        assertTrue("Should run when exactly at interval", config.shouldRun(60000L));
    }

    @Test
    public void shouldRun_withFirstRun_alwaysReturnsTrue() {
        config.setEnabled(true);
        config.setIntervalMillis(60000L);
        config.setLastRunTimestamp(0L); // Never run

        // With lastRun=0, any currentTime >= interval will trigger the run
        assertTrue("Should run on first run when time exceeds interval", config.shouldRun(60000L));
    }

    // ==================== INTERVAL OPTIONS TESTS ====================

    @Test
    public void intervalOptions_15minutes() {
        config.setIntervalMillis(15 * 60 * 1000L);
        assertEquals(15 * 60 * 1000L, config.getIntervalMillis());
    }

    @Test
    public void intervalOptions_30minutes() {
        config.setIntervalMillis(30 * 60 * 1000L);
        assertEquals(30 * 60 * 1000L, config.getIntervalMillis());
    }

    @Test
    public void intervalOptions_1hour() {
        config.setIntervalMillis(60 * 60 * 1000L);
        assertEquals(60 * 60 * 1000L, config.getIntervalMillis());
    }

    @Test
    public void intervalOptions_2hours() {
        config.setIntervalMillis(120 * 60 * 1000L);
        assertEquals(120 * 60 * 1000L, config.getIntervalMillis());
    }

    // ==================== getDefaults() TESTS ====================

    @Test
    public void getDefaults_returnsDisabledConfig() {
        HeartbeatConfig defaults = HeartbeatConfig.getDefaults();

        assertFalse("Defaults should be disabled", defaults.isEnabled());
        assertEquals("Defaults should have 30 min interval",
                30 * 60 * 1000L, defaults.getIntervalMillis());
        assertEquals("Defaults should have 0 last run", 0, defaults.getLastRunTimestamp());
    }

    @Test
    public void getDefaults_returnsNewInstance() {
        HeartbeatConfig config1 = HeartbeatConfig.getDefaults();
        HeartbeatConfig config2 = HeartbeatConfig.getDefaults();

        // They should be different instances
        config1.setEnabled(true);
        assertFalse("Should be independent instances", config2.isEnabled());
    }
}
