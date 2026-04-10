package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for viewing execution history of a specific scheduled task.
 */
public class ViewTaskHistoryTool implements Tool {

    private static final String TAG = "ViewTaskHistoryTool";
    private static final String NAME = "view_task_history";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    public ViewTaskHistoryTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private TaskRepository getTaskRepository() {
        if (taskRepository == null) {
            taskRepository = new TaskRepository(context);
        }
        return taskRepository;
    }

    private ToolDefinition createDefinition() {
        ParametersBuilder builder = new ParametersBuilder()
                .addString("task_id", "ID of the task to view history for", false)
                .addString("task_name", "Name of the task to view history for (will find by name)", false)
                .addInteger("limit", "Number of recent executions to show (default: 10, max: 50)", false);

        return new ToolDefinition(
                NAME,
                "View execution history for a scheduled task. Shows timestamps, success/failure status, " +
                        "duration, and error messages. Provide either task_id or task_name. " +
                        "Optional limit parameter controls how many recent executions to show (default: 10).",
                builder.build()
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (arguments == null) {
                return ToolResult.error("Missing parameters: provide either task_id or task_name");
            }

            CronJob job = findTask(arguments);
            if (job == null) {
                return ToolResult.error("Task not found. Use list_tasks to see available tasks.");
            }

            int limit = 10;
            if (arguments.has("limit")) {
                limit = arguments.get("limit").getAsInt();
                if (limit < 1 || limit > 50) {
                    limit = 10;
                }
            }

            List<TaskExecutionRecord> records = getTaskRepository().getExecutionHistory(job.getId());

            // Limit the results
            if (records.size() > limit) {
                records = records.subList(0, limit);
            }

            JsonArray historyArray = new JsonArray();
            int successCount = 0;
            int failureCount = 0;

            for (TaskExecutionRecord record : records) {
                JsonObject recordObj = new JsonObject();
                recordObj.addProperty("timestamp_ms", record.getStartTime());
                recordObj.addProperty("timestamp", dateFormat.format(new Date(record.getStartTime())));
                recordObj.addProperty("success", record.isSuccess());
                recordObj.addProperty("duration_ms", record.getDurationMillis());

                // Format duration
                long durationSec = record.getDurationMillis() / 1000;
                String durationText;
                if (durationSec < 60) {
                    durationText = String.format(Locale.US, "%.1fs", record.getDurationMillis() / 1000.0);
                } else {
                    long minutes = durationSec / 60;
                    long seconds = durationSec % 60;
                    durationText = minutes + "m " + seconds + "s";
                }
                recordObj.addProperty("duration", durationText);

                recordObj.addProperty("iterations", record.getIterations());
                recordObj.addProperty("tokens_used", record.getTokensUsed());

                if (!record.isSuccess() && record.getErrorMessage() != null) {
                    String errorMsg = record.getErrorMessage();
                    if (errorMsg.length() > 200) {
                        errorMsg = errorMsg.substring(0, 200) + "...";
                    }
                    recordObj.addProperty("error", errorMsg);
                    failureCount++;
                } else {
                    successCount++;
                }

                historyArray.add(recordObj);
            }

            JsonObject result = new JsonObject();
            result.addProperty("task_id", job.getId());
            result.addProperty("task_name", job.getName());
            result.add("history", historyArray);
            result.addProperty("total_shown", historyArray.size());
            result.addProperty("total_available", getTaskRepository().getExecutionHistory(job.getId()).size());
            result.addProperty("success_count", successCount);
            result.addProperty("failure_count", failureCount);

            // Create human-readable summary
            StringBuilder summary = new StringBuilder();
            summary.append("Execution History for '").append(job.getName()).append("'\n");
            summary.append("Showing ").append(historyArray.size()).append(" of ")
                    .append(getTaskRepository().getExecutionHistory(job.getId()).size()).append(" total executions\n\n");

            if (historyArray.size() == 0) {
                summary.append("No execution history recorded.");
            } else {
                for (int i = 0; i < historyArray.size(); i++) {
                    JsonObject record = historyArray.get(i).getAsJsonObject();
                    String icon = record.get("success").getAsBoolean() ? "✓" : "✗";
                    summary.append(icon).append(" ").append(record.get("timestamp").getAsString()).append("\n");
                    summary.append("   Duration: ").append(record.get("duration").getAsString());
                    summary.append(", Tokens: ").append(record.get("tokens_used").getAsInt());
                    summary.append(", Steps: ").append(record.get("iterations").getAsInt()).append("\n");

                    if (record.has("error")) {
                        summary.append("   Error: ").append(record.get("error").getAsString()).append("\n");
                    }

                    if (i < historyArray.size() - 1) {
                        summary.append("\n");
                    }
                }
            }

            result.addProperty("summary", summary.toString());

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to view task history", e);
            return ToolResult.error("Failed to view task history: " + e.getMessage());
        }
    }

    private CronJob findTask(JsonObject arguments) {
        String taskId = null;
        String taskName = null;

        if (arguments.has("task_id")) {
            taskId = arguments.get("task_id").getAsString().trim();
        }

        if (arguments.has("task_name")) {
            taskName = arguments.get("task_name").getAsString().trim();
        }

        if (taskId != null && !taskId.isEmpty()) {
            return getTaskRepository().getCronJob(taskId);
        }

        if (taskName != null && !taskName.isEmpty()) {
            // Search by name (case-insensitive partial match)
            List<CronJob> allJobs = getTaskRepository().getCronJobs();
            for (CronJob job : allJobs) {
                if (job.getName().toLowerCase().contains(taskName.toLowerCase())) {
                    return job;
                }
            }
        }

        return null;
    }
}
