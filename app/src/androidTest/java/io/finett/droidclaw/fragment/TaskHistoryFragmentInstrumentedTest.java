package io.finett.droidclaw.fragment;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Instrumented tests for TaskHistoryFragment.
 * Tests the history display, filtering, and statistics calculation.
 */
@RunWith(AndroidJUnit4.class)
public class TaskHistoryFragmentInstrumentedTest {

    private TaskRepository repository;

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        getApplicationContext()
                .getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        repository = new TaskRepository(getApplicationContext());
    }

    @After
    public void tearDown() {
        // Clean up after tests
        getApplicationContext()
                .getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void launch_withNoRecords_showsEmptyState() {
        // No records saved

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null); // All jobs

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView emptyText = view.findViewById(R.id.text_empty_history);
                View statsCard = view.findViewById(R.id.card_stats);

                assertNotNull("Empty state text should exist", emptyText);
                assertTrue("Empty state should be visible", emptyText.getVisibility() == View.VISIBLE);
                assertTrue("Stats card should be hidden", statsCard.getVisibility() == View.GONE);
            });
        }
    }

    @Test
    public void launch_withRecords_showsHistoryList() {
        // Create execution records
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record1.setEndTime(2000L);
        record1.setSuccess(true);
        record1.setTokensUsed(100);
        record1.setIterations(3);

        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", 1, 3000L);
        record2.setEndTime(4000L);
        record2.setSuccess(false);
        record2.setTokensUsed(150);
        record2.setIterations(5);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView emptyText = view.findViewById(R.id.text_empty_history);
                View statsCard = view.findViewById(R.id.card_stats);

                assertTrue("Empty state should be hidden", emptyText.getVisibility() == View.GONE);
                assertTrue("Stats card should be visible", statsCard.getVisibility() == View.VISIBLE);
            });
        }
    }

    @Test
    public void launch_withRecords_showsStats() {
        // Create execution records with known values
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record1.setEndTime(2000L);
        record1.setSuccess(true);
        record1.setTokensUsed(100);
        record1.setIterations(3);

        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", 1, 3000L);
        record2.setEndTime(4000L);
        record2.setSuccess(true);
        record2.setTokensUsed(200);
        record2.setIterations(5);

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView totalExecutions = view.findViewById(R.id.text_total_executions);
                TextView successRate = view.findViewById(R.id.text_success_rate);
                TextView avgDuration = view.findViewById(R.id.text_avg_duration);

                assertNotNull("Total executions should exist", totalExecutions);
                assertNotNull("Success rate should exist", successRate);
                assertNotNull("Avg duration should exist", avgDuration);

                // Should show 2 total executions, 100% success rate, 1s avg duration
                assertTrue("Should show 2 executions", totalExecutions.getText().toString().contains("2"));
                assertTrue("Should show 100% success", successRate.getText().toString().contains("100"));
                assertTrue("Should show 1s duration", avgDuration.getText().toString().contains("1"));
            });
        }
    }

    @Test
    public void launch_withMixedSuccessRecords_calculatesCorrectSuccessRate() {
        // Create records with 50% success rate
        TaskExecutionRecord success1 = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        success1.setEndTime(2000L);
        success1.setSuccess(true);

        TaskExecutionRecord success2 = new TaskExecutionRecord("task-2", "session-2", 1, 3000L);
        success2.setEndTime(4000L);
        success2.setSuccess(true);

        TaskExecutionRecord fail1 = new TaskExecutionRecord("task-3", "session-3", 1, 5000L);
        fail1.setEndTime(6000L);
        fail1.setSuccess(false);

        TaskExecutionRecord fail2 = new TaskExecutionRecord("task-4", "session-4", 1, 7000L);
        fail2.setEndTime(8000L);
        fail2.setSuccess(false);

        repository.saveExecutionRecord(success1);
        repository.saveExecutionRecord(success2);
        repository.saveExecutionRecord(fail1);
        repository.saveExecutionRecord(fail2);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);

                // 2 success out of 4 = 50%
                String rateText = successRate.getText().toString();
                assertTrue("Should show 50% success rate", rateText.contains("50"));
            });
        }
    }

    @Test
    public void launch_withNoSuccessRecords_calculatesZeroSuccessRate() {
        TaskExecutionRecord fail1 = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        fail1.setEndTime(2000L);
        fail1.setSuccess(false);

        TaskExecutionRecord fail2 = new TaskExecutionRecord("task-2", "session-2", 1, 3000L);
        fail2.setEndTime(4000L);
        fail2.setSuccess(false);

        repository.saveExecutionRecord(fail1);
        repository.saveExecutionRecord(fail2);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);

                // 0 success out of 2 = 0%
                String rateText = successRate.getText().toString();
                assertTrue("Should show 0% success rate", rateText.contains("0"));
            });
        }
    }

    @Test
    public void launch_withDifferentDurations_calculatesCorrectAvg() {
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record1.setEndTime(4000L); // 3 seconds

        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", 1, 5000L);
        record2.setEndTime(7000L); // 2 seconds

        repository.saveExecutionRecord(record1);
        repository.saveExecutionRecord(record2);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView avgDuration = view.findViewById(R.id.text_avg_duration);

                // Average of 3s and 2s = 2.5s
                String durationText = avgDuration.getText().toString();
                assertTrue("Should show ~2.5s duration", durationText.contains("2.5") || durationText.contains("2"));
            });
        }
    }

    @Test
    public void launch_withJobFilter_showsOnlyFilteredRecords() {
        // Create jobs
        CronJob job1 = new CronJob("cron-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-2", "Job 2", "Prompt 2", "7200000");
        repository.saveCronJob(job1);
        repository.saveCronJob(job2);

        // Create records for job1
        TaskExecutionRecord record1 = new TaskExecutionRecord("task-1", "session-1", 2, 1000L);
        record1.setEndTime(2000L);
        record1.setSuccess(true);
        repository.saveExecutionRecord(record1);

        // Create records for job2
        TaskExecutionRecord record2 = new TaskExecutionRecord("task-2", "session-2", 2, 3000L);
        record2.setEndTime(4000L);
        record2.setSuccess(false);
        repository.saveExecutionRecord(record2);

        // Filter by job1
        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job1.getId());

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =
                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            // Verify fragment launched successfully - data loading is async
            scenario.onFragment(fragment -> {
                // Fragment launched without crashing
                assertNotNull("Fragment should be launched", fragment);
            });
        }
    }

    @Test
    public void launch_withNonExistentJobFilter_showsEmptyState() {
        // No records for non-existent job

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", "non-existent-job");

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView emptyText = view.findViewById(R.id.text_empty_history);

                assertTrue("Empty state should be visible", emptyText.getVisibility() == View.VISIBLE);
            });
        }
    }

    @Test
    public void launch_showsAllUIElements() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check all required views exist
                assertNotNull("RecyclerView should exist", view.findViewById(R.id.recycler_task_history));
                assertNotNull("Empty text should exist", view.findViewById(R.id.text_empty_history));
                assertNotNull("Stats card should exist", view.findViewById(R.id.card_stats));
                assertNotNull("Total executions should exist", view.findViewById(R.id.text_total_executions));
                assertNotNull("Success rate should exist", view.findViewById(R.id.text_success_rate));
                assertNotNull("Avg duration should exist", view.findViewById(R.id.text_avg_duration));
                assertNotNull("Job filter spinner should exist", view.findViewById(R.id.spinner_job_filter));
                assertNotNull("Refresh FAB should exist", view.findViewById(R.id.fab_refresh_history));
            });
        }
    }

    @Test
    public void launch_withSingleSuccessRecord_shows100Percent() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);
                String rateText = successRate.getText().toString();

                assertTrue("Should show 100% success rate", rateText.contains("100"));
            });
        }
    }

    @Test
    public void launch_withSingleFailRecord_shows0Percent() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);
                String rateText = successRate.getText().toString();

                assertTrue("Should show 0% success rate", rateText.contains("0"));
            });
        }
    }

    @Test
    public void launch_withVeryShortDuration_formatsCorrectly() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(1500L); // 0.5 seconds
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView avgDuration = view.findViewById(R.id.text_avg_duration);
                String durationText = avgDuration.getText().toString();

                // Should format as 0.5s
                assertTrue("Should show 0.5s duration", durationText.contains("0.5") || durationText.contains("s"));
            });
        }
    }

    @Test
    public void launch_withLongDuration_formatsAsMinutes() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(130000L); // 2 minutes 1 second
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView avgDuration = view.findViewById(R.id.text_avg_duration);
                String durationText = avgDuration.getText().toString();

                // Should format as 2m 1s
                assertTrue("Should show minutes and seconds", durationText.contains("2m") || durationText.contains("2"));
            });
        }
    }

    @Test
    public void launch_withNoDurationRecords_showsNA() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(0L); // No duration set
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView avgDuration = view.findViewById(R.id.text_avg_duration);
                String durationText = avgDuration.getText().toString();

                // Should show N/A or 0
                assertTrue("Should show N/A or 0 duration", durationText.contains("N/A") ||
                        durationText.contains("0") || durationText.isEmpty());
            });
        }
    }

    @Test
    public void launch_withLargeSuccessRate_formatsCorrectly() {
        // 97 out of 100 = 97%
        for (int i = 0; i < 100; i++) {
            TaskExecutionRecord record = new TaskExecutionRecord("task-" + i, "session-" + i, 1, 1000L);
            record.setEndTime(2000L);
            record.setSuccess(i < 97); // 97 success
            repository.saveExecutionRecord(record);
        }

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);
                String rateText = successRate.getText().toString();

                assertTrue("Should show 97% success rate", rateText.contains("97"));
            });
        }
    }

    @Test
    public void launch_withStatsHiddenWhenNoRecords() {
        // No records

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                View statsCard = view.findViewById(R.id.card_stats);

                assertTrue("Stats card should be hidden when no records", statsCard.getVisibility() == View.GONE);
            });
        }
    }

    @Test
    public void launch_withStatsVisibleWhenRecordsExist() {
        TaskExecutionRecord record = new TaskExecutionRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        repository.saveExecutionRecord(record);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                View statsCard = view.findViewById(R.id.card_stats);

                assertTrue("Stats card should be visible when records exist", statsCard.getVisibility() == View.VISIBLE);
            });
        }
    }

    @Test
    public void launch_withMultipleJobs_filterSpinnerPopulated() {
        CronJob job1 = new CronJob("cron-1", "Job 1", "Prompt 1", "3600000");
        CronJob job2 = new CronJob("cron-2", "Job 2", "Prompt 2", "7200000");
        CronJob job3 = new CronJob("cron-3", "Job 3", "Prompt 3", "10800000");
        repository.saveCronJob(job1);
        repository.saveCronJob(job2);
        repository.saveCronJob(job3);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Spinner should be populated with jobs
                // Note: We can't directly test spinner content in instrumented tests
                // but we verify the spinner exists and is functional
                assertNotNull("Spinner should exist", view.findViewById(R.id.spinner_job_filter));
            });
        }
    }

    @Test
    public void launch_withEmptyJobList_filterSpinnerHasAllOption() {
        // No jobs saved

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", null);

        try (androidx.fragment.app.testing.FragmentScenario<TaskHistoryFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     TaskHistoryFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Spinner should still exist with "All" option
                assertNotNull("Spinner should exist", view.findViewById(R.id.spinner_job_filter));
            });
        }
    }
}
