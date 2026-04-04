package io.finett.droidclaw.model;

import java.util.UUID;

/**
 * Unified result model for both HEARTBEAT and CRON JOB executions.
 * 
 * This model is used for the Zen Result Screen UI, providing a clean
 * view of background task results with "Chat about this" functionality.
 */
public class TaskResult {
    private String id;
    private String taskType;          // "heartbeat" or "cronjob"
    private String taskId;            // Parent task ID (cronJobId or "heartbeat")
    private String taskName;          // Display name
    private String sessionId;         // Hidden session ID (for cron) or main session (for heartbeat)
    
    // Agent-generated notification content
    private String notificationTitle;     // e.g., "⚠️ Server Alert"
    private String notificationSummary;   // e.g., "Found 3 critical errors in logs"
    
    // Full response
    private String response;          // Full markdown response
    private String prompt;            // Original prompt/task
    
    // Execution metadata
    private long executedAt;
    private long durationMs;
    private int iterationCount;
    private int toolCallsCount;
    private int tokensUsed;
    
    // Status
    private String status;            // "success", "failure", "silent"
    private String errorMessage;
    
    // User interaction tracking
    private boolean wasNotified = false;
    private boolean userViewed = false;
    private boolean userChatted = false;       // User clicked "Chat about this"
    private String continuedInSessionId;       // Chat session created from this result
    
    public TaskResult() {
        this.id = UUID.randomUUID().toString();
        this.executedAt = System.currentTimeMillis();
    }
    
    // Factory methods
    
    /**
     * Create result for a CRON JOB execution.
     */
    public static TaskResult fromCronJob(CronJob job, TaskRecord record) {
        TaskResult result = new TaskResult();
        result.taskType = "cronjob";
        result.taskId = job.getId();
        result.taskName = job.getName();
        result.sessionId = record.getSessionId();
        result.prompt = record.getPrompt();
        result.response = record.getResponse();
        result.executedAt = record.getCompletedAt();
        result.durationMs = record.getDurationMs();
        result.iterationCount = record.getIterationCount();
        result.toolCallsCount = record.getToolCallsCount();
        result.tokensUsed = record.getTokensUsed();
        result.status = record.getStatus();
        result.errorMessage = record.getErrorMessage();
        result.notificationTitle = record.getNotificationTitle();
        result.notificationSummary = record.getNotificationSummary();
        result.wasNotified = record.wasNotified();
        result.userViewed = record.isUserViewed();
        return result;
    }
    
    /**
     * Create result for a HEARTBEAT alert.
     */
    public static TaskResult fromHeartbeat(String sessionId, String response, long executedAt) {
        TaskResult result = new TaskResult();
        result.taskType = "heartbeat";
        result.taskId = "heartbeat";
        result.taskName = "Heartbeat Alert";
        result.sessionId = sessionId;
        result.prompt = "Heartbeat check";
        result.response = response;
        result.executedAt = executedAt;
        result.status = "success";
        return result;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTaskType() {
        return taskType;
    }
    
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public long getExecutedAt() {
        return executedAt;
    }
    
    public void setExecutedAt(long executedAt) {
        this.executedAt = executedAt;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
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
    
    public boolean isUserChatted() {
        return userChatted;
    }
    
    public void setUserChatted(boolean userChatted) {
        this.userChatted = userChatted;
    }
    
    public String getContinuedInSessionId() {
        return continuedInSessionId;
    }
    
    public void setContinuedInSessionId(String continuedInSessionId) {
        this.continuedInSessionId = continuedInSessionId;
    }
    
    // Utility methods
    
    /**
     * Check if this is a CRON JOB result.
     */
    public boolean isCronJob() {
        return "cronjob".equals(taskType);
    }
    
    /**
     * Check if this is a HEARTBEAT result.
     */
    public boolean isHeartbeat() {
        return "heartbeat".equals(taskType);
    }
    
    /**
     * Check if result is successful.
     */
    public boolean isSuccess() {
        return "success".equals(status);
    }
    
    /**
     * Check if result was silent (HEARTBEAT_OK).
     */
    public boolean isSilent() {
        return "silent".equals(status);
    }
    
    /**
     * Mark as viewed by user.
     */
    public void markViewed() {
        this.userViewed = true;
    }
    
    /**
     * Mark as chatted about.
     */
    public void markChatted(String sessionId) {
        this.userChatted = true;
        this.continuedInSessionId = sessionId;
    }
    
    /**
     * Get notification title with fallback.
     */
    public String getNotificationTitleOrDefault() {
        if (notificationTitle != null && !notificationTitle.isEmpty()) {
            return notificationTitle;
        }
        return taskName;
    }
    
    /**
     * Get notification summary with fallback.
     */
    public String getNotificationSummaryOrDefault() {
        if (notificationSummary != null && !notificationSummary.isEmpty()) {
            return notificationSummary;
        }
        // Extract first line of response as summary
        if (response != null && !response.isEmpty()) {
            String[] lines = response.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
                }
            }
        }
        return "Task completed";
    }
}