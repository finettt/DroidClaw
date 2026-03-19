package io.finett.droidclaw.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;

public class ChatRepository {
    private static final String TAG = "ChatRepository";
    private static final String PREFS_NAME = "chat_messages";
    private static final String KEY_PREFIX = "session_";
    private static final String KEY_SESSIONS = "chat_sessions";
    
    private final SharedPreferences prefs;

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
                
                sessions.add(new ChatSession(id, title, updatedAt));
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
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                
                String content = jsonObject.getString("content");
                int type = jsonObject.getInt("type");
                long timestamp = jsonObject.getLong("timestamp");
                
                ChatMessage message = new ChatMessage(content, type);
                message.setTimestamp(timestamp);
                messages.add(message);
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
}