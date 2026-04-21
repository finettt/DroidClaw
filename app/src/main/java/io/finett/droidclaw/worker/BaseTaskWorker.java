package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.impl.SubmitNotificationTool;
import io.finett.droidclaw.util.DeviceStateHelper;
import io.finett.droidclaw.util.SettingsManager;

public abstract class BaseTaskWorker extends Worker {

    private static final String TAG = "BaseTaskWorker";

    protected static final int MAX_ITERATIONS = 10;
    protected static final long MAX_EXECUTION_TIME_MS = 5 * 60 * 1000L;

    protected final Context appContext;
    protected WorkspaceManager workspaceManager;
    protected IdentityManager identityManager;
    protected SettingsManager settingsManager;
    protected TaskRepository taskRepository;
    protected ChatRepository chatRepository;
    protected MemoryRepository memoryRepository;

    public BaseTaskWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.appContext = context.getApplicationContext();
        initializeComponents();
    }

    private void initializeComponents() {
        this.workspaceManager = new WorkspaceManager(appContext);
        this.identityManager = new IdentityManager(appContext, workspaceManager);
        this.settingsManager = new SettingsManager(appContext);
        this.taskRepository = new TaskRepository(appContext);
        this.chatRepository = new ChatRepository(appContext);
        this.memoryRepository = new MemoryRepository(workspaceManager);
    }

    protected ChatSession createIsolatedSession(int sessionType) {
        String sessionId = UUID.randomUUID().toString();
        String title = getTaskTitle(sessionType);
        long timestamp = System.currentTimeMillis();

        ChatSession session = new ChatSession(sessionId, title, timestamp);
        session.setSessionType(sessionType);
        session.setParentTaskId(getParentTaskId());

        List<ChatSession> sessions = chatRepository.loadSessions();
        sessions.add(session);
        chatRepository.saveSessions(sessions);

        Log.d(TAG, "Created isolated session: " + sessionId + " (type: " + sessionType + ")");
        return session;
    }

    protected List<ChatMessage> loadFreshContext(ChatSession session) {
        List<ChatMessage> contextMessages = new ArrayList<>();

        try {
            if (identityManager.identityFilesExist()) {
                List<ChatMessage> identityMessages = identityManager.getIdentityMessages();
                if (identityMessages != null && !identityMessages.isEmpty()) {
                    contextMessages.addAll(identityMessages);
                    Log.d(TAG, "Loaded " + identityMessages.size() + " identity messages");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load identity messages", e);
        }

        try {
            String memoryContext = buildMemoryContext();
            if (!memoryContext.isEmpty()) {
                ChatMessage memoryMessage = new ChatMessage(memoryContext, ChatMessage.TYPE_SYSTEM);
                contextMessages.add(memoryMessage);
                Log.d(TAG, "Loaded memory context: " + memoryContext.length() + " chars");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load memory context", e);
        }

        return contextMessages;
    }

    protected TaskResult executeWithSandbox(ChatSession session, String prompt) {
        String taskId = session.getId();
        int taskType = getTaskType();
        long startTime = System.currentTimeMillis();

        TaskExecutionRecord executionRecord = new TaskExecutionRecord(taskId, session.getId(), taskType, startTime);
        TaskResult result;

        if (!DeviceStateHelper.shouldExecuteTask(appContext)) {
            String skipReason = buildSkipReason();
            Log.w(TAG, "Skipping task execution due to device state: " + skipReason);
            executionRecord.fail(startTime, skipReason);
            result = createFailureResult(taskId, taskType, startTime, skipReason);
            taskRepository.saveExecutionRecord(executionRecord);
            return result;
        }

        try {
            LlmApiService apiService = createApiService();
            ToolRegistry toolRegistry = createToolRegistry();

            AgentLoop agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager);

            customizeAgentLoop(agentLoop);

            List<ChatMessage> contextMessages = loadFreshContext(session);
            agentLoop.setIdentityContext(contextMessages);

            ChatMessage userMessage = new ChatMessage(prompt, ChatMessage.TYPE_USER);

            List<ChatMessage> conversationHistory = new ArrayList<>();
            conversationHistory.add(userMessage);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> finalResponse = new AtomicReference<>();
            AtomicReference<List<ChatMessage>> finalHistory = new AtomicReference<>();
            AtomicReference<String> errorRef = new AtomicReference<>();

            agentLoop.start(conversationHistory, new AgentLoop.AgentCallback() {
                @Override
                public void onProgress(String status) {
                    Log.d(TAG, "Progress: " + status);
                }

                @Override
                public void onToolCall(String toolName, String arguments) {
                    Log.d(TAG, "Tool call: " + toolName);
                }

                @Override
                public void onToolResult(String toolName, String result) {
                    Log.d(TAG, "Tool result: " + toolName);
                }

                @Override
                public void onComplete(String response, List<ChatMessage> history) {
                    finalResponse.set(response);
                    finalHistory.set(history);
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    errorRef.set(error);
                    latch.countDown();
                }

                @Override
                public void onApprovalRequired(String toolName, String description, com.google.gson.JsonObject arguments, AgentLoop.ApprovalCallback approvalCallback) {
                    Log.d(TAG, "Auto-approving tool: " + toolName);
                    approvalCallback.onApproved();
                }
            });

            boolean completed = latch.await(MAX_EXECUTION_TIME_MS, TimeUnit.MILLISECONDS);

            long endTime = System.currentTimeMillis();

            if (!completed) {
                String errorMsg = "Task execution timed out after " + (MAX_EXECUTION_TIME_MS / 1000) + " seconds";
                Log.w(TAG, errorMsg);
                executionRecord.fail(endTime, errorMsg);
                result = createFailureResult(taskId, taskType, endTime, errorMsg);
            } else if (errorRef.get() != null) {
                String errorMsg = "Task execution failed: " + errorRef.get();
                Log.e(TAG, errorMsg);
                executionRecord.fail(endTime, errorMsg);
                result = createFailureResult(taskId, taskType, endTime, errorMsg);
            } else {
                String response = finalResponse.get();
                List<ChatMessage> history = finalHistory.get();

                executionRecord.complete(endTime);
                executionRecord.setIterations(countIterations(history));
                executionRecord.setTokensUsed(estimateTokens(response));

                result = createSuccessResult(taskId, taskType, endTime, response, history);
                extractAndCacheNotificationContent(result, response, history);

                if (history != null && !history.isEmpty()) {
                    chatRepository.saveMessages(session.getId(), history);
                }
            }

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            String errorMsg = "Task execution error: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            executionRecord.fail(endTime, errorMsg);
            result = createFailureResult(taskId, taskType, endTime, errorMsg);
        }

        taskRepository.saveExecutionRecord(executionRecord);

        return result;
    }

    private String buildSkipReason() {
        StringBuilder sb = new StringBuilder("Task skipped due to device state: ");

        if (DeviceStateHelper.isAirplaneModeOn(appContext)) {
            sb.append("Airplane mode is ON");
        } else if (DeviceStateHelper.isBatteryCritical(appContext)) {
            sb.append("Battery is critically low (").append(DeviceStateHelper.getBatteryLevel(appContext)).append("%)");
        } else if (DeviceStateHelper.isStorageCritical(appContext)) {
            sb.append("Storage is critically low");
        } else {
            sb.append("Device conditions not suitable");
        }

        return sb.toString();
    }

    protected String generateNotificationContent(TaskResult result) {
        String content = result.getContent();
        if (content == null || content.isEmpty()) {
            return "Task completed with no output";
        }

        int maxLength = 500;
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength) + "...";
        }

        return content;
    }

    private String buildMemoryContext() {
        StringBuilder context = new StringBuilder();

        try {
            String longTermMemory = memoryRepository.readLongTermMemory();
            if (longTermMemory != null && !longTermMemory.trim().isEmpty()) {
                context.append("# Long-term Memory\n\n");
                context.append(longTermMemory);
                context.append("\n\n");
            }

            String todayNote = memoryRepository.readTodayNote();
            if (todayNote != null && !todayNote.trim().isEmpty()) {
                context.append("# Today's Notes\n\n");
                context.append(todayNote);
                context.append("\n\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to build memory context", e);
        }

        return context.toString();
    }

    private LlmApiService createApiService() {
        return new LlmApiService(settingsManager);
    }

    private ToolRegistry createToolRegistry() {
        return new ToolRegistry(appContext, settingsManager);
    }

    private int countIterations(List<ChatMessage> history) {
        if (history == null) return 0;
        int count = 0;
        for (ChatMessage msg : history) {
            if (msg.getType() == ChatMessage.TYPE_ASSISTANT) {
                count++;
            }
        }
        return count;
    }

    /** Uses 1 token ≈ 4 characters as a rough approximation. */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }

    private TaskResult createSuccessResult(String taskId, int taskType, long timestamp,
                                           String content, List<ChatMessage> history) {
        TaskResult result = new TaskResult(taskId, taskType, timestamp, content);
        result.putMetadata("status", "success");
        result.putMetadata("session_id", taskId);
        return result;
    }

    private TaskResult createFailureResult(String taskId, int taskType, long timestamp,
                                           String errorMessage) {
        TaskResult result = new TaskResult(taskId, taskType, timestamp, "Failed: " + errorMessage);
        result.putMetadata("status", "failed");
        result.putMetadata("error", errorMessage);
        return result;
    }

    /**
     * Extract agent-generated notification content from the response and cache it in metadata.
     * 
     * Priority order:
     * 1. Check for submit_notification tool call in conversation history (structured, guaranteed format)
     * 2. Parse TITLE: and SUMMARY: markers from text response (legacy fallback)
     *
     * @param result The task result to update
     * @param response The agent's response text
     * @param history Full conversation history including tool calls
     */
    private void extractAndCacheNotificationContent(TaskResult result, String response, List<ChatMessage> history) {
        JsonObject notification = SubmitNotificationTool.getLastNotification();
        if (notification != null && notification.has("title") && notification.has("summary")) {
            result.putMetadata("notification_title", notification.get("title").getAsString());
            result.putMetadata("notification_summary", notification.get("summary").getAsString());
            
            String status = "success";
            if (notification.has("status")) {
                status = notification.get("status").getAsString();
            }
            result.putMetadata("notification_status", status);
            
            Log.d(TAG, "Extracted notification from submit_notification tool: " + notification.get("title").getAsString());
            
            SubmitNotificationTool.clearLastNotification();
            return;
        }

        if (response == null || response.isEmpty()) {
            return;
        }

        String notificationTitle = null;
        String notificationSummary = null;

        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (notificationTitle == null && line.startsWith("TITLE:")) {
                notificationTitle = line.substring(6).trim();
            } else if (notificationSummary == null && line.startsWith("SUMMARY:")) {
                notificationSummary = line.substring(8).trim();
            }
            if (notificationTitle != null && notificationSummary != null) {
                break;
            }
        }

        if (notificationTitle != null && !notificationTitle.isEmpty()) {
            result.putMetadata("notification_title", notificationTitle);
            Log.d(TAG, "Extracted notification title from text: " + notificationTitle);
        }

        if (notificationSummary != null && !notificationSummary.isEmpty()) {
            result.putMetadata("notification_summary", notificationSummary);
            Log.d(TAG, "Extracted notification summary from text: " + notificationSummary);
        }
    }

    /**
     * Get the task title for the session type.
     */
    protected abstract String getTaskTitle(int sessionType);

    /**
     * Get the parent task ID (for linking sessions to cron jobs).
     */
    protected abstract String getParentTaskId();

    /**
     * Get the task type constant for this worker.
     */
    protected abstract int getTaskType();

    /**
     * Hook method for subclasses to customize the AgentLoop before execution.
     * Default implementation does nothing.
     *
     * @param agentLoop The AgentLoop instance to customize
     */
    protected void customizeAgentLoop(AgentLoop agentLoop) {
        // No-op by default
    }

    /**
     * Execute the worker's specific task logic.
     * Called by doWork() after common setup.
     *
     * @return Result indicating success or failure
     */
    @NonNull
    protected abstract Result executeTask();
}
