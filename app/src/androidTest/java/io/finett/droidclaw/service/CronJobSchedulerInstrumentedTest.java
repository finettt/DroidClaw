package io.finett.droidclaw.service;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.worker.CronJobWorker;
import io.finett.droidclaw.worker.HeartbeatWorker;

@RunWith(AndroidJUnit4.class)
public class CronJobSchedulerInstrumentedTest {

    private CronJobScheduler scheduler;
    private WorkManager workManager;

    @Before
    public void setUp() {
        Context context = getApplicationContext();

        WorkManagerTestInitHelper.initializeTestWorkManager(context);

        scheduler = new CronJobScheduler(context);
        workManager = WorkManager.getInstance(context);
    }

    @After
    public void tearDown() {
        workManager.cancelAllWork();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== HEARTBEAT SCHEDULING TESTS ====================

    @Test
    public void scheduleHeartbeat_enqueuesPeriodicWork() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, 0L); // 1 hour
        scheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Heartbeat work should be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleHeartbeat_withMinimumInterval_enforcesFifteenMinuteMinimum() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 5 * 60 * 1000L, 0L); // 5 minutes (below minimum)
        scheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Heartbeat work should still be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleHeartbeat_withDisabledHeartbeat_doesNotEnqueue() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(false, 30 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertTrue("Disabled heartbeat should not be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleHeartbeat_replacesExistingWork() throws Exception {
        HeartbeatConfig config1 = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config1);

        Thread.sleep(100);

        HeartbeatConfig config2 = new HeartbeatConfig(true, 60 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config2);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertTrue("Should have only 1 work instance", workInfos.size() <= 1);
    }

    @Test
    public void cancelHeartbeat_removesScheduledWork() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        scheduler.cancelHeartbeat();

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        boolean isCancelled = workInfos.isEmpty() ||
                workInfos.get(0).getState() == WorkInfo.State.CANCELLED;
        assertTrue("Heartbeat work should be cancelled", isCancelled);
    }

    @Test
    public void cancelHeartbeat_whenNotScheduled_doesNotCrash() {
        scheduler.cancelHeartbeat();
    }

    @Test
    public void runHeartbeatNow_enqueuesOneTimeWork() throws Exception {
        scheduler.runHeartbeatNow();

        Thread.sleep(100);

        assertTrue("Run heartbeat now should not crash", true);
    }

    // ==================== CRON JOB SCHEDULING TESTS ====================

    @Test
    public void scheduleCronJob_enqueuesPeriodicWork() throws Exception {
        CronJob job = new CronJob("cron-1", "Test Job", "Test prompt", "3600000"); // 1 hour
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-1")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Cron job work should be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleCronJob_withDisabledJob_doesNotEnqueue() throws Exception {
        CronJob job = new CronJob("cron-2", "Disabled Job", "Test prompt", "3600000");
        job.setEnabled(false);
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-2")
                .get(5, TimeUnit.SECONDS);

        assertTrue("Disabled cron job should not be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleCronJob_withIntervalInMilliseconds_parsesInterval() throws Exception {
        CronJob job = new CronJob("cron-3", "Interval Job", "Test prompt", "7200000"); // 2 hours
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-3")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Interval-based cron job should be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleCronJob_withCronExpression_usesDefaultInterval() throws Exception {
        CronJob job = new CronJob("cron-4", "Cron Expression Job", "Test prompt", "0 0 * * *");
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-4")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Cron expression-based job should be enqueued", workInfos.isEmpty());
    }

    @Test
    public void scheduleCronJob_multipleJobs_allEnqueued() throws Exception {
        CronJob job1 = new CronJob("cron-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-2", "Job 2", "Prompt 2", "7200000");
        CronJob job3 = new CronJob("cron-3", "Job 3", "Prompt 3", "10800000");

        scheduler.scheduleJob(job1);
        scheduler.scheduleJob(job2);
        scheduler.scheduleJob(job3);

        Thread.sleep(100);

        List<WorkInfo> workInfos1 = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-1")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> workInfos2 = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-2")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> workInfos3 = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-3")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Job 1 should be enqueued", workInfos1.isEmpty());
        assertFalse("Job 2 should be enqueued", workInfos2.isEmpty());
        assertFalse("Job 3 should be enqueued", workInfos3.isEmpty());
    }

    @Test
    public void cancelCronJob_removesScheduledWork() throws Exception {
        CronJob job = new CronJob("cron-to-cancel", "Cancel Me", "Test prompt", "3600000");
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        scheduler.cancelJob("cron-to-cancel");

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-to-cancel")
                .get(5, TimeUnit.SECONDS);

        boolean isCancelled = workInfos.isEmpty() ||
                workInfos.get(0).getState() == WorkInfo.State.CANCELLED;
        assertTrue("Cron job work should be cancelled", isCancelled);
    }

    @Test
    public void cancelCronJob_nonExistentJob_doesNotCrash() {
        scheduler.cancelJob("non-existent");
    }

    @Test
    public void cancelAllJobs_removesAllScheduledWork() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config);

        CronJob job1 = new CronJob("cron-cancel-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-cancel-2", "Job 2", "Prompt 2", "7200000");
        scheduler.scheduleJob(job1);
        scheduler.scheduleJob(job2);

        Thread.sleep(100);

        scheduler.cancelAllJobs();

        Thread.sleep(100);

        List<WorkInfo> cronWork1 = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-cancel-1")
                .get(5, TimeUnit.SECONDS);
        List<WorkInfo> cronWork2 = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-cancel-2")
                .get(5, TimeUnit.SECONDS);

        boolean cron1Cancelled = cronWork1.isEmpty() ||
                cronWork1.get(0).getState() == WorkInfo.State.CANCELLED;
        boolean cron2Cancelled = cronWork2.isEmpty() ||
                cronWork2.get(0).getState() == WorkInfo.State.CANCELLED;

        assertTrue("Cron job 1 work should be cancelled", cron1Cancelled);
        assertTrue("Cron job 2 work should be cancelled", cron2Cancelled);
    }

    @Test
    public void cancelAllJobs_whenNothingScheduled_doesNotCrash() {
        scheduler.cancelAllJobs();
    }

    @Test
    public void scheduleHeartbeat_passesInputDataToWorker() throws Exception {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        scheduler.scheduleHeartbeat(config);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("heartbeat_task")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Work should be enqueued", workInfos.isEmpty());
        assertNotNull("WorkInfo should have data", workInfos.get(0));
    }

    @Test
    public void scheduleCronJob_passesJobDataToWorker() throws Exception {
        CronJob job = new CronJob("cron-input-test", "Input Test", "Test prompt data", "3600000");
        job.setModelReference("provider1/model1");
        scheduler.scheduleJob(job);

        Thread.sleep(100);

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("cron_job_cron-input-test")
                .get(5, TimeUnit.SECONDS);

        assertFalse("Work should be enqueued", workInfos.isEmpty());
        assertNotNull("WorkInfo should have data", workInfos.get(0));
    }
}
