package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.HeartbeatResponse;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.util.NotificationManager;

/**
 * Worker that executes heartbeat checks in the background.
 * Reads HEARTBEAT.md from workspace, executes it in an isolated session,
 * and uses Structured Outputs to guarantee valid JSON schema adherence.
 *
 * The model responds with a structured JSON object:
 * {
 *   "healthy": boolean,
 *   "summary": string,
 *   "issues": [{"category": string, "description": string, "severity": "low"|"medium"|"high"}]
 * }
 *
 * Falls back to regex detection for backward compatibility with non-structured responses.
 */
public class HeartbeatWorker extends BaseTaskWorker {

    private static final String TAG = "HeartbeatWorker";

    // Fallback regex pattern for backward compatibility
    private static final Pattern HEARTBEAT_JSON_PATTERN = Pattern.compile(
            "\\{\\s*\"HEARTBEAT_OK\"\\s*:\\s*(true|false)\\s*\\}",
            Pattern.CASE_INSENSITIVE
    );

    private final HeartbeatConfigRepository heartbeatConfigRepo;
    private final NotificationManager notificationManager;

    public HeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.heartbeatConfigRepo = new HeartbeatConfigRepository(appContext);
        this.notificationManager = new NotificationManager(appContext);
    }

    @Override
    protected void customizeAgentLoop(AgentLoop agentLoop) {
        // Enable Structured Outputs for heartbeat responses
        agentLoop.setResponseSchema(HeartbeatResponse.getJsonSchema());
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
     * Check if the heartbeat result indicates a healthy system.
     * First tries to parse as Structured Output (HeartbeatResponse),
     * then falls back to legacy {"HEARTBEAT_OK": bool} regex detection.
     *
     * @param content The heartbeat result content
     * @return true if the system is healthy
     */
    private boolean checkHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            Log.d(TAG, "Heartbeat content is null or empty - assuming unhealthy");
            return false;
        }

        // Handle refusal
        if (content.startsWith("[REFUSAL]")) {
            Log.w(TAG, "Model refused to respond: " + content);
            return false;
        }

        // Try parsing as Structured Output first
        try {
            HeartbeatResponse response = HeartbeatResponse.fromJson(content);
            Log.d(TAG, "Parsed structured heartbeat: healthy=" + response.isHealthy() +
                    ", summary=" + response.getSummary() +
                    ", issues=" + response.getIssues().size());
            return response.isHealthy();
        } catch (Exception e) {
            Log.d(TAG, "Not a structured heartbeat, trying regex: " + e.getMessage());
        }

        // Fallback to legacy regex detection
        Matcher matcher = HEARTBEAT_JSON_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonStr = matcher.group(0);
            Log.d(TAG, "Found legacy heartbeat JSON: " + jsonStr);

            try {
                JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                if (json.has("HEARTBEAT_OK")) {
                    boolean isOk = json.get("HEARTBEAT_OK").getAsBoolean();
                    Log.d(TAG, "HEARTBEAT_OK value: " + isOk);
                    return isOk;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse legacy heartbeat JSON", e);
            }
        }

        Log.d(TAG, "No heartbeat marker found - assuming issues need attention");
        return false;
    }

    /**
     * Get default heartbeat prompt if HEARTBEAT.md is missing.
     */
    private String getDefaultHeartbeatPrompt() {
        return "Perform a system health check. Review the current state of the workspace, " +
               "check for any pending tasks or incomplete work, and verify that all systems " +
               "are functioning correctly. Report your findings.\n\n" +
               "Respond with a JSON object containing:\n" +
               "- \"healthy\": true if everything is healthy, false otherwise\n" +
               "- \"summary\": A brief summary of the system status\n" +
               "- \"issues\": An array of any issues found, each with category, description, and severity (low/medium/high)\n\n" +
               "If everything is healthy, set healthy to true and list no issues.";
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
