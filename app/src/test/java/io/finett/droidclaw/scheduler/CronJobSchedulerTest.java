package io.finett.droidclaw.scheduler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Parametric tests for CronJobScheduler.parseScheduleToInterval and parseCronExpression.
 */
public class CronJobSchedulerTest {

    // --- parseScheduleToInterval: simple intervals ---

    @Test
    public void parseScheduleToInterval_null() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval(null));
    }

    @Test
    public void parseScheduleToInterval_empty() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval(""));
    }

    @Test
    public void parseScheduleToInterval_numeric() {
        assertEquals(3600000L,
                CronJobScheduler.parseScheduleToInterval("3600000"));
    }

    @Test
    public void parseScheduleToInterval_hourly() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("hourly"));
    }

    @Test
    public void parseScheduleToInterval_daily() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("daily"));
    }

    @Test
    public void parseScheduleToInterval_weekly() {
        assertEquals(TimeUnit.DAYS.toMillis(7),
                CronJobScheduler.parseScheduleToInterval("weekly"));
    }

    @Test
    public void parseScheduleToInterval_dailyAt() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("daily@08:00"));
    }

    @Test
    public void parseScheduleToInterval_weeklyAt() {
        assertEquals(TimeUnit.DAYS.toMillis(7),
                CronJobScheduler.parseScheduleToInterval("weekly@08:00"));
    }

    @Test
    public void parseScheduleToInterval_every_30_minute() {
        assertEquals(30 * 60 * 1000L,
                CronJobScheduler.parseScheduleToInterval("every_30_minute"));
    }

    @Test
    public void parseScheduleToInterval_every_5_minutes() {
        assertEquals(5 * 60 * 1000L,
                CronJobScheduler.parseScheduleToInterval("every_5_minutes"));
    }

    @Test
    public void parseScheduleToInterval_every_2_hours() {
        assertEquals(2 * TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_2_hours"));
    }

    @Test
    public void parseScheduleToInterval_every_1_day() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_1_day"));
    }

    // --- parseScheduleToInterval: cron expressions (the bug area) ---

    @Test
    public void parseCronExpression_single_digit_hours() {
        // "0 */2 * * *" -> 2 hours
        assertEquals(TimeUnit.HOURS.toMillis(2),
                CronJobScheduler.parseScheduleToInterval("0 */2 * * *"));
    }

    @Test
    public void parseCronExpression_N1() {
        // "0 */1 * * *" -> 1 hour
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("0 */1 * * *"));
    }

    @Test
    public void parseCronExpression_N2() {
        // "0 */2 * * *" -> 2 hours
        assertEquals(TimeUnit.HOURS.toMillis(2),
                CronJobScheduler.parseScheduleToInterval("0 */2 * * *"));
    }

    @Test
    public void parseCronExpression_N6() {
        // "0 */6 * * *" -> 6 hours
        assertEquals(TimeUnit.HOURS.toMillis(6),
                CronJobScheduler.parseScheduleToInterval("0 */6 * * *"));
    }

    @Test
    public void parseCronExpression_N12() {
        // "0 */12 * * *" -> 12 hours
        assertEquals(TimeUnit.HOURS.toMillis(12),
                CronJobScheduler.parseScheduleToInterval("0 */12 * * *"));
    }

    @Test
    public void parseCronExpression_N24() {
        // "0 */24 * * *" -> 24 hours
        assertEquals(TimeUnit.HOURS.toMillis(24),
                CronJobScheduler.parseScheduleToInterval("0 */24 * * *"));
    }

    @Test
    public void parseCronExpression_N30() {
        // "0 */30 * * *" -> 30 hours (unusual but valid)
        assertEquals(TimeUnit.HOURS.toMillis(30),
                CronJobScheduler.parseScheduleToInterval("0 */30 * * *"));
    }

    // --- Daily / weekly cron expressions ---

    @Test
    public void parseCronExpression_daily_midnight() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("0 0 * * *"));
    }

    @Test
    public void parseCronExpression_weekly_sunday() {
        assertEquals(TimeUnit.DAYS.toMillis(7),
                CronJobScheduler.parseScheduleToInterval("0 0 * * 0"));
    }

    @Test
    public void parseCronExpression_weekly_monday() {
        assertEquals(TimeUnit.DAYS.toMillis(7),
                CronJobScheduler.parseScheduleToInterval("0 0 * * 1"));
    }

    @Test
    public void parseCronExpression_unsupported() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("0 17 * * *"));
    }

    // --- formatInterval and formatScheduleForDisplay ---

    @Test
    public void formatInterval_minutes() {
        assertEquals("Every 15 minutes",
                CronJobScheduler.formatInterval(15 * 60 * 1000L));
    }

    @Test
    public void formatInterval_hours() {
        assertEquals("Every 2 hours",
                CronJobScheduler.formatInterval(2 * TimeUnit.HOURS.toMillis(1)));
    }

    @Test
    public void formatInterval_days() {
        assertEquals("Weekly",
                CronJobScheduler.formatInterval(7 * TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void formatInterval_weekly() {
        assertEquals("Weekly",
                CronJobScheduler.formatInterval(7 * TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void formatInterval_two_days() {
        assertEquals("Every 2 days",
                CronJobScheduler.formatInterval(2 * TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void formatScheduleForDisplay_hourly() {
        assertEquals("Every hour",
                CronJobScheduler.formatScheduleForDisplay("hourly"));
    }

    @Test
    public void formatScheduleForDisplay_cron_expression() {
        assertEquals("Custom schedule",
                CronJobScheduler.formatScheduleForDisplay("0 */6 * * *"));
    }

    @Test
    public void formatScheduleForDisplay_numeric() {
        assertEquals("Every 2 hours",
                CronJobScheduler.formatScheduleForDisplay("7200000"));
    }

    // --- capitalizeFirst ---

    @Test
    public void capitalizeFirst_empty() {
        assertEquals("", CronJobScheduler.capitalizeFirst(""));
    }

    @Test
    public void capitalizeFirst_single_char() {
        assertEquals("A", CronJobScheduler.capitalizeFirst("a"));
    }

    @Test
    public void capitalizeFirst_multi_char() {
        assertEquals("Monday", CronJobScheduler.capitalizeFirst("monday"));
    }

    // --- formatTime ---

    @Test
    public void formatTime_am() {
        assertEquals("8:00 AM", CronJobScheduler.formatTime("08:00"));
    }

    @Test
    public void formatTime_pm() {
        assertEquals("5:30 PM", CronJobScheduler.formatTime("17:30"));
    }

    @Test
    public void formatTime_midnight() {
        assertEquals("12:00 AM", CronJobScheduler.formatTime("00:00"));
    }

    @Test
    public void formatTime_noon() {
        assertEquals("12:00 PM", CronJobScheduler.formatTime("12:00"));
    }

    // --- computeRetryBackoffMinutes ---

    @Test
    public void computeRetryBackoffMinutes_zero() {
        assertEquals(1L, CronJobScheduler.computeRetryBackoffMinutes(0));
    }

    @Test
    public void computeRetryBackoffMinutes_one() {
        assertEquals(2L, CronJobScheduler.computeRetryBackoffMinutes(1));
    }

    @Test
    public void computeRetryBackoffMinutes_two() {
        assertEquals(4L, CronJobScheduler.computeRetryBackoffMinutes(2));
    }

    @Test
    public void computeRetryBackoffMinutes_three() {
        assertEquals(8L, CronJobScheduler.computeRetryBackoffMinutes(3));
    }

    @Test
    public void computeRetryBackoffMinutes_capped() {
        assertEquals(TimeUnit.DAYS.toMinutes(1), CronJobScheduler.computeRetryBackoffMinutes(100));
    }

    // --- computeRetryBackoffMinutes: edge cases ---

    @Test
    public void computeRetryBackoffMinutes_negative_clampsToOne() {
        assertEquals(1L, CronJobScheduler.computeRetryBackoffMinutes(-5));
    }

    @Test
    public void computeRetryBackoffMinutes_ten() {
        assertEquals(1024L, CronJobScheduler.computeRetryBackoffMinutes(10));
    }

    @Test
    public void computeRetryBackoffMinutes_eleven_cappedAtOneDay() {
        // 2^11 = 2048 minutes exceeds the 1-day cap (1440 minutes).
        assertEquals(TimeUnit.DAYS.toMinutes(1), CronJobScheduler.computeRetryBackoffMinutes(11));
    }

    // --- parseScheduleToInterval: malformed and edge inputs ---

    @Test
    public void parseScheduleToInterval_whitespaceOnly() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("   "));
    }

    @Test
    public void parseScheduleToInterval_surroundingWhitespace_trimmed() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("  daily  "));
    }

    @Test
    public void parseScheduleToInterval_every_invalid_number() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_abc_minutes"));
    }

    @Test
    public void parseScheduleToInterval_every_unknown_unit() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_5_furlongs"));
    }

    @Test
    public void parseScheduleToInterval_every_missing_value() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_5"));
    }

    @Test
    public void parseScheduleToInterval_every_singular_minute() {
        assertEquals(TimeUnit.MINUTES.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_1_minute"));
    }

    @Test
    public void parseScheduleToInterval_every_singular_hour() {
        assertEquals(TimeUnit.HOURS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("every_1_hour"));
    }

    @Test
    public void parseScheduleToInterval_caseInsensitive() {
        assertEquals(TimeUnit.DAYS.toMillis(1),
                CronJobScheduler.parseScheduleToInterval("DAILY"));
    }

    // --- formatScheduleForDisplay: more variants ---

    @Test
    public void formatScheduleForDisplay_null() {
        assertEquals("Unknown", CronJobScheduler.formatScheduleForDisplay(null));
    }

    @Test
    public void formatScheduleForDisplay_blank() {
        assertEquals("Unknown", CronJobScheduler.formatScheduleForDisplay("   "));
    }

    @Test
    public void formatScheduleForDisplay_daily() {
        assertEquals("Daily", CronJobScheduler.formatScheduleForDisplay("daily"));
    }

    @Test
    public void formatScheduleForDisplay_weekly() {
        assertEquals("Weekly", CronJobScheduler.formatScheduleForDisplay("weekly"));
    }

    @Test
    public void formatScheduleForDisplay_dailyAt() {
        assertEquals("Daily at 8:00 AM",
                CronJobScheduler.formatScheduleForDisplay("daily@08:00"));
    }

    @Test
    public void formatScheduleForDisplay_weeklyAt_full() {
        assertEquals("Monday at 8:00 AM",
                CronJobScheduler.formatScheduleForDisplay("weekly@monday@08:00"));
    }

    @Test
    public void formatScheduleForDisplay_weeklyAt_malformed() {
        assertEquals("Weekly",
                CronJobScheduler.formatScheduleForDisplay("weekly@onlyonepart"));
    }

    // --- formatTime: malformed input ---

    @Test
    public void formatTime_invalid_returnsInput() {
        assertEquals("garbage", CronJobScheduler.formatTime("garbage"));
    }

    // --- getWorkName ---

    @Test
    public void getWorkName_hasCronPrefix() {
        assertEquals("cron_job_abc123", CronJobScheduler.getWorkName("abc123"));
    }
}
