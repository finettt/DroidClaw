package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.finett.droidclaw.model.HeartbeatConfig.StalenessLevel;

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

    // ==================== STALENESS RATIO TESTS ====================

    @Test
    public void getStalenessRatio_zeroWhenNeverRun() {
        config.setLastRunTimestamp(0L);
        config.setIntervalMillis(30 * 60 * 1000L);

        assertEquals("Should be 0 when never run", 0.0, config.getStalenessRatio(60000L), 0.001);
    }

    @Test
    public void getStalenessRatio_oneIntervalExactlyOnTime() {
        config.setIntervalMillis(30 * 60 * 1000L); // 30 min
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // 30 min after last run = exactly 1.0 ratio
        assertEquals("Should be 1.0 at exactly one interval",
                1.0, config.getStalenessRatio(baseTime + 30 * 60 * 1000L), 0.001);
    }

    @Test
    public void getStalenessRatio_halfIntervalEarly() {
        config.setIntervalMillis(30 * 60 * 1000L); // 30 min
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // 15 min after last run = 0.5 ratio
        assertEquals("Should be 0.5 at half interval",
                0.5, config.getStalenessRatio(baseTime + 15 * 60 * 1000L), 0.001);
    }

    @Test
    public void getStalenessRatio_twoIntervalsLate() {
        config.setIntervalMillis(30 * 60 * 1000L); // 30 min
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // 60 min after last run = 2.0 ratio
        assertEquals("Should be 2.0 after two intervals",
                2.0, config.getStalenessRatio(baseTime + 60 * 60 * 1000L), 0.001);
    }

    @Test
    public void getStalenessRatio_fourIntervalsVeryLate() {
        config.setIntervalMillis(30 * 60 * 1000L); // 30 min
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // 120 min after last run = 4.0 ratio
        assertEquals("Should be 4.0 after four intervals",
                4.0, config.getStalenessRatio(baseTime + 120 * 60 * 1000L), 0.001);
    }

    @Test
    public void getStalenessRatio_handlesArbitraryLastRun() {
        config.setIntervalMillis(30 * 60 * 1000L); // 30 min
        long lastRun = 1000000L;
        config.setLastRunTimestamp(lastRun);

        // 30 min after last run
        assertEquals("Should be 1.0 regardless of base timestamp",
                1.0, config.getStalenessRatio(lastRun + 30 * 60 * 1000L), 0.001);
    }

    // ==================== STALENESS LEVEL TESTS ====================

    @Test
    public void getStalenessLevel_freshWhenNeverRun() {
        config.setLastRunTimestamp(0L);
        assertEquals("Should be FRESH when never run",
                StalenessLevel.FRESH, config.getStalenessLevel(System.currentTimeMillis()));
    }

    @Test
    public void getStalenessLevel_freshWhenWithinInterval() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        assertEquals("Should be FRESH at half interval",
                StalenessLevel.FRESH, config.getStalenessLevel(baseTime + 15 * 60 * 1000L));
    }

    @Test
    public void getStalenessLevel_slightlyLateAtOneInterval() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        assertEquals("Should be SLIGHTLY_LATE at exactly 1.0",
                StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(baseTime + 30 * 60 * 1000L));
    }

    @Test
    public void getStalenessLevel_slightlyLateAtTwoIntervals() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        assertEquals("Should be SLIGHTLY_LATE at 2.0",
                StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(baseTime + 60 * 60 * 1000L));
    }

    @Test
    public void getStalenessLevel_deadAtFourIntervals() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        assertEquals("Should be DEAD at 4.0",
                StalenessLevel.DEAD, config.getStalenessLevel(baseTime + 120 * 60 * 1000L));
    }

    @Test
    public void getStalenessLevel_deadAtExactlyThreeIntervals() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // At exactly 3.0, it's the boundary. We use <= 3.0 for SLIGHTLY_LATE.
        assertEquals("Should be SLIGHTLY_LATE at exactly 3.0",
                StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(baseTime + 90 * 60 * 1000L));
    }

    @Test
    public void getStalenessLevel_deadJustOverThreeIntervals() {
        config.setIntervalMillis(30 * 60 * 1000L);
        long baseTime = 1000000L;
        config.setLastRunTimestamp(baseTime);

        // 91 min / 30 min = 3.033...
        assertEquals("Should be DEAD just over 3.0",
                StalenessLevel.DEAD, config.getStalenessLevel(baseTime + 91 * 60 * 1000L));
    }
}
