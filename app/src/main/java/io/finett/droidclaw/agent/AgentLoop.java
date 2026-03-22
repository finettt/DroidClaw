package io.finett.droidclaw.agent;

import android.util.Log;

import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

/**
 * AgentLoop handles the iterative tool-calling workflow.
 * 
 * Flow:
 * 1. User sends a message
 * 2. LLM responds (may include tool calls)
 * 3. If tool calls exist, execute them
 * 4. Send tool results back to LLM
 * 5. Repeat until LLM provides final text response
 */
public class AgentLoop {
    private static final String TAG = "AgentLoop";
    private static final int MAX_ITERATIONS = 20;

    private final LlmApiService apiService;
    private final ToolRegistry toolRegistry;
    private int iterationCount;

    public interface AgentCallback {
        void onProgress(String status);
        void onToolCall(String toolName, String arguments);
        void onToolResult(String toolName, String result);
        void onComplete(String finalResponse, List<ChatMessage> conversationHistory);
        void onError(String error);
    }

    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry) {
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.iterationCount = 0;
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
        
        if (iterationCount > MAX_ITERATIONS) {
            callback.onError("Maximum iterations reached. The agent may be stuck in a loop.");
            return;
        }

        Log.d(TAG, "Iteration " + iterationCount + "/" + MAX_ITERATIONS);

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

        // Execute each tool call and add results to history
        for (LlmApiService.ToolCall toolCall : toolCalls) {
            String toolName = toolCall.getName();
            String arguments = toolCall.getArguments().toString();
            
            Log.d(TAG, "Executing tool: " + toolName + " with args: " + arguments);
            callback.onToolCall(toolName, arguments);

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
        }

        // Continue the loop - send tool results back to LLM
        callback.onProgress("Sending tool results to LLM...");
        executeIteration(conversationHistory, callback);
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