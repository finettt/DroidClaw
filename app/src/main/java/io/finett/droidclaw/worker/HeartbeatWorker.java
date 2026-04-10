package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.util.NotificationManager;

/**
 * Worker that executes heartbeat checks in the background.
 * Reads HEARTBEAT.md from workspace, executes it in an isolated session,
 * and checks for HEARTBEAT_OK marker in the result.
 */
public class HeartbeatWorker extends BaseTaskWorker {

    private static final String TAG = "HeartbeatWorker";
    private static final String HEARTBEAT_OK_MARKER = "HEARTBEAT_OK";

    private final HeartbeatConfigRepository heartbeatConfigRepo;
    private final NotificationManager notificationManager;

    public HeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.heartbeatConfigRepo = new HeartbeatConfigRepository(appContext);
        this.notificationManager = new NotificationManager(appContext);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting heartbeat worker");

        try {
            // Check if heartbeat is enabled
            HeartbeatConfig config = heartbeatConfigRepo.getConfig();
            if (!config.isEnabled()) {
                Log.d(TAG, "Heartbeat is disabled, skipping");
                return Result.success();
            }

            // Check if we should run based on interval
            long currentTime = System.currentTimeMillis();
            if (!config.shouldRun(currentTime)) {
                Log.d(TAG, "Heartbeat interval not elapsed, skipping");
                return Result.success();
            }

            // Read HEARTBEAT.md file
            String heartbeatPrompt = readHeartbeatFile();
            if (heartbeatPrompt == null || heartbeatPrompt.trim().isEmpty()) {
                Log.w(TAG, "HEARTBEAT.md is empty or missing, using default heartbeat prompt");
                heartbeatPrompt = getDefaultHeartbeatPrompt();
            }

            // Create isolated session
            ChatSession session = createIsolatedSession(SessionType.HIDDEN_HEARTBEAT);

            // Execute heartbeat prompt in sandbox
            TaskResult result = executeWithSandbox(session, heartbeatPrompt);

            // Save task result
            taskRepository.saveTaskResult(result);

            // Check for HEARTBEAT_OK marker with enhanced detection
            boolean isHealthy = checkHeartbeatOk(result.getContent());
            result.putMetadata("healthy", String.valueOf(isHealthy));
            taskRepository.saveTaskResult(result);

            // Update last run timestamp
            heartbeatConfigRepo.updateLastRun(currentTime);

            // Handle notification based on heartbeat status
            if (isHealthy) {
                Log.d(TAG, "Heartbeat completed successfully - system healthy");
                // Silent - no notification needed when system is healthy
            } else {
                Log.w(TAG, "Heartbeat completed - potential issues detected, showing notification");
                notificationManager.sendTaskNotification(result);
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Heartbeat worker failed", e);
            // Show error notification
            notificationManager.showErrorNotification("Heartbeat Failed", "Heartbeat failed: " + e.getMessage());
            return Result.failure();
        }
    }

    /**
     * Read the HEARTBEAT.md file from workspace.
     *
     * @return Content of HEARTBEAT.md or null if file doesn't exist
     */
    private String readHeartbeatFile() {
        try {
            File workspaceRoot = workspaceManager.getWorkspaceRoot();
            File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

            if (!heartbeatFile.exists()) {
                Log.d(TAG, "HEARTBEAT.md not found in workspace");
                return null;
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            Log.d(TAG, "Read HEARTBEAT.md: " + content.length() + " chars");
            return content.toString();

        } catch (IOException e) {
            Log.w(TAG, "Failed to read HEARTBEAT.md", e);
            return null;
        }
    }

    /**
     * Check if the heartbeat result contains the HEARTBEAT_OK marker.
     * Uses enhanced detection: checks start of response, end of response,
     * and anywhere in the content.
     *
     * @param content The heartbeat result content
     * @return true if HEARTBEAT_OK marker is found
     */
    private boolean checkHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String trimmed = content.trim();

        // Check if content starts with HEARTBEAT_OK
        if (trimmed.startsWith(HEARTBEAT_OK_MARKER)) {
            Log.d(TAG, "HEARTBEAT_OK detected at start of response");
            return true;
        }

        // Check if content ends with HEARTBEAT_OK
        if (trimmed.endsWith(HEARTBEAT_OK_MARKER)) {
            Log.d(TAG, "HEARTBEAT_OK detected at end of response");
            return true;
        }

        // Check anywhere in content
        if (content.contains(HEARTBEAT_OK_MARKER)) {
            Log.d(TAG, "HEARTBEAT_OK detected in response");
            return true;
        }

        Log.d(TAG, "HEARTBEAT_OK not found - issues may need attention");
        return false;
    }

    /**
     * Get default heartbeat prompt if HEARTBEAT.md is missing.
     */
    private String getDefaultHeartbeatPrompt() {
        return "Perform a system health check. Review the current state of the workspace, " +
               "check for any pending tasks or incomplete work, and verify that all systems " +
               "are functioning correctly. Report your findings.\n\n" +
               "If everything is healthy and ready, respond with ONLY: HEARTBEAT_OK";
    }

    @Override
    protected String getTaskTitle(int sessionType) {
        return "Heartbeat Check";
    }

    @Override
    protected String getParentTaskId() {
        return "heartbeat";
    }

    @Override
    protected int getTaskType() {
        return TaskResult.TYPE_HEARTBEAT;
    }

    @Override
    protected Result executeTask() {
        // This method is not used for HeartbeatWorker as doWork() is overridden directly
        // The abstract method is required by BaseTaskWorker but heartbeat has custom logic
        return Result.success();
    }
}
