package io.finett.droidclaw.model;

import java.util.UUID;

/**
 * A durable, reusable lesson extracted from a completed conversation
 * (user correction, tool-usage pattern, failure cause, workflow insight).
 *
 * Lessons are stored as JSONL in {@code .agent/memory/lessons/YYYY-MM-DD.jsonl},
 * injected into fresh conversations until consolidation absorbs them into
 * long-term memory, and pruned after consumption + retention period.
 */
public class Lesson {

    /** Where the lesson belongs. */
    public static final String SCOPE_MEMORY = "memory";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_SKILL = "skill";

    /** Lesson categories. */
    public static final String CATEGORY_USER_PREFERENCE = "user_preference";
    public static final String CATEGORY_TOOL_PATTERN = "tool_pattern";
    public static final String CATEGORY_FAILURE_CAUSE = "failure_cause";
    public static final String CATEGORY_WORKFLOW = "workflow";
    public static final String CATEGORY_FACT = "fact";

    /** Where the lesson came from. */
    public static final String SOURCE_REFLECTION = "reflection";
    public static final String SOURCE_AGENT = "agent";

    private String id;
    private String sessionId;
    private long timestamp;
    private String category;
    private String content;
    private String evidence;
    private String scope;
    private String source;
    private int confidence;
    private boolean consumed;

    /** Gson requires a no-arg constructor. */
    public Lesson() {
    }

    public Lesson(String sessionId, String category, String content,
                  String evidence, String scope, String source, int confidence) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
        this.category = category;
        this.content = content;
        this.evidence = evidence;
        this.scope = scope;
        this.source = source;
        this.confidence = confidence;
        this.consumed = false;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getScope() {
        return scope;
    }

    public String getSource() {
        return source;
    }

    public int getConfidence() {
        return confidence;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}