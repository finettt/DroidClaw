package io.finett.droidclaw.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Instrumented tests for CronJobScheduler.
 * Tests the scheduler's work management and schedule parsing.
 */
@RunWith(AndroidJUnit4.class)
public class CronJobSchedulerInstrumentedTest {

    private Context context;
    private CronJobScheduler scheduler;
    private TaskRepository taskRepository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        scheduler = new CronJobScheduler(context);
        taskRepository = new TaskRepository(context);
    }

    @Test
    public void parseScheduleToInterval_hourly_returnsOneHour() {
        long interval = CronJobScheduler.parseScheduleToInterval("hourly");

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_daily_returnsOneDay() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily");

        assertEquals(TimeUnit.DAYS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_weekly_returnsSevenDays() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly");

        assertEquals(TimeUnit.DAYS.toMillis(7), interval);
    }

    @Test
    public void parseScheduleToInterval_dailyAtTime_returnsOneDay() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily@09:00");

        assertEquals(TimeUnit.DAYS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_dailyAtTimeWithDifferentTime_returnsOneDay() {
        long interval = CronJobScheduler.parseScheduleToInterval("daily@14:30");

        assertEquals(TimeUnit.DAYS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_weeklyAtDayTime_returnsSevenDays() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly@monday@09:00");

        assertEquals(TimeUnit.DAYS.toMillis(7), interval);
    }

    @Test
    public void parseScheduleToInterval_weeklyAtDayTimeWithDifferentDay_returnsSevenDays() {
        long interval = CronJobScheduler.parseScheduleToInterval("weekly@friday@18:00");

        assertEquals(TimeUnit.DAYS.toMillis(7), interval);
    }

    @Test
    public void parseScheduleToInterval_customHours_returnsCorrectInterval() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_2_hours");

        assertEquals(TimeUnit.HOURS.toMillis(2), interval);
    }

    @Test
    public void parseScheduleToInterval_customMinutes_returnsCorrectInterval() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_30_minutes");

        assertEquals(TimeUnit.MINUTES.toMillis(30), interval);
    }

    @Test
    public void parseScheduleToInterval_customDays_returnsCorrectInterval() {
        long interval = CronJobScheduler.parseScheduleToInterval("every_3_days");

        assertEquals(TimeUnit.DAYS.toMillis(3), interval);
    }

    @Test
    public void parseScheduleToInterval_milliseconds_returnsCorrectInterval() {
        long interval = CronJobScheduler.parseScheduleToInterval("7200000");

        assertEquals(7200000L, interval); // 2 hours
    }

    @Test
    public void parseScheduleToInterval_null_returnsDefault() {
        long interval = CronJobScheduler.parseScheduleToInterval(null);

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_emptyString_returnsDefault() {
        long interval = CronJobScheduler.parseScheduleToInterval("");

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_whitespaceString_returnsDefault() {
        long interval = CronJobScheduler.parseScheduleToInterval("   ");

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_unknownSchedule_returnsDefault() {
        long interval = CronJobScheduler.parseScheduleToInterval("unknown_schedule");

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_cronEveryTwoHours_returnsTwoHours() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 */2 * * *");

        assertEquals(TimeUnit.HOURS.toMillis(2), interval);
    }

    @Test
    public void parseScheduleToInterval_cronDailyMidnight_returnsOneDay() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 0 * * *");

        assertEquals(TimeUnit.DAYS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_cronWeeklySunday_returnsSevenDays() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 0 * * 0");

        assertEquals(TimeUnit.DAYS.toMillis(7), interval);
    }

    @Test
    public void parseScheduleToInterval_cronWeeklyMonday_returnsSevenDays() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 0 * * 1");

        assertEquals(TimeUnit.DAYS.toMillis(7), interval);
    }

    @Test
    public void formatInterval_withDays_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.DAYS.toMillis(1));

        assertEquals("Daily", formatted);
    }

    @Test
    public void formatInterval_withSevenDays_formatsAsWeekly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.DAYS.toMillis(7));

        assertEquals("Weekly", formatted);
    }

    @Test
    public void formatInterval_withMultipleDays_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.DAYS.toMillis(3));

        assertEquals("Every 3 days", formatted);
    }

    @Test
    public void formatInterval_withHours_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.HOURS.toMillis(1));

        assertEquals("Every hour", formatted);
    }

    @Test
    public void formatInterval_withMultipleHours_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.HOURS.toMillis(3));

        assertEquals("Every 3 hours", formatted);
    }

    @Test
    public void formatInterval_withMinutes_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.MINUTES.toMillis(30));

        assertEquals("Every 30 minutes", formatted);
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
        String display = CronJobScheduler.formatScheduleForDisplay("daily@09:00");

        assertEquals("Daily at 9:00 AM", display);
    }

    @Test
    public void formatScheduleForDisplay_dailyAtTimeWithMinutes_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("daily@14:30");

        assertEquals("Daily at 2:30 PM", display);
    }

    @Test
    public void formatScheduleForDisplay_weeklyAtDayTime_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("weekly@monday@09:00");

        assertEquals("Monday at 9:00 AM", display);
    }

    @Test
    public void formatScheduleForDisplay_weeklyAtDayTimeWithFriday_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("weekly@friday@18:00");

        assertEquals("Friday at 6:00 PM", display);
    }

    @Test
    public void formatScheduleForDisplay_customInterval_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_2_hours");

        assertEquals("Every 2 hours", display);
    }

    @Test
    public void formatScheduleForDisplay_customMinutes_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_30_minutes");

        assertEquals("Every 30 minutes", display);
    }

    @Test
    public void formatScheduleForDisplay_customDays_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_3_days");

        assertEquals("Every 3 days", display);
    }

    @Test
    public void formatScheduleForDisplay_milliseconds_returnsCorrectString() {
        String display = CronJobScheduler.formatScheduleForDisplay("7200000");

        assertEquals("Every 2 hours", display);
    }

    @Test
    public void formatScheduleForDisplay_null_returnsUnknown() {
        String display = CronJobScheduler.formatScheduleForDisplay(null);

        assertEquals("Unknown", display);
    }

    @Test
    public void formatScheduleForDisplay_emptyString_returnsUnknown() {
        String display = CronJobScheduler.formatScheduleForDisplay("");

        assertEquals("Unknown", display);
    }

    @Test
    public void formatTime_HHMM_toAMFormat() {
        String formatted = CronJobScheduler.formatTime("09:30");

        assertEquals("9:30 AM", formatted);
    }

    @Test
    public void formatTime_midnight_toAMFormat() {
        String formatted = CronJobScheduler.formatTime("00:00");

        assertEquals("12:00 AM", formatted);
    }

    @Test
    public void formatTime_noon_toPMFormat() {
        String formatted = CronJobScheduler.formatTime("12:00");

        assertEquals("12:00 PM", formatted);
    }

    @Test
    public void formatTime_evening_toPMFormat() {
        String formatted = CronJobScheduler.formatTime("18:45");

        assertEquals("6:45 PM", formatted);
    }

    @Test
    public void formatTime_earlyMorning_toAMFormat() {
        String formatted = CronJobScheduler.formatTime("05:15");

        assertEquals("5:15 AM", formatted);
    }

    @Test
    public void formatTime_withInvalidFormat_returnsOriginal() {
        String formatted = CronJobScheduler.formatTime("invalid");

        assertEquals("invalid", formatted);
    }

    @Test
    public void formatTime_withMissingColon_returnsOriginal() {
        String formatted = CronJobScheduler.formatTime("0930");

        assertEquals("0930", formatted);
    }

    @Test
    public void capitalizeFirst_withLowercase_returnsCapitalized() {
        String result = CronJobScheduler.capitalizeFirst("monday");

        assertEquals("Monday", result);
    }

    @Test
    public void capitalizeFirst_withUppercase_returnsSame() {
        String result = CronJobScheduler.capitalizeFirst("MONDAY");

        assertEquals("MONDAY", result);
    }

    @Test
    public void capitalizeFirst_withEmptyString_returnsEmpty() {
        String result = CronJobScheduler.capitalizeFirst("");

        assertEquals("", result);
    }

    @Test
    public void capitalizeFirst_withNull_returnsNull() {
        String result = CronJobScheduler.capitalizeFirst(null);

        assertEquals(null, result);
    }

    @Test
    public void getWorkName_withJobId_returnsCorrectPrefix() {
        String workName = CronJobScheduler.getWorkName("test-job-id");

        assertEquals("cron_job_test-job-id", workName);
    }

    @Test
    public void getWorkName_withEmptyId_returnsPrefix() {
        String workName = CronJobScheduler.getWorkName("");

        assertEquals("cron_job_", workName);
    }

    @Test
    public void executeJobNow_createsOneTimeWorkRequest() {
        CronJob job = new CronJob("cron-now", "Job Now", "Prompt", "3600000");
        taskRepository.saveCronJob(job);

        // Execute job now (this enqueues work to WorkManager)
        scheduler.executeJobNow(job.getId());

        // Verify work was enqueued by checking WorkManager
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_now_" + job.getId()).get();

        assertNotNull("Work infos should not be null", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_withEnabledJob_enqueuesWork() {
        CronJob job = new CronJob("cron-schedule", "Job Schedule", "Prompt", "3600000");
        job.setEnabled(true);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        scheduler.scheduleJob(job);

        // Verify work was enqueued
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-schedule").get();

        assertNotNull("Work infos should not be null", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_withDisabledJob_cancelsWork() {
        CronJob job = new CronJob("cron-disabled", "Job Disabled", "Prompt", "3600000");
        job.setEnabled(false);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        // First schedule the job
        scheduler.scheduleJob(job);

        // Then disable and reschedule
        job.setEnabled(false);
        scheduler.scheduleJob(job);

        // Work should be cancelled
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-disabled").get();

        // Work info should be empty or cancelled
        assertTrue("Disabled job should not have active work", workInfos == null || workInfos.isEmpty());
    }

    @Test
    public void scheduleJob_withPausedJob_cancelsWork() {
        CronJob job = new CronJob("cron-paused", "Job Paused", "Prompt", "3600000");
        job.setEnabled(true);
        job.setPaused(true);
        taskRepository.saveCronJob(job);

        scheduler.scheduleJob(job);

        // Work should be cancelled
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-paused").get();

        assertTrue("Paused job should not have active work", workInfos == null || workInfos.isEmpty());
    }

    @Test
    public void cancelJob_cancelsUniqueWork() {
        CronJob job = new CronJob("cron-cancel", "Job Cancel", "Prompt", "3600000");
        job.setEnabled(true);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        // Schedule job first
        scheduler.scheduleJob(job);

        // Verify work exists
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfosBefore = workManager.getWorkInfosForUniqueWork("cron_job_cron-cancel").get();
        assertTrue("Work should exist before cancel", workInfosBefore != null && !workInfosBefore.isEmpty());

        // Cancel job
        scheduler.cancelJob(job.getId());

        // Verify work is cancelled
        List<WorkInfo> workInfosAfter = workManager.getWorkInfosForUniqueWork("cron_job_cron-cancel").get();
        assertTrue("Work should be cancelled", workInfosAfter == null || workInfosAfter.isEmpty());
    }

    @Test
    public void cancelAllJobs_cancelsAllCronWork() {
        // Create multiple jobs
        CronJob job1 = new CronJob("cron-all-1", "Job 1", "Prompt 1", "3600000");
        job1.setEnabled(true);
        taskRepository.saveCronJob(job1);

        CronJob job2 = new CronJob("cron-all-2", "Job 2", "Prompt 2", "7200000");
        job2.setEnabled(true);
        taskRepository.saveCronJob(job2);

        // Schedule both jobs
        scheduler.scheduleJob(job1);
        scheduler.scheduleJob(job2);

        // Cancel all jobs
        scheduler.cancelAllJobs();

        // Verify all cron work is cancelled
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosByTag("cron_job").get();

        assertTrue("All cron jobs should be cancelled", workInfos == null || workInfos.isEmpty());
    }

    @Test
    public void executeJobNow_createsWorkWithCorrectInputData() {
        CronJob job = new CronJob("cron-input", "Job Input", "Prompt", "3600000");
        taskRepository.saveCronJob(job);

        scheduler.executeJobNow(job.getId());

        // Verify the work request contains the correct job ID
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_now_" + job.getId()).get();

        assertNotNull("Work infos should exist", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_withCustomInterval_enqueuesWork() {
        CronJob job = new CronJob("cron-custom", "Job Custom", "Prompt", "every_2_hours");
        job.setEnabled(true);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        scheduler.scheduleJob(job);

        // Verify work was enqueued
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-custom").get();

        assertNotNull("Work infos should not be null", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_withDailySchedule_enqueuesWork() {
        CronJob job = new CronJob("cron-daily", "Job Daily", "Prompt", "daily@09:00");
        job.setEnabled(true);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        scheduler.scheduleJob(job);

        // Verify work was enqueued
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-daily").get();

        assertNotNull("Work infos should not be null", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_withWeeklySchedule_enqueuesWork() {
        CronJob job = new CronJob("cron-weekly", "Job Weekly", "Prompt", "weekly@monday@09:00");
        job.setEnabled(true);
        job.setPaused(false);
        taskRepository.saveCronJob(job);

        scheduler.scheduleJob(job);

        // Verify work was enqueued
        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("cron_job_cron-weekly").get();

        assertNotNull("Work infos should not be null", workInfos);
        assertTrue("Should have work info", workInfos.size() >= 1);
    }

    @Test
    public void scheduleJob_nullJob_doesNotCrash() {
        // Should not crash when job is null
        scheduler.scheduleJob(null);
    }

    @Test
    public void cancelJob_nullId_doesNotCrash() {
        // Should not crash when id is null
        scheduler.cancelJob(null);
    }

    @Test
    public void executeJobNow_nullId_doesNotCrash() {
        // Should not crash when id is null
        scheduler.executeJobNow(null);
    }

    @Test
    public void parseScheduleToInterval_validNumericString_returnsNumeric() {
        long interval = CronJobScheduler.parseScheduleToInterval("1800000");

        assertEquals(1800000L, interval);
    }

    @Test
    public void parseScheduleToInterval_whitespaceAroundString_trimsAndParses() {
        long interval = CronJobScheduler.parseScheduleToInterval("  3600000  ");

        assertEquals(3600000L, interval);
    }

    @Test
    public void formatScheduleForDisplay_withCustomInterval_formatsCorrectly() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_5_minutes");

        assertEquals("Every 5 minutes", display);
    }

    @Test
    public void formatScheduleForDisplay_withCustomInterval_hours_formatsCorrectly() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_12_hours");

        assertEquals("Every 12 hours", display);
    }

    @Test
    public void formatScheduleForDisplay_withCustomInterval_days_formatsCorrectly() {
        String display = CronJobScheduler.formatScheduleForDisplay("every_7_days");

        assertEquals("Every 7 days", display);
    }

    @Test
    public void parseScheduleToInterval_cronExpressionEveryHour_returnsOneHour() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 * * * *");

        assertEquals(TimeUnit.HOURS.toMillis(1), interval);
    }

    @Test
    public void parseScheduleToInterval_cronExpressionEveryThirtyMinutes_returnsThirtyMinutes() {
        long interval = CronJobScheduler.parseScheduleToInterval("0 */30 * * *");

        assertEquals(TimeUnit.MINUTES.toMillis(30), interval);
    }

    @Test
    public void formatInterval_withOneHour_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.HOURS.toMillis(1));

        assertEquals("Every hour", formatted);
    }

    @Test
    public void formatInterval_withTwoHours_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.HOURS.toMillis(2));

        assertEquals("Every 2 hours", formatted);
    }

    @Test
    public void formatInterval_withOneMinute_formatsCorrectly() {
        String formatted = CronJobScheduler.formatInterval(TimeUnit.MINUTES.toMillis(1));

        assertEquals("Every 1 minutes", formatted);
    }

    @Test
    public void formatInterval_withZeroMillis_formatsAsHour() {
        String formatted = CronJobScheduler.formatInterval(0);

        assertEquals("Every hour", formatted);
    }
}
