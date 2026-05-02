package io.finett.droidclaw.service;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.tool.ToolResult;

/**
 * Singleton that manages agent-initiated background tool executions.
 *
 * <p>Any tool call tagged with {@code "background": true} by the agent is submitted
 * here instead of being executed synchronously in the agent loop. The agent receives
 * a {@code process_id} immediately and can query or cancel it later via the
 * {@code list_background_processes} and {@code kill_background_process} tools.
 *
 * <p>The underlying {@link ThreadPoolExecutor} keeps the process list bounded and
 * the {@link BackgroundProcessService} foreground service alive while work is in flight.
 */
public class BackgroundProcessManager {

    private static final String TAG = "BackgroundProcessManager";

    /** Maximum concurrent background tool executions. */
    private static final int MAX_CONCURRENT = 8;

    /** Maximum number of completed/failed/killed entries retained in memory. */
    private static final int MAX_HISTORY = 50;

    private static volatile BackgroundProcessManager instance;

    private final Context appContext;
    private final ConcurrentHashMap<String, BackgroundProcess> processes = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    private BackgroundProcessManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = new ThreadPoolExecutor(
                2,
                MAX_CONCURRENT,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
    }

    public static BackgroundProcessManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BackgroundProcessManager.class) {
                if (instance == null) {
                    instance = new BackgroundProcessManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Submit a tool for background execution.
     *
     * @param toolName     Name of the tool being executed (for display)
     * @param argumentsJson JSON string of the arguments (stripped of the "background" key)
     * @param task         The callable that performs the actual tool execution
     * @return The {@code process_id} assigned to this execution
     */
    public String submit(String toolName, String argumentsJson, Callable<ToolResult> task) {
        purgeCompletedIfNeeded();

        String processId = UUID.randomUUID().toString();
        BackgroundProcess process = new BackgroundProcess(processId, toolName, argumentsJson);
        processes.put(processId, process);

        Future<?> future = executor.submit(() -> {
            Log.d(TAG, "Background process started: " + processId + " tool=" + toolName);
            try {
                ToolResult result = task.call();
                process.setFinishedAt(System.currentTimeMillis());
                if (result.isSuccess()) {
                    process.setResult(result.getContent());
                    process.setStatus(BackgroundProcess.STATUS_COMPLETED);
                    Log.d(TAG, "Background process completed: " + processId);
                } else {
                    process.setError(result.getError());
                    process.setStatus(BackgroundProcess.STATUS_FAILED);
                    Log.w(TAG, "Background process failed: " + processId + " — " + result.getError());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                process.setFinishedAt(System.currentTimeMillis());
                process.setStatus(BackgroundProcess.STATUS_KILLED);
                Log.d(TAG, "Background process interrupted (killed): " + processId);
            } catch (Exception e) {
                process.setFinishedAt(System.currentTimeMillis());
                process.setError(e.getMessage());
                process.setStatus(BackgroundProcess.STATUS_FAILED);
                Log.e(TAG, "Background process threw exception: " + processId, e);
            } finally {
                updateServiceNotification();
                stopServiceIfIdle();
            }
        });

        process.setFuture(future);

        // Ensure the foreground service is alive while we have running work
        BackgroundProcessService.ensureRunning(appContext);
        updateServiceNotification();

        Log.d(TAG, "Submitted background process: " + processId + " tool=" + toolName);
        return processId;
    }

    /**
     * Attempt to kill a running background process.
     *
     * @param processId The process to kill
     * @return {@code true} if the process was found and cancellation was requested
     */
    public boolean kill(String processId) {
        BackgroundProcess process = processes.get(processId);
        if (process == null) {
            Log.w(TAG, "Kill requested for unknown process: " + processId);
            return false;
        }

        if (!process.isRunning()) {
            Log.d(TAG, "Kill requested but process already finished: " + processId + " status=" + process.getStatus());
            return false;
        }

        Future<?> future = process.getFuture();
        if (future != null) {
            future.cancel(true);
        }

        process.setFinishedAt(System.currentTimeMillis());
        process.setStatus(BackgroundProcess.STATUS_KILLED);
        Log.d(TAG, "Kill requested for process: " + processId);

        updateServiceNotification();
        stopServiceIfIdle();
        return true;
    }

    /**
     * Kill all currently running background processes.
     * Called from {@link BackgroundProcessService#onDestroy()} when the service is destroyed.
     */
    public void killAll() {
        for (BackgroundProcess process : processes.values()) {
            if (process.isRunning()) {
                Future<?> future = process.getFuture();
                if (future != null) {
                    future.cancel(true);
                }
                process.setFinishedAt(System.currentTimeMillis());
                process.setStatus(BackgroundProcess.STATUS_KILLED);
            }
        }
        Log.d(TAG, "All background processes killed");
    }

    /**
     * Get a single process by ID, or {@code null} if not found.
     */
    public BackgroundProcess get(String processId) {
        return processes.get(processId);
    }

    /**
     * Return all processes sorted by start time descending (newest first).
     */
    public List<BackgroundProcess> listAll() {
        List<BackgroundProcess> list = new ArrayList<>(processes.values());
        Collections.sort(list, (a, b) -> Long.compare(b.getStartedAt(), a.getStartedAt()));
        return list;
    }

    /**
     * Return only processes matching the given status, sorted by start time descending.
     */
    public List<BackgroundProcess> listByStatus(String status) {
        List<BackgroundProcess> list = new ArrayList<>();
        for (BackgroundProcess p : processes.values()) {
            if (status.equals(p.getStatus())) {
                list.add(p);
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.getStartedAt(), a.getStartedAt()));
        return list;
    }

    /** Number of processes currently in the RUNNING state. */
    public int runningCount() {
        int count = 0;
        for (BackgroundProcess p : processes.values()) {
            if (p.isRunning()) count++;
        }
        return count;
    }

    // ==================== Internals ====================

    private void purgeCompletedIfNeeded() {
        List<BackgroundProcess> finished = new ArrayList<>();
        for (BackgroundProcess p : processes.values()) {
            if (!p.isRunning()) {
                finished.add(p);
            }
        }

        if (finished.size() <= MAX_HISTORY) return;

        // Remove oldest finished entries beyond the cap
        Collections.sort(finished, Comparator.comparingLong(BackgroundProcess::getFinishedAt));
        int toRemove = finished.size() - MAX_HISTORY;
        for (int i = 0; i < toRemove; i++) {
            processes.remove(finished.get(i).getId());
        }
        Log.d(TAG, "Purged " + toRemove + " old background process records");
    }

    private void updateServiceNotification() {
        int running = runningCount();
        BackgroundProcessService.updateNotification(appContext, running);
    }

    private void stopServiceIfIdle() {
        if (runningCount() == 0) {
            BackgroundProcessService.stopIfIdle(appContext);
        }
    }
}