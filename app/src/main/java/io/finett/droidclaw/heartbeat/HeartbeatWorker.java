package io.finett.droidclaw.heartbeat;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonArray;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.agent.ConversationSummarizer;
import io.finett.droidclaw.agent.MemoryContextBuilder;

/**
 * WorkManager Worker that executes the Heartbeat check.
 *
 * Runs periodically in the main chat session:
 * 1. Loads main chat session and history
 * 2. Reads HEARTBEAT.md from workspace if it exists
 * 3. Runs agent loop with heartbeat prompt
 * 4. If response is HEARTBEAT_OK, stays silent
 * 5. Otherwise, adds alert to main chat and creates TaskResult
 */
public class HeartbeatWorker extends Worker {
    private static final String TAG = "HeartbeatWorker";
    private static final String HEARTBEAT_OK_MARKER = "HEARTBEAT_OK";

    private final ChatRepository chatRepository;
    private final TaskRepository taskRepository;
    private final SettingsManager settingsManager;
    private final MemoryRepository memoryRepository;

    public HeartbeatWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
        WorkspaceManager workspaceManager = new WorkspaceManager(context);
        this.chatRepository = new ChatRepository(context);
        this.taskRepository = new TaskRepository(context);
        this.settingsManager = new SettingsManager(context);
        this.memoryRepository = new MemoryRepository(workspaceManager);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Heartbeat execution started");

