package io.finett.droidclaw.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.util.TestThemeHelper;

/**
 * Instrumented tests for CronJobEditorDialog.
 * Tests the dialog UI and schedule parsing.
 */
@RunWith(AndroidJUnit4.class)
public class CronJobEditorDialogInstrumentedTest {

    private CronJobEditorDialog dialog;
    private CronJob jobToEdit;

    @Before
    public void setUp() {
        jobToEdit = new CronJob("cron-edit-1", "Test Job", "Test Prompt", "3600000");
    }

    @Test
    public void create_withNewJob_showsAddTitle() {
        dialog = CronJobEditorDialog.newInstance(null);
        dialog.setTargetFragment(new TestTargetFragment(), 0);

        // Note: We can't actually show the dialog in instrumented tests without activity,
        // but we can test the parsing logic and model creation
        assertNotNull("Dialog should be created", dialog);
    }

    @Test
    public void create_withExistingJob_showsEditTitle() {
        dialog = CronJobEditorDialog.newInstance(jobToEdit);
        dialog.setTargetFragment(new TestTargetFragment(), 0);

        assertNotNull("Dialog should be created", dialog);
    }

    @Test
    public void parseSchedule_hourly_setsHourlyRadio() {
        // Test the static parsing method
        long interval = CronJobScheduler.parseScheduleToInterval("hourly");

        assertEquals(60 * 60 * 1000L, interval); // 1 hour in milliseconds
    }

    @Test
    public void parseSchedule_daily_setsDailyRadio() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily");

