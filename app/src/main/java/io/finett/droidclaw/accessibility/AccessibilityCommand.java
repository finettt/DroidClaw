package io.finett.droidclaw.accessibility;

import android.os.Bundle;

/**
 * Immutable value object representing a single accessibility command dispatched through
 * {@link AccessibilityBridge} to {@link DroidClawAccessibilityService}.
 */
public final class AccessibilityCommand {

    public enum Type {
        GET_UI_TREE,
        TAP,
        SWIPE,
        SET_TEXT,
        CLICK_NODE,
        GLOBAL_ACTION
    }

    private final Type type;

    // TAP / SWIPE coordinates
    private final int x;
    private final int y;
    private final int x2;
    private final int y2;
    private final int durationMs;

    // Node-targeted actions
    private final String resourceId;
    private final String nodeText;

    // Text to type
    private final String text;

    // Global action constant (AccessibilityService.GLOBAL_ACTION_*)
    private final int globalAction;

    // UI tree depth cap
    private final int depth;

    private AccessibilityCommand(Builder b) {
        this.type = b.type;
        this.x = b.x;
        this.y = b.y;
        this.x2 = b.x2;
        this.y2 = b.y2;
        this.durationMs = b.durationMs;
        this.resourceId = b.resourceId;
        this.nodeText = b.nodeText;
        this.text = b.text;
        this.globalAction = b.globalAction;
        this.depth = b.depth;
    }

    public Type getType() { return type; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getDurationMs() { return durationMs; }
    public String getResourceId() { return resourceId; }
    public String getNodeText() { return nodeText; }
    public String getText() { return text; }
    public int getGlobalAction() { return globalAction; }
    public int getDepth() { return depth; }

    // ── Factory helpers ──────────────────────────────────────────────────────

    public static AccessibilityCommand getUiTree(int depth) {
        return new Builder(Type.GET_UI_TREE).depth(depth).build();
    }

    public static AccessibilityCommand tapAt(int x, int y) {
        return new Builder(Type.TAP).x(x).y(y).build();
    }

    public static AccessibilityCommand tapByResourceId(String resourceId) {
        return new Builder(Type.CLICK_NODE).resourceId(resourceId).build();
    }

    public static AccessibilityCommand tapByText(String text) {
        return new Builder(Type.CLICK_NODE).nodeText(text).build();
    }

    public static AccessibilityCommand swipe(int x1, int y1, int x2, int y2, int durationMs) {
        return new Builder(Type.SWIPE).x(x1).y(y1).x2(x2).y2(y2).durationMs(durationMs).build();
    }

    public static AccessibilityCommand setTextOnFocused(String text) {
        return new Builder(Type.SET_TEXT).text(text).build();
    }

    public static AccessibilityCommand setTextOnNode(String resourceId, String text) {
        return new Builder(Type.SET_TEXT).resourceId(resourceId).text(text).build();
    }

    public static AccessibilityCommand globalAction(int action) {
        return new Builder(Type.GLOBAL_ACTION).globalAction(action).build();
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private final Type type;
        private int x, y, x2, y2;
        private int durationMs = 300;
        private String resourceId;
        private String nodeText;
        private String text;
        private int globalAction;
        private int depth = 6;

        public Builder(Type type) { this.type = type; }

        public Builder x(int v)             { this.x = v; return this; }
        public Builder y(int v)             { this.y = v; return this; }
        public Builder x2(int v)            { this.x2 = v; return this; }
        public Builder y2(int v)            { this.y2 = v; return this; }
        public Builder durationMs(int v)    { this.durationMs = v; return this; }
        public Builder resourceId(String v) { this.resourceId = v; return this; }
        public Builder nodeText(String v)   { this.nodeText = v; return this; }
        public Builder text(String v)       { this.text = v; return this; }
        public Builder globalAction(int v)  { this.globalAction = v; return this; }
        public Builder depth(int v)         { this.depth = v; return this; }

        public AccessibilityCommand build() { return new AccessibilityCommand(this); }
    }
}