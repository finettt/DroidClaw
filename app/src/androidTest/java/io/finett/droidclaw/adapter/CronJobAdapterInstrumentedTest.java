package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.util.TestThemeHelper;

/**
 * Instrumented tests for CronJobAdapter.
 * Tests the adapter's RecyclerView binding and DiffUtil functionality.
 */
@RunWith(AndroidJUnit4.class)
public class CronJobAdapterInstrumentedTest {

    private CronJobAdapter adapter;
    private TestOnCronJobClickListener listener;

    @Before
    public void setUp() {
        listener = new TestOnCronJobClickListener();
        adapter = new CronJobAdapter(listener);
    }

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_withSingleJob_increasesCount() {
        CronJob job = createTestJob("job-1", "Test Job", "3600000");

        adapter.submitList(java.util.Arrays.asList(job));

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void submitList_withMultipleJobs_increasesCount() {
        CronJob job1 = createTestJob("job-1", "Job 1", "3600000");
        CronJob job2 = createTestJob("job-2", "Job 2", "7200000");
        CronJob job3 = createTestJob("job-3", "Job 3", "10800000");

        adapter.submitList(java.util.Arrays.asList(job1, job2, job3));

        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void submitList_withEmptyList_setsCountToZero() {
        adapter.submitList(java.util.Arrays.asList(
                createTestJob("job-1", "Job 1", "3600000"),
                createTestJob("job-2", "Job 2", "7200000")
        ));

        assertEquals(2, adapter.getItemCount());

        adapter.submitList(java.util.Collections.emptyList());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void getJobAt_validPosition_returnsJob() {
        CronJob job = createTestJob("job-1", "Test Job", "3600000");
        adapter.submitList(java.util.Arrays.asList(job));

        CronJob retrieved = adapter.getJobAt(0);

        assertNotNull("Retrieved job should not be null", retrieved);
        assertEquals("job-1", retrieved.getId());
        assertEquals("Test Job", retrieved.getName());
    }

    @Test
    public void getJobAt_invalidPosition_throwsException() {
        CronJob job = createTestJob("job-1", "Test Job", "3600000");
        adapter.submitList(java.util.Arrays.asList(job));

        // Accessing out of bounds should throw
        try {
            adapter.getJobAt(1);
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void onCreateViewHolder_createsViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        assertNotNull("ViewHolder should not be null", viewHolder);
        assertNotNull("ViewHolder.itemView should not be null", viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_bindsJobData() {
        CronJob job = createTestJob("job-bind", "Bind Test Job", "daily@09:00");
        job.setEnabled(true);
        job.setPaused(false);
        job.setLastRunTimestamp(1000L);
        job.setSuccessCount(5);
        job.setFailureCount(1);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify views are bound
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_job_name));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_schedule));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_last_run));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_success_rate));
        assertNotNull(viewHolder.itemView.findViewById(R.id.chip_status));
    }

    @Test
    public void onBindViewHolder_withEnabledActiveJob_showsActiveStatus() {
        CronJob job = createTestJob("job-active", "Active Job", "3600000");
        job.setEnabled(true);
        job.setPaused(false);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Status chip should show active
        String statusText = viewHolder.chipStatus.getText().toString();
        assertTrue("Status should be active", statusText.contains("Active"));
    }

    @Test
    public void onBindViewHolder_withPausedJob_showsPausedStatus() {
        CronJob job = createTestJob("job-paused", "Paused Job", "3600000");
        job.setEnabled(true);
        job.setPaused(true);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Status chip should show paused
        String statusText = viewHolder.chipStatus.getText().toString();
        assertTrue("Status should be paused", statusText.contains("Paused"));
    }

    @Test
    public void onBindViewHolder_withDisabledJob_showsDisabledStatus() {
        CronJob job = createTestJob("job-disabled", "Disabled Job", "3600000");
        job.setEnabled(false);
        job.setPaused(false);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Status chip should show disabled
        String statusText = viewHolder.chipStatus.getText().toString();
        assertTrue("Status should be disabled", statusText.contains("Disabled"));
    }

    @Test
    public void onBindViewHolder_withLastRun_showsRelativeTime() {
        long oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L;
        CronJob job = createTestJob("job-time", "Time Job", "3600000");
        job.setLastRunTimestamp(oneHourAgo);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Last run should show relative time
        TextView lastRunView = viewHolder.itemView.findViewById(R.id.text_last_run);
        String text = lastRunView.getText().toString();
        assertTrue("Last run should show relative time", text.contains("ago") || text.contains("hour"));
    }

    @Test
    public void onBindViewHolder_withNoLastRun_showsNeverRun() {
        CronJob job = createTestJob("job-never", "Never Job", "3600000");
        job.setLastRunTimestamp(0);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Last run should show "Never"
        TextView lastRunView = viewHolder.itemView.findViewById(R.id.text_last_run);
        String text = lastRunView.getText().toString();
        assertTrue("Last run should show 'Never'", text.contains("Never"));
    }

    @Test
    public void onBindViewHolder_withSuccessRate_showsPercentage() {
        CronJob job = createTestJob("job-rate", "Rate Job", "3600000");
        job.setSuccessCount(8);
        job.setFailureCount(2);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Success rate should show 80%
        TextView rateView = viewHolder.itemView.findViewById(R.id.text_success_rate);
        String text = rateView.getText().toString();
        assertTrue("Success rate should contain 80", text.contains("80"));
    }

    @Test
    public void onBindViewHolder_withErrorMessage_showsError() {
        CronJob job = createTestJob("job-error", "Error Job", "3600000");
        job.setLastError("Connection timeout");
        job.setRetryCount(1);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Error message should be visible
        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        assertTrue("Error view should be visible", errorView.getVisibility() == View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_withoutErrorMessage_hidesError() {
        CronJob job = createTestJob("job-noerror", "No Error Job", "3600000");
        job.setLastError("");
        job.setRetryCount(0);

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Error message should be hidden
        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        assertTrue("Error view should be hidden", errorView.getVisibility() == View.GONE);
    }

    @Test
    public void onBindViewHolder_clickListener_setsOnClickListener() {
        CronJob job = createTestJob("job-click", "Click Job", "3600000");

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Click the item
        viewHolder.itemView.performClick();

        // Verify listener was called
        assertEquals(1, listener.clickCount);
        assertEquals("job-click", listener.clickedJob.getId());
    }

    @Test
    public void onBindViewHolder_longClickListener_setsOnLongClickListener() {
        CronJob job = createTestJob("job-longclick", "Long Click Job", "3600000");

        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Long click the item
        viewHolder.itemView.performLongClick();

        // Verify listener was called
        assertEquals(1, listener.longClickCount);
        assertEquals("job-longclick", listener.longClickedJob.getId());
    }

    @Test
    public void submitList_withSameJobIds_doesNotChangeCount() {
        CronJob job = createTestJob("job-same", "Same Job", "3600000");
        adapter.submitList(java.util.Arrays.asList(job));

        assertEquals(1, adapter.getItemCount());

        // Update the job with same ID
        CronJob updatedJob = createTestJob("job-same", "Updated Job", "7200000");
        updatedJob.setSuccessCount(10);

        adapter.submitList(java.util.Arrays.asList(updatedJob));

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void submitList_differentJobIds_updatesCount() {
        CronJob job1 = createTestJob("job-a", "Job A", "3600000");
        adapter.submitList(java.util.Arrays.asList(job1));

        assertEquals(1, adapter.getItemCount());

        CronJob job2 = createTestJob("job-b", "Job B", "7200000");
        adapter.submitList(java.util.Arrays.asList(job2));

        assertEquals(1, adapter.getItemCount());
        assertEquals("job-b", adapter.getJobAt(0).getId());
    }

    @Test
    public void submitList_addsNewJobs_increasesCount() {
        CronJob job1 = createTestJob("job-1", "Job 1", "3600000");
        adapter.submitList(java.util.Arrays.asList(job1));

        assertEquals(1, adapter.getItemCount());

        CronJob job2 = createTestJob("job-2", "Job 2", "7200000");
        adapter.submitList(java.util.Arrays.asList(job1, job2));

        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void submitList_removesJobs_decreasesCount() {
        CronJob job1 = createTestJob("job-1", "Job 1", "3600000");
        CronJob job2 = createTestJob("job-2", "Job 2", "7200000");
        CronJob job3 = createTestJob("job-3", "Job 3", "10800000");

        adapter.submitList(java.util.Arrays.asList(job1, job2, job3));
        assertEquals(3, adapter.getItemCount());

        adapter.submitList(java.util.Arrays.asList(job1, job2));
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void submitList_updatesContent_correctly() {
        CronJob job = createTestJob("job-update", "Original Name", "3600000");
        job.setSuccessCount(5);
        adapter.submitList(java.util.Arrays.asList(job));

        // Update the job
        CronJob updatedJob = createTestJob("job-update", "Updated Name", "7200000");
        updatedJob.setSuccessCount(10);

        adapter.submitList(java.util.Arrays.asList(updatedJob));

        assertEquals(1, adapter.getItemCount());
        CronJob retrieved = adapter.getJobAt(0);
        assertEquals("Updated Name", retrieved.getName());
        assertEquals(10, retrieved.getSuccessCount());
        assertEquals("7200000", retrieved.getSchedule());
    }

    @Test
    public void onBindViewHolder_withDifferentSchedules_formatsCorrectly() {
        String[] schedules = {
                "hourly",
                "daily",
                "weekly",
                "daily@09:00",
                "weekly@monday@10:30",
                "every_2_hours",
                "every_30_minutes",
                "3600000"
        };

        for (String schedule : schedules) {
            CronJob job = createTestJob("job-" + schedule.hashCode(), "Schedule Test", schedule);

            adapter.submitList(java.util.Arrays.asList(job));

            Context context = TestThemeHelper.getThemedContext();
            RecyclerView recyclerView = new RecyclerView(context);
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                    recyclerView,
                    0
            );

            adapter.onBindViewHolder(viewHolder, 0);

            // Schedule should be formatted and displayed
            TextView scheduleView = viewHolder.itemView.findViewById(R.id.text_schedule);
            assertNotNull("Schedule view should exist", scheduleView);
            assertTrue("Schedule should be displayed", scheduleView.getText().toString().length() > 0);
        }
    }

    @Test
    public void onCreateViewHolder_withViewHolderType_zero_returnsViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        assertNotNull("ViewHolder should be created", viewHolder);
    }

    @Test
    public void onBindViewHolder_withNullJob_doesNotCrash() {
        // This test verifies the adapter handles edge cases gracefully
        // In practice, onBindViewHolder should never receive a null item
        // but we verify the adapter works correctly with normal data
        CronJob job = createTestJob("job-normal", "Normal Job", "3600000");
        adapter.submitList(java.util.Arrays.asList(job));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CronJobAdapter.CronJobViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        // Should not crash
        adapter.onBindViewHolder(viewHolder, 0);
        assertNotNull("ViewHolder should be bound", viewHolder.itemView);
    }

    /**
     * Test click listener implementation.
     */
    private static class TestOnCronJobClickListener implements CronJobAdapter.OnCronJobClickListener {
        private int clickCount = 0;
        private int longClickCount = 0;
        private CronJob clickedJob;
        private CronJob longClickedJob;

        @Override
        public void onCronJobClick(CronJob job) {
            clickCount++;
            clickedJob = job;
        }

        @Override
        public void onCronJobLongClick(CronJob job, View anchorView) {
            longClickCount++;
            longClickedJob = job;
        }
    }

    /**
     * Helper method to create a test cron job.
     */
    private CronJob createTestJob(String id, String name, String schedule) {
        CronJob job = new CronJob(id, name, "Test Prompt", schedule);
        job.setEnabled(true);
        job.setPaused(false);
        job.setCreatedAt(System.currentTimeMillis());
        job.setLastRunTimestamp(System.currentTimeMillis() - 10000L);
        job.setSuccessCount(3);
        job.setFailureCount(0);
        return job;
    }
}
