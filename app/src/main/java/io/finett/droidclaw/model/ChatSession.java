package io.finett.droidclaw.model;

/**
 * Represents a chat session with token tracking.
 * Implements the "Last Usage" algorithm:
 * - Current context tokens: Actual context size from last API response
 * - Session cumulative tokens: Total tokens spent across all requests
 */
public class ChatSession {
    private String id;
    private String title;
    private long updatedAt;
    
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
}