package io.finett.droidclaw.heartbeat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskSchedulerInitializer.
 * Tests initialization logic for background tasks.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskSchedulerInitializerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        // Initialize WorkManager for testing
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
    }

    @Test
    public void testInitialize() {
        // Should not throw any exception
        TaskSchedulerInitializer.initialize(context);
    }

    @Test
    public void testInitializeWithConfig() {
        io.finett.droidclaw.model.HeartbeatConfig config = new io.finett.droidclaw.model.HeartbeatConfig();
        config.setIntervalMinutes(15);
        config.setEnabled(true);

        // Should not throw any exception
        TaskSchedulerInitializer.initializeWithConfig(context, config);
    }

    @Test
    public void testCancelAll() {
        // First initialize
        TaskSchedulerInitializer.initialize(context);

        // Then cancel
        TaskSchedulerInitializer.cancelAll(context);

        // Should not throw any exception
    }

    @Test
    public void testInitializeMultipleTimes() {
        // Calling initialize multiple times should be safe
        TaskSchedulerInitializer.initialize(context);
        TaskSchedulerInitializer.initialize(context);
        TaskSchedulerInitializer.initialize(context);

        // Should not throw any exception
    }
}
