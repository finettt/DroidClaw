package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a scheduled background task (CRON JOB).
 * 
 * Cron jobs are agent-created scheduled tasks that run in isolated
 * hidden sessions, separate from the main chat.
 */
public class CronJob {
    private String id;
    private String name;              // Display name: "Server Log Checker"
    private String prompt;            // What the agent should do
    private long intervalMinutes;     // Execution interval
    private boolean enabled;          // Active/paused state
    
    // Execution constraints
    private boolean requireNetwork = true;
    private boolean requireCharging = false;
    private boolean batteryNotLow = true;
    
    // Agent configuration overrides
    private String modelOverride;     // Optional model override for this job
    private int maxIterations = 10;   // Lower than interactive (20) for safety
    
    // Memory settings
    private boolean loadMemories = true;
    private List<String> memoryTags;  // Specific memory tags to load
    
    // Metadata
    private long createdAt;
    private long updatedAt;
    private long lastRunAt;
    private long nextRunAt;           // Calculated next execution time
    
    // Statistics
    private int runCount = 0;
    private int successCount = 0;
    private int failureCount = 0;
    
    // Notification settings
    private boolean notifyOnSuccess = true;
    private boolean notifyOnFailure = true;
    
    // Creator info (which chat session created this)
    private String createdInSessionId;
    
    public CronJob() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.enabled = true;
        this.memoryTags = new ArrayList<>();
    }
    
    public CronJob(String name, String prompt, long intervalMinutes) {
        this();
        this.name = name;
        this.prompt = prompt;
        this.intervalMinutes = intervalMinutes;
    }
    
    // Factory method for quick creation
    public static CronJob create(String name, String prompt, long intervalMinutes) {
        return new CronJob(name, prompt, intervalMinutes);
    }
    
    // Getters and setters
    
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
    
    public long getIntervalMinutes() {
        return intervalMinutes;
    }
    
    public void setIntervalMinutes(long intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isRequireNetwork() {
        return requireNetwork;
    }
    
    public void setRequireNetwork(boolean requireNetwork) {
        this.requireNetwork = requireNetwork;
    }
    
    public boolean isRequireCharging() {
        return requireCharging;
    }
    
    public void setRequireCharging(boolean requireCharging) {
        this.requireCharging = requireCharging;
    }
    
    public boolean isBatteryNotLow() {
        return batteryNotLow;
    }
    
    public void setBatteryNotLow(boolean batteryNotLow) {
        this.batteryNotLow = batteryNotLow;
    }
    
    public String getModelOverride() {
        return modelOverride;
    }
    
    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride;
    }
    
    public int getMaxIterations() {
        return maxIterations;
    }
    
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
    
    public boolean isLoadMemories() {
        return loadMemories;
    }
    
    public void setLoadMemories(boolean loadMemories) {
        this.loadMemories = loadMemories;
    }
    
    public List<String> getMemoryTags() {
        return memoryTags;
    }
    
    public void setMemoryTags(List<String> memoryTags) {
        this.memoryTags = memoryTags;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public long getLastRunAt() {
        return lastRunAt;
    }
    
    public void setLastRunAt(long lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
    
    public long getNextRunAt() {
        return nextRunAt;
    }
    
    public void setNextRunAt(long nextRunAt) {
        this.nextRunAt = nextRunAt;
    }
    
    public int getRunCount() {
        return runCount;
    }
    
    public void setRunCount(int runCount) {
        this.runCount = runCount;
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
    
    public boolean isNotifyOnSuccess() {
        return notifyOnSuccess;
    }
    
    public void setNotifyOnSuccess(boolean notifyOnSuccess) {
        this.notifyOnSuccess = notifyOnSuccess;
    }
    
    public boolean isNotifyOnFailure() {
        return notifyOnFailure;
    }
    
    public void setNotifyOnFailure(boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }
    
    public String getCreatedInSessionId() {
        return createdInSessionId;
    }
    
    public void setCreatedInSessionId(String createdInSessionId) {
        this.createdInSessionId = createdInSessionId;
    }
    
    // Utility methods
    
    /**
     * Record a successful execution.
     */
    public void recordSuccess() {
        this.lastRunAt = System.currentTimeMillis();
        this.runCount++;
        this.successCount++;
        calculateNextRun();
    }
    
    /**
     * Record a failed execution.
     */
    public void recordFailure() {
        this.lastRunAt = System.currentTimeMillis();
        this.runCount++;
        this.failureCount++;
        calculateNextRun();
    }
    
    /**
     * Calculate next run time based on interval.
     */
    public void calculateNextRun() {
        if (enabled && lastRunAt > 0) {
            this.nextRunAt = lastRunAt + (intervalMinutes * 60 * 1000);
        }
    }
    
    /**
     * Get success rate as percentage.
     */
    public int getSuccessRate() {
        if (runCount == 0) return 0;
        return (int) ((successCount * 100.0) / runCount);
    }
    
    /**
     * Get human-readable interval string.
     */
    public String getIntervalDisplayString() {
        if (intervalMinutes < 60) {
            return intervalMinutes + " minutes";
        } else if (intervalMinutes == 60) {
            return "1 hour";
        } else if (intervalMinutes < 1440) {
            long hours = intervalMinutes / 60;
            return hours + " hours";
        } else if (intervalMinutes == 1440) {
            return "1 day";
        } else {
            long days = intervalMinutes / 1440;
            return days + " days";
        }
    }
    
    /**
     * Toggle enabled state.
     */
    public void toggle() {
        this.enabled = !this.enabled;
        this.updatedAt = System.currentTimeMillis();
        if (enabled) {
            calculateNextRun();
        }
    }
}