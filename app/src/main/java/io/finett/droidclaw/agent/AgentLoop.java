package io.finett.droidclaw.agent;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.service.BackgroundProcessManager;
import io.finett.droidclaw.shell.ExecPlan;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.repository.MemoryRepository;

public class AgentLoop {
    private static final String TAG = "AgentLoop";
    private static final int DEFAULT_MAX_ITERATIONS = 20;

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(?:`([^`]+)`|(/data/data/[^\\s]+|uploads/[^\\s]+|home/[^\\s]+|tmp/[^\\s]+))"
    );

    private final LlmApiService apiService;
    private final ToolRegistry toolRegistry;
    private final SettingsManager settingsManager;
    private final ConversationSummarizer summarizer;
    private final MemoryContextBuilder memoryContext;
    private int iterationCount;
    private int maxIterations;
    private boolean requireApproval;
    private List<ChatMessage> identityMessages;
    private JsonObject responseSchema;

    /**
     * Look up the per-tool approval override for a given tool name.
     * Returns {@link ToolApprovalMode#DEFAULT} if no override is set or if
     * reading the overrides fails (logged at warn level).
     */
    private ToolApprovalMode getPerToolApprovalMode(String toolName) {
        try {
            java.util.Map<String, String> overrides = settingsManager.getAgentConfig()
                    .getToolApprovalOverrides();
            if (overrides == null) return ToolApprovalMode.DEFAULT;
            String value = overrides.get(toolName);
            if (value == null) return ToolApprovalMode.DEFAULT;
            try {
                return ToolApprovalMode.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return ToolApprovalMode.DEFAULT;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading tool approval override for " + toolName, e);
            return ToolApprovalMode.DEFAULT;
        }
    }

    // Token tracking - "Last Usage" algorithm
    // Current context tokens (from last API response - this is the ACTUAL context size)
    private int currentContextTokens = 0;
    private int currentPromptTokens = 0;
    private int currentCompletionTokens = 0;

    // Session cumulative tokens (total spent across all requests)
    private int totalTokens = 0;
    private int totalPromptTokens = 0;
    private int totalCompletionTokens = 0;
    private int totalToolCalls = 0;

    public interface AgentCallback {
        void onProgress(String status);
        void onToolCall(String toolName, String arguments);
        void onToolResult(String toolName, String result);
        void onComplete(String finalResponse, List<ChatMessage> conversationHistory);
        void onError(String error);

        /**
         * Called when a tool requires user approval before execution.
         *
         * @param toolName Name of the tool requesting approval
         * @param description Human-readable description of what the tool will do
         * @param arguments The tool arguments
         * @param approvalCallback Callback to indicate approval or denial
         */
        void onApprovalRequired(String toolName, String description, JsonObject arguments, ApprovalCallback approvalCallback);
    }

    public interface ApprovalCallback {
        void onApproved();
        void onDenied();
    }

    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager) {
        this(apiService, toolRegistry, settingsManager, null, null);
    }

    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager,
                     ConversationSummarizer summarizer, MemoryContextBuilder memoryContext) {
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
        this.summarizer = summarizer;
        this.memoryContext = memoryContext;
        this.iterationCount = 0;

        if (settingsManager != null) {
            this.maxIterations = settingsManager.getMaxAgentIterations();
            this.requireApproval = settingsManager.isRequireApproval();
        } else {
            this.maxIterations = DEFAULT_MAX_ITERATIONS;
            this.requireApproval = true;
        }
    }

    public void setIdentityContext(List<ChatMessage> identityMessages) {
        this.identityMessages = identityMessages;
        Log.d(TAG, "Identity context set: " + (identityMessages != null ? identityMessages.size() : 0) + " message(s)");
    }

    public void setResponseSchema(JsonObject schema) {
        this.responseSchema = schema;
        Log.d(TAG, "Response schema set: " + (schema != null ? "enabled" : "disabled"));
    }

    public void start(List<ChatMessage> conversationHistory, AgentCallback callback) {
        iterationCount = 0;

        // Make a mutable copy of the conversation history
        List<ChatMessage> workingHistory = new ArrayList<>(conversationHistory);

        callback.onProgress("Sending message to LLM...");
        executeIteration(workingHistory, callback);
    }

    /**
     * Execute one iteration of the agent loop.
     */
    private void executeIteration(List<ChatMessage> conversationHistory, AgentCallback callback) {
        iterationCount++;

        if (iterationCount > maxIterations) {
            callback.onError("Maximum iterations (" + maxIterations + ") reached. The agent may be stuck in a loop.");
            return;
        }

        Log.d(TAG, "Iteration " + iterationCount + "/" + maxIterations);

        // Check if summarization needed using "Last Usage" algorithm (if summarizer available)
        // Use actual current context tokens instead of estimated tokens
        if (summarizer != null && summarizer.needsSummarization(currentContextTokens)) {
            callback.onProgress("Context limit approaching (" + currentContextTokens + " tokens), summarizing conversation...");

            summarizer.summarizeAndSave(conversationHistory, new ConversationSummarizer.SummarizeCallback() {
                @Override
                public void onResult(List<ChatMessage> compressedHistory) {
                    callback.onProgress("Summary saved, continuing conversation...");

                    // Replace conversation history with compressed version
                    conversationHistory.clear();
                    conversationHistory.addAll(compressedHistory);

                    // Reset current context tokens after compression (context is now clean)
                    // Session cumulative tokens are preserved
                    resetCurrentContext();

                    // Continue with compressed conversation
                    continueIteration(conversationHistory, callback);
                }

                @Override
                public void onError(Throwable error) {
                    Log.e(TAG, "Summarization failed, continuing with full history", error);
                    // Continue anyway with full history
                    continueIteration(conversationHistory, callback);
                }
            });
            return;
        }

        // Continue normal iteration
        continueIteration(conversationHistory, callback);
    }

    /**
     * Continue iteration with conversation (after optional summarization).
     */
    private void continueIteration(List<ChatMessage> conversationHistory, AgentCallback callback) {
        List<ChatMessage> contextMessages = new ArrayList<>();

        if (identityMessages != null) {
            contextMessages.addAll(identityMessages);
        }

        if (memoryContext != null) {
            String memoryCtx = memoryContext.buildMemoryContext();
            if (!memoryCtx.isEmpty()) {
                contextMessages.add(new ChatMessage(memoryCtx, ChatMessage.TYPE_SYSTEM));
                Log.d(TAG, "Added memory context: " + memoryCtx.length() + " chars");
            }
        }

        JsonArray tools = toolRegistry.getToolDefinitions();

        if (responseSchema != null) {
            sendStructuredMessage(conversationHistory, tools, contextMessages, callback);
        } else {
            sendStandardMessage(conversationHistory, tools, contextMessages, callback);
        }
    }

    /**
     * Send message using structured outputs API.
     */
    private void sendStructuredMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                       List<ChatMessage> contextMessages, AgentCallback callback) {
        apiService.sendMessageStructured(conversationHistory, tools, contextMessages, responseSchema,
                new LlmApiService.StructuredResponseCallback() {
            @Override
            public void onSuccess(LlmApiService.StructuredResponse response) {
                if (response.getUsage() != null && response.getUsage().isAvailable()) {
                    currentContextTokens = response.getUsage().getTotalTokens();
                    currentPromptTokens = response.getUsage().getPromptTokens();
                    currentCompletionTokens = response.getUsage().getCompletionTokens();

                    totalTokens += response.getUsage().getTotalTokens();
                    totalPromptTokens += response.getUsage().getPromptTokens();
                    totalCompletionTokens += response.getUsage().getCompletionTokens();

                    Log.d(TAG, "Token usage - Current context: " + currentContextTokens +
                          ", Session total: " + totalTokens);
                }

                if (response.isRefusal()) {
                    Log.w(TAG, "Model refused to respond: " + response.getRefusal());
                    handleRefusal(response, conversationHistory, callback);
                    return;
                }

                handleStructuredLlmResponse(response, conversationHistory, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Send message using standard API.
     */
    private void sendStandardMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                     List<ChatMessage> contextMessages, AgentCallback callback) {
        apiService.sendMessageWithTools(conversationHistory, tools, contextMessages, new LlmApiService.ChatCallbackWithTools() {
            @Override
            public void onSuccess(LlmApiService.LlmResponse response) {
                if (response.getUsage() != null && response.getUsage().isAvailable()) {
                    currentContextTokens = response.getUsage().getTotalTokens();
                    currentPromptTokens = response.getUsage().getPromptTokens();
                    currentCompletionTokens = response.getUsage().getCompletionTokens();

                    totalTokens += response.getUsage().getTotalTokens();
                    totalPromptTokens += response.getUsage().getPromptTokens();
                    totalCompletionTokens += response.getUsage().getCompletionTokens();

                    Log.d(TAG, "Token usage - Current context: " + currentContextTokens +
                          ", Session total: " + totalTokens);
                }

                handleLlmResponse(response, conversationHistory, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void handleLlmResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (response.hasToolCalls()) {
            handleToolCalls(response, conversationHistory, callback);
        } else {
            handleFinalResponse(response, conversationHistory, callback);
        }
    }

    private void handleToolCalls(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        List<LlmApiService.ToolCall> toolCalls = response.getToolCalls();

        Log.d(TAG, "LLM requested " + toolCalls.size() + " tool call(s)");
        callback.onProgress("Executing " + toolCalls.size() + " tool(s)...");

        ChatMessage toolCallMessage = ChatMessage.createToolCallMessage(toolCalls);
        conversationHistory.add(toolCallMessage);

        totalToolCalls += toolCalls.size();

        processToolCallsWithApproval(toolCalls, 0, conversationHistory, callback);
    }

    /**
     * Process tool calls sequentially, requesting approval when needed.
     */
    private void processToolCallsWithApproval(List<LlmApiService.ToolCall> toolCalls, int index,
                                               List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (index >= toolCalls.size()) {
            // All tool calls processed, continue the loop
            callback.onProgress("Sending tool results to LLM...");
            executeIteration(conversationHistory, callback);
            return;
        }

        LlmApiService.ToolCall toolCall = toolCalls.get(index);
        String toolName = toolCall.getName();
        JsonObject arguments = toolCall.getArguments();

        Log.d(TAG, "Processing tool: " + toolName + " with args: " + arguments.toString());
        callback.onToolCall(toolName, arguments.toString());

        boolean wantsBackground = arguments.has("background")
                && !arguments.get("background").isJsonNull()
                && arguments.get("background").getAsBoolean();
        boolean bgExecEnabled = settingsManager != null
                && settingsManager.getAgentConfig().isBackgroundExecEnabled();

        if (wantsBackground && bgExecEnabled) {
            JsonObject cleanedArgs = arguments.deepCopy();
            cleanedArgs.remove("background");

            dispatchBackgroundTool(toolCall, cleanedArgs, toolCalls, index, conversationHistory, callback);
            return;
        }

        // Check if this tool requires approval
        Tool tool = toolRegistry.getTool(toolName);
        ToolApprovalMode mode = getPerToolApprovalMode(toolName);

        // ALWAYS_REJECT: block immediately, no prompt
        if (mode == ToolApprovalMode.ALWAYS_REJECT) {
            String resultContent = "Tool execution blocked by per-tool setting";
            Log.d(TAG, "Tool " + toolName + " blocked (ALWAYS_REJECT)");
            callback.onToolResult(toolName, resultContent);

            ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                    toolCall.getId(),
                    toolName,
                    resultContent
            );
            conversationHistory.add(toolResultMessage);

            // Process next tool call
            processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
            return;
        }

        // Determine whether we need user approval
        boolean needsApproval = (mode == ToolApprovalMode.ALWAYS_APPROVE)
                ? false
                : requireApproval && tool != null && tool.requiresApproval();

        if (needsApproval) {
            // Build a normalised ExecPlan for exec-type tools (shell, etc.).
            // The approval dialog shows the plan's description — the canonical exe path,
            // tokenised argv, and plan hash — NOT the raw LLM-provided string.
            // This prevents prompt-injection where approval description differs from execution.
            ExecPlan execPlan = tool.buildExecPlan(arguments);
            String description = (execPlan != null)
                    ? execPlan.toApprovalDescription()
                    : tool.getApprovalDescription(arguments);

            callback.onApprovalRequired(toolName, description, arguments, new ApprovalCallback() {
                @Override
                public void onApproved() {
                    // Execute the tool and continue
                    executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback);
                }

                @Override
                public void onDenied() {
                    // Add denial result to history and continue
                    String resultContent = "Tool execution denied by user";
                    Log.d(TAG, "Tool " + toolName + " denied by user");
                    callback.onToolResult(toolName, resultContent);

                    ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                        toolCall.getId(),
                        toolName,
                        resultContent
                    );
                    conversationHistory.add(toolResultMessage);

                    // Process next tool call
                    processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
                }
            });
        } else {
            // Execute without approval
            executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback);
        }
    }

    /**
     * Dispatch a tool call to run asynchronously, bypassing the approval flow.
     * The user has already opted in via the background_exec agent setting, so re-prompting
     * for approval would defeat the purpose of fire-and-forget execution.
     */
    private void dispatchBackgroundTool(LlmApiService.ToolCall toolCall, JsonObject cleanedArgs,
                                        List<LlmApiService.ToolCall> toolCalls, int index,
                                        List<ChatMessage> conversationHistory, AgentCallback callback) {
        String toolName = toolCall.getName();

        android.content.Context context = null;
        try {
            context = toolRegistry.getContext();
        } catch (Exception e) {
            Log.w(TAG, "Could not get context for background dispatch, falling back to sync execution");
            executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback);
            return;
        }

        final android.content.Context finalContext = context;

        String processId = BackgroundProcessManager.getInstance(finalContext).submit(
                toolName,
                cleanedArgs.toString(),
                () -> toolRegistry.executeTool(toolName, cleanedArgs)
        );

        Log.d(TAG, "Tool " + toolName + " dispatched to background: process_id=" + processId);
        callback.onToolCall(toolName, cleanedArgs.toString());

        JsonObject bgResult = new JsonObject();
        bgResult.addProperty("process_id", processId);
        bgResult.addProperty("status", "running");
        bgResult.addProperty("message",
                "Tool '" + toolName + "' is running in background. "
                + "Use list_background_processes to check status or kill_background_process to cancel.");

        String resultContent = bgResult.toString();
        callback.onToolResult(toolName, resultContent);

        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                toolCall.getId(),
                toolName,
                resultContent
        );
        conversationHistory.add(toolResultMessage);

        processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
    }

    private void executeToolAndContinue(LlmApiService.ToolCall toolCall, List<LlmApiService.ToolCall> toolCalls,
                                        int index, List<ChatMessage> conversationHistory, AgentCallback callback) {
        String toolName = toolCall.getName();

        ToolResult result = toolRegistry.executeTool(toolName, toolCall.getArguments());

        String resultContent;
        if (result.isSuccess()) {
            resultContent = result.getContent();
            Log.d(TAG, "Tool " + toolName + " succeeded: " + resultContent);
        } else {
            resultContent = "Error: " + result.getError();
            Log.e(TAG, "Tool " + toolName + " failed: " + result.getError());
        }

        callback.onToolResult(toolName, resultContent);

        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
            toolCall.getId(),
            toolName,
            resultContent
        );
        conversationHistory.add(toolResultMessage);

        processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
    }

    private void handleFinalResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        String content = response.getContent();

        if (content == null || content.isEmpty()) {
            content = "No response from assistant.";
        }

        Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s)");

        ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(assistantMessage);

        detectAndAddFileReferences(content, conversationHistory);

        callback.onComplete(content, conversationHistory);
    }

    private void detectAndAddFileReferences(String content, List<ChatMessage> conversationHistory) {
        if (content == null) return;

        Matcher matcher = FILE_PATH_PATTERN.matcher(content);
        while (matcher.find()) {
            String rawPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (rawPath == null || rawPath.isEmpty()) continue;

            // Skip if it looks like a code example (has common code markers)
            if (rawPath.contains("```") || rawPath.contains("\n")) continue;

            String displayName = rawPath;
            String filePath = rawPath;
            String mimeType = null;

            // Resolve relative paths to absolute
            if (!rawPath.startsWith("/")) {
                // Try to find the file in workspace
                File workspaceFile = findFileInWorkspace(rawPath);
                if (workspaceFile != null && workspaceFile.exists()) {
                    filePath = workspaceFile.getAbsolutePath();
                    displayName = workspaceFile.getName();
                    mimeType = io.finett.droidclaw.filesystem.FileUploadManager.resolveMimeType(displayName);
                }
            } else if (rawPath.startsWith("/data/data/")) {
                // Absolute workspace path
                File file = new File(rawPath);
                if (file.exists()) {
                    displayName = file.getName();
                    mimeType = io.finett.droidclaw.filesystem.FileUploadManager.resolveMimeType(displayName);
                }
            }

            // Only create attachment message if file exists
            File file = new File(filePath);
            if (file.exists()) {
                ChatMessage attachmentMsg = ChatMessage.createAttachmentMessage(
                    filePath, displayName, mimeType);
                conversationHistory.add(attachmentMsg);
                Log.d(TAG, "Detected file reference: " + displayName);
            }
        }
    }

    private File findFileInWorkspace(String relativePath) {
        String[] searchDirs = {
            "uploads",
            "home",
            "home/documents",
            "home/scripts",
            "home/notes",
            "tmp"
        };

        File workspaceRoot = getWorkspaceRoot();
        if (workspaceRoot != null) {
            File directFile = new File(workspaceRoot, relativePath);
            if (directFile.exists()) {
                return directFile;
            }

            // Search in standard directories
            for (String dir : searchDirs) {
                File file = new File(workspaceRoot, dir + "/" + relativePath);
                if (file.exists()) {
                    return file;
                }
            }
        }

        return null;
    }

    private File getWorkspaceRoot() {
        try {
            return toolRegistry.getWorkspaceRoot();
        } catch (Exception e) {
            return null;
        }
    }

    private void handleStructuredLlmResponse(LlmApiService.StructuredResponse response,
                                              List<ChatMessage> conversationHistory,
                                              AgentCallback callback) {
        if (response.hasToolCalls()) {
            LlmApiService.LlmResponse llmResponse = new LlmApiService.LlmResponse(
                    response.getContent(), response.getToolCalls(), response.getUsage());
            handleToolCalls(llmResponse, conversationHistory, callback);
        } else {
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                content = "No response from assistant.";
            }

            Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s) (structured output)");

            ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
            conversationHistory.add(assistantMessage);

            detectAndAddFileReferences(content, conversationHistory);

            callback.onComplete(content, conversationHistory);
        }
    }

    private void handleRefusal(LlmApiService.StructuredResponse response,
                               List<ChatMessage> conversationHistory,
                               AgentCallback callback) {
        String refusalMessage = response.getRefusal() != null ? response.getRefusal() : "Model refused to respond";

        Log.w(TAG, "Handling refusal: " + refusalMessage);

        ChatMessage refusalMsg = new ChatMessage("Refusal: " + refusalMessage, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(refusalMsg);

        callback.onComplete("[REFUSAL] " + refusalMessage, conversationHistory);
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void reset() {
        iterationCount = 0;
        resetCurrentContext();
        totalTokens = 0;
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
        totalToolCalls = 0;
        Log.d(TAG, "Agent loop fully reset (all counters cleared)");
    }

    public int getCurrentContextTokens() {
        return currentContextTokens;
    }

    public int getCurrentPromptTokens() {
        return currentPromptTokens;
    }

    public int getCurrentCompletionTokens() {
        return currentCompletionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public int getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public int getTotalToolCalls() {
        return totalToolCalls;
    }

    public void resetCurrentContext() {
        currentContextTokens = 0;
        currentPromptTokens = 0;
        currentCompletionTokens = 0;
        Log.d(TAG, "Current context tokens reset (session totals preserved)");
    }

}