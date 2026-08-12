package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for CronJob schedule/retry/statistics logic.
 *
 * Covers the regression areas fixed in the cron-reliability work:
 * next-run gating via shouldRun, retry bookkeeping via
 * recordSuccess/recordFailure/canRetry, and the derived statistics
 * used by the cron UI.
 */
public class CronJobTest {

    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    // ==================== shouldRun: gating ====================

    @Test
    public void shouldRun_disabledJob_returnsFalse() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        job.setEnabled(false);
        job.setLastRunTimestamp(0);

        assertFalse(job.shouldRun(HOUR * 10));
    }

    @Test
    public void shouldRun_pausedJob_returnsFalse() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        job.setEnabled(true);
        job.setPaused(true);
        job.setLastRunTimestamp(0);

        assertFalse(job.shouldRun(HOUR * 10));
    }

    // ==================== shouldRun: numeric interval ====================

    @Test
    public void shouldRun_intervalElapsed_returnsTrue() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        long now = 5_000_000L;
        job.setLastRunTimestamp(now - HOUR);

        assertTrue(job.shouldRun(now));
    }

    @Test
    public void shouldRun_intervalNotElapsed_returnsFalse() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        long now = 5_000_000L;
        job.setLastRunTimestamp(now - (HOUR / 2));

        assertFalse(job.shouldRun(now));
    }

    @Test
    public void shouldRun_exactlyAtInterval_returnsTrue() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        long now = 5_000_000L;
        job.setLastRunTimestamp(now - HOUR);

        // Boundary: elapsed == interval must run (>=, not >).
        assertTrue(job.shouldRun(now));
    }

    @Test
    public void shouldRun_neverRun_returnsTrue() {
        CronJob job = new CronJob("id", "name", "prompt", String.valueOf(HOUR));
        // lastRunTimestamp defaults to 0, so any sensible "now" is past due.
        assertTrue(job.shouldRun(System.currentTimeMillis()));
    }

    // ==================== shouldRun: non-numeric schedule fallback ====================

    @Test
    public void shouldRun_nonNumericSchedule_fallsBackToOneHour() {
        CronJob job = new CronJob("id", "name", "prompt", "0 */2 * * *");
        long now = 10_000_000L;

        // 59 minutes since last run -> not yet due under the 1h fallback.
        job.setLastRunTimestamp(now - TimeUnit.MINUTES.toMillis(59));
        assertFalse(job.shouldRun(now));

        // 61 minutes since last run -> due under the 1h fallback.
        job.setLastRunTimestamp(now - TimeUnit.MINUTES.toMillis(61));
        assertTrue(job.shouldRun(now));
    }

    // ==================== retry bookkeeping ====================

    @Test
    public void recordFailure_incrementsRetryAndStoresError() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");

        job.recordFailure("boom");

        assertEquals(1, job.getRetryCount());
        assertEquals(1, job.getFailureCount());
        assertEquals("boom", job.getLastError());
    }

    @Test
    public void recordSuccess_resetsRetryAndClearsError() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.recordFailure("boom");
        job.recordFailure("boom again");
        assertEquals(2, job.getRetryCount());

        job.recordSuccess(1234L);

        assertEquals(0, job.getRetryCount());
        assertEquals("", job.getLastError());
        assertEquals(1, job.getSuccessCount());
        assertEquals(1234L, job.getTotalExecutionTime());
        assertTrue(job.getLastSuccessTimestamp() > 0);
    }

    @Test
    public void canRetry_belowMax_returnsTrue() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.setMaxRetries(3);
        job.setRetryCount(2);

        assertTrue(job.canRetry());
    }

    @Test
    public void canRetry_atMax_returnsFalse() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.setMaxRetries(3);
        job.setRetryCount(3);

        assertFalse(job.canRetry());
    }

    @Test
    public void incrementAndResetRetry() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.incrementRetry();
        job.incrementRetry();
        assertEquals(2, job.getRetryCount());

        job.resetRetry();
        assertEquals(0, job.getRetryCount());
    }

    // ==================== statistics ====================

    @Test
    public void getSuccessRate_neverRun_returns100() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        assertEquals(100, job.getSuccessRate());
    }

    @Test
    public void getSuccessRate_mixedRuns_computesPercentage() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.setSuccessCount(3);
        job.setFailureCount(1);

        // 3 / 4 = 75%
        assertEquals(75, job.getSuccessRate());
    }

    @Test
    public void getSuccessRate_allFailures_returnsZero() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.setSuccessCount(0);
        job.setFailureCount(5);

        assertEquals(0, job.getSuccessRate());
    }

    @Test
    public void getAverageExecutionTime_noRuns_returnsZero() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        assertEquals(0, job.getAverageExecutionTime());
    }

    @Test
    public void getAverageExecutionTime_mixedRuns_computesAverage() {
        CronJob job = new CronJob("id", "name", "prompt", "hourly");
        job.setSuccessCount(3);
        job.setFailureCount(1);
        job.setTotalExecutionTime(4000L);

        // 4000 / 4 runs = 1000
        assertEquals(1000L, job.getAverageExecutionTime());
    }

    // ==================== constructor defaults ====================

    @Test
    public void parameterizedConstructor_defaults() {
        CronJob job = new CronJob("id-1", "My Job", "do things", "daily");

        assertEquals("id-1", job.getId());
        assertEquals("My Job", job.getName());
        assertEquals("do things", job.getPrompt());
        assertEquals("daily", job.getSchedule());
        assertTrue(job.isEnabled());
        assertFalse(job.isPaused());
        assertEquals(0, job.getRetryCount());
        assertEquals(3, job.getMaxRetries());
        assertTrue(job.getCreatedAt() > 0);
    }

    @Test
    public void defaultConstructor_isDisabled() {
        CronJob job = new CronJob();
        assertFalse(job.isEnabled());
        assertFalse(job.isPaused());
    }
}
