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
 * Tool for resuming a paused scheduled task.
 */
public class ResumeTaskTool implements Tool {

    private static final String TAG = "ResumeTaskTool";
    private static final String NAME = "resume_task";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;
    private CronJobScheduler scheduler;

    public ResumeTaskTool(Context context) {
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
                .addString("task_id", "ID of the task to resume (use this if you have the exact ID)", false)
                .addString("task_name", "Name of the task to resume (will find by name matching)", false);

        return new ToolDefinition(
                NAME,
                "Resume a previously paused scheduled task. The task will start executing again on its schedule. " +
                        "Provide either task_id or task_name. Use list_tasks first if you need to find the task.",
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

            if (!job.isPaused() && job.isEnabled()) {
                JsonObject result = new JsonObject();
                result.addProperty("status", "already_active");
                result.addProperty("message", "Task '" + job.getName() + "' is already running");
                return ToolResult.success(result);
            }

            if (!job.isEnabled()) {
                // Re-enable it
                job.setEnabled(true);
            }

            // Resume the job
            job.setPaused(false);
            getTaskRepository().updateCronJob(job);
            getScheduler().scheduleJob(job);

            Log.d(TAG, "Resumed task: " + job.getName());

            JsonObject result = new JsonObject();
            result.addProperty("status", "resumed");
            result.addProperty("task_id", job.getId());
            result.addProperty("task_name", job.getName());
            result.addProperty("message", "Task '" + job.getName() + "' resumed successfully");

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to resume task", e);
            return ToolResult.error("Failed to resume task: " + e.getMessage());
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
