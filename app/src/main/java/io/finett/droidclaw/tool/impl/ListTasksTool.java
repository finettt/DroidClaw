package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

public class ListTasksTool implements Tool {

    private static final String TAG = "ListTasksTool";
    private static final String NAME = "list_tasks";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;

    public ListTasksTool(Context context) {
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
                .addString("filter", "Filter tasks by status: 'all', 'active', 'paused', 'disabled' (default: 'all')", false);

        return new ToolDefinition(
                NAME,
                "List scheduled background tasks with their status and statistics. " +
                        "Use this when the user asks about their automated tasks, scheduled jobs, " +
                        "or wants to see what tasks are configured. " +
                        "Optional filter: 'all' (default), 'active' (enabled and not paused), " +
                        "'paused' (temporarily stopped), 'disabled' (turned off).",
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
            String filter = "all";
            if (arguments != null && arguments.has("filter")) {
                filter = arguments.get("filter").getAsString().toLowerCase();
            }

            List<CronJob> allJobs = getTaskRepository().getCronJobs();
            JsonArray tasksArray = new JsonArray();
            int activeCount = 0;
            int pausedCount = 0;
            int disabledCount = 0;

            for (CronJob job : allJobs) {
                boolean isActive = job.isEnabled() && !job.isPaused();
                boolean isPaused = job.isEnabled() && job.isPaused();
                boolean isDisabled = !job.isEnabled();

                if (filter.equals("active") && !isActive) continue;
                if (filter.equals("paused") && !isPaused) continue;
                if (filter.equals("disabled") && !isDisabled) continue;

                JsonObject taskObj = new JsonObject();
                taskObj.addProperty("id", job.getId());
                taskObj.addProperty("name", job.getName());
                taskObj.addProperty("schedule", job.getSchedule());
                taskObj.addProperty("schedule_display", CronJobScheduler.formatScheduleForDisplay(job.getSchedule()));

                String status;
                String icon;
                if (isActive) {
                    status = "active";
                    icon = "✓";
                    activeCount++;
                } else if (isPaused) {
                    status = "paused";
                    icon = "⏸";
                    pausedCount++;
                } else {
                    status = "disabled";
                    icon = "✗";
                    disabledCount++;
                }

                taskObj.addProperty("status", status);
                taskObj.addProperty("status_icon", icon);

                int totalRuns = job.getSuccessCount() + job.getFailureCount();
                taskObj.addProperty("success_rate", job.getSuccessRate());
                taskObj.addProperty("total_runs", totalRuns);

                long lastRun = job.getLastRunTimestamp();
                if (lastRun > 0) {
                    taskObj.addProperty("last_run_ms", lastRun);
                    taskObj.addProperty("last_run_relative", formatRelativeTime(lastRun));
                } else {
                    taskObj.addProperty("last_run_relative", "Never run");
                }

                tasksArray.add(taskObj);
            }

            JsonObject result = new JsonObject();
            result.add("tasks", tasksArray);
            result.addProperty("total_count", tasksArray.size());
            result.addProperty("active_count", activeCount);
            result.addProperty("paused_count", pausedCount);
            result.addProperty("disabled_count", disabledCount);

            StringBuilder summary = new StringBuilder();
            summary.append("Scheduled Tasks: ").append(tasksArray.size()).append("\n\n");

            if (tasksArray.size() == 0) {
                summary.append("No scheduled tasks found.");
            } else {
                for (int i = 0; i < tasksArray.size(); i++) {
                    JsonObject task = tasksArray.get(i).getAsJsonObject();
                    summary.append(task.get("status_icon").getAsString()).append(" ");
                    summary.append(task.get("name").getAsString()).append("\n");
                    summary.append("   Schedule: ").append(task.get("schedule_display").getAsString()).append("\n");
                    summary.append("   Status: ").append(task.get("status").getAsString()).append("\n");
                    if (task.has("success_rate")) {
                        summary.append("   Success Rate: ").append(task.get("success_rate").getAsInt()).append("%");
                        summary.append(" (").append(task.get("total_runs").getAsInt()).append(" runs)\n");
                    }
                    summary.append("   Last Run: ").append(task.get("last_run_relative").getAsString()).append("\n");
                    if (i < tasksArray.size() - 1) {
                        summary.append("\n");
                    }
                }
            }

            result.addProperty("summary", summary.toString());

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to list tasks", e);
            return ToolResult.error("Failed to list tasks: " + e.getMessage());
        }
    }

    private String formatRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "Just now";
        }
    }
}
