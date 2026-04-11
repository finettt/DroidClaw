package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for permanently deleting a scheduled task.
 */
public class DeleteTaskTool implements Tool {

    private static final String TAG = "DeleteTaskTool";
    private static final String NAME = "delete_task";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;
    private CronJobScheduler scheduler;

    public DeleteTaskTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private TaskRepository getTaskRepository() {
        if (taskRepository == null) {
            taskRepository = new TaskRepository(context);
        }
        return taskRepository;
    }

    private CronJobScheduler getScheduler() {
        if (scheduler == null) {
            scheduler = new CronJobScheduler(context);
        }
        return scheduler;
    }

    private ToolDefinition createDefinition() {
        ParametersBuilder builder = new ParametersBuilder()
                .addString("task_id", "ID of the task to delete (use this if you have the exact ID)", false)
                .addString("task_name", "Name of the task to delete (will find by name matching)", false)
                .addBoolean("confirm", "Confirmation flag - must be true to confirm deletion", true);

        return new ToolDefinition(
                NAME,
                "Permanently delete a scheduled task and all its execution history. " +
                        "This action cannot be undone. Provide either task_id or task_name, " +
                        "and confirm=true to confirm the deletion. Use list_tasks first if you need to find the task.",
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
    public boolean requiresApproval() {
        return true; // Deletion should always require approval
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        if (arguments != null) {
            String taskName = arguments.has("task_name") ? arguments.get("task_name").getAsString() : "Unknown";
            if (!arguments.has("task_name")) {
                String taskId = arguments.has("task_id") ? arguments.get("task_id").getAsString() : "Unknown";
                return "Permanently delete task (ID: " + taskId + ") and all its history";
            }
            return "Permanently delete task '" + taskName + "' and all its history";
        }
        return "Permanently delete a scheduled task";
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (arguments == null) {
                return ToolResult.error("Missing parameters: provide either task_id or task_name, and confirm=true");
            }

            if (!arguments.has("confirm") || !arguments.get("confirm").getAsBoolean()) {
                return ToolResult.error("Deletion not confirmed. Set confirm=true to proceed with deletion.");
            }

            CronJob job = findTask(arguments);
            if (job == null) {
                return ToolResult.error("Task not found. Use list_tasks to see available tasks.");
            }

            String taskName = job.getName();
            String taskId = job.getId();

            // Delete the job and its history
            getTaskRepository().deleteCronJob(taskId);
            getTaskRepository().deleteExecutionRecords(taskId);
            getScheduler().cancelJob(taskId);

            Log.d(TAG, "Deleted task: " + taskName);

            JsonObject result = new JsonObject();
            result.addProperty("status", "deleted");
            result.addProperty("task_id", taskId);
            result.addProperty("task_name", taskName);
            result.addProperty("message", "Task '" + taskName + "' deleted permanently");

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to delete task", e);
            return ToolResult.error("Failed to delete task: " + e.getMessage());
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
            java.util.List<CronJob> allJobs = getTaskRepository().getCronJobs();
            for (CronJob job : allJobs) {
                if (job.getName().toLowerCase().contains(taskName.toLowerCase())) {
                    return job;
                }
            }
        }

        return null;
    }
}
