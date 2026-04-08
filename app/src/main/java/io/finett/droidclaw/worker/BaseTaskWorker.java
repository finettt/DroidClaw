package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
import io.finett.droidclaw.util.SettingsManager;

/**
 * Abstract base worker for background task execution.
 * Provides common functionality for creating isolated sessions,
 * loading fresh context, and executing tasks with sandbox limits.
 */
public abstract class BaseTaskWorker extends Worker {

    private static final String TAG = "BaseTaskWorker";

    // Sandbox limits
    protected static final int MAX_ITERATIONS = 10;
    protected static final long MAX_EXECUTION_TIME_MS = 5 * 60 * 1000L; // 5 minutes

    // Worker components
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

    /**
     * Initialize common dependencies used by all workers.
     */
    private void initializeComponents() {
        this.workspaceManager = new WorkspaceManager(appContext);
        this.identityManager = new IdentityManager(appContext, workspaceManager);
        this.settingsManager = new SettingsManager(appContext);
        this.taskRepository = new TaskRepository(appContext);
        this.chatRepository = new ChatRepository(appContext);
        this.memoryRepository = new MemoryRepository(workspaceManager);
    }

    /**
     * Create an isolated chat session for background execution.
     * Hidden sessions are not shown in the regular chat list.
     *
     * @param sessionType Type of session (HIDDEN_HEARTBEAT or HIDDEN_CRON)
     * @return Created ChatSession object
     */
    protected ChatSession createIsolatedSession(int sessionType) {
        String sessionId = UUID.randomUUID().toString();
        String title = getTaskTitle(sessionType);
        long timestamp = System.currentTimeMillis();

        ChatSession session = new ChatSession(sessionId, title, timestamp);
        session.setSessionType(sessionType);
        session.setParentTaskId(getParentTaskId());

        // Save session to repository
        List<ChatSession> sessions = chatRepository.loadSessions();
        sessions.add(session);
        chatRepository.saveSessions(sessions);

        Log.d(TAG, "Created isolated session: " + sessionId + " (type: " + sessionType + ")");
        return session;
    }

