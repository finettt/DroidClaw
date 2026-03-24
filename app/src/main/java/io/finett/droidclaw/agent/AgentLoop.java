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
    private int iterationCount;
    private int maxIterations;
    private boolean requireApproval;

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
        this(apiService, toolRegistry, null);
    }
    
    /**
     * Creates an AgentLoop with settings for configuration.
     */
    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager) {
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
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

        // Get tool definitions
        JsonArray tools = toolRegistry.getToolDefinitions();

        // Send message to LLM with tools
        apiService.sendMessageWithTools(conversationHistory, tools, new LlmApiService.ChatCallbackWithTools() {
            @Override
            public void onSuccess(LlmApiService.LlmResponse response) {
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
}