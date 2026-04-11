package io.finett.droidclaw.model;

/**
 * Enum representing the type of a chat session.
 * Used to differentiate between user-visible sessions and hidden background task sessions.
 */
public class SessionType {
    public static final int NORMAL = 0;
    public static final int HIDDEN_HEARTBEAT = 1;
    public static final int HIDDEN_CRON = 2;

    private SessionType() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the string representation of a session type value.
     */
    public static String toString(int type) {
        switch (type) {
            case NORMAL:
                return "NORMAL";
            case HIDDEN_HEARTBEAT:
                return "HIDDEN_HEARTBEAT";
            case HIDDEN_CRON:
                return "HIDDEN_CRON";
            default:
                return "UNKNOWN(" + type + ")";
        }
    }
}
