package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.finett.droidclaw.model.HeartbeatConfig;

@RunWith(RobolectricTestRunner.class)
public class HeartbeatConfigRepositoryTest {

    private HeartbeatConfigRepository repository;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        repository = new HeartbeatConfigRepository(context);
        sharedPreferences = context.getSharedPreferences("droidclaw_heartbeat", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
    }

    @Test
    public void getConfig_returnsDefaults_whenNoConfigExists() {
        HeartbeatConfig config = repository.getConfig();

        assertFalse(config.isEnabled());
        assertEquals(30 * 60 * 1000L, config.getIntervalMillis());
        assertEquals(0, config.getLastRunTimestamp());
    }

    @Test
    public void updateConfig_persistsConfig() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 5000L);

        repository.updateConfig(config);

        HeartbeatConfig loaded = repository.getConfig();

        assertTrue(loaded.isEnabled());
        assertEquals(60000L, loaded.getIntervalMillis());
        assertEquals(5000L, loaded.getLastRunTimestamp());
    }

    @Test
    public void updateConfig_overwritesPreviousConfig() {
        HeartbeatConfig config1 = new HeartbeatConfig(true, 60000L, 5000L);
        repository.updateConfig(config1);

        HeartbeatConfig config2 = new HeartbeatConfig(false, 120000L, 10000L);
        repository.updateConfig(config2);

        HeartbeatConfig loaded = repository.getConfig();

        assertFalse(loaded.isEnabled());
        assertEquals(120000L, loaded.getIntervalMillis());
        assertEquals(10000L, loaded.getLastRunTimestamp());
    }

    @Test
    public void isHeartbeatEnabled_returnsTrue_whenConfigured() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 0L);
        repository.updateConfig(config);

        assertTrue(repository.isHeartbeatEnabled());
    }

    @Test
    public void isHeartbeatEnabled_returnsFalse_whenNotConfigured() {
        assertFalse(repository.isHeartbeatEnabled());
    }

    @Test
    public void shouldRun_returnsTrue_whenEnabledAndIntervalPassed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 0L);
        repository.updateConfig(config);

        assertTrue(repository.shouldRun(120000L));
    }

    @Test
    public void shouldRun_returnsFalse_whenDisabled() {
        assertFalse(repository.shouldRun(120000L));
    }

    @Test
    public void shouldRun_returnsFalse_whenIntervalNotPassed() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 50000L);
        repository.updateConfig(config);

        assertFalse(repository.shouldRun(60000L));
    }

    @Test
    public void updateLastRun_updatesTimestamp() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 0L);
        repository.updateConfig(config);

        repository.updateLastRun(10000L);

        HeartbeatConfig loaded = repository.getConfig();

        assertEquals(10000L, loaded.getLastRunTimestamp());
    }

    @Test
    public void updateLastRun_doesNotAffectOtherFields() {
        HeartbeatConfig config = new HeartbeatConfig(true, 60000L, 5000L);
        repository.updateConfig(config);

        repository.updateLastRun(10000L);

        HeartbeatConfig loaded = repository.getConfig();

        assertTrue(loaded.isEnabled());
        assertEquals(60000L, loaded.getIntervalMillis());
        assertEquals(10000L, loaded.getLastRunTimestamp());
    }
}
