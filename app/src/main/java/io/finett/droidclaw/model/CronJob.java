package io.finett.droidclaw.model;

public class CronJob {

    private String id;
    private String name;
    private String prompt;
    private String schedule;
    private boolean enabled;
    private boolean paused;
    private long lastRunTimestamp;
    private long lastSuccessTimestamp;
    private long createdAt;
    private int retryCount;
    private int maxRetries;
    private String lastError;
    private String modelReference;
    private int successCount;
    private int failureCount;
    private long totalExecutionTime;

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

    public long getAverageExecutionTime() {
        int totalRuns = successCount + failureCount;
        if (totalRuns == 0 || totalExecutionTime == 0) {
            return 0;
        }
        return totalExecutionTime / totalRuns;
    }

    public int getSuccessRate() {
        int totalRuns = successCount + failureCount;
        if (totalRuns == 0) {
            return 100; // No failures if never run
        }
        return (successCount * 100) / totalRuns;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void resetRetry() {
        this.retryCount = 0;
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void recordSuccess(long duration) {
        this.successCount++;
        this.lastSuccessTimestamp = System.currentTimeMillis();
        this.totalExecutionTime += duration;
        this.resetRetry();
        this.lastError = "";
    }

    public void recordFailure(String error) {
        this.failureCount++;
        this.lastError = error;
        this.incrementRetry();
    }

    public boolean shouldRun(long currentTimeMillis) {
        if (!enabled || paused) {
            return false;
        }

        try {
            long interval = Long.parseLong(schedule);
            return (currentTimeMillis - lastRunTimestamp) >= interval;
        } catch (NumberFormatException e) {
            // Full cron expression parsing is not supported; fall back to 1-hour interval
            return (currentTimeMillis - lastRunTimestamp) >= 60 * 60 * 1000L;
        }
    }
}
