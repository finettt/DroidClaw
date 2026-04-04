package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.model.TaskSecurityConfig;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.AuditLogger;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Tool for managing scheduled tasks (cron jobs) via conversation.
 * 
 * Allows the agent to:
 * - Create new cron jobs
 * - List existing cron jobs
 * - Pause/resume cron jobs
 * - Delete cron jobs
 * - View task execution history
 * - View task execution details
 */
public class TaskManagementTool implements Tool {
    private static final String TOOL_NAME = "manage_tasks";
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_LIST = "list";
    private static final String ACTION_PAUSE = "pause";
    private static final String ACTION_RESUME = "resume";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_HISTORY = "history";
    private static final String ACTION_STATUS = "status";
    private static final String ACTION_EMERGENCY_DISABLE = "emergency_disable";
    private static final String ACTION_EMERGENCY_ENABLE = "emergency_enable";
    private static final String ACTION_AUDIT_LOG = "audit_log";
    private static final String ACTION_SECURITY_CONFIG = "security_config";

    private final TaskRepository taskRepository;
    private final Context context;
    private final SettingsManager settingsManager;
    private final AuditLogger auditLogger;

    public TaskManagementTool(Context context) {
        this.context = context;
        this.taskRepository = new TaskRepository(context);
        this.settingsManager = new SettingsManager(context);
        this.auditLogger = new AuditLogger(context);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                TOOL_NAME,
                "Manage scheduled tasks (cron jobs). Can create, list, pause, resume, delete tasks, view execution history, manage security, and emergency controls.",
                new ToolDefinition.ParametersBuilder()
                        .addString("action", "Action to perform: create, list, pause, resume, delete, history, status, emergency_disable, emergency_enable, audit_log, security_config", true)
                        .addString("name", "Task name (required for create, pause, resume, delete, history)", false)
                        .addString("prompt", "Task instructions - what the agent should do (required for create)", false)
                        .addString("interval", "Execution interval: 15min, 30min, 1h, 2h, 4h, 6h, 12h, 1d (required for create)", false)
                        .addBoolean("require_network", "Require network connection (default: true)", false)
                        .addBoolean("notify_on_success", "Send notification on success (default: true)", false)
                        .addBoolean("notify_on_failure", "Send notification on failure (default: true)", false)
                        .addInteger("max_history", "Maximum number of history entries to return (default: 10)", false)
                        .addString("reason", "Reason for emergency disable/enable (required for emergency_disable)", false)
                        .addInteger("max_entries", "Maximum number of audit log entries to return (default: 20)", false)
                        .build()
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        String action = arguments.has("action") ? arguments.get("action").getAsString() : null;
        if (action == null) {
            return ToolResult.error("Missing required parameter: action. Valid actions: create, list, pause, resume, delete, history, status");
        }

        String actionLower = action.toLowerCase();
        if (actionLower.equals(ACTION_CREATE)) {
            return createTask(arguments);
        } else if (actionLower.equals(ACTION_LIST)) {
            return listTasks();
        } else if (actionLower.equals(ACTION_PAUSE)) {
            return pauseTask(arguments);
        } else if (actionLower.equals(ACTION_RESUME)) {
            return resumeTask(arguments);
        } else if (actionLower.equals(ACTION_DELETE)) {
            return deleteTask(arguments);
        } else if (actionLower.equals(ACTION_HISTORY)) {
            return viewHistory(arguments);
        } else if (actionLower.equals(ACTION_STATUS)) {
            return taskStatus();
        } else if (actionLower.equals(ACTION_EMERGENCY_DISABLE)) {
            return emergencyDisable(arguments);
        } else if (actionLower.equals(ACTION_EMERGENCY_ENABLE)) {
            return emergencyEnable();
        } else if (actionLower.equals(ACTION_AUDIT_LOG)) {
            return viewAuditLog(arguments);
        } else if (actionLower.equals(ACTION_SECURITY_CONFIG)) {
            return viewSecurityConfig();
        } else {
            return ToolResult.error("Unknown action: " + action + ". Valid actions: create, list, pause, resume, delete, history, status, emergency_disable, emergency_enable, audit_log, security_config");
        }
    }

