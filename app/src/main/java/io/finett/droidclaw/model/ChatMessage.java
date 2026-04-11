package io.finett.droidclaw.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;
    public static final int TYPE_TOOL_CALL = 2;
    public static final int TYPE_TOOL_RESULT = 3;
    public static final int TYPE_SYSTEM = 4;
    public static final int TYPE_CONTEXT_CARD = 5;
    public static final int TYPE_ATTACHMENT = 6;

    private String content;
    private int type;
    private long timestamp;

    // For tool-related messages
    private List<LlmApiService.ToolCall> toolCalls;
    private String toolCallId;
    private String toolName;

    // For attachment messages (user uploads and agent file references)
    private List<FileAttachment> attachments;
    private String filePath;    // For TYPE_ATTACHMENT: path to agent-referenced file
    private String fileMimeType; // For TYPE_ATTACHMENT
    private String displayName; // For TYPE_ATTACHMENT: friendly name to display

    // For context card messages
    private boolean isContextCard;
    private String contextType; // "heartbeat", "cron", "manual"
    private String originalTaskId; // Links to the original task

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

    public boolean isContextCard() {
        return isContextCard;
    }

    public void setIsContextCard(boolean isContextCard) {
        this.isContextCard = isContextCard;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public String getOriginalTaskId() {
        return originalTaskId;
    }

    public void setOriginalTaskId(String originalTaskId) {
        this.originalTaskId = originalTaskId;
    }

    // Attachment-related methods

    public List<FileAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<FileAttachment> attachments) {
        this.attachments = attachments;
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean isAttachment() {
        return type == TYPE_ATTACHMENT;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileMimeType() {
        return fileMimeType;
    }

    public void setFileMimeType(String fileMimeType) {
        this.fileMimeType = fileMimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Creates a user message with file attachments.
     */
    public static ChatMessage createUserMessageWithAttachments(String content, List<FileAttachment> attachments) {
        ChatMessage message = new ChatMessage(content, TYPE_USER);
        message.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
        return message;
    }

    /**
     * Creates an attachment message for files referenced by the agent.
     */
    public static ChatMessage createAttachmentMessage(String filePath, String displayName, String mimeType) {
        ChatMessage message = new ChatMessage(null, TYPE_ATTACHMENT);
        message.filePath = filePath;
        message.displayName = displayName;
        message.fileMimeType = mimeType;
        return message;
    }

    /**
     * Create a context card message for task result continuation.
     */
    public static ChatMessage createContextCardMessage(TaskResult taskResult) {
        ChatMessage message = new ChatMessage(taskResult.getContent(), TYPE_CONTEXT_CARD);
        message.isContextCard = true;
        message.contextType = TaskResult.typeToString(taskResult.getType()).toLowerCase();
        message.originalTaskId = taskResult.getId();
        message.timestamp = taskResult.getTimestamp();
        return message;
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
                if (hasAttachments()) {
                    // Build multipart content
                    message.add("content", buildUserContentWithAttachments());
                } else {
                    message.addProperty("content", content);
                }
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

            case TYPE_CONTEXT_CARD:
                // Context card - treat as system message with task context
                message.addProperty("role", "system");
                String contextContent = "[Task Context: " + contextType + "]\n" +
                                       (content != null ? content : "");
                message.addProperty("content", contextContent);
                break;

            case TYPE_ATTACHMENT:
                // Agent-referenced file - send as user message with file info
                message.addProperty("role", "user");
                String attachmentText = "File: `" + (displayName != null ? displayName : filePath) + "`\n" +
                        "The agent mentioned or produced this file. It is available at: " +
                        (filePath != null ? filePath : "unknown path");
                message.addProperty("content", attachmentText);
                break;
        }

        return message;
    }

    /**
     * Builds multipart content array for user messages with attachments.
     * Creates an array with text content and image_url content for images.
     */
    private JsonArray buildUserContentWithAttachments() {
        JsonArray contentArray = new JsonArray();

        // Add text content first
        if (content != null && !content.isEmpty()) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("type", "text");
            textPart.addProperty("text", content);
            contentArray.add(textPart);
        }

        // Add each attachment as appropriate content part
        for (FileAttachment attachment : attachments) {
            if (attachment.isImage()) {
                // For images: use base64 data URL for vision-capable models
                String base64Data = encodeFileToBase64(attachment.getAbsolutePath());
                if (base64Data != null) {
                    JsonObject imagePart = new JsonObject();
                    imagePart.addProperty("type", "image_url");

                    JsonObject imageUrl = new JsonObject();
                    imageUrl.addProperty("url", "data:" + attachment.getMimeType() + ";base64," + base64Data);
                    imagePart.add("image_url", imageUrl);

                    contentArray.add(imagePart);
                }

                // Always add text reference for the image
                JsonObject textRef = new JsonObject();
                textRef.addProperty("type", "text");
                textRef.addProperty("text", "`" + attachment.getOriginalName() +
                        "` file attached by user, you can find it in uploads");
                contentArray.add(textRef);
            } else {
                // Non-image files: add text reference
                String fileRef = "`" + attachment.getOriginalName() +
                        "` file attached by user, you can find it in uploads";
                JsonObject textPart = new JsonObject();
                textPart.addProperty("type", "text");
                textPart.addProperty("text", fileRef);
                contentArray.add(textPart);
            }
        }

        // If no content parts were added, add plain text
        if (contentArray.size() == 0) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("type", "text");
            textPart.addProperty("text", content != null ? content : "");
            contentArray.add(textPart);
        }

        return contentArray;
    }

    /**
     * Encodes a file's content to base64 string.
     */
    private static String encodeFileToBase64(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                android.util.Log.w("ChatMessage", "File not found for base64 encoding: " + filePath);
                return null;
            }
            byte[] fileContent = new byte[(int) file.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int bytesRead = fis.read(fileContent);
                if (bytesRead != fileContent.length) {
                    android.util.Log.w("ChatMessage", "Could not read entire file: " + filePath);
                    return null;
                }
            }
            return android.util.Base64.encodeToString(fileContent, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("ChatMessage", "Failed to encode file to base64: " + filePath, e);
            return null;
        }
    }
}