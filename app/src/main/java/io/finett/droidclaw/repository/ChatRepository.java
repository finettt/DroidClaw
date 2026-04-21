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

    public interface TitleGenerationCallback {
        void onTitleGenerated(String title);
        void onError(String error);
    }

    public ChatRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveSessions(List<ChatSession> sessions) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ChatSession session : sessions) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", session.getId());
                jsonObject.put("title", session.getTitle());
                jsonObject.put("updatedAt", session.getUpdatedAt());

                jsonObject.put("currentContextTokens", session.getCurrentContextTokens());
                jsonObject.put("currentPromptTokens", session.getCurrentPromptTokens());
                jsonObject.put("currentCompletionTokens", session.getCurrentCompletionTokens());

                jsonObject.put("totalTokens", session.getTotalTokens());
                jsonObject.put("totalPromptTokens", session.getTotalPromptTokens());
                jsonObject.put("totalCompletionTokens", session.getTotalCompletionTokens());
                jsonObject.put("totalToolCalls", session.getTotalToolCalls());

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

                session.setCurrentContextTokens(jsonObject.optInt("currentContextTokens", 0));
                session.setCurrentPromptTokens(jsonObject.optInt("currentPromptTokens", 0));
                session.setCurrentCompletionTokens(jsonObject.optInt("currentCompletionTokens", 0));

                session.setTotalTokens(jsonObject.optInt("totalTokens", 0));
                session.setTotalPromptTokens(jsonObject.optInt("totalPromptTokens", 0));
                session.setTotalCompletionTokens(jsonObject.optInt("totalCompletionTokens", 0));
                session.setTotalToolCalls(jsonObject.optInt("totalToolCalls", 0));

                session.setSessionType(jsonObject.optInt("sessionType", SessionType.NORMAL));
                if (jsonObject.has("parentTaskId") && !jsonObject.isNull("parentTaskId")) {
                    session.setParentTaskId(jsonObject.getString("parentTaskId"));
                }

                sessions.add(session);
            }
            
            Collections.sort(sessions, (s1, s2) -> Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt()));
            
            Log.d(TAG, "Loaded " + sessions.size() + " sessions");
        } catch (JSONException e) {
            Log.e(TAG, "Error loading sessions - clearing corrupted data", e);
            prefs.edit().remove(KEY_SESSIONS).apply();
        }
        
        return sessions;
    }
    
    public void deleteSession(String sessionId, List<ChatSession> remainingSessions) {
        deleteMessages(sessionId);
        saveSessions(remainingSessions);
        Log.d(TAG, "Deleted session and messages for: " + sessionId);
    }
    
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
    
    public String generateTitleFromMessage(String messageContent) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return "New Chat";
        }
        
        String trimmed = messageContent.trim();
        if (trimmed.length() <= 30) {
            return trimmed;
        }
        
        int lastSpace = trimmed.lastIndexOf(' ', 30);
        if (lastSpace > 15) {
            return trimmed.substring(0, lastSpace) + "...";
        }
        
        return trimmed.substring(0, 27) + "...";
    }

    public void generateTitleWithLLM(LlmApiService apiService, List<ChatMessage> messages,
                                     String fallbackTitle, TitleGenerationCallback callback) {
        if (messages == null || messages.isEmpty()) {
            callback.onTitleGenerated(fallbackTitle);
            return;
        }

        List<ChatMessage> titleMessages = new ArrayList<>();

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

        ChatMessage systemPrompt = new ChatMessage(
            "Generate a concise, descriptive title for this conversation. " +
            "The title must be at most 50 characters. " +
            "Respond with ONLY the title text, nothing else.",
            ChatMessage.TYPE_SYSTEM
        );
        titleMessages.add(systemPrompt);
        titleMessages.add(new ChatMessage(firstUserMessage.getContent(), ChatMessage.TYPE_USER));

        apiService.sendMessage(titleMessages, new LlmApiService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                if (response != null && !response.isEmpty() && !response.equals("No response received")) {
                    String title = response.trim();

                    if (title.startsWith("\"") && title.endsWith("\"")) {
                        title = title.substring(1, title.length() - 1);
                    }

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

    public void saveMessages(String sessionId, List<ChatMessage> messages) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ChatMessage message : messages) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("content", message.getContent());
                jsonObject.put("type", message.getType());
                jsonObject.put("timestamp", message.getTimestamp());

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

                if (message.getType() == ChatMessage.TYPE_CONTEXT_CARD) {
                    jsonObject.put("isContextCard", message.isContextCard());
                    if (message.getContextType() != null) {
                        jsonObject.put("contextType", message.getContextType());
                    }
                    if (message.getOriginalTaskId() != null) {
                        jsonObject.put("originalTaskId", message.getOriginalTaskId());
                    }
                }

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
                    
                    String content = jsonObject.has("content") && !jsonObject.isNull("content")
                        ? jsonObject.getString("content")
                        : null;
                    int type = jsonObject.getInt("type");
                    long timestamp = jsonObject.getLong("timestamp");

                    ChatMessage message;

                    if (type == ChatMessage.TYPE_TOOL_CALL && jsonObject.has("toolCalls")) {
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
                        String toolCallId = jsonObject.has("toolCallId") && !jsonObject.isNull("toolCallId")
                            ? jsonObject.getString("toolCallId")
                            : null;
                        String toolName = jsonObject.has("toolName") && !jsonObject.isNull("toolName")
                            ? jsonObject.getString("toolName")
                            : null;
                        message = ChatMessage.createToolResultMessage(toolCallId, toolName, content);
                        Log.d(TAG, "Restored tool result message for tool: " + toolName);
                    } else if (type == ChatMessage.TYPE_CONTEXT_CARD) {
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
                }
            }
            
            Log.d(TAG, "Loaded " + messages.size() + " messages for session: " + sessionId);
        } catch (JSONException e) {
            Log.e(TAG, "Error loading messages for session: " + sessionId, e);
        }
        
        return messages;
    }

    public void deleteMessages(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        prefs.edit().remove(key).apply();
        Log.d(TAG, "Deleted messages for session: " + sessionId);
    }

    public void clearAllMessages() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all saved messages");
    }

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

    public List<ChatSession> getAllSessionsIncludingHidden() {
        return loadSessions();
    }
}