    private ToolResult createTask(JsonObject arguments) {
        String name = arguments.has("name") ? arguments.get("name").getAsString() : null;
        String prompt = arguments.has("prompt") ? arguments.get("prompt").getAsString() : null;
        String interval = arguments.has("interval") ? arguments.get("interval").getAsString() : null;

        if (name == null || name.isEmpty()) {
            return ToolResult.error("Missing required parameter: name. Provide a descriptive task name.");
        }
        if (prompt == null || prompt.isEmpty()) {
            return ToolResult.error("Missing required parameter: prompt. Provide task instructions.");
        }
        if (interval == null || interval.isEmpty()) {
            return ToolResult.error("Missing required parameter: interval. Valid intervals: 15min, 30min, 1h, 2h, 4h, 6h, 12h, 1d");
        }

        // Parse interval
        long intervalMinutes = parseInterval(interval);
        if (intervalMinutes < 0) {
            return ToolResult.error("Invalid interval: " + interval + ". Valid: 15min, 30min, 1h, 2h, 4h, 6h, 12h, 1d");
        }

        // Check for duplicate name
        List<CronJob> existingJobs = taskRepository.getAllCronJobs();
        for (CronJob job : existingJobs) {
            if (job.getName().equalsIgnoreCase(name)) {
                return ToolResult.error("A task with name '" + name + "' already exists. Use a different name.");
            }
        }

        // Create cron job
        CronJob job = new CronJob(name, prompt, intervalMinutes);

        // Optional settings
        if (arguments.has("require_network")) {
            job.setRequireNetwork(arguments.get("require_network").getAsBoolean());
        }
        if (arguments.has("notify_on_success")) {
            job.setNotifyOnSuccess(arguments.get("notify_on_success").getAsBoolean());
        }
        if (arguments.has("notify_on_failure")) {
            job.setNotifyOnFailure(arguments.get("notify_on_failure").getAsBoolean());
        }

        taskRepository.saveCronJob(job);

        return ToolResult.success(formatTaskCreated(job));
    }

