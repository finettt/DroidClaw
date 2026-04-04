package io.finett.droidclaw.heartbeat;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.ConversationSummarizer;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.agent.MemoryContextBuilder;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * WorkManager Worker that executes an isolated Cron Job.
 *
 * Each execution:
 * 1. Creates a new hidden ChatSession (isolated from main chat)
 * 2. Loads fresh context (identity + memories)
 * 3. Runs isolated AgentLoop with full tool access
 * 4. Saves complete conversation to hidden session
 * 5. Creates TaskRecord with execution metadata
 * 6. Creates TaskResult for Zen UI
 */
public class CronJobWorker extends Worker {
    private static final String TAG = "CronJobWorker";

    private final ChatRepository chatRepository;
    private final TaskRepository taskRepository;
    private final SettingsManager settingsManager;
    private final MemoryRepository memoryRepository;

    public CronJobWorker(
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
        String cronJobId = getInputData().getString("cronJobId");

        if (cronJobId == null || cronJobId.isEmpty()) {
            Log.e(TAG, "No cronJobId provided in work input");
            return Result.failure();
        }

        Log.d(TAG, "Cron job execution started: " + cronJobId);

        try {
            // 1. Load the cron job
            CronJob job = taskRepository.getCronJob(cronJobId);
            if (job == null) {
                Log.e(TAG, "Cron job not found: " + cronJobId);
                return Result.failure();
            }

            if (!job.isEnabled()) {
                Log.d(TAG, "Cron job disabled: " + job.getName());
                return Result.success();
            }

            // 2. Create task record
            TaskRecord record = TaskRecord.create(job.getId(), job.getName(), job.getPrompt());

            // 3. Create hidden isolated session
            ChatSession hiddenSession = ChatSession.createCronJobSession(
                    job.getId(),
                    record.getId()
            );
            record.setSessionId(hiddenSession.getId());
            chatRepository.saveHiddenSession(hiddenSession);

            Log.d(TAG, "Created hidden session for cron job: " + hiddenSession.getId());

            // 4. Load fresh context (identity)
            List<ChatMessage> identityMessages = loadIdentityContext();

            // 5. Build memory context if enabled
            String memoryContext = "";
            if (job.isLoadMemories()) {
                try {
                    MemoryContextBuilder memoryContextBuilder = new MemoryContextBuilder(memoryRepository);
                    memoryContext = memoryContextBuilder.buildMemoryContext();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to build memory context", e);
                }
            }

            // 6. Build initial conversation
            List<ChatMessage> messages = new ArrayList<>();

            // Add memory context if available
            if (!memoryContext.isEmpty()) {
                messages.add(new ChatMessage(memoryContext, ChatMessage.TYPE_SYSTEM));
            }

            // Add the task prompt
            messages.add(new ChatMessage(job.getPrompt(), ChatMessage.TYPE_USER));

            // 7. Create API service and isolated agent loop
            LlmApiService apiService = new LlmApiService(settingsManager);
            ToolRegistry toolRegistry = new ToolRegistry(getApplicationContext(), settingsManager);

            AgentConfig agentConfig = settingsManager.getAgentConfig();

            // Use job's max iterations or default from agent config
            int maxIterations = job.getMaxIterations() > 0 ? job.getMaxIterations() : agentConfig.getMaxIterations();

            AgentLoop agentLoop = new AgentLoop(
                    apiService,
                    toolRegistry,
                    settingsManager,
                    null, // No summarizer for cron jobs
                    null  // No memory context builder (already added manually)
            );

            // Set identity context
            agentLoop.setIdentityContext(identityMessages);

            // 8. Run isolated agent loop synchronously
            final String[] finalResponse = {null};
            final List<ChatMessage>[] updatedHistory = new List[]{null};
            final String[] error = {null};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            agentLoop.start(messages, new AgentLoop.AgentCallback() {
                @Override
                public void onProgress(String status) {
                    Log.d(TAG, "Cron job progress: " + status);
                }

                @Override
                public void onToolCall(String toolName, String arguments) {
                    Log.d(TAG, "Cron job tool call: " + toolName);
                    // Save intermediate messages to hidden session
                    saveIntermediateMessages(hiddenSession.getId(), updatedHistory[0]);
                }

                @Override
                public void onToolResult(String toolName, String result) {
                    Log.d(TAG, "Cron job tool result: " + toolName);
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
                    // Auto-approve for cron jobs (no user interaction)
                    Log.d(TAG, "Cron job auto-approved: " + toolName);
                    approvalCallback.onApproved();
                }
            });

            // Wait for completion with timeout (10 minutes max for cron jobs)
            boolean completed = latch.await(10, java.util.concurrent.TimeUnit.MINUTES);
            if (!completed) {
                Log.w(TAG, "Cron job execution timed out: " + job.getName());
                error[0] = "Execution exceeded time limit (10 minutes)";
            }

            // 9. Handle result
            if (error[0] != null) {
                Log.e(TAG, "Cron job failed: " + job.getName() + " - " + error[0]);
                record.markFailure(error[0]);
                job.recordFailure();

                // Save error record
                taskRepository.saveTaskRecord(record);
                taskRepository.saveCronJob(job);

                // Create TaskResult for failure
                TaskResult result = new TaskResult();
                result.setTaskType("cronjob");
                result.setTaskId(job.getId());
                result.setTaskName(job.getName());
                result.setSessionId(hiddenSession.getId());
                result.setPrompt(job.getPrompt());
                result.setResponse("Error: " + error[0]);
                result.setStatus("failure");
                result.setErrorMessage(error[0]);
                result.setExecutedAt(System.currentTimeMillis());
                result.setNotificationTitle("❌ " + job.getName());
                result.setNotificationSummary("Execution failed: " + error[0]);
                taskRepository.saveTaskResult(result);

                return Result.success(); // Return success to avoid retry
            }

            // 10. Save final conversation to hidden session
            chatRepository.saveMessages(hiddenSession.getId(), updatedHistory[0]);

            // 11. Update task record
            record.markSuccess(finalResponse[0]);
            record.setIterationCount(agentLoop.getIterationCount());
            record.setToolCallsCount(agentLoop.getTotalToolCalls());
            record.setTokensUsed(agentLoop.getTotalTokens());
            record.setNotificationTitle("✅ " + job.getName());
            record.setNotificationSummary(extractSummary(finalResponse[0]));
            taskRepository.saveTaskRecord(record);

            // 12. Create TaskResult for Zen UI
            TaskResult taskResult = TaskResult.fromCronJob(job, record);
            taskResult.setNotificationTitle(record.getNotificationTitle());
            taskResult.setNotificationSummary(record.getNotificationSummary());
            taskRepository.saveTaskResult(taskResult);

            // 13. Update cron job metadata
            job.recordSuccess();
            taskRepository.saveCronJob(job);

            Log.d(TAG, "Cron job completed successfully: " + job.getName());
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Cron job execution failed", e);
            return Result.retry();
        }
    }

    /**
     * Load identity context for isolated session.
     */
    private List<ChatMessage> loadIdentityContext() {
        try {
            WorkspaceManager workspaceManager = new WorkspaceManager(getApplicationContext());
            IdentityManager identityManager = new IdentityManager(getApplicationContext(), workspaceManager);

            if (identityManager.identityFilesExist()) {
                return identityManager.getIdentityMessages();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load identity context", e);
        }

        // Return empty list if identity not available
        return new ArrayList<>();
    }

    /**
     * Save intermediate messages during execution (for progress tracking).
     */
    private void saveIntermediateMessages(String sessionId, List<ChatMessage> messages) {
        if (messages != null && !messages.isEmpty()) {
            chatRepository.saveMessages(sessionId, messages);
        }
    }

    /**
     * Extract a summary from the response (first meaningful line).
     */
    private String extractSummary(String response) {
        if (response == null || response.isEmpty()) {
            return "Task completed";
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
            }
        }

        return "Task completed";
    }
}