        try {
            // 1. Load heartbeat config
            HeartbeatConfig config = loadHeartbeatConfig();

            // Check if heartbeat is enabled
            if (!config.isEnabled()) {
                Log.d(TAG, "Heartbeat disabled, skipping");
                return Result.success();
            }

            // 2. Check active hours
            int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (!config.isWithinActiveHours(currentHour)) {
                Log.d(TAG, "Outside active hours, skipping");
                return Result.success();
            }

            // 3. Load main chat session
            ChatSession mainSession = chatRepository.getMainSession();
            List<ChatMessage> history = chatRepository.loadMessages(mainSession.getId());

            // 4. Build heartbeat prompt
            String heartbeatPrompt = buildHeartbeatPrompt(config);

            // 5. Add heartbeat check message to history
            history.add(new ChatMessage(heartbeatPrompt, ChatMessage.TYPE_USER));

            // 6. Create API service and agent loop for background execution
            LlmApiService apiService = new LlmApiService(settingsManager);
            ToolRegistry toolRegistry = new ToolRegistry(getApplicationContext(), settingsManager);

            AgentConfig agentConfig = settingsManager.getAgentConfig();
            AgentLoop agentLoop = new AgentLoop(
                    apiService,
                    toolRegistry,
                    settingsManager,
                    null, // No summarizer for heartbeat (keep it simple)
                    null  // No memory context builder
            );

            // Set max iterations lower for background execution
            agentLoop.setIdentityContext(null); // No identity for heartbeat

            // 7. Run agent loop synchronously (using CountDownLatch)
            final String[] finalResponse = {null};
            final List<ChatMessage>[] updatedHistory = new List[]{null};
            final String[] error = {null};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            agentLoop.start(history, new AgentLoop.AgentCallback() {
                @Override
                public void onProgress(String status) {
                    Log.d(TAG, "Heartbeat progress: " + status);
                }

                @Override
                public void onToolCall(String toolName, String arguments) {
                    Log.d(TAG, "Heartbeat tool call: " + toolName);
                }

                @Override
                public void onToolResult(String toolName, String result) {
                    Log.d(TAG, "Heartbeat tool result: " + toolName);
                }

                @Override
                public void onComplete(String response, List<ChatMessage> updated) {
                    finalResponse[0] = response;
                    updatedHistory[0] = updated;
                    latch.countDown();
                }

                @Override
                public void onError(String err) {
                    error[0] = err;
                    latch.countDown();
                }

                @Override
                public void onApprovalRequired(String toolName, String description,
                                               com.google.gson.JsonObject arguments,
                                               AgentLoop.ApprovalCallback approvalCallback) {
                    // Auto-approve for heartbeat (no user interaction)
                    Log.d(TAG, "Heartbeat auto-approved: " + toolName);
                    approvalCallback.onApproved();
                }
            });

            // Wait for completion with timeout (5 minutes max)
            boolean completed = latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
            if (!completed) {
                Log.w(TAG, "Heartbeat execution timed out");
                error[0] = "Execution timed out";
            }

            // 8. Handle result
            if (error[0] != null) {
                Log.e(TAG, "Heartbeat error: " + error[0]);
                config.setLastStatus("error");
                saveHeartbeatConfig(config);
                return Result.success(); // Return success to avoid retry
            }

            // Check if response is HEARTBEAT_OK
            if (isHeartbeatOk(finalResponse[0], config)) {
                Log.d(TAG, "Heartbeat OK - nothing to report");
                config.setLastStatus("ok");
                config.setLastRunAt(System.currentTimeMillis());
                saveHeartbeatConfig(config);
                // Silent - don't add to chat
                return Result.success();
            }

            // 9. Alert - add to main chat
            Log.d(TAG, "Heartbeat alert - adding to main chat");
            chatRepository.saveMessages(mainSession.getId(), updatedHistory[0]);

            // Create TaskResult for zen UI
            TaskResult result = TaskResult.fromHeartbeat(
                    mainSession.getId(),
                    finalResponse[0],
                    System.currentTimeMillis()
            );
            result.setNotificationTitle("Heartbeat Alert");
            result.setNotificationSummary(extractSummary(finalResponse[0]));
            taskRepository.saveTaskResult(result);

            config.setLastStatus("alert");
            config.setLastRunAt(System.currentTimeMillis());
            saveHeartbeatConfig(config);

            Log.d(TAG, "Heartbeat execution completed successfully");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Heartbeat execution failed", e);
            return Result.retry();
        }
    }

    /**
     * Load heartbeat config from settings.
     */
    private HeartbeatConfig loadHeartbeatConfig() {
        // For now, return default config
        // In Phase 6, this will be loaded from SettingsManager
        return new HeartbeatConfig();
    }

    /**
     * Save heartbeat config to settings.
     */
    private void saveHeartbeatConfig(HeartbeatConfig config) {
        // In Phase 6, this will be saved via SettingsManager
    }

    /**
     * Build the heartbeat prompt from HEARTBEAT.md or default.
     */
    private String buildHeartbeatPrompt(HeartbeatConfig config) {
        // Try to read HEARTBEAT.md from workspace
        File workspace = new File(getApplicationContext().getFilesDir(), "workspace");
        File heartbeatFile = new File(workspace, ".agent/HEARTBEAT.md");

        if (heartbeatFile.exists()) {
            try {
                String content = new String(
                        java.nio.file.Files.readAllBytes(heartbeatFile.toPath()),
                        StandardCharsets.UTF_8
                );
                return "Read HEARTBEAT.md and follow it strictly. " +
                        "If nothing needs attention, reply HEARTBEAT_OK.\n\n" +
                        content;
            } catch (IOException e) {
                Log.w(TAG, "Failed to read HEARTBEAT.md", e);
            }
        }

        // Default prompt
        return "Quick check: anything urgent that needs attention? " +
                "If nothing, reply HEARTBEAT_OK.";
    }

    /**
     * Check if response indicates nothing needs attention.
     */
    private boolean isHeartbeatOk(String response, HeartbeatConfig config) {
        if (response == null || response.isEmpty()) {
            return false;
        }

        String trimmed = response.trim();
        int maxChars = config.getAckMaxChars();

        // Check if starts with HEARTBEAT_OK
        if (trimmed.startsWith(HEARTBEAT_OK_MARKER)) {
            String remaining = trimmed.substring(HEARTBEAT_OK_MARKER.length()).trim();
            return remaining.isEmpty() || remaining.length() <= maxChars;
        }

        // Check if ends with HEARTBEAT_OK
        if (trimmed.endsWith(HEARTBEAT_OK_MARKER)) {
            String remaining = trimmed.substring(0, trimmed.length() - HEARTBEAT_OK_MARKER.length()).trim();
            return remaining.isEmpty() || remaining.length() <= maxChars;
        }

        return false;
    }

    /**
     * Extract a summary from the response (first meaningful line).
     */
    private String extractSummary(String response) {
        if (response == null || response.isEmpty()) {
            return "Heartbeat check completed";
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith(HEARTBEAT_OK_MARKER)) {
                return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
            }
        }

        return "Heartbeat check completed";
    }
}
