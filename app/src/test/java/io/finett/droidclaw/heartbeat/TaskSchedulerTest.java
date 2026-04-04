package io.finett.droidclaw.heartbeat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.repository.TaskRepository;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskScheduler.
 * Tests scheduling logic for heartbeat and cron jobs.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskSchedulerTest {

    private TaskScheduler scheduler;
    private TaskRepository taskRepository;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        // Initialize WorkManager for testing
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);

        taskRepository = new TaskRepository(context);
        scheduler = new TaskScheduler(context, taskRepository);
    }

    @Test
    public void testSchedulerCreation() {
        assertNotNull(scheduler);
    }

    // ========== HEARTBEAT SCHEDULING TESTS ==========

    @Test
    public void testScheduleHeartbeat() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setIntervalMinutes(30);

        // Should not throw any exception
        scheduler.scheduleHeartbeat(config);
    }

    @Test
    public void testScheduleHeartbeatDisabled() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setEnabled(false);

        scheduler.scheduleHeartbeat(config);
        // Should cancel immediately
    }

    @Test
    public void testScheduleHeartbeatBelowMinimum() {
        HeartbeatConfig config = new HeartbeatConfig();
        config.setIntervalMinutes(5); // Below 15 min minimum

        scheduler.scheduleHeartbeat(config);
        // Should adjust to 15 minutes
    }

    @Test
    public void testCancelHeartbeat() {
        HeartbeatConfig config = new HeartbeatConfig();
        scheduler.scheduleHeartbeat(config);

        // Should not throw any exception
        scheduler.cancelHeartbeat();
    }

    // ========== CRON JOB SCHEDULING TESTS ==========

    @Test
    public void testScheduleCronJob() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        taskRepository.saveCronJob(job);

        // Should not throw any exception
        scheduler.scheduleCronJob(job);
    }

    @Test
    public void testScheduleCronJobDisabled() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.setEnabled(false);
        taskRepository.saveCronJob(job);

        scheduler.scheduleCronJob(job);
        // Should cancel immediately
    }

    @Test
    public void testScheduleCronJobBelowMinimum() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 5); // Below 15 min minimum
        taskRepository.saveCronJob(job);

        scheduler.scheduleCronJob(job);
        // Should adjust to 15 minutes
    }

    @Test
    public void testCancelCronJob() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        taskRepository.saveCronJob(job);

        scheduler.scheduleCronJob(job);

        // Should not throw any exception
        scheduler.cancelCronJob(job.getId());
    }

    @Test
    public void testScheduleAllEnabledCronJobs() {
        CronJob job1 = CronJob.create("Job 1", "Prompt 1", 30);
        CronJob job2 = CronJob.create("Job 2", "Prompt 2", 60);
        CronJob job3 = CronJob.create("Job 3", "Prompt 3", 120);
        job3.setEnabled(false);

        taskRepository.saveCronJob(job1);
        taskRepository.saveCronJob(job2);
        taskRepository.saveCronJob(job3);

        // Should only schedule job1 and job2 (job3 is disabled)
        scheduler.scheduleAllEnabledCronJobs();
    }

    @Test
    public void testCancelAllCronJobs() {
        CronJob job1 = CronJob.create("Job 1", "Prompt 1", 30);
        CronJob job2 = CronJob.create("Job 2", "Prompt 2", 60);

        taskRepository.saveCronJob(job1);
        taskRepository.saveCronJob(job2);

        scheduler.scheduleCronJob(job1);
        scheduler.scheduleCronJob(job2);

        // Should not throw any exception
        scheduler.cancelAllCronJobs();
    }

    @Test
    public void testRunCronJobNow() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        taskRepository.saveCronJob(job);

        // Should not throw any exception
        scheduler.runCronJobNow(job.getId());
    }

    @Test
    public void testRunCronJobNowNotFound() {
        // Should not throw any exception, just log a warning
        scheduler.runCronJobNow("non-existent-id");
    }
}
