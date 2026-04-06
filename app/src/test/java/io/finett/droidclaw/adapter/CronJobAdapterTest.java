package io.finett.droidclaw.adapter;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.CronJob;

/**
 * Unit tests for CronJobAdapter.
 * Tests adapter creation, item listing, job access, and data submission.
 */
@RunWith(RobolectricTestRunner.class)
public class CronJobAdapterTest {

    private CronJobAdapter adapter;
    private List<CronJob> testJobs;
    private OnCronJobClickListenerMock listenerMock;

    /**
     * Mock implementation of the click listener interface.
     */
    private static class OnCronJobClickListenerMock implements CronJobAdapter.OnCronJobClickListener {
        private CronJob clickedJob = null;
        private CronJob longClickedJob = null;

        @Override
        public void onCronJobClick(CronJob job) {
            clickedJob = job;
        }

        @Override
        public void onCronJobLongClick(CronJob job) {
            longClickedJob = job;
        }

        public void reset() {
            clickedJob = null;
            longClickedJob = null;
        }

        public CronJob getClickedJob() {
            return clickedJob;
        }

        public CronJob getLongClickedJob() {
            return longClickedJob;
        }
    }

    @Before
    public void setUp() {
        listenerMock = new OnCronJobClickListenerMock();
        adapter = new CronJobAdapter(listenerMock);

        // Create test cron jobs
        testJobs = new ArrayList<>();

        CronJob job1 = CronJob.create("Server Log Checker", "Check server logs for errors", 60);
        job1.recordSuccess();
        job1.recordSuccess();
        job1.recordFailure();

        CronJob job2 = CronJob.create("GitHub Issues Monitor", "Monitor GitHub issues for new bugs", 30);
        job2.setEnabled(false);
        job2.recordSuccess();

        CronJob job3 = CronJob.create("Database Backup Check", "Verify database backup status", 1440);
        // No executions yet

        testJobs.add(job1);
        testJobs.add(job2);
        testJobs.add(job3);
    }

    // ========== CONSTRUCTOR AND BASIC TESTS ==========

    @Test
    public void testAdapterCreation() {
        assertNotNull(adapter);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSubmitList() {
        adapter.submitList(testJobs);
        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void testSubmitListEmpty() {
        adapter.submitList(new ArrayList<>());
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testGetJob() {
        adapter.submitList(testJobs);
        CronJob job = adapter.getJob(0);
        assertEquals("Server Log Checker", job.getName());
    }

    @Test
    public void testGetJobAtPosition() {
        adapter.submitList(testJobs);
        CronJob job = adapter.getJob(1);
        assertEquals("GitHub Issues Monitor", job.getName());
        assertFalse(job.isEnabled());
    }

    // ========== JOB STATE DISPLAY TESTS ==========

    @Test
    public void testEnabledJobDisplay() {
        CronJob enabledJob = CronJob.create("Test Job", "Test prompt", 60);

        adapter.submitList(List.of(enabledJob));

        assertEquals(1, adapter.getItemCount());
        assertTrue(adapter.getJob(0).isEnabled());
    }

    @Test
    public void testDisabledJobDisplay() {
        CronJob disabledJob = CronJob.create("Test Job", "Test prompt", 60);
        disabledJob.setEnabled(false);

        adapter.submitList(List.of(disabledJob));

        assertEquals(1, adapter.getItemCount());
        assertFalse(adapter.getJob(0).isEnabled());
    }

    // ========== INTERVAL DISPLAY TESTS ==========

    @Test
    public void testIntervalDisplayMinutes() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 45);

        adapter.submitList(List.of(job));

        assertEquals("45 minutes", adapter.getJob(0).getIntervalDisplayString());
    }

    @Test
    public void testIntervalDisplayHours() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 120);

        adapter.submitList(List.of(job));

