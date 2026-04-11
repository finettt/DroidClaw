package io.finett.droidclaw.model;

/**
 * Records the execution details of a background task.
 * Used for tracking task history, performance metrics, and debugging.
 */
public class TaskExecutionRecord {

    private String taskId;
    private String sessionId;
    private int taskType; // TaskResult.TYPE_HEARTBEAT, TYPE_CRON_JOB, etc.
    private long startTime;
    private long endTime;
    private long durationMillis;
    private int tokensUsed;
    private int iterations;
    private boolean success;
    private String errorMessage;

    public TaskExecutionRecord() {
        this.taskId = "";
        this.sessionId = "";
        this.taskType = 0;
        this.startTime = 0;
        this.endTime = 0;
        this.durationMillis = 0;
        this.tokensUsed = 0;
        this.iterations = 0;
        this.success = false;
        this.errorMessage = null;
    }

    public TaskExecutionRecord(String taskId, String sessionId, int taskType, long startTime) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.taskType = taskType;
        this.startTime = startTime;
        this.endTime = 0;
        this.durationMillis = 0;
        this.tokensUsed = 0;
        this.iterations = 0;
        this.success = false;
        this.errorMessage = null;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getTaskType() {
        return taskType;
    }

    public void setTaskType(int taskType) {
        this.taskType = taskType;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
        if (this.startTime > 0) {
            this.durationMillis = endTime - startTime;
        }
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Mark this execution as completed with the given end time.
     */
    public void complete(long endTime) {
        this.endTime = endTime;
        this.durationMillis = endTime - startTime;
        this.success = true;
        this.errorMessage = null;
    }

    /**
     * Mark this execution as failed with an error message.
     */
    public void fail(long endTime, String error) {
        this.endTime = endTime;
        this.durationMillis = endTime - startTime;
        this.success = false;
        this.errorMessage = error;
    }
}
