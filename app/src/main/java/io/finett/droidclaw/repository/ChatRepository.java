package io.finett.droidclaw.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.SessionType;

public class ChatRepository {
    private static final String TAG = "ChatRepository";
    private static final String PREFS_NAME = "chat_messages";
    private static final String KEY_PREFIX = "session_";
    private static final String KEY_SESSIONS = "chat_sessions";

    private final SharedPreferences prefs;

    /**
     * Callback interface for async title generation.
     */
    public interface TitleGenerationCallback {
        void onTitleGenerated(String title);
        void onError(String error);
    }

    public ChatRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // ==================== SESSION PERSISTENCE ====================
    
    /**
     * Save all chat sessions to SharedPreferences
     */
    public void saveSessions(List<ChatSession> sessions) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ChatSession session : sessions) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", session.getId());
                jsonObject.put("title", session.getTitle());
                jsonObject.put("updatedAt", session.getUpdatedAt());
                
                // Save current context tokens (Last Usage algorithm)
                jsonObject.put("currentContextTokens", session.getCurrentContextTokens());
                jsonObject.put("currentPromptTokens", session.getCurrentPromptTokens());
                jsonObject.put("currentCompletionTokens", session.getCurrentCompletionTokens());
                
                // Save session cumulative tokens
                jsonObject.put("totalTokens", session.getTotalTokens());
                jsonObject.put("totalPromptTokens", session.getTotalPromptTokens());
                jsonObject.put("totalCompletionTokens", session.getTotalCompletionTokens());
                jsonObject.put("totalToolCalls", session.getTotalToolCalls());

                // Save session type and visibility
                jsonObject.put("sessionType", session.getSessionType());
                if (session.getParentTaskId() != null) {
                    jsonObject.put("parentTaskId", session.getParentTaskId());
                }

                jsonArray.put(jsonObject);
            }
            
            prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply();
            Log.d(TAG, "Saved " + sessions.size() + " sessions");
        } catch (JSONException e) {
            Log.e(TAG, "Error saving sessions", e);
        }
    }
    
    /**
     * Load all chat sessions from SharedPreferences
     * Returns sessions sorted by updatedAt (newest first)
     */
    public List<ChatSession> loadSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        
        try {
            String jsonString = prefs.getString(KEY_SESSIONS, null);
            
            if (jsonString == null || jsonString.isEmpty()) {
                Log.d(TAG, "No saved sessions found");
                return sessions;
            }
            
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                
                String id = jsonObject.getString("id");
                String title = jsonObject.getString("title");
                long updatedAt = jsonObject.getLong("updatedAt");
                
                ChatSession session = new ChatSession(id, title, updatedAt);
                
                // Load current context tokens (Last Usage algorithm)
                session.setCurrentContextTokens(jsonObject.optInt("currentContextTokens", 0));
                session.setCurrentPromptTokens(jsonObject.optInt("currentPromptTokens", 0));
                session.setCurrentCompletionTokens(jsonObject.optInt("currentCompletionTokens", 0));
                
                // Load session cumulative tokens
                session.setTotalTokens(jsonObject.optInt("totalTokens", 0));
                session.setTotalPromptTokens(jsonObject.optInt("totalPromptTokens", 0));
                session.setTotalCompletionTokens(jsonObject.optInt("totalCompletionTokens", 0));
                session.setTotalToolCalls(jsonObject.optInt("totalToolCalls", 0));

                // Load session type and visibility
                session.setSessionType(jsonObject.optInt("sessionType", SessionType.NORMAL));
                if (jsonObject.has("parentTaskId") && !jsonObject.isNull("parentTaskId")) {
                    session.setParentTaskId(jsonObject.getString("parentTaskId"));
                }

                sessions.add(session);
            }
            
            // Sort by updatedAt descending (newest first)
            Collections.sort(sessions, (s1, s2) -> Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt()));
            
            Log.d(TAG, "Loaded " + sessions.size() + " sessions");
        } catch (JSONException e) {
            Log.e(TAG, "Error loading sessions - clearing corrupted data", e);
            prefs.edit().remove(KEY_SESSIONS).apply();
        }
        
        return sessions;
    }
    
    /**
     * Delete a session and its associated messages (cascade delete)
     */
    public void deleteSession(String sessionId, List<ChatSession> remainingSessions) {
        // Delete messages first
        deleteMessages(sessionId);
        
        // Save the updated sessions list
        saveSessions(remainingSessions);
        
        Log.d(TAG, "Deleted session and messages for: " + sessionId);
    }
    
    /**
     * Update session metadata (title and timestamp)
     */
    public void updateSession(String sessionId, String newTitle, long newTimestamp, List<ChatSession> allSessions) {
        for (ChatSession session : allSessions) {
            if (session.getId().equals(sessionId)) {
                session.setTitle(newTitle);
                session.setUpdatedAt(newTimestamp);
                break;
            }
        }
        saveSessions(allSessions);
        Log.d(TAG, "Updated session: " + sessionId + " with title: " + newTitle);
    }
    
    /**
     * Generate a title from the first message content
     * Truncates at 30 characters, trying to break at word boundary
     */
    public String generateTitleFromMessage(String messageContent) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return "New Chat";
        }
        
        String trimmed = messageContent.trim();
        if (trimmed.length() <= 30) {
            return trimmed;
        }
        
        // Try to find a word boundary before 30 chars
        int lastSpace = trimmed.lastIndexOf(' ', 30);
        if (lastSpace > 15) {
            return trimmed.substring(0, lastSpace) + "...";
        }
        
        return trimmed.substring(0, 27) + "...";
    }

    /**
     * Generate a chat title using the LLM to summarize the conversation.
     * Falls back to generateTitleFromMessage if the LLM call fails.
     *
     * @param apiService   The LLM API service
     * @param messages     The conversation messages to summarize
     * @param fallbackTitle Title to use if LLM generation fails
     * @param callback     Callback for the generated title
     */
    public void generateTitleWithLLM(LlmApiService apiService, List<ChatMessage> messages,
                                     String fallbackTitle, TitleGenerationCallback callback) {
        if (messages == null || messages.isEmpty()) {
            callback.onTitleGenerated(fallbackTitle);
            return;
        }

        // Build a concise message list for title generation (first user message + first assistant response)
        List<ChatMessage> titleMessages = new ArrayList<>();

        // Find the first user message
        ChatMessage firstUserMessage = null;
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_USER) {
                firstUserMessage = msg;
                break;
            }
        }

        if (firstUserMessage == null) {
            callback.onTitleGenerated(fallbackTitle);
            return;
        }

        // Create a system prompt for title generation
        ChatMessage systemPrompt = new ChatMessage(
            "Generate a concise, descriptive title for this conversation. " +
            "The title must be at most 50 characters. " +
            "Respond with ONLY the title text, nothing else.",
            ChatMessage.TYPE_SYSTEM
        );
        titleMessages.add(systemPrompt);
        titleMessages.add(new ChatMessage(firstUserMessage.getContent(), ChatMessage.TYPE_USER));

        // Use low temperature for deterministic output
        apiService.sendMessage(titleMessages, new LlmApiService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                if (response != null && !response.isEmpty() && !response.equals("No response received")) {
                    String title = response.trim();
                    // Remove surrounding quotes if present
                    if (title.startsWith("\"") && title.endsWith("\"")) {
                        title = title.substring(1, title.length() - 1);
                    }
                    // Enforce 50 character limit
                    if (title.length() > 50) {
                        int lastSpace = title.lastIndexOf(' ', 47);
                        if (lastSpace > 20) {
                            title = title.substring(0, lastSpace);
                        } else {
                            title = title.substring(0, 47) + "...";
                        }
                    }
                    if (title.isEmpty()) {
                        title = fallbackTitle;
                    }
                    Log.d(TAG, "LLM-generated title: " + title);
                    callback.onTitleGenerated(title);
                } else {
                    Log.w(TAG, "LLM returned empty response, using fallback");
                    callback.onTitleGenerated(fallbackTitle);
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "LLM title generation failed: " + error + ", using fallback");
                callback.onTitleGenerated(fallbackTitle);
            }
        });
    }

    // ==================== MESSAGE PERSISTENCE ====================

    /**
     * Save messages for a specific chat session
     */
    public void saveMessages(String sessionId, List<ChatMessage> messages) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ChatMessage message : messages) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("content", message.getContent());
                jsonObject.put("type", message.getType());
                jsonObject.put("timestamp", message.getTimestamp());
                
                // Save tool-related fields
                if (message.getType() == ChatMessage.TYPE_TOOL_CALL) {
                    if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                        JSONArray toolCallsArray = new JSONArray();
                        for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                            JSONObject tcObj = new JSONObject();
                            tcObj.put("id", toolCall.getId());
                            tcObj.put("name", toolCall.getName());
                            tcObj.put("arguments", toolCall.getArguments().toString());
                            toolCallsArray.put(tcObj);
                        }
                        jsonObject.put("toolCalls", toolCallsArray);
                        Log.d(TAG, "Saving tool call message with " + message.getToolCalls().size() + " tool calls");
                    } else {
                        Log.w(TAG, "Tool call message has null or empty toolCalls list!");
                    }
                }

                if (message.getType() == ChatMessage.TYPE_TOOL_RESULT) {
                    if (message.getToolCallId() != null) {
                        jsonObject.put("toolCallId", message.getToolCallId());
                    }
                    if (message.getToolName() != null) {
                        jsonObject.put("toolName", message.getToolName());
                    }
                }

                // Save context card fields
                if (message.getType() == ChatMessage.TYPE_CONTEXT_CARD) {
                    jsonObject.put("isContextCard", message.isContextCard());
                    if (message.getContextType() != null) {
                        jsonObject.put("contextType", message.getContextType());
                    }
                    if (message.getOriginalTaskId() != null) {
                        jsonObject.put("originalTaskId", message.getOriginalTaskId());
                    }
                }

                // Save attachment fields (user messages with file uploads)
                if (message.hasAttachments()) {
                    JSONArray attachmentsArray = new JSONArray();
                    for (io.finett.droidclaw.model.FileAttachment attachment : message.getAttachments()) {
                        JSONObject attObj = new JSONObject();
                        attObj.put("filename", attachment.getFilename());
                        attObj.put("originalName", attachment.getOriginalName());
                        attObj.put("absolutePath", attachment.getAbsolutePath());
                        attObj.put("mimeType", attachment.getMimeType());
                        attachmentsArray.put(attObj);
                    }
                    jsonObject.put("attachments", attachmentsArray);
                }

                // Save TYPE_ATTACHMENT fields
                if (message.getType() == ChatMessage.TYPE_ATTACHMENT) {
                    if (message.getFilePath() != null) {
                        jsonObject.put("filePath", message.getFilePath());
                    }
                    if (message.getFileMimeType() != null) {
                        jsonObject.put("fileMimeType", message.getFileMimeType());
                    }
                    if (message.getDisplayName() != null) {
                        jsonObject.put("displayName", message.getDisplayName());
                    }
                }

                jsonArray.put(jsonObject);
            }
            
            String key = KEY_PREFIX + sessionId;
            prefs.edit().putString(key, jsonArray.toString()).apply();
            
            Log.d(TAG, "Saved " + messages.size() + " messages for session: " + sessionId);
        } catch (JSONException e) {
            Log.e(TAG, "Error saving messages for session: " + sessionId, e);
        }
    }

    /**
     * Load messages for a specific chat session
     */
    public List<ChatMessage> loadMessages(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        
        try {
            String key = KEY_PREFIX + sessionId;
            String jsonString = prefs.getString(key, null);
            
            if (jsonString == null || jsonString.isEmpty()) {
                Log.d(TAG, "No saved messages found for session: " + sessionId);
                return messages;
            }
            
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    
                    // Handle content - optString returns "null" string if key doesn't exist
                    String content = jsonObject.has("content") && !jsonObject.isNull("content")
                        ? jsonObject.getString("content")
                        : null;
                    int type = jsonObject.getInt("type");
                    long timestamp = jsonObject.getLong("timestamp");
                    
                    ChatMessage message;

                    if (type == ChatMessage.TYPE_TOOL_CALL && jsonObject.has("toolCalls")) {
                        // Restore tool calls
                        JSONArray toolCallsArray = jsonObject.getJSONArray("toolCalls");
                        List<LlmApiService.ToolCall> toolCalls = new ArrayList<>();
                        for (int j = 0; j < toolCallsArray.length(); j++) {
                            JSONObject tcObj = toolCallsArray.getJSONObject(j);
                            String id = tcObj.getString("id");
                            String name = tcObj.getString("name");
                            String argsStr = tcObj.getString("arguments");
                            JsonObject arguments = JsonParser.parseString(argsStr).getAsJsonObject();
                            toolCalls.add(new LlmApiService.ToolCall(id, name, arguments));
                        }
                        message = ChatMessage.createToolCallMessage(toolCalls);
                        Log.d(TAG, "Restored tool call message with " + toolCalls.size() + " tool calls");
                    } else if (type == ChatMessage.TYPE_TOOL_RESULT) {
                        // Restore tool result
                        String toolCallId = jsonObject.has("toolCallId") && !jsonObject.isNull("toolCallId")
                            ? jsonObject.getString("toolCallId")
                            : null;
                        String toolName = jsonObject.has("toolName") && !jsonObject.isNull("toolName")
                            ? jsonObject.getString("toolName")
                            : null;
                        message = ChatMessage.createToolResultMessage(toolCallId, toolName, content);
                        Log.d(TAG, "Restored tool result message for tool: " + toolName);
                    } else if (type == ChatMessage.TYPE_CONTEXT_CARD) {
                        // Restore context card
                        message = new ChatMessage(content, type);
                        message.setIsContextCard(jsonObject.optBoolean("isContextCard", true));
                        if (jsonObject.has("contextType") && !jsonObject.isNull("contextType")) {
                            message.setContextType(jsonObject.getString("contextType"));
                        }
                        if (jsonObject.has("originalTaskId") && !jsonObject.isNull("originalTaskId")) {
                            message.setOriginalTaskId(jsonObject.getString("originalTaskId"));
                        }
                        Log.d(TAG, "Restored context card message for task: " + message.getOriginalTaskId());
                    } else {
                        message = new ChatMessage(content, type);
                    }

                    // Restore attachments (for user messages with file uploads)
                    if (jsonObject.has("attachments")) {
                        JSONArray attachmentsArray = jsonObject.getJSONArray("attachments");
                        List<io.finett.droidclaw.model.FileAttachment> attachments = new ArrayList<>();
                        for (int j = 0; j < attachmentsArray.length(); j++) {
                            JSONObject attObj = attachmentsArray.getJSONObject(j);
                            io.finett.droidclaw.model.FileAttachment attachment =
                                new io.finett.droidclaw.model.FileAttachment(
                                    attObj.optString("filename", ""),
                                    attObj.optString("originalName", ""),
                                    attObj.optString("absolutePath", ""),
                                    attObj.optString("mimeType", "")
                                );
                            attachments.add(attachment);
                        }
                        message.setAttachments(attachments);
                    }

                    // Restore TYPE_ATTACHMENT fields
                    if (type == ChatMessage.TYPE_ATTACHMENT) {
                        if (jsonObject.has("filePath") && !jsonObject.isNull("filePath")) {
                            message.setFilePath(jsonObject.getString("filePath"));
                        }
                        if (jsonObject.has("fileMimeType") && !jsonObject.isNull("fileMimeType")) {
                            message.setFileMimeType(jsonObject.getString("fileMimeType"));
                        }
                        if (jsonObject.has("displayName") && !jsonObject.isNull("displayName")) {
                            message.setDisplayName(jsonObject.getString("displayName"));
                        }
                    }

                    message.setTimestamp(timestamp);
                    messages.add(message);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading message at index " + i + " for session: " + sessionId, e);
                    // Continue loading other messages
                }
            }
            
            Log.d(TAG, "Loaded " + messages.size() + " messages for session: " + sessionId);
        } catch (JSONException e) {
            Log.e(TAG, "Error loading messages for session: " + sessionId, e);
        }
        
        return messages;
    }

    /**
     * Delete messages for a specific chat session
     */
    public void deleteMessages(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        prefs.edit().remove(key).apply();
        Log.d(TAG, "Deleted messages for session: " + sessionId);
    }

    /**
     * Clear all saved messages
     */
    public void clearAllMessages() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all saved messages");
    }

    // ==================== HIDDEN SESSION SUPPORT ====================

    /**
     * Get only visible (normal) sessions.
     * Filters out hidden sessions used for background tasks.
     */
    public List<ChatSession> getVisibleSessions() {
        List<ChatSession> allSessions = loadSessions();
        List<ChatSession> visible = new ArrayList<>();

        for (ChatSession session : allSessions) {
            if (!session.isHidden()) {
                visible.add(session);
            }
        }

        Log.d(TAG, "Returning " + visible.size() + " visible sessions (filtered from " + allSessions.size() + " total)");
        return visible;
    }

    /**
     * Get a hidden session by task ID.
     * Used for debugging background task sessions.
     */
    public ChatSession getHiddenSession(String taskId) {
        List<ChatSession> allSessions = loadSessions();

        for (ChatSession session : allSessions) {
            if (session.isHidden() && taskId.equals(session.getParentTaskId())) {
                return session;
            }
        }

        Log.d(TAG, "No hidden session found for task: " + taskId);
        return null;
    }

    /**
     * Get all sessions including hidden ones.
     * Use with caution - hidden sessions should not appear in UI.
     */
    public List<ChatSession> getAllSessionsIncludingHidden() {
        return loadSessions();
    }
}