package io.finett.droidclaw.agent;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.repository.MemoryRepository;

/**
 * AgentLoop handles the iterative tool-calling workflow.
 *
 * Flow:
 * 1. User sends a message
 * 2. LLM responds (may include tool calls)
 * 3. If tool calls exist, execute them (with approval if required)
 * 4. Send tool results back to LLM
 * 5. Repeat until LLM provides final text response
 */
public class AgentLoop {
    private static final String TAG = "AgentLoop";
    private static final int DEFAULT_MAX_ITERATIONS = 20;

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

    /**
     * Callback interface for agent events.
     */
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
    
    /**
     * Callback for tool approval decisions.
     */
    public interface ApprovalCallback {
        void onApproved();
        void onDenied();
    }

    /**
     * Creates an AgentLoop without settings (for backwards compatibility).
     * Uses default values for max iterations and approval.
     */
    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry) {
        this(apiService, toolRegistry, null, null, null);
    }
    
    /**
     * Creates an AgentLoop with settings for configuration.
     */
    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager) {
        this(apiService, toolRegistry, settingsManager, null, null);
    }
    
    /**
     * Creates an AgentLoop with full memory support.
     */
    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager,
                     ConversationSummarizer summarizer, MemoryContextBuilder memoryContext) {
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
        this.summarizer = summarizer;
        this.memoryContext = memoryContext;
        this.iterationCount = 0;
        
        // Load settings
        if (settingsManager != null) {
            this.maxIterations = settingsManager.getMaxAgentIterations();
            this.requireApproval = settingsManager.isRequireApproval();
        } else {
            this.maxIterations = DEFAULT_MAX_ITERATIONS;
            this.requireApproval = true; // Default to requiring approval
        }
    }

    /**
     * Sets the identity context (soul.md and user.md) to be prepended to conversations.
     *
     * @param identityMessages List of system messages containing identity context
     */
    public void setIdentityContext(List<ChatMessage> identityMessages) {
        this.identityMessages = identityMessages;
        Log.d(TAG, "Identity context set: " + (identityMessages != null ? identityMessages.size() : 0) + " message(s)");
    }

    /**
     * Sets a JSON Schema for Structured Outputs.
     * When set, the model's final text response will adhere to the provided schema.
     *
     * @param schema JSON Schema for Structured Outputs (null to disable)
     */
    public void setResponseSchema(JsonObject schema) {
        this.responseSchema = schema;
        Log.d(TAG, "Response schema set: " + (schema != null ? "enabled" : "disabled"));
    }

    /**
     * Start the agent loop with a user message.
     *
     * @param conversationHistory Full conversation history including the new user message
     * @param callback Callback for progress and completion
     */
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
        // Build context messages (identity + memory)
        List<ChatMessage> contextMessages = new ArrayList<>();

        // Add identity messages
        if (identityMessages != null) {
            contextMessages.addAll(identityMessages);
        }

        // Add memory context (if available)
        if (memoryContext != null) {
            String memoryCtx = memoryContext.buildMemoryContext();
            if (!memoryCtx.isEmpty()) {
                contextMessages.add(new ChatMessage(memoryCtx, ChatMessage.TYPE_SYSTEM));
                Log.d(TAG, "Added memory context: " + memoryCtx.length() + " chars");
            }
        }

        // Get tool definitions
        JsonArray tools = toolRegistry.getToolDefinitions();

        // Use structured outputs if a schema is set
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
                // Update token tracking
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

                // Check for refusal
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
                // Update token tracking using "Last Usage" algorithm
                if (response.getUsage() != null && response.getUsage().isAvailable()) {
                    // Current context (from last API response - this is the ACTUAL context size)
                    currentContextTokens = response.getUsage().getTotalTokens();
                    currentPromptTokens = response.getUsage().getPromptTokens();
                    currentCompletionTokens = response.getUsage().getCompletionTokens();

                    // Session cumulative stats (total spent across all requests)
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

    /**
     * Handle the response from the LLM.
     */
    private void handleLlmResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (response.hasToolCalls()) {
            // LLM wants to call tools
            handleToolCalls(response, conversationHistory, callback);
        } else {
            // LLM provided final text response
            handleFinalResponse(response, conversationHistory, callback);
        }
    }

    /**
     * Handle tool calls from the LLM.
     */
    private void handleToolCalls(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        List<LlmApiService.ToolCall> toolCalls = response.getToolCalls();
        
        Log.d(TAG, "LLM requested " + toolCalls.size() + " tool call(s)");
        callback.onProgress("Executing " + toolCalls.size() + " tool(s)...");

        // Add assistant message with tool calls to history
        ChatMessage toolCallMessage = ChatMessage.createToolCallMessage(toolCalls);
        conversationHistory.add(toolCallMessage);
        
        // Increment tool call counter
        totalToolCalls += toolCalls.size();

        // Process tool calls sequentially with approval support
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
        
        // Check if this tool requires approval
        Tool tool = toolRegistry.getTool(toolName);
        boolean needsApproval = requireApproval && tool != null && tool.requiresApproval();
        
        if (needsApproval) {
            // Request approval from user
            String description = tool.getApprovalDescription(arguments);
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
     * Execute a tool and continue processing remaining tool calls.
     */
    private void executeToolAndContinue(LlmApiService.ToolCall toolCall, List<LlmApiService.ToolCall> toolCalls,
                                        int index, List<ChatMessage> conversationHistory, AgentCallback callback) {
        String toolName = toolCall.getName();
        
        // Execute the tool
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

        // Add tool result to conversation history
        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
            toolCall.getId(),
            toolName,
            resultContent
        );
        conversationHistory.add(toolResultMessage);
        
        // Process next tool call
        processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
    }

    /**
     * Handle final text response from the LLM.
     */
    private void handleFinalResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        String content = response.getContent();

        if (content == null || content.isEmpty()) {
            content = "No response from assistant.";
        }

        Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s)");

        // Add final assistant response to history
        ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(assistantMessage);

        callback.onComplete(content, conversationHistory);
    }

    /**
     * Handle a structured response from the LLM (with schema adherence).
     * Processes tool calls or treats content as final response.
     */
    private void handleStructuredLlmResponse(LlmApiService.StructuredResponse response,
                                              List<ChatMessage> conversationHistory,
                                              AgentCallback callback) {
        if (response.hasToolCalls()) {
            // Convert StructuredResponse to LlmResponse for tool handling
            LlmApiService.LlmResponse llmResponse = new LlmApiService.LlmResponse(
                    response.getContent(), response.getToolCalls(), response.getUsage());
            handleToolCalls(llmResponse, conversationHistory, callback);
        } else {
            // Final text response - content adheres to the schema
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                content = "No response from assistant.";
            }

            Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s) (structured output)");

            ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
            conversationHistory.add(assistantMessage);

            callback.onComplete(content, conversationHistory);
        }
    }

    /**
     * Handle a refusal from the LLM when using Structured Outputs.
     */
    private void handleRefusal(LlmApiService.StructuredResponse response,
                               List<ChatMessage> conversationHistory,
                               AgentCallback callback) {
        String refusalMessage = response.getRefusal() != null ? response.getRefusal() : "Model refused to respond";

        Log.w(TAG, "Handling refusal: " + refusalMessage);

        // Add refusal to conversation history
        ChatMessage refusalMsg = new ChatMessage("Refusal: " + refusalMessage, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(refusalMsg);

        // Notify callback of completion with refusal indicator
        callback.onComplete("[REFUSAL] " + refusalMessage, conversationHistory);
    }

    /**
     * Get the current iteration count.
     */
    public int getIterationCount() {
        return iterationCount;
    }

    /**
     * Reset the iteration count.
     */
    public void reset() {
        iterationCount = 0;
    }
    
    // Token tracking getters - "Last Usage" algorithm
    
    /**
     * Get current context tokens (from last API response).
     * This represents the ACTUAL context size.
     */
    public int getCurrentContextTokens() {
        return currentContextTokens;
    }
    
    public int getCurrentPromptTokens() {
        return currentPromptTokens;
    }
    
    public int getCurrentCompletionTokens() {
        return currentCompletionTokens;
    }
    
    /**
     * Get session cumulative tokens (total spent across all requests).
     */
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
    
    /**
     * Reset all token counters (current context and session cumulative).
     * Called when starting a new session or clearing context.
     */
    public void resetTokens() {
        currentContextTokens = 0;
        currentPromptTokens = 0;
        currentCompletionTokens = 0;
        totalTokens = 0;
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
        totalToolCalls = 0;
        Log.d(TAG, "Token counters reset");
    }
    
    /**
     * Reset only current context tokens (called after compression).
     * Session cumulative tokens are preserved.
     */
    public void resetCurrentContext() {
        currentContextTokens = 0;
        currentPromptTokens = 0;
        currentCompletionTokens = 0;
        Log.d(TAG, "Current context tokens reset (session totals preserved)");
    }
    
    /**
     * Set token values from saved session.
     * Used when restoring a session.
     */
    public void setTokensFromSession(int currentContext, int currentPrompt, int currentCompletion,
                                      int total, int totalPrompt, int totalCompletion, int toolCalls) {
        this.currentContextTokens = currentContext;
        this.currentPromptTokens = currentPrompt;
        this.currentCompletionTokens = currentCompletion;
        this.totalTokens = total;
        this.totalPromptTokens = totalPrompt;
        this.totalCompletionTokens = totalCompletion;
        this.totalToolCalls = toolCalls;
        Log.d(TAG, "Token counters restored from session");
    }
}