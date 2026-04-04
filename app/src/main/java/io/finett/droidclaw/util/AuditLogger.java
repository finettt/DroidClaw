package io.finett.droidclaw.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Audit trail for background task execution.
 *
 * Logs all cron job and heartbeat executions for security tracking.
 * Provides an immutable audit log that can be reviewed for compliance.
 */
public class AuditLogger {
    private static final String TAG = "AuditLogger";
    private static final String PREFS_NAME = "audit_log";
    private static final String KEY_AUDIT_ENTRIES = "audit_entries";
    private static final int MAX_ENTRIES = 500; // Keep last 500 entries

    private final SharedPreferences prefs;
    private final Gson gson;

    public AuditLogger(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    /**
     * Log a task execution event.
     */
    public void logExecution(AuditEntry entry) {
        List<AuditEntry> entries = getAllEntries();
        entries.add(entry);

        // Prune old entries if exceeding limit
        if (entries.size() > MAX_ENTRIES) {
            Collections.sort(entries, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            entries = entries.subList(0, MAX_ENTRIES);
        }

        String json = gson.toJson(entries);
        prefs.edit().putString(KEY_AUDIT_ENTRIES, json).apply();
    }

    /**
     * Log a security event (e.g., blocked tool, resource limit exceeded).
     */
    public void logSecurityEvent(String taskId, String taskType, String eventType, String details) {
        AuditEntry entry = new AuditEntry(
                taskId,
                taskType,
                AuditEntry.EVENT_SECURITY,
                eventType,
                details
        );
        logExecution(entry);
    }

    /**
     * Log a tool blocked event.
     */
    public void logToolBlocked(String taskId, String taskType, String toolName) {
        AuditEntry entry = AuditEntry.toolBlocked(taskId, taskType, toolName);
        logExecution(entry);
    }

    /**
     * Log an emergency control event.
     */
    public void logEmergencyEvent(String action, String reason) {
        AuditEntry entry = new AuditEntry(
                null,
                "system",
                AuditEntry.EVENT_EMERGENCY,
                action,
                reason
        );
        logExecution(entry);
    }

    /**
     * Get all audit entries.
     */
    public List<AuditEntry> getAllEntries() {
        String json = prefs.getString(KEY_AUDIT_ENTRIES, "[]");
        Type type = new TypeToken<List<AuditEntry>>(){}.getType();
        List<AuditEntry> entries = gson.fromJson(json, type);
        return entries != null ? entries : new ArrayList<>();
    }

    /**
     * Get audit entries for a specific task.
     */
    public List<AuditEntry> getEntriesForTask(String taskId) {
        List<AuditEntry> allEntries = getAllEntries();
        List<AuditEntry> filtered = new ArrayList<>();

        for (AuditEntry entry : allEntries) {
            if (taskId.equals(entry.getTaskId())) {
                filtered.add(entry);
            }
        }

        Collections.sort(filtered, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return filtered;
    }

    /**
     * Get recent audit entries (last N).
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        List<AuditEntry> allEntries = getAllEntries();
        Collections.sort(allEntries, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return allEntries.subList(0, Math.min(limit, allEntries.size()));
    }

    /**
     * Get security events only.
     */
    public List<AuditEntry> getSecurityEvents() {
        List<AuditEntry> allEntries = getAllEntries();
        List<AuditEntry> securityEvents = new ArrayList<>();

        for (AuditEntry entry : allEntries) {
            if (AuditEntry.EVENT_SECURITY.equals(entry.getEventType()) ||
                AuditEntry.EVENT_EMERGENCY.equals(entry.getEventType())) {
                securityEvents.add(entry);
            }
        }

        Collections.sort(securityEvents, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return securityEvents;
    }

    /**
     * Clear all audit entries.
     */
    public void clearAuditLog() {
        prefs.edit().remove(KEY_AUDIT_ENTRIES).apply();
    }

    /**
     * Audit entry model.
     */
    public static class AuditEntry {
        // Event types
        public static final String EVENT_EXECUTION = "execution";
        public static final String EVENT_SECURITY = "security";
        public static final String EVENT_EMERGENCY = "emergency";
        public static final String EVENT_RESOURCE_LIMIT = "resource_limit";

        private String taskId;
        private String taskType;       // "cronjob" or "heartbeat"
        private String eventType;      // execution, security, emergency, resource_limit
        private String action;         // start, success, failure, blocked, etc.
        private String details;        // Human-readable description
        private long timestamp;

        public AuditEntry() {
            this.timestamp = System.currentTimeMillis();
        }

        public AuditEntry(String taskId, String taskType, String eventType, String action, String details) {
            this();
            this.taskId = taskId;
            this.taskType = taskType;
            this.eventType = eventType;
            this.action = action;
            this.details = details;
        }

        // Factory methods
        public static AuditEntry executionStart(String taskId, String taskType, String taskName) {
            return new AuditEntry(
                    taskId,
                    taskType,
                    EVENT_EXECUTION,
                    "start",
                    "Task started: " + taskName
            );
        }

        public static AuditEntry executionSuccess(String taskId, String taskType, String taskName, long durationMs) {
            return new AuditEntry(
                    taskId,
                    taskType,
                    EVENT_EXECUTION,
                    "success",
                    "Task completed: " + taskName + " in " + durationMs + "ms"
            );
        }

        public static AuditEntry executionFailure(String taskId, String taskType, String taskName, String error) {
            return new AuditEntry(
                    taskId,
                    taskType,
                    EVENT_EXECUTION,
                    "failure",
                    "Task failed: " + taskName + " - " + error
            );
        }

        public static AuditEntry toolBlocked(String taskId, String taskType, String toolName) {
            return new AuditEntry(
                    taskId,
                    taskType,
                    EVENT_SECURITY,
                    "tool_blocked",
                    "Tool blocked: " + toolName
            );
        }

        public static AuditEntry resourceLimitExceeded(String taskId, String taskType, String resource, String value) {
            return new AuditEntry(
                    taskId,
                    taskType,
                    EVENT_RESOURCE_LIMIT,
                    "limit_exceeded",
                    "Resource limit exceeded: " + resource + " = " + value
            );
        }

        public static AuditEntry emergencyDisable(String reason) {
            return new AuditEntry(
                    null,
                    "system",
                    EVENT_EMERGENCY,
                    "emergency_disable",
                    "Emergency disable activated: " + reason
            );
        }

        public static AuditEntry emergencyEnable(String reason) {
            return new AuditEntry(
                    null,
                    "system",
                    EVENT_EMERGENCY,
                    "emergency_enable",
                    "Emergency disable deactivated: " + reason
            );
        }

        // Getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
