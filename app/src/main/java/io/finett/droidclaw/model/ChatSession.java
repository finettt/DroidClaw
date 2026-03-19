package io.finett.droidclaw.model;

public class ChatSession {
    private String id;
    private String title;
    private long updatedAt;

    public ChatSession(String id, String title, long updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
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
}