package io.finett.droidclaw.model;

/**
 * Configuration for the heartbeat background task.
 * The heartbeat periodically runs a background check to keep the agent active
 * and maintain context freshness.
 */
public class HeartbeatConfig {

    /** Staleness level indicating how overdue the heartbeat is. */
    public enum StalenessLevel {
        /** On time or never run yet (ratio < 1.0 or no previous run). */
        FRESH,
        /** Missed 1-2 intervals (ratio 1.0–3.0). */
        SLIGHTLY_LATE,
        /** Missed 3+ intervals (ratio > 3.0). Heartbeat system is likely broken. */
        DEAD
    }

    private boolean enabled;
    private long intervalMillis;
    private long lastRunTimestamp;

    public HeartbeatConfig() {
        this.enabled = false;
        this.intervalMillis = 30 * 60 * 1000L; // 30 minutes default
        this.lastRunTimestamp = 0;
    }

    public HeartbeatConfig(boolean enabled, long intervalMillis, long lastRunTimestamp) {
        this.enabled = enabled;
        this.intervalMillis = intervalMillis;
        this.lastRunTimestamp = lastRunTimestamp;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public long getLastRunTimestamp() {
        return lastRunTimestamp;
    }

    public void setLastRunTimestamp(long lastRunTimestamp) {
        this.lastRunTimestamp = lastRunTimestamp;
    }

    /**
     * Check if the heartbeat should run based on the current time and last run.
     */
    public boolean shouldRun(long currentTimeMillis) {
        return enabled && (currentTimeMillis - lastRunTimestamp) >= intervalMillis;
    }

    /**
     * Compute how many intervals have elapsed since the last heartbeat run.
     * A ratio of 1.0 means exactly one interval has passed (on schedule).
     * A ratio of 3.0 means three intervals have passed (2 missed).
     * Returns 0 if the heartbeat has never run (lastRunTimestamp == 0).
     */
    public double getStalenessRatio(long currentTimeMillis) {
        if (lastRunTimestamp == 0 || intervalMillis <= 0) {
            return 0;
        }
        long elapsed = currentTimeMillis - lastRunTimestamp;
        return (double) elapsed / intervalMillis;
    }

    /**
     * Get the staleness level based on the current time.
     * - FRESH: ratio < 1.0 or never run
     * - SLIGHTLY_LATE: ratio 1.0–3.0
     * - DEAD: ratio > 3.0
     */
    public StalenessLevel getStalenessLevel(long currentTimeMillis) {
        double ratio = getStalenessRatio(currentTimeMillis);
        if (ratio == 0 || ratio < 1.0) {
            return StalenessLevel.FRESH;
        } else if (ratio <= 3.0) {
            return StalenessLevel.SLIGHTLY_LATE;
        } else {
            return StalenessLevel.DEAD;
        }
    }

    /**
     * Create a default config with heartbeat disabled.
     */
    public static HeartbeatConfig getDefaults() {
        return new HeartbeatConfig();
    }
}