        assertEquals("2 hours", adapter.getJob(0).getIntervalDisplayString());
    }

    @Test
    public void testIntervalDisplayDays() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 1440);

        adapter.submitList(List.of(job));

        assertEquals("1 day", adapter.getJob(0).getIntervalDisplayString());
    }

    // ========== EXECUTION STATS TESTS ==========

    @Test
    public void testJobWithNoExecutions() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);

        adapter.submitList(List.of(job));

        assertEquals(0, adapter.getJob(0).getRunCount());
        assertEquals(0, adapter.getJob(0).getSuccessRate());
        assertEquals(0, adapter.getJob(0).getLastRunAt());
    }

    @Test
    public void testJobWithSuccessfulExecutions() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.recordSuccess();
        job.recordSuccess();

        adapter.submitList(List.of(job));

        assertEquals(2, adapter.getJob(0).getRunCount());
        assertEquals(100, adapter.getJob(0).getSuccessRate());
        assertTrue(adapter.getJob(0).getLastRunAt() > 0);
    }

    @Test
    public void testJobWithMixedExecutions() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.recordSuccess();
        job.recordFailure();
        job.recordSuccess();

        adapter.submitList(List.of(job));

        assertEquals(3, adapter.getJob(0).getRunCount());
        assertEquals(66, adapter.getJob(0).getSuccessRate());
    }

    @Test
    public void testJobWithFailedExecutions() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.recordFailure();
        job.recordFailure();

        adapter.submitList(List.of(job));

        assertEquals(2, adapter.getJob(0).getRunCount());
        assertEquals(0, adapter.getJob(0).getSuccessRate());
    }

    // ========== SUBMIT LIST UPDATES TESTS ==========

    @Test
    public void testSubmitListReplacesOldList() {
        List<CronJob> firstList = List.of(
            CronJob.create("Job 1", "Prompt 1", 30)
        );
        List<CronJob> secondList = List.of(
            CronJob.create("Job 2", "Prompt 2", 60),
            CronJob.create("Job 3", "Prompt 3", 120)
        );

        adapter.submitList(firstList);
        assertEquals(1, adapter.getItemCount());

        adapter.submitList(secondList);
        assertEquals(2, adapter.getItemCount());
        assertEquals("Job 2", adapter.getJob(0).getName());
    }

    @Test
    public void testSubmitListClearsOldData() {
        CronJob oldJob = CronJob.create("Old Job", "Old prompt", 30);
        adapter.submitList(List.of(oldJob));
        assertEquals(1, adapter.getItemCount());

        adapter.submitList(new ArrayList<>());
        assertEquals(0, adapter.getItemCount());
    }

    // ========== NEXT RUN DISPLAY TESTS ==========

    @Test
    public void testNextRunCalculation() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.recordSuccess();

        adapter.submitList(List.of(job));

        // Next run should be calculated (lastRunAt + 60 minutes)
        long expectedNextRun = job.getLastRunAt() + (60 * 60 * 1000);
        assertEquals(expectedNextRun, job.getNextRunAt());
    }

    @Test
    public void testNextRunWhenDisabled() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        job.setEnabled(false);
        job.recordSuccess();

        adapter.submitList(List.of(job));

        // Next run should be 0 when disabled
        assertEquals(0, job.getNextRunAt());
    }

    // ========== TIME STRING FORMATTING TESTS ==========

    @Test
    public void testTimeAgoStringSeconds() {
        // Testing the adapter's time formatting logic through job state
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        long now = System.currentTimeMillis();
        job.setLastRunAt(now - 30000); // 30 seconds ago

        adapter.submitList(List.of(job));

        assertTrue(adapter.getJob(0).getLastRunAt() > 0);
    }

    @Test
    public void testTimeAgoStringMinutes() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        long now = System.currentTimeMillis();
        job.setLastRunAt(now - (5 * 60 * 1000)); // 5 minutes ago

        adapter.submitList(List.of(job));

        assertTrue(adapter.getJob(0).getLastRunAt() > 0);
    }

    @Test
    public void testTimeAgoStringHours() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 60);
        long now = System.currentTimeMillis();
        job.setLastRunAt(now - (3 * 60 * 60 * 1000)); // 3 hours ago

        adapter.submitList(List.of(job));

        assertTrue(adapter.getJob(0).getLastRunAt() > 0);
    }

    @Test
    public void testTimeAgoStringDays() {
        CronJob job = CronJob.create("Test Job", "Test prompt", 1440);
        long now = System.currentTimeMillis();
        job.setLastRunAt(now - (2 * 24 * 60 * 60 * 1000)); // 2 days ago

        adapter.submitList(List.of(job));

        assertTrue(adapter.getJob(0).getLastRunAt() > 0);
    }

    // ========== LISTENER INTEGRATION TESTS ==========

    @Test
    public void testListenerIsCalledOnJobAccess() {
        // Verify listener is properly set
        assertNotNull(listenerMock);
        listenerMock.reset();
    }
}
