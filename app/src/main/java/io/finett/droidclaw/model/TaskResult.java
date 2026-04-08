package io.finett.droidclaw.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of a background task execution.
 * Used to track outcomes of heartbeat checks, cron jobs, and other automated tasks.
 */
public class TaskResult {

    // Task types
    public static final int TYPE_HEARTBEAT = 1;
    public static final int TYPE_CRON_JOB = 2;
    public static final int TYPE_MANUAL = 3;

    private String id;
    private int type;
    private long timestamp;
    private String content;
    private Map<String, String> metadata;

    public TaskResult(String id, int type, long timestamp, String content) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.content = content;
        this.metadata = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Add a metadata key-value pair.
     */
    public void putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get a metadata value by key.
     */
    public String getMetadataValue(String key) {
        if (this.metadata == null) {
            return null;
        }
        return this.metadata.get(key);
    }

    /**
     * Get the string representation of a task type.
     */
    public static String typeToString(int type) {
        switch (type) {
            case TYPE_HEARTBEAT:
                return "HEARTBEAT";
            case TYPE_CRON_JOB:
                return "CRON_JOB";
            case TYPE_MANUAL:
                return "MANUAL";
            default:
                return "UNKNOWN(" + type + ")";
        }
    }
}
