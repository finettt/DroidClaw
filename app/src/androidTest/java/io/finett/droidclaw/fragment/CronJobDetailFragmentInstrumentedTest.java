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
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Instrumented tests for CronJobDetailFragment.
 * Tests the job details display and actions.
 */
@RunWith(AndroidJUnit4.class)
public class CronJobDetailFragmentInstrumentedTest {

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
    public void launch_withCronJob_displaysAllFields() {
        // Create and save a cron job
        CronJob job = new CronJob("cron-detail-1", "Daily Report", "Generate daily report", "86400000");
        job.setEnabled(true);
        job.setCreatedAt(1000L);
        job.setLastRunTimestamp(2000L);
        job.setSuccessCount(5);
        job.setFailureCount(1);
        job.setTotalExecutionTime(60000L); // 60 seconds total
        repository.saveCronJob(job);

        // Launch fragment with job ID
        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check all text views exist
                TextView jobName = view.findViewById(R.id.text_job_name);
                TextView jobPrompt = view.findViewById(R.id.text_job_prompt);
                TextView schedule = view.findViewById(R.id.text_schedule);
                TextView status = view.findViewById(R.id.text_status);
                TextView created = view.findViewById(R.id.text_created);
                TextView successRate = view.findViewById(R.id.text_success_rate);
                TextView avgDuration = view.findViewById(R.id.text_avg_duration);
                TextView totalRuns = view.findViewById(R.id.text_total_runs);
                TextView lastRun = view.findViewById(R.id.text_last_run);

                assertNotNull("Job name view should exist", jobName);
                assertNotNull("Job prompt view should exist", jobPrompt);
                assertNotNull("Schedule view should exist", schedule);
                assertNotNull("Status view should exist", status);
                assertNotNull("Created view should exist", created);
                assertNotNull("Success rate view should exist", successRate);
                assertNotNull("Avg duration view should exist", avgDuration);
                assertNotNull("Total runs view should exist", totalRuns);
                assertNotNull("Last run view should exist", lastRun);

                // Verify content is displayed
                assertNotNull("Job name should not be null", jobName.getText());
                assertNotNull("Job prompt should not be null", jobPrompt.getText());
                // Schedule is displayed - format depends on the schedule string
                String scheduleText = schedule.getText().toString();
                assertTrue("Schedule should be displayed", scheduleText.length() > 0 && !scheduleText.contains("Unknown"));
            });
        }
    }

    @Test
    public void launch_withPausedJob_showsPausedStatus() {
        CronJob job = new CronJob("cron-paused-1", "Paused Job", "Test prompt", "3600000");
        job.setEnabled(true);
        job.setPaused(true);
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView status = view.findViewById(R.id.text_status);

                String statusText = status.getText().toString();
                assertTrue("Status should show paused", statusText.contains("Paused"));
            });
        }
    }

    @Test
    public void launch_withDisabledJob_showsDisabledStatus() {
        CronJob job = new CronJob("cron-disabled-1", "Disabled Job", "Test prompt", "3600000");
        job.setEnabled(false);
        job.setPaused(false);
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView status = view.findViewById(R.id.text_status);

                String statusText = status.getText().toString();
                assertTrue("Status should show disabled", statusText.contains("Disabled"));
            });
        }
    }

    @Test
    public void launch_withActiveJob_showsActiveStatus() {
        CronJob job = new CronJob("cron-active-1", "Active Job", "Test prompt", "3600000");
        job.setEnabled(true);
        job.setPaused(false);
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView status = view.findViewById(R.id.text_status);

                String statusText = status.getText().toString();
                assertTrue("Status should show active", statusText.contains("Active"));
            });
        }
    }

    @Test
    public void launch_withMetrics_calculatesCorrectly() {
        CronJob job = new CronJob("cron-metrics-1", "Metrics Job", "Test prompt", "3600000");
        job.setSuccessCount(8);
        job.setFailureCount(2);
        job.setTotalExecutionTime(150000L); // 150 seconds for 10 runs = 15s avg
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView successRate = view.findViewById(R.id.text_success_rate);
                TextView totalRuns = view.findViewById(R.id.text_total_runs);
                TextView avgDuration = view.findViewById(R.id.text_avg_duration);

                // Success rate should be 80% (8/10)
                assertTrue("Success rate should contain 80", successRate.getText().toString().contains("80"));

                // Total runs should be 10
                assertTrue("Total runs should contain 10", totalRuns.getText().toString().contains("10"));

                // Average duration should be around 15s
                String avgText = avgDuration.getText().toString();
                assertTrue("Avg duration should contain 15", avgText.contains("15") || avgText.contains("15.0"));
            });
        }
    }

    @Test
    public void launch_withNeverRunJob_showsNeverRun() {
        CronJob job = new CronJob("cron-never-1", "Never Run Job", "Test prompt", "3600000");
        job.setLastRunTimestamp(0);
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView lastRun = view.findViewById(R.id.text_last_run);

                String lastRunText = lastRun.getText().toString();
                assertTrue("Last run should show 'Never'", lastRunText.contains("Never"));
            });
        }
    }

    @Test
    public void launch_showsAllButtons() {
        CronJob job = new CronJob("cron-buttons-1", "Buttons Job", "Test prompt", "3600000");
        repository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check all buttons exist
                assertNotNull("View history button should exist",
                        view.findViewById(R.id.button_view_history));
                assertNotNull("Run now button should exist",
                        view.findViewById(R.id.button_run_now));
                assertNotNull("Pause/resume button should exist",
                        view.findViewById(R.id.button_pause_resume));
                assertNotNull("Edit button should exist",
                        view.findViewById(R.id.button_edit));
                assertNotNull("Delete button should exist",
                        view.findViewById(R.id.button_delete));
            });
        }
    }

    @Test
    public void launch_withJobFromDifferentRepositoryInstance_displaysCorrectly() {
        // Save job with first repository
        CronJob job = new CronJob("cron-persist-1", "Persist Job", "Test prompt", "3600000");
        repository.saveCronJob(job);

        // Create new repository instance (simulating new fragment)
        TaskRepository newRepository = new TaskRepository(getApplicationContext());
        // Re-save with new repository to ensure it's accessible
        newRepository.saveCronJob(job);

        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", job.getId());

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView jobName = view.findViewById(R.id.text_job_name);

                assertTrue("Job name should be displayed", jobName.getText().toString().contains("Persist Job"));
            });
        }
    }

    @Test
    public void launch_withNullArguments_showsEmptyState() {
        // Launch without arguments - should handle gracefully
        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =
                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView jobName = view.findViewById(R.id.text_job_name);

                // With null job, name should be empty or show placeholder
                String text = jobName.getText().toString();
                // Either empty or shows default
                assertTrue("Job name should be empty or show default", text.isEmpty() || text.contains("Unknown"));
            });
        }
    }

    @Test
    public void launch_withNonExistentJobId_showsEmptyState() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("job_id", "non-existent-job");

        try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =

                     androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                     CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();
                TextView jobName = view.findViewById(R.id.text_job_name);

                String text = jobName.getText().toString();
                assertTrue("Job name should be empty for non-existent job", text.isEmpty() || text.contains("Unknown"));
            });
        }
    }

    @Test
    public void launch_withDifferentScheduleTypes_displaysCorrectly() {
        String[] schedules = {
                "hourly",
                "daily",
                "weekly",
                "daily@09:00",
                "weekly@monday@09:00",
                "every_2_hours",
                "3600000" // milliseconds
        };

        for (String schedule : schedules) {
            CronJob job = new CronJob("cron-schedule-" + schedule.hashCode(), "Schedule Test " + schedule.hashCode(), "Test prompt", schedule);
            repository.saveCronJob(job);

            android.os.Bundle args = new android.os.Bundle();
            args.putString("job_id", job.getId());

            try (androidx.fragment.app.testing.FragmentScenario<CronJobDetailFragment> scenario =
                         androidx.fragment.app.testing.FragmentScenario.launchInContainer(
                                         CronJobDetailFragment.class, args, R.style.Theme_DroidClaw)) {
                scenario.onFragment(fragment -> {
                    View view = fragment.requireView();
                    TextView scheduleView = view.findViewById(R.id.text_schedule);

                    // Schedule should be displayed (formatted)
                    String scheduleText = scheduleView.getText().toString();
                    assertTrue("Schedule should be displayed for: " + schedule,
                            scheduleText.length() > 0 && !scheduleText.contains("Unknown"));
                });
            }
        }
    }
}
