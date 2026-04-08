package io.finett.droidclaw.model;

/**
 * Configuration for the heartbeat background task.
 * The heartbeat periodically runs a background check to keep the agent active
 * and maintain context freshness.
 */
public class HeartbeatConfig {

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
     * Create a default config with heartbeat disabled.
     */
    public static HeartbeatConfig getDefaults() {
        return new HeartbeatConfig();
    }
}