    private ToolResult listTasks() {
        List<CronJob> jobs = taskRepository.getAllCronJobs();

        if (jobs.isEmpty()) {
            return ToolResult.success("No scheduled tasks configured.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Scheduled Tasks (").append(jobs.size()).append(")\n\n");

        for (int i = 0; i < jobs.size(); i++) {
            CronJob job = jobs.get(i);
            sb.append(formatTaskSummary(job));
            if (i < jobs.size() - 1) {
                sb.append("\n");
            }
        }

        return ToolResult.success(sb.toString());
    }

    private ToolResult pauseTask(JsonObject arguments) {
        String name = arguments.has("name") ? arguments.get("name").getAsString() : null;
        if (name == null || name.isEmpty()) {
            return ToolResult.error("Missing required parameter: name. Provide the task name to pause.");
        }

        CronJob job = findTaskByName(name);
        if (job == null) {
            return ToolResult.error("Task not found: " + name);
        }

        if (!job.isEnabled()) {
            return ToolResult.success("Task '" + name + "' is already paused.");
        }

        job.setEnabled(false);
        taskRepository.saveCronJob(job);

        return ToolResult.success("Task '" + name + "' has been paused.");
    }

    private ToolResult resumeTask(JsonObject arguments) {
        String name = arguments.has("name") ? arguments.get("name").getAsString() : null;
        if (name == null || name.isEmpty()) {
            return ToolResult.error("Missing required parameter: name. Provide the task name to resume.");
        }

        CronJob job = findTaskByName(name);
        if (job == null) {
            return ToolResult.error("Task not found: " + name);
        }

        if (job.isEnabled()) {
            return ToolResult.success("Task '" + name + "' is already active.");
        }

        job.setEnabled(true);
        taskRepository.saveCronJob(job);

        return ToolResult.success("Task '" + name + "' has been resumed.");
    }

    private ToolResult deleteTask(JsonObject arguments) {
        String name = arguments.has("name") ? arguments.get("name").getAsString() : null;
        if (name == null || name.isEmpty()) {
            return ToolResult.error("Missing required parameter: name. Provide the task name to delete.");
        }

        CronJob job = findTaskByName(name);
        if (job == null) {
            return ToolResult.error("Task not found: " + name);
        }

        taskRepository.deleteCronJob(job.getId());

        return ToolResult.success("Task '" + name + "' and all its execution history have been deleted.");
    }

    private ToolResult viewHistory(JsonObject arguments) {
        String name = arguments.has("name") ? arguments.get("name").getAsString() : null;
        if (name == null || name.isEmpty()) {
            return ToolResult.error("Missing required parameter: name. Provide the task name to view history.");
        }

        CronJob job = findTaskByName(name);
        if (job == null) {
            return ToolResult.error("Task not found: " + name);
        }

        int maxHistory = 10;
        if (arguments.has("max_history")) {
            maxHistory = arguments.get("max_history").getAsInt();
        }

        List<TaskRecord> records = taskRepository.getTaskRecordsForJob(job.getId());
        if (records.isEmpty()) {
            return ToolResult.success("No execution history for '" + name + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Execution History: ").append(name).append("\n\n");

        int count = Math.min(maxHistory, records.size());
        sb.append("Showing last ").append(count).append(" of ").append(records.size()).append(" executions\n\n");

        for (int i = 0; i < count; i++) {
            TaskRecord record = records.get(i);
            sb.append(formatRecordSummary(record));
            if (i < count - 1) {
                sb.append("\n");
            }
        }

        return ToolResult.success(sb.toString());
    }

    private ToolResult taskStatus() {
        int total = taskRepository.getTotalCronJobs();
        int active = taskRepository.getEnabledCronJobsCount();
        int paused = total - active;
        int executions = taskRepository.getTotalExecutions();
        int successRate = taskRepository.getOverallSuccessRate();

        StringBuilder sb = new StringBuilder();
        sb.append("## Task Manager Status\n\n");
        sb.append("- **Total tasks:** ").append(total).append("\n");
        sb.append("- **Active:** ").append(active).append("\n");
        sb.append("- **Paused:** ").append(paused).append("\n");
        sb.append("- **Total executions:** ").append(executions).append("\n");
        sb.append("- **Overall success rate:** ").append(successRate).append("%\n");

        return ToolResult.success(sb.toString());
    }

    // Helper methods

    private CronJob findTaskByName(String name) {
        List<CronJob> jobs = taskRepository.getAllCronJobs();
        for (CronJob job : jobs) {
            if (job.getName().equalsIgnoreCase(name)) {
                return job;
            }
        }
        return null;
    }

    private long parseInterval(String interval) {
        String lower = interval.toLowerCase().trim();
        if (lower.equals("15min") || lower.equals("15m")) return 15;
        if (lower.equals("30min") || lower.equals("30m")) return 30;
        if (lower.equals("1h") || lower.equals("1hour") || lower.equals("60min")) return 60;
        if (lower.equals("2h") || lower.equals("2hours")) return 120;
        if (lower.equals("4h") || lower.equals("4hours")) return 240;
        if (lower.equals("6h") || lower.equals("6hours")) return 360;
        if (lower.equals("12h") || lower.equals("12hours")) return 720;
        if (lower.equals("1d") || lower.equals("1day") || lower.equals("24h")) return 1440;
        return -1;
    }

    private String formatTaskCreated(CronJob job) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task Created: ").append(job.getName()).append("\n\n");
        sb.append("- **Interval:** ").append(job.getIntervalDisplayString()).append("\n");
        sb.append("- **Status:** Active\n");
        sb.append("- **Notifications:** ").append(job.isNotifyOnSuccess() ? "Enabled" : "Disabled").append("\n");
        sb.append("\n**Instructions:** ").append(job.getPrompt());
        return sb.toString();
    }

    private String formatTaskSummary(CronJob job) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(job.getName());
        sb.append(job.isEnabled() ? " [Active]" : " [Paused]").append("\n\n");
        sb.append("- **Interval:** ").append(job.getIntervalDisplayString()).append("\n");
        sb.append("- **Status:** ").append(job.isEnabled() ? "Active" : "Paused").append("\n");

        if (job.getRunCount() > 0) {
            sb.append("- **Executions:** ").append(job.getRunCount());
            sb.append(" (Success: ").append(job.getSuccessCount());
            sb.append(", Failed: ").append(job.getFailureCount()).append(")\n");
            sb.append("- **Success rate:** ").append(job.getSuccessRate()).append("%\n");

            if (job.getLastRunAt() > 0) {
                sb.append("- **Last run:** ").append(getTimeAgoString(job.getLastRunAt())).append("\n");
            }
        } else {
            sb.append("- **Executions:** None yet\n");
        }

        return sb.toString();
    }

    private String formatRecordSummary(TaskRecord record) {
        StringBuilder sb = new StringBuilder();
        String statusEmoji = record.isSuccess() ? "[OK]" : "[FAIL]";
        sb.append(statusEmoji).append(" **").append(getFormattedTime(record.getStartedAt())).append("** - ");
        sb.append(record.getDurationDisplayString());

        if (record.getToolCallsCount() > 0) {
            sb.append(", ").append(record.getToolCallsCount()).append(" tool calls");
        }
        if (record.getTokensUsed() > 0) {
            sb.append(", ").append(record.getTokensUsed()).append(" tokens");
        }

        if (record.isFailed() && record.getErrorMessage() != null) {
            sb.append("\n   **Error:** ").append(record.getErrorMessage());
        }

        return sb.toString();
    }

    private String getTimeAgoString(long millis) {
        long seconds = (System.currentTimeMillis() - millis) / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    private String getFormattedTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    // ========== EMERGENCY CONTROLS ==========

    private ToolResult emergencyDisable(JsonObject arguments) {
        String reason = arguments.has("reason") ? arguments.get("reason").getAsString() : null;
        if (reason == null || reason.isEmpty()) {
            return ToolResult.error("Missing required parameter: reason. Provide a reason for emergency disable.");
        }

        // Activate emergency disable
        settingsManager.activateTaskEmergencyDisable(reason);

        // Log to audit trail
        auditLogger.logEmergencyEvent("disable", reason);

        return ToolResult.success("## Emergency Disable Activated\n\n**Reason:** " + reason + "\n\nAll background task execution has been halted. Use action='emergency_enable' to resume.");
    }

    private ToolResult emergencyEnable() {
        if (!settingsManager.isTaskEmergencyDisable()) {
            return ToolResult.success("Emergency disable is not currently active.");
        }

        String reason = settingsManager.getTaskSecurityConfig().getEmergencyDisableReason();

        // Deactivate emergency disable
        settingsManager.deactivateTaskEmergencyDisable();

        // Log to audit trail
        auditLogger.logEmergencyEvent("enable", "Previous reason: " + reason);

        return ToolResult.success("## Emergency Disable Deactivated\n\nBackground task execution has been resumed.\n\n**Previous reason:** " + reason);
    }

    // ========== AUDIT LOG ==========

    private ToolResult viewAuditLog(JsonObject arguments) {
        int maxEntries = 20;
        if (arguments.has("max_entries")) {
            maxEntries = arguments.get("max_entries").getAsInt();
        }

        List<AuditLogger.AuditEntry> entries = auditLogger.getRecentEntries(maxEntries);

        if (entries.isEmpty()) {
            return ToolResult.success("No audit log entries found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Audit Log (Last ").append(entries.size()).append(" entries)\n\n");

        for (AuditLogger.AuditEntry entry : entries) {
            sb.append("- **").append(getFormattedTime(entry.getTimestamp())).append("** [")
              .append(entry.getEventType()).append("] ")
              .append(entry.getAction()).append("\n");
            if (entry.getDetails() != null && !entry.getDetails().isEmpty()) {
                sb.append("  ").append(entry.getDetails()).append("\n");
            }
        }

        return ToolResult.success(sb.toString());
    }

    // ========== SECURITY CONFIG ==========

    private ToolResult viewSecurityConfig() {
        TaskSecurityConfig config = settingsManager.getTaskSecurityConfig();

        StringBuilder sb = new StringBuilder();
        sb.append("## Task Security Configuration\n\n");

        // Emergency status
        if (config.isEmergencyActive()) {
            sb.append("### [EMERGENCY DISABLE ACTIVE]\n\n");
            sb.append("- **Reason:** ").append(config.getEmergencyDisableReason()).append("\n");
            sb.append("- **Activated:** ").append(getFormattedTime(config.getEmergencyDisableTimestamp())).append("\n\n");
        }

        // Sandbox settings
        sb.append("### Sandbox Settings\n\n");
        sb.append("- **Restrict to workspace:** ").append(config.isRestrictToWorkspace() ? "Yes" : "No").append("\n");
        sb.append("- **Block destructive ops:** ").append(config.isBlockDestructiveOps() ? "Yes" : "No").append("\n");
        sb.append("- **Block shell access:** ").append(config.isBlockShellAccess() ? "Yes" : "No").append("\n");
        sb.append("- **Block Python access:** ").append(config.isBlockPythonAccess() ? "Yes" : "No").append("\n");

        // Resource limits
        sb.append("\n### Resource Limits\n\n");
        sb.append("- **Max execution time:** ").append(config.getMaxExecutionTimeSeconds()).append("s\n");
        sb.append("- **Max iterations:** ").append(config.getMaxIterations()).append("\n");
        sb.append("- **Max tool calls:** ").append(config.getMaxToolCalls()).append("\n");
        sb.append("- **Max token usage:** ").append(config.getMaxTokenUsage()).append("\n");
        sb.append("- **Max memory context:** ").append(config.getMaxMemoryContextSize()).append(" chars\n");

        return ToolResult.success(sb.toString());
    }
}
