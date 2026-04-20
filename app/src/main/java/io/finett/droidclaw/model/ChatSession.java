package io.finett.droidclaw.model;

public class ChatSession {
    private String id;
    private String title;
    private long updatedAt;

    private int currentContextTokens;
    private int currentPromptTokens;
    private int currentCompletionTokens;

    private int totalTokens;
    private int totalPromptTokens;
    private int totalCompletionTokens;
    private int totalToolCalls;

    private int sessionType;
    private String parentTaskId;

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
        this.sessionType = SessionType.NORMAL;
        this.parentTaskId = null;
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

    public int getSessionType() {
        return sessionType;
    }

    public void setSessionType(int sessionType) {
        this.sessionType = sessionType;
    }

    public boolean isHidden() {
        return sessionType != SessionType.NORMAL;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }
}