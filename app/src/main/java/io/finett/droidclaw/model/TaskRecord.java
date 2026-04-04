package io.finett.droidclaw.model;

import java.util.UUID;

/**
 * Record of a single CRON JOB execution.
 * 
 * Each execution creates a new TaskRecord with complete execution history
 * stored in an isolated hidden ChatSession.
 */
public class TaskRecord {
    private String id;
    private String cronJobId;         // Parent cron job
    private String cronJobName;       // Snapshot of name at execution time
    private String sessionId;         // Hidden session ID with full conversation
    
    // Execution metadata
    private long startedAt;
    private long completedAt;
    private long durationMs;
    
    // Task details
    private String prompt;            // Task prompt that was executed
    private String response;          // Final agent response
    
    // Execution statistics
    private int iterationCount = 0;
    private int toolCallsCount = 0;
    private int tokensUsed = 0;
    
    // Status tracking
    private String status;            // "success", "failure", "timeout", "cancelled"
    private String errorMessage;      // If failed
    
    // Notification tracking
    private String notificationTitle;    // Agent-generated title
    private String notificationSummary;  // Agent-generated summary
    private boolean wasNotified = false;
    private boolean userViewed = false;
    
    // Result handling (for Zen UI)
    private String resultId;          // Links to TaskResult if user viewed
    
    public TaskRecord() {
        this.id = UUID.randomUUID().toString();
        this.startedAt = System.currentTimeMillis();
    }
    
    public TaskRecord(String cronJobId, String cronJobName, String prompt) {
        this();
        this.cronJobId = cronJobId;
        this.cronJobName = cronJobName;
        this.prompt = prompt;
    }
    
    // Factory method
    public static TaskRecord create(String cronJobId, String cronJobName, String prompt) {
        return new TaskRecord(cronJobId, cronJobName, prompt);
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCronJobId() {
        return cronJobId;
    }
    
    public void setCronJobId(String cronJobId) {
        this.cronJobId = cronJobId;
    }
    
    public String getCronJobName() {
        return cronJobName;
    }
    
    public void setCronJobName(String cronJobName) {
        this.cronJobName = cronJobName;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
    
    public long getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
        this.durationMs = completedAt - startedAt;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public int getIterationCount() {
        return iterationCount;
    }
    
    public void setIterationCount(int iterationCount) {
        this.iterationCount = iterationCount;
    }
    
    public int getToolCallsCount() {
        return toolCallsCount;
    }
    
    public void setToolCallsCount(int toolCallsCount) {
        this.toolCallsCount = toolCallsCount;
    }
    
    public int getTokensUsed() {
        return tokensUsed;
    }
    
    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getNotificationTitle() {
        return notificationTitle;
    }
    
    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }
    
    public String getNotificationSummary() {
        return notificationSummary;
    }
    
    public void setNotificationSummary(String notificationSummary) {
        this.notificationSummary = notificationSummary;
    }
    
    public boolean wasNotified() {
        return wasNotified;
    }
    
    public void setWasNotified(boolean wasNotified) {
        this.wasNotified = wasNotified;
    }
    
    public boolean isUserViewed() {
        return userViewed;
    }
    
    public void setUserViewed(boolean userViewed) {
        this.userViewed = userViewed;
    }
    
    public String getResultId() {
        return resultId;
    }
    
    public void setResultId(String resultId) {
        this.resultId = resultId;
    }
    
    // Utility methods
    
    /**
     * Check if execution was successful.
     */
    public boolean isSuccess() {
        return "success".equals(status);
    }
    
    /**
     * Check if execution failed.
     */
    public boolean isFailed() {
        return "failure".equals(status) || "timeout".equals(status);
    }
    
    /**
     * Mark execution as successful.
     */
    public void markSuccess(String response) {
        this.status = "success";
        this.response = response;
        this.completedAt = System.currentTimeMillis();
        this.durationMs = completedAt - startedAt;
    }
    
    /**
     * Mark execution as failed.
     */
    public void markFailure(String errorMessage) {
        this.status = "failure";
        this.errorMessage = errorMessage;
        this.completedAt = System.currentTimeMillis();
        this.durationMs = completedAt - startedAt;
    }
    
    /**
     * Mark execution as timed out.
     */
    public void markTimeout() {
        this.status = "timeout";
        this.errorMessage = "Execution exceeded time limit";
        this.completedAt = System.currentTimeMillis();
        this.durationMs = completedAt - startedAt;
    }
    
    /**
     * Get human-readable duration string.
     */
    public String getDurationDisplayString() {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    /**
     * Get status badge color.
     */
    public String getStatusBadgeColor() {
        switch (status) {
            case "success":
                return "#4CAF50";  // Green
            case "failure":
            case "timeout":
                return "#F44336";  // Red
            case "cancelled":
                return "#9E9E9E";  // Gray
            default:
                return "#2196F3";  // Blue (in progress)
        }
    }
    
    /**
     * Get status display text.
     */
    public String getStatusDisplayText() {
        switch (status) {
            case "success":
                return "Success";
            case "failure":
                return "Failed";
            case "timeout":
                return "Timed Out";
            case "cancelled":
                return "Cancelled";
            default:
                return "Running";
        }
    }
}