package io.finett.droidclaw.service;

import java.util.concurrent.Future;

/**
 * Represents a single tool execution dispatched asynchronously by the agent.
 *
 * Lifecycle: running → completed | failed | killed
 */
public class BackgroundProcess {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_KILLED = "killed";

    private final String id;
    private final String toolName;
    private final String argumentsJson;
    private final long startedAt;

    private volatile String status;
    private volatile long finishedAt;
    private volatile String result;
    private volatile String error;

    // Not serialized — used internally for cancellation only
    private Future<?> future;

    public BackgroundProcess(String id, String toolName, String argumentsJson) {
        this.id = id;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.startedAt = System.currentTimeMillis();
        this.status = STATUS_RUNNING;
        this.finishedAt = 0;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Future<?> getFuture() {
        return future;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public boolean isRunning() {
        return STATUS_RUNNING.equals(status);
    }

    /** Duration in milliseconds. Returns 0 while still running. */
    public long getDurationMs() {
        if (finishedAt == 0) return 0;
        return finishedAt - startedAt;
    }
}