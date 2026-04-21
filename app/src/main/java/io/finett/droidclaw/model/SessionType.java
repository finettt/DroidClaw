package io.finett.droidclaw.model;

public class SessionType {
    public static final int NORMAL = 0;
    public static final int HIDDEN_HEARTBEAT = 1;
    public static final int HIDDEN_CRON = 2;

    private SessionType() {
    }

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
