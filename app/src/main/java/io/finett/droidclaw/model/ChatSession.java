package io.finett.droidclaw.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Represents a chat session with token tracking.
 * Implements the "Last Usage" algorithm:
 * - Current context tokens: Actual context size from last API response
 * - Session cumulative tokens: Total tokens spent across all requests
 *
 * Supports different session types:
 * - TYPE_USER: Normal user chat sessions
 * - TYPE_CRON_JOB: Hidden isolated sessions for cron job executions
 */
public class ChatSession {
    // Session types
    public static final int TYPE_USER = 0;
    public static final int TYPE_CRON_JOB = 2;   // Isolated cron job session
    
    private String id;
    private String title;
    private long updatedAt;
    
    // Session type and metadata
    private int sessionType = TYPE_USER;
    private String cronJobId;          // Parent job if TYPE_CRON_JOB
    private String taskRecordId;       // Task record if TYPE_CRON_JOB
    
    // Current context tokens (from last API response - "Last Usage" algorithm)
    private int currentContextTokens;
    private int currentPromptTokens;
    private int currentCompletionTokens;
    
    // Session cumulative tokens (total spent across all requests)
    private int totalTokens;
    private int totalPromptTokens;
    private int totalCompletionTokens;
    private int totalToolCalls;

    public ChatSession(String id, String title, long updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
        this.currentContextTokens = 0;
        this.currentPromptTokens = 0;
        this.currentCompletionTokens = 0;
        this.totalTokens = 0;
        this.totalPromptTokens = 0;
        this.totalCompletionTokens = 0;
        this.totalToolCalls = 0;
    }
    
    /**
     * Create a hidden cron job session.
     */
    public static ChatSession createCronJobSession(String cronJobId, String taskRecordId) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        
        ChatSession session = new ChatSession(
            java.util.UUID.randomUUID().toString(),
            "Cron Job: " + timestamp,
            System.currentTimeMillis()
        );
        session.sessionType = TYPE_CRON_JOB;
        session.cronJobId = cronJobId;
        session.taskRecordId = taskRecordId;
        return session;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Current context tokens (Last Usage algorithm)
    public int getCurrentContextTokens() {
        return currentContextTokens;
    }
    
    public void setCurrentContextTokens(int currentContextTokens) {
        this.currentContextTokens = currentContextTokens;
    }
    
    public int getCurrentPromptTokens() {
        return currentPromptTokens;
    }
    
    public void setCurrentPromptTokens(int currentPromptTokens) {
        this.currentPromptTokens = currentPromptTokens;
    }
    
    public int getCurrentCompletionTokens() {
        return currentCompletionTokens;
    }
    
    public void setCurrentCompletionTokens(int currentCompletionTokens) {
        this.currentCompletionTokens = currentCompletionTokens;
    }
    
    // Session cumulative tokens
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    public int getTotalPromptTokens() {
        return totalPromptTokens;
    }
    
    public void setTotalPromptTokens(int totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }
    
    public int getTotalCompletionTokens() {
        return totalCompletionTokens;
    }
    
    public void setTotalCompletionTokens(int totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }
    
    public int getTotalToolCalls() {
        return totalToolCalls;
    }
    
    public void setTotalToolCalls(int totalToolCalls) {
        this.totalToolCalls = totalToolCalls;
    }
    
    // Session type methods
    
    public int getSessionType() {
        return sessionType;
    }
    
    public void setSessionType(int sessionType) {
        this.sessionType = sessionType;
    }
    
    public String getCronJobId() {
        return cronJobId;
    }
    
    public void setCronJobId(String cronJobId) {
        this.cronJobId = cronJobId;
    }
    
    public String getTaskRecordId() {
        return taskRecordId;
    }
    
    public void setTaskRecordId(String taskRecordId) {
        this.taskRecordId = taskRecordId;
    }
    
    /**
     * Check if this session should be hidden from user's session list.
     */
    public boolean isHidden() {
        return sessionType == TYPE_CRON_JOB;
    }
    
    /**
     * Check if this is a user session.
     */
    public boolean isUserSession() {
        return sessionType == TYPE_USER;
    }
    
    /**
     * Check if this is a cron job session.
     */
    public boolean isCronJobSession() {
        return sessionType == TYPE_CRON_JOB;
    }
}