package io.finett.droidclaw.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.api.LlmApiService;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;
    public static final int TYPE_TOOL_CALL = 2;
    public static final int TYPE_TOOL_RESULT = 3;
    public static final int TYPE_SYSTEM = 4;

    private String content;
    private int type;
    private long timestamp;
    
    // For tool-related messages
    private List<LlmApiService.ToolCall> toolCalls;
    private String toolCallId;
    private String toolName;

    public ChatMessage(String content, int type) {
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Create a message with tool calls (from assistant).
     */
    public static ChatMessage createToolCallMessage(List<LlmApiService.ToolCall> toolCalls) {
        ChatMessage message = new ChatMessage(null, TYPE_TOOL_CALL);
        message.toolCalls = toolCalls;
        return message;
    }

    /**
     * Create a tool result message.
     */
    public static ChatMessage createToolResultMessage(String toolCallId, String toolName, String result) {
        ChatMessage message = new ChatMessage(result, TYPE_TOOL_RESULT);
        message.toolCallId = toolCallId;
        message.toolName = toolName;
        return message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isUser() {
        return type == TYPE_USER;
    }

    public boolean isAssistant() {
        return type == TYPE_ASSISTANT;
    }

    public boolean isToolCall() {
        return type == TYPE_TOOL_CALL;
    }

    public boolean isToolResult() {
        return type == TYPE_TOOL_RESULT;
    }

    public List<LlmApiService.ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<LlmApiService.ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isSystem() {
        return type == TYPE_SYSTEM;
    }

    /**
     * Convert this message to the API format required by OpenAI.
     *
     * @return JsonObject representing the message
     */
    public JsonObject toApiMessage() {
        JsonObject message = new JsonObject();

        switch (type) {
            case TYPE_SYSTEM:
                message.addProperty("role", "system");
                message.addProperty("content", content);
                break;

            case TYPE_USER:
                message.addProperty("role", "user");
                message.addProperty("content", content);
                break;

            case TYPE_ASSISTANT:
                message.addProperty("role", "assistant");
                if (content != null) {
                    message.addProperty("content", content);
                }
                break;

            case TYPE_TOOL_CALL:
                // Assistant message with tool calls
                message.addProperty("role", "assistant");
                message.add("content", com.google.gson.JsonNull.INSTANCE);
                
                JsonArray toolCallsArray = new JsonArray();
                if (toolCalls != null) {
                    for (LlmApiService.ToolCall toolCall : toolCalls) {
                        JsonObject toolCallObj = new JsonObject();
                        toolCallObj.addProperty("id", toolCall.getId());
                        toolCallObj.addProperty("type", "function");
                        
                        JsonObject function = new JsonObject();
                        function.addProperty("name", toolCall.getName());
                        function.addProperty("arguments", toolCall.getArguments().toString());
                        
                        toolCallObj.add("function", function);
                        toolCallsArray.add(toolCallObj);
                    }
                }
                message.add("tool_calls", toolCallsArray);
                break;

            case TYPE_TOOL_RESULT:
                // Tool result message
                message.addProperty("role", "tool");
                message.addProperty("tool_call_id", toolCallId);
                message.addProperty("content", content != null ? content : "");
                break;
        }

        return message;
    }
}