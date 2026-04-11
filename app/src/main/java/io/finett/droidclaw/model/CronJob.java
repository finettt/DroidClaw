package io.finett.droidclaw.model;

/**
 * Represents a scheduled cron job that runs automated background tasks.
 * Cron jobs execute prompts on a schedule and store results in hidden sessions.
 */
public class CronJob {

    private String id;
    private String name;
    private String prompt;
    private String schedule; // Cron expression, interval in milliseconds, or simple schedule (daily/weekly)
    private boolean enabled;
    private boolean paused; // User-paused without deleting
    private long lastRunTimestamp;
    private long lastSuccessTimestamp;
    private long createdAt;
    private int retryCount; // Current retry attempts
    private int maxRetries; // Maximum retry attempts (default 3)
    private String lastError; // Last error message
    private String modelReference; // Optional: specific model to use (providerId/modelId)
    private int successCount; // Total successful executions
    private int failureCount; // Total failed executions
    private long totalExecutionTime; // For calculating averages

    public CronJob() {
        this.id = "";
        this.name = "";
        this.prompt = "";
        this.schedule = "";
        this.enabled = false;
        this.paused = false;
        this.lastRunTimestamp = 0;
        this.lastSuccessTimestamp = 0;
        this.createdAt = System.currentTimeMillis();
        this.retryCount = 0;
        this.maxRetries = 3;
        this.lastError = "";
        this.modelReference = "";
        this.successCount = 0;
        this.failureCount = 0;
        this.totalExecutionTime = 0;
    }

    public CronJob(String id, String name, String prompt, String schedule) {
        this.id = id;
        this.name = name;
        this.prompt = prompt;
        this.schedule = schedule;
        this.enabled = true;
        this.paused = false;
        this.lastRunTimestamp = 0;
        this.lastSuccessTimestamp = 0;
        this.createdAt = System.currentTimeMillis();
        this.retryCount = 0;
        this.maxRetries = 3;
        this.lastError = "";
        this.modelReference = "";
        this.successCount = 0;
        this.failureCount = 0;
        this.totalExecutionTime = 0;
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

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public long getLastSuccessTimestamp() {
        return lastSuccessTimestamp;
    }

    public void setLastSuccessTimestamp(long lastSuccessTimestamp) {
        this.lastSuccessTimestamp = lastSuccessTimestamp;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }

    public void setTotalExecutionTime(long totalExecutionTime) {
        this.totalExecutionTime = totalExecutionTime;
    }

    /**
     * Calculate average execution time in milliseconds.
     */
    public long getAverageExecutionTime() {
        int totalRuns = successCount + failureCount;
        if (totalRuns == 0 || totalExecutionTime == 0) {
            return 0;
        }
        return totalExecutionTime / totalRuns;
    }

    /**
     * Calculate success rate as percentage (0-100).
     */
    public int getSuccessRate() {
        int totalRuns = successCount + failureCount;
        if (totalRuns == 0) {
            return 100; // No failures if never run
        }
        return (successCount * 100) / totalRuns;
    }

    /**
     * Increment retry count after failure.
     */
    public void incrementRetry() {
        this.retryCount++;
    }

    /**
     * Reset retry count after success.
     */
    public void resetRetry() {
        this.retryCount = 0;
    }

    /**
     * Check if this job can be retried.
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * Record a successful execution.
     */
    public void recordSuccess(long duration) {
        this.successCount++;
        this.lastSuccessTimestamp = System.currentTimeMillis();
        this.totalExecutionTime += duration;
        this.resetRetry();
        this.lastError = "";
    }

    /**
     * Record a failed execution.
     */
    public void recordFailure(String error) {
        this.failureCount++;
        this.lastError = error;
        this.incrementRetry();
    }

    /**
     * Check if this job should run based on the current time.
     * Supports simple interval in milliseconds or cron-like patterns.
     */
    public boolean shouldRun(long currentTimeMillis) {
        if (!enabled || paused) {
            return false;
        }

        // If schedule is a number, treat it as milliseconds interval
        try {
            long interval = Long.parseLong(schedule);
            return (currentTimeMillis - lastRunTimestamp) >= interval;
        } catch (NumberFormatException e) {
            // For cron expressions or simple schedules, use basic interval
            // Full cron parsing would be more complex; this is a simplified version
            return (currentTimeMillis - lastRunTimestamp) >= 60 * 60 * 1000L; // 1 hour default
        }
    }
}
