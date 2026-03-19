package io.finett.droidclaw.model;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;

    private String content;
    private int type;
    private long timestamp;

    public ChatMessage(String content, int type) {
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
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
}