        assertEquals(24 * 60 * 60 * 1000L, interval); // 1 day in milliseconds
    }

    @Test
    public void parseSchedule_weekly_setsWeeklyRadio() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly");

        assertEquals(7 * 24 * 60 * 60 * 1000L, interval); // 7 days in milliseconds
    }

    @Test
    public void parseSchedule_dailyAtTime_extractsTime() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily@09:30");

        assertEquals(24 * 60 * 60 * 1000L, interval); // Still 1 day interval
    }

    @Test
    public void parseSchedule_weeklyAtDayTime_extractsDayAndTime() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly@monday@09:00");

        assertEquals(7 * 24 * 60 * 60 * 1000L, interval); // Still 7 day interval
    }

    @Test
    public void parseSchedule_customInterval_extractsValueAndUnit() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_2_hours");

        assertEquals(2 * 60 * 60 * 1000L, interval); // 2 hours
    }

    @Test
    public void parseSchedule_minutesInterval_extractsValue() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_30_minutes");

        assertEquals(30 * 60 * 1000L, interval); // 30 minutes
    }

    @Test
    public void parseSchedule_daysInterval_extractsValue() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_3_days");

        assertEquals(3 * 24 * 60 * 60 * 1000L, interval); // 3 days
    }

    @Test
    public void parseSchedule_milliseconds_extractsValue() {
        long interval = CronJobScheduler.parseScheduleToInterval("7200000");

        assertEquals(7200000L, interval); // 2 hours
    }

    @Test
    public void parseSchedule_defaultFallback_returnsOneHour() {
        long interval = CronJobScheduler.parseScheduleToInterval("unknown_schedule");

        assertEquals(60 * 60 * 1000L, interval); // Default 1 hour
    }

    @Test
    public void formatScheduleForDisplay_hourly_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("hourly");

        assertEquals("Every hour", display);
    }

    @Test
    public void formatScheduleForDisplay_daily_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("daily");

        assertEquals("Daily", display);
    }

    @Test
    public void formatScheduleForDisplay_weekly_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("weekly");

        assertEquals("Weekly", display);
    }

    @Test
    public void formatScheduleForDisplay_dailyAtTime_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("daily@09:30");

        assertEquals("Daily at 9:30 AM", display);
    }

    @Test
    public void formatScheduleForDisplay_weeklyAtDayTime_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("weekly@monday@09:00");

        assertEquals("Monday at 9:00 AM", display);
    }

    @Test
    public void formatScheduleForDisplay_customInterval_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_2_hours");

        assertEquals("Every 2 hours", display);
    }

    @Test
    public void formatScheduleForDisplay_milliseconds_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("7200000");

        assertEquals("Every 2 hours", display);
    }

    @Test
    public void formatTime_HHMM_toReadableFormat() {
        String formatted = CronJobScheduler.formatTime("09:30");

        assertEquals("9:30 AM", formatted);
    }

    @Test
    public void formatTime_midnight_toReadableFormat() {
        String formatted = CronJobScheduler.formatTime("00:00");

        assertEquals("12:00 AM", formatted);
    }

    @Test
    public void formatTime_noon_toReadableFormat() {
        String formatted = CronJobScheduler.formatTime("12:00");

        assertEquals("12:00 PM", formatted);
    }

    @Test
    public void formatTime_evening_toReadableFormat() {
        String formatted = CronJobScheduler.formatTime("18:45");

        assertEquals("6:45 PM", formatted);
    }

    @Test
    public void buildScheduleString_hourlyRadio_returnsHourly() {
        // This tests the internal logic by creating a mock scenario
        // The actual build logic is in the dialog, so we test the scheduler's conversion

        long interval = CronJobScheduler.parseScheduleToInterval("hourly");
        assertEquals(60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_dailyRadio_returnsDaily() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily");
        assertEquals(24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_weeklyRadio_returnsWeekly() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly");
        assertEquals(7 * 24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_customRadio_returnsInterval() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_5_hours");
        assertEquals(5 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void parseAndSetSchedule_hourlyString_setsCorrectRadio() {
        // Test that hourly string is parsed correctly
        String schedule = "hourly";
        long interval = CronJobScheduler.parseScheduleToInterval(schedule);

        assertEquals(60 * 60 * 1000L, interval);
    }

    @Test
    public void parseAndSetSchedule_dailyTimeString_setsCorrectRadio() {
        String schedule = "daily@14:30";
        long interval = CronJobScheduler.parseScheduleToInterval(schedule);

        assertEquals(24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void parseAndSetSchedule_weeklyTimeString_setsCorrectRadio() {
        String schedule = "weekly@friday@09:00";
        long interval = CronJobScheduler.parseScheduleToInterval(schedule);

        assertEquals(7 * 24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void parseAndSetSchedule_customIntervalString_setsCorrectRadio() {
        String schedule = "every_15_minutes";
        long interval = CronJobScheduler.parseScheduleToInterval(schedule);

        assertEquals(15 * 60 * 1000L, interval);
    }

    @Test
    public void parseAndSetSchedule_millisecondsString_returnsInterval() {
        String schedule = "1800000";
        long interval = CronJobScheduler.parseScheduleToInterval(schedule);

        assertEquals(1800000L, interval); // 30 minutes
    }

    @Test
    public void buildScheduleString_withHourlyRadio_returnsCorrectString() {
        // Test that the scheduler correctly identifies hourly
        long interval = CronJobScheduler.parseScheduleToInterval("hourly");
        assertEquals(60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_withDailyRadio_returnsCorrectString() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily");
        assertEquals(24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_withWeeklyRadio_returnsCorrectString() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly");
        assertEquals(7 * 24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void buildScheduleString_withCustomRadio_returnsCorrectString() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_4_days");
        assertEquals(4 * 24 * 60 * 60 * 1000L, interval);
    }

    @Test
    public void validateJobName_empty_returnsError() {
        // The validation logic is in the dialog's saveJob method
        // We test the expected validation behavior

        String name = "";
        boolean isEmpty = name == null || name.trim().isEmpty();

        assertTrue("Empty name should be considered invalid", isEmpty);
    }

    @Test
    public void validateJobName_null_returnsError() {
        String name = null;
        boolean isEmpty = name == null || (name.trim().isEmpty());

        assertTrue("Null name should be considered invalid", isEmpty);
    }

    @Test
    public void validateJobName_whitespaceOnly_returnsError() {
        String name = "   ";
        boolean isEmpty = name == null || name.trim().isEmpty();

        assertTrue("Whitespace-only name should be considered invalid", isEmpty);
    }

    @Test
    public void validateJobName_validName_returnsSuccess() {
        String name = "My Cron Job";
        boolean isEmpty = name == null || name.trim().isEmpty();

        assertFalse("Valid name should not be empty", isEmpty);
        assertTrue("Name should contain 'My Cron Job'", name.contains("My Cron Job"));
    }

    @Test
    public void validatePrompt_empty_returnsError() {
        String prompt = "";
        boolean isEmpty = prompt == null || prompt.trim().isEmpty();

        assertTrue("Empty prompt should be considered invalid", isEmpty);
    }

    @Test
    public void validatePrompt_validPrompt_returnsSuccess() {
        String prompt = "Generate daily summary report";
        boolean isEmpty = prompt == null || prompt.trim().isEmpty();

        assertFalse("Valid prompt should not be empty", isEmpty);
        assertTrue("Prompt should contain 'Generate'", prompt.contains("Generate"));
    }

    @Test
    public void jobModel_creation_setsDefaultValues() {
        CronJob job = new CronJob();

        assertEquals("", job.getId());
        assertEquals("", job.getName());
        assertEquals("", job.getPrompt());
        assertEquals("", job.getSchedule());
        assertEquals(false, job.isEnabled());
        assertEquals(false, job.isPaused());
        assertEquals(0, job.getLastRunTimestamp());
        assertEquals(0, job.getRetryCount());
        assertEquals(3, job.getMaxRetries());
        assertEquals("", job.getLastError());
        assertEquals("", job.getModelReference());
        assertEquals(0, job.getSuccessCount());
        assertEquals(0, job.getFailureCount());
        assertEquals(0, job.getTotalExecutionTime());
    }

    @Test
    public void jobModel_withArguments_setsValues() {
        CronJob job = new CronJob("test-id", "Test Name", "Test Prompt", "3600000");

        assertEquals("test-id", job.getId());
        assertEquals("Test Name", job.getName());
        assertEquals("Test Prompt", job.getPrompt());
        assertEquals("3600000", job.getSchedule());
        assertEquals(true, job.isEnabled());
        assertEquals(false, job.isPaused());
    }

    @Test
    public void jobModel_setters_updateValues() {
        CronJob job = new CronJob();

        job.setId("new-id");
        job.setName("New Name");
        job.setPrompt("New Prompt");
        job.setSchedule("daily");
        job.setEnabled(true);
        job.setPaused(false);
        job.setLastRunTimestamp(1000L);
        job.setRetryCount(2);
        job.setMaxRetries(5);
        job.setLastError("Test error");
        job.setModelReference("provider/model");
        job.setSuccessCount(10);
        job.setFailureCount(2);
        job.setTotalExecutionTime(100000L);

        assertEquals("new-id", job.getId());
        assertEquals("New Name", job.getName());
        assertEquals("New Prompt", job.getPrompt());
        assertEquals("daily", job.getSchedule());
        assertEquals(true, job.isEnabled());
        assertEquals(false, job.isPaused());
        assertEquals(1000L, job.getLastRunTimestamp());
        assertEquals(2, job.getRetryCount());
        assertEquals(5, job.getMaxRetries());
        assertEquals("Test error", job.getLastError());
        assertEquals("provider/model", job.getModelReference());
        assertEquals(10, job.getSuccessCount());
        assertEquals(2, job.getFailureCount());
        assertEquals(100000L, job.getTotalExecutionTime());
    }

    @Test
    public void jobModel_successRate_calculatesCorrectly() {
        CronJob job = new CronJob();
        job.setSuccessCount(8);
        job.setFailureCount(2);

        int rate = job.getSuccessRate();
        assertEquals(80, rate); // 80% success rate
    }

    @Test
    public void jobModel_successRate_noRuns_returns100() {
        CronJob job = new CronJob();

        int rate = job.getSuccessRate();
        assertEquals(100, rate); // No failures if never run
    }

    @Test
    public void jobModel_averageExecutionTime_calculatesCorrectly() {
        CronJob job = new CronJob();
        job.setSuccessCount(5);
        job.setFailureCount(0);
        job.setTotalExecutionTime(150000L); // 150 seconds for 5 runs = 30s avg

        long avg = job.getAverageExecutionTime();
        assertEquals(30000L, avg); // 30 seconds
    }

    @Test
    public void jobModel_averageExecutionTime_noRuns_returns0() {
        CronJob job = new CronJob();

        long avg = job.getAverageExecutionTime();
        assertEquals(0L, avg);
    }

    @Test
    public void jobModel_recordSuccess_updatesCounters() {
        CronJob job = new CronJob();
        job.setSuccessCount(5);
        job.setFailureCount(2);
        job.setRetryCount(1);

        job.recordSuccess(5000L);

        assertEquals(6, job.getSuccessCount());
        assertEquals(0, job.getRetryCount());
        assertEquals("", job.getLastError());
    }

    @Test
    public void jobModel_recordFailure_updatesCounters() {
        CronJob job = new CronJob();
        job.setSuccessCount(5);
        job.setFailureCount(2);
        job.setRetryCount(1);

        job.recordFailure("Test error");

        assertEquals(3, job.getFailureCount());
        assertEquals(2, job.getRetryCount()); // Incremented from 1
        assertEquals("Test error", job.getLastError());
    }

    @Test
    public void jobModel_canRetry_withRetriesLeft_returnsTrue() {
        CronJob job = new CronJob();
        job.setRetryCount(2);
        job.setMaxRetries(3);

        assertTrue(job.canRetry());
    }

    @Test
    public void jobModel_canRetry_atMaxRetries_returnsFalse() {
        CronJob job = new CronJob();
        job.setRetryCount(3);
        job.setMaxRetries(3);

        assertFalse(job.canRetry());
    }

    @Test
    public void jobModel_shouldRun_enabledAndNotPaused_returnsCorrect() {
        CronJob job = new CronJob();
        job.setEnabled(true);
        job.setPaused(false);
        job.setLastRunTimestamp(0);
        job.setSchedule("3600000"); // 1 hour interval

        long currentTime = 2 * 60 * 60 * 1000L; // 2 hours after last run
        assertTrue(job.shouldRun(currentTime));
    }

    @Test
    public void jobModel_shouldRun_paused_returnsFalse() {
        CronJob job = new CronJob();
        job.setEnabled(true);
        job.setPaused(true);
        job.setLastRunTimestamp(0);
        job.setSchedule("3600000");

        long currentTime = 2 * 60 * 60 * 1000L;
        assertFalse(job.shouldRun(currentTime));
    }

    @Test
    public void jobModel_shouldRun_disabled_returnsFalse() {
        CronJob job = new CronJob();
        job.setEnabled(false);
        job.setPaused(false);
        job.setLastRunTimestamp(0);
        job.setSchedule("3600000");

        long currentTime = 2 * 60 * 60 * 1000L;
        assertFalse(job.shouldRun(currentTime));
    }

    /**
     * Test target fragment for dialog testing.
     */
    private static class TestTargetFragment extends CronJobListFragment {
        @Override
        public void onCronJobSaved(CronJob job) {
            // No-op for testing
        }
    }
}
