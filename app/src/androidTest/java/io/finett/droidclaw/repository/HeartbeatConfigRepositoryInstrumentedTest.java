package io.finett.droidclaw.repository;

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

import io.finett.droidclaw.model.HeartbeatConfig;

@RunWith(AndroidJUnit4.class)
public class HeartbeatConfigRepositoryInstrumentedTest {

    private HeartbeatConfigRepository repository;

    @Before
    public void setUp() {
        getApplicationContext()
                .getSharedPreferences("droidclaw_heartbeat", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        repository = new HeartbeatConfigRepository(getApplicationContext());
    }

    @After
    public void tearDown() {
        getApplicationContext()
                .getSharedPreferences("droidclaw_heartbeat", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void getConfig_withNoConfig_returnsDefaults() {
        HeartbeatConfig config = repository.getConfig();

        assertNotNull("Config should not be null", config);
        assertFalse("Heartbeat should be disabled by default", config.isEnabled());
        assertEquals("Default interval should be 30 minutes",
                30 * 60 * 1000L, config.getIntervalMillis());
        assertEquals("Last run should be 0 initially", 0L, config.getLastRunTimestamp());
    }

    @Test
    public void updateConfig_savesAndRetrievesConfig() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        HeartbeatConfig retrieved = repository.getConfig();

        assertNotNull("Config should be retrievable", retrieved);
        assertTrue("Heartbeat should be enabled", retrieved.isEnabled());
        assertEquals("Interval should be 1 hour", 60 * 60 * 1000L, retrieved.getIntervalMillis());
        assertEquals("Last run timestamp should match", 1000L, retrieved.getLastRunTimestamp());
    }

    @Test
    public void updateConfig_overwritesPreviousConfig() {
        HeartbeatConfig config1 = new HeartbeatConfig(true, 30 * 60 * 1000L, 500L);
        repository.updateConfig(config1);

        HeartbeatConfig config2 = new HeartbeatConfig(false, 120 * 60 * 1000L, 2000L);
        repository.updateConfig(config2);

        HeartbeatConfig retrieved = repository.getConfig();

        assertFalse("Heartbeat should be disabled (from config2)", retrieved.isEnabled());
        assertEquals("Interval should be 2 hours (from config2)",
                120 * 60 * 1000L, retrieved.getIntervalMillis());
        assertEquals("Last run should be 2000 (from config2)", 2000L, retrieved.getLastRunTimestamp());
    }

    @Test
    public void isHeartbeatEnabled_reflectsConfigState() {
        assertFalse("Should be disabled initially", repository.isHeartbeatEnabled());

        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        repository.updateConfig(config);

        assertTrue("Should be enabled after update", repository.isHeartbeatEnabled());

        config.setEnabled(false);
        repository.updateConfig(config);

        assertFalse("Should be disabled after update", repository.isHeartbeatEnabled());
    }

    @Test
    public void shouldRun_returnsTrueWhenIntervalElapsed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        long currentTime = 1000L + 30 * 60 * 1000L + 1000L; // 30 min + 1 sec after last run

        assertTrue("Should run after interval elapsed", repository.shouldRun(currentTime));
    }

    @Test
    public void shouldRun_returnsFalseWhenIntervalNotElapsed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        long currentTime = 1000L + 15 * 60 * 1000L; // 15 min after last run

        assertFalse("Should not run before interval elapsed", repository.shouldRun(currentTime));
    }

    @Test
    public void shouldRun_returnsFalseWhenDisabled() {
        HeartbeatConfig config = new HeartbeatConfig(false, 30 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        long currentTime = 1000L + 60 * 60 * 1000L; // 1 hour after last run

        assertFalse("Should not run when disabled", repository.shouldRun(currentTime));
    }

    @Test
    public void shouldRun_returnsTrueAtExactIntervalBoundary() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        long currentTime = 1000L + 30 * 60 * 1000L;

        assertTrue("Should run at exact interval boundary", repository.shouldRun(currentTime));
    }

    @Test
    public void updateLastRun_updatesTimestampInConfig() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        long newTimestamp = 5000L;
        repository.updateLastRun(newTimestamp);

        HeartbeatConfig retrieved = repository.getConfig();
        assertEquals("Last run timestamp should be updated", newTimestamp, retrieved.getLastRunTimestamp());
    }

    @Test
    public void updateLastRun_preservesOtherConfigFields() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, 1000L);
        repository.updateConfig(config);

        repository.updateLastRun(5000L);

        HeartbeatConfig retrieved = repository.getConfig();
        assertEquals("Enabled state should be preserved", true, retrieved.isEnabled());
        assertEquals("Interval should be preserved", 60 * 60 * 1000L, retrieved.getIntervalMillis());
        assertEquals("Last run should be updated", 5000L, retrieved.getLastRunTimestamp());
    }

    @Test
    public void multipleSequentialUpdates_allPersistCorrectly() {
        for (int i = 0; i < 5; i++) {
            HeartbeatConfig config = new HeartbeatConfig(i % 2 == 0, (i + 1) * 10 * 60 * 1000L, i * 1000L);
            repository.updateConfig(config);
        }

        HeartbeatConfig retrieved = repository.getConfig();
        assertTrue("Should reflect last update (i=4, even)", retrieved.isEnabled());
        assertEquals("Interval should be 50 minutes", 50 * 60 * 1000L, retrieved.getIntervalMillis());
        assertEquals("Last run should be 4000", 4000L, retrieved.getLastRunTimestamp());
    }

    @Test
    public void configPersistsAcrossRepositoryInstances() {
        HeartbeatConfig config = new HeartbeatConfig(true, 45 * 60 * 1000L, 3000L);
        repository.updateConfig(config);

        HeartbeatConfigRepository newRepository = new HeartbeatConfigRepository(getApplicationContext());
        HeartbeatConfig retrieved = newRepository.getConfig();

        assertTrue("Heartbeat should be enabled", retrieved.isEnabled());
        assertEquals("Interval should be 45 minutes", 45 * 60 * 1000L, retrieved.getIntervalMillis());
        assertEquals("Last run should be 3000", 3000L, retrieved.getLastRunTimestamp());
    }

    @Test
    public void shouldRun_withZeroInterval_alwaysReturnsTrue() {
        HeartbeatConfig config = new HeartbeatConfig(true, 0L, 0L);
        repository.updateConfig(config);

        assertTrue("Should run with zero interval", repository.shouldRun(0L));
        assertTrue("Should run with zero interval at any time", repository.shouldRun(1000L));
    }
}
