package io.finett.droidclaw.model;

/**
 * Represents a scheduled cron job that runs automated background tasks.
 * Cron jobs execute prompts on a schedule and store results in hidden sessions.
 */
public class CronJob {

    private String id;
    private String name;
    private String prompt;
    private String schedule; // Cron expression or interval in milliseconds
    private boolean enabled;
    private long lastRunTimestamp;
    private String modelReference; // Optional: specific model to use (providerId/modelId)

    public CronJob() {
        this.id = "";
        this.name = "";
        this.prompt = "";
        this.schedule = "";
        this.enabled = false;
        this.lastRunTimestamp = 0;
        this.modelReference = "";
    }

    public CronJob(String id, String name, String prompt, String schedule) {
        this.id = id;
        this.name = name;
        this.prompt = prompt;
        this.schedule = schedule;
        this.enabled = true;
        this.lastRunTimestamp = 0;
        this.modelReference = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastRunTimestamp() {
        return lastRunTimestamp;
    }

    public void setLastRunTimestamp(long lastRunTimestamp) {
        this.lastRunTimestamp = lastRunTimestamp;
    }

    public String getModelReference() {
        return modelReference;
    }

    public void setModelReference(String modelReference) {
        this.modelReference = modelReference;
    }

    /**
     * Check if this job should run based on the current time.
     * Supports simple interval in milliseconds or cron-like patterns.
     */
    public boolean shouldRun(long currentTimeMillis) {
        if (!enabled) {
            return false;
        }

        // If schedule is a number, treat it as milliseconds interval
        try {
            long interval = Long.parseLong(schedule);
            return (currentTimeMillis - lastRunTimestamp) >= interval;
        } catch (NumberFormatException e) {
            // For cron expressions, simple check: run if never run or past interval
            // Full cron parsing would be more complex; this is a simplified version
            return (currentTimeMillis - lastRunTimestamp) >= 60 * 60 * 1000L; // 1 hour default
        }
    }
}