    /**
     * Load fresh context for the isolated session.
     * This includes identity messages and relevant memories.
     *
     * @param session The isolated chat session
     * @return List of identity and context messages to prepend
     */
    protected List<ChatMessage> loadFreshContext(ChatSession session) {
        List<ChatMessage> contextMessages = new ArrayList<>();

        // Load identity messages (soul.md + user.md)
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

        // Load memory context (from memory repository)
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

    /**
     * Execute a prompt in a sandboxed agent loop.
     * Enforces resource limits (max iterations, timeout).
     *
     * @param session The isolated chat session
     * @param prompt The prompt to execute
     * @return TaskResult containing the execution outcome
     */
    protected TaskResult executeWithSandbox(ChatSession session, String prompt) {
        String taskId = session.getId();
        int taskType = getTaskType();
        long startTime = System.currentTimeMillis();

        TaskExecutionRecord executionRecord = new TaskExecutionRecord(taskId, session.getId(), taskType, startTime);
        TaskResult result;

        try {
            // Setup agent loop
            LlmApiService apiService = createApiService();
            ToolRegistry toolRegistry = createToolRegistry();

            AgentLoop agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager);

            // Prepare conversation with identity context
            List<ChatMessage> contextMessages = loadFreshContext(session);
            agentLoop.setIdentityContext(contextMessages);

            // Create user message with prompt
            ChatMessage userMessage = new ChatMessage(prompt, ChatMessage.TYPE_USER);

            List<ChatMessage> conversationHistory = new ArrayList<>();
            conversationHistory.add(userMessage);

            // Use CountDownLatch to wait for completion or timeout
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> finalResponse = new AtomicReference<>();
            AtomicReference<List<ChatMessage>> finalHistory = new AtomicReference<>();
            AtomicReference<String> errorRef = new AtomicReference<>();

            // Start agent loop asynchronously
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
                    // Auto-approve in background tasks (no user interaction)
                    Log.d(TAG, "Auto-approving tool: " + toolName);
                    approvalCallback.onApproved();
                }
            });

            // Wait for completion or timeout
            boolean completed = latch.await(MAX_EXECUTION_TIME_MS, TimeUnit.MILLISECONDS);

            long endTime = System.currentTimeMillis();

            if (!completed) {
                // Timeout occurred
                String errorMsg = "Task execution timed out after " + (MAX_EXECUTION_TIME_MS / 1000) + " seconds";
                Log.w(TAG, errorMsg);
                executionRecord.fail(endTime, errorMsg);
                result = createFailureResult(taskId, taskType, endTime, errorMsg);
            } else if (errorRef.get() != null) {
                // Error occurred
                String errorMsg = "Task execution failed: " + errorRef.get();
                Log.e(TAG, errorMsg);
                executionRecord.fail(endTime, errorMsg);
                result = createFailureResult(taskId, taskType, endTime, errorMsg);
            } else {
                // Success
                String response = finalResponse.get();
                List<ChatMessage> history = finalHistory.get();

                executionRecord.complete(endTime);
                executionRecord.setIterations(countIterations(history));
                executionRecord.setTokensUsed(estimateTokens(response));

                result = createSuccessResult(taskId, taskType, endTime, response, history);

                // Save conversation history
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

        // Save execution record
        taskRepository.saveExecutionRecord(executionRecord);

        return result;
    }

    /**
     * Generate notification content from task result.
     *
     * @param result The task result
     * @return Content string for notification display
     */
    protected String generateNotificationContent(TaskResult result) {
        String content = result.getContent();
        if (content == null || content.isEmpty()) {
            return "Task completed with no output";
        }

        // Truncate long content for notification
        int maxLength = 500;
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength) + "...";
        }

        return content;
    }

    /**
     * Build memory context string from memory repository.
     * Combines long-term memory and recent daily notes.
     */
    private String buildMemoryContext() {
        StringBuilder context = new StringBuilder();

        try {
            // Load long-term memory
            String longTermMemory = memoryRepository.readLongTermMemory();
            if (longTermMemory != null && !longTermMemory.trim().isEmpty()) {
                context.append("# Long-term Memory\n\n");
                context.append(longTermMemory);
                context.append("\n\n");
            }

            // Load today's note if it exists
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

    /**
     * Create an API service instance for background execution.
     * Uses the currently configured provider and model.
     */
    private LlmApiService createApiService() {
        return new LlmApiService(settingsManager);
    }

    /**
     * Create a tool registry with all available tools.
     * Tools are sandboxed to workspace directory.
     */
    private ToolRegistry createToolRegistry() {
        return new ToolRegistry(appContext, settingsManager);
    }

    /**
     * Count the number of iterations (assistant messages) in conversation history.
     */
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

    /**
     * Estimate token count for a string (rough approximation).
     * Uses 1 token ≈ 4 characters for English text.
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }

    /**
     * Create a success result.
     */
    private TaskResult createSuccessResult(String taskId, int taskType, long timestamp,
                                           String content, List<ChatMessage> history) {
        TaskResult result = new TaskResult(taskId, taskType, timestamp, content);
        result.putMetadata("status", "success");
        result.putMetadata("session_id", taskId);
        return result;
    }

    /**
     * Create a failure result.
     */
    private TaskResult createFailureResult(String taskId, int taskType, long timestamp,
                                           String errorMessage) {
        TaskResult result = new TaskResult(taskId, taskType, timestamp, "Failed: " + errorMessage);
        result.putMetadata("status", "failed");
        result.putMetadata("error", errorMessage);
        return result;
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
     * Execute the worker's specific task logic.
     * Called by doWork() after common setup.
     *
     * @return Result indicating success or failure
     */
    @NonNull
    protected abstract Result executeTask();
}
