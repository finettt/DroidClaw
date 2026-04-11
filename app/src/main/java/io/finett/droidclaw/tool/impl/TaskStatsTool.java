package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

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
 * Tool for getting aggregate statistics across all tasks or a specific task.
 */
public class TaskStatsTool implements Tool {

    private static final String TAG = "TaskStatsTool";
    private static final String NAME = "task_stats";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;

    public TaskStatsTool(Context context) {
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
                .addString("task_id", "ID of specific task to get stats for (omit for all tasks)", false)
                .addString("task_name", "Name of specific task to get stats for (omit for all tasks)", false);

        return new ToolDefinition(
                NAME,
                "Get aggregate statistics for scheduled tasks. Shows total executions, success rate, " +
                        "average duration, and success/failure counts. Provide task_id or task_name for " +
                        "a specific task, or omit both for overall statistics across all tasks.",
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
            List<TaskExecutionRecord> records;
            String taskName = "All Tasks";
            String taskId = null;

            if (arguments != null && arguments.has("task_id")) {
                taskId = arguments.get("task_id").getAsString().trim();
                CronJob job = getTaskRepository().getCronJob(taskId);
                if (job != null) {
                    taskName = job.getName();
                    records = getTaskRepository().getExecutionHistory(taskId);
                } else {
                    return ToolResult.error("Task not found. Use list_tasks to see available tasks.");
                }
            } else if (arguments != null && arguments.has("task_name")) {
                String searchName = arguments.get("task_name").getAsString().trim();
                CronJob job = findTaskByName(searchName);
                if (job != null) {
                    taskName = job.getName();
                    taskId = job.getId();
                    records = getTaskRepository().getExecutionHistory(taskId);
                } else {
                    return ToolResult.error("Task not found. Use list_tasks to see available tasks.");
                }
            } else {
                // All tasks
                records = getTaskRepository().getAllExecutionRecords();
            }

            // Calculate statistics
            int totalExecutions = records.size();
            int successCount = 0;
            int failureCount = 0;
            long totalDuration = 0;
            int durationCount = 0;
            long totalTokens = 0;
            long totalIterations = 0;

            for (TaskExecutionRecord record : records) {
                if (record.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                }

                if (record.getDurationMillis() > 0) {
                    totalDuration += record.getDurationMillis();
                    durationCount++;
                }

                totalTokens += record.getTokensUsed();
                totalIterations += record.getIterations();
            }

            int successRate = totalExecutions > 0 ? (successCount * 100) / totalExecutions : 0;
            long avgDuration = durationCount > 0 ? totalDuration / durationCount : 0;
            double avgTokens = totalExecutions > 0 ? (double) totalTokens / totalExecutions : 0;
            double avgIterations = totalExecutions > 0 ? (double) totalIterations / totalExecutions : 0;

            // Format average duration
            String avgDurationText;
            long avgSec = avgDuration / 1000;
            if (avgSec < 60) {
                avgDurationText = String.format(Locale.US, "%.1fs", avgDuration / 1000.0);
            } else {
                long minutes = avgSec / 60;
                long seconds = avgSec % 60;
                avgDurationText = minutes + "m " + seconds + "s";
            }

            JsonObject result = new JsonObject();
            result.addProperty("task_name", taskName);
            if (taskId != null) {
                result.addProperty("task_id", taskId);
            }
            result.addProperty("total_executions", totalExecutions);
            result.addProperty("success_count", successCount);
            result.addProperty("failure_count", failureCount);
            result.addProperty("success_rate", successRate);
            result.addProperty("avg_duration_ms", avgDuration);
            result.addProperty("avg_duration", avgDurationText);
            result.addProperty("avg_tokens", String.format(Locale.US, "%.0f", avgTokens));
            result.addProperty("avg_iterations", String.format(Locale.US, "%.1f", avgIterations));

            // Create human-readable summary
            StringBuilder summary = new StringBuilder();
            summary.append("Statistics: ").append(taskName).append("\n\n");

            if (totalExecutions == 0) {
                summary.append("No executions recorded yet.");
            } else {
                summary.append("Total Executions: ").append(totalExecutions).append("\n");
                summary.append("Success Rate: ").append(successRate).append("%")
                        .append(" (").append(successCount).append(" succeeded, ")
                        .append(failureCount).append(" failed)\n");
                summary.append("Average Duration: ").append(avgDurationText).append("\n");
                summary.append("Average Tokens: ").append(String.format(Locale.US, "%.0f", avgTokens)).append("\n");
                summary.append("Average Steps: ").append(String.format(Locale.US, "%.1f", avgIterations));
            }

            result.addProperty("summary", summary.toString());

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to get task stats", e);
            return ToolResult.error("Failed to get task stats: " + e.getMessage());
        }
    }

    private CronJob findTaskByName(String name) {
        List<CronJob> allJobs = getTaskRepository().getCronJobs();
        for (CronJob job : allJobs) {
            if (job.getName().toLowerCase().contains(name.toLowerCase())) {
                return job;
            }
        }
        return null;
    }
}
