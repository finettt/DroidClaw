package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import java.util.UUID;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.scheduler.CronJobScheduler;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

public class CreateTaskTool implements Tool {

    private static final String TAG = "CreateTaskTool";
    private static final String NAME = "create_task";

    private final ToolDefinition definition;
    private final Context context;
    private TaskRepository taskRepository;
    private CronJobScheduler scheduler;

    public CreateTaskTool(Context context) {
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
                .addString("name", "Name for the scheduled task (e.g., 'Daily Email Summary')", true)
                .addString("prompt", "The prompt/instructions for the AI to execute when the task runs", true)
                .addString("schedule", "Schedule: 'hourly', 'daily', 'weekly', 'daily@HH:MM' (e.g., 'daily@08:00'), 'weekly@DAY@HH:MM' (e.g., 'weekly@MON@09:00'), 'every_N_unit' (e.g., 'every_6_hours'), or milliseconds", true);

        return new ToolDefinition(
                NAME,
                "Create a new scheduled background task that executes an AI prompt automatically. " +
                        "Use this when the user wants to set up automated task execution. " +
                        "Schedule formats: 'hourly', 'daily', 'weekly', 'daily@08:00', 'weekly@MON@09:00', " +
                        "'every_6_hours', or milliseconds (e.g., '3600000' for hourly).",
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
        return true;
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        if (arguments != null) {
            String name = arguments.has("name") ? arguments.get("name").getAsString() : "Unknown";
            String schedule = arguments.has("schedule") ? arguments.get("schedule").getAsString() : "Unknown";
            return "Create automated task '" + name + "' to run on schedule: " + schedule;
        }
        return "Create a new scheduled background task";
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (arguments == null || !arguments.has("name") || !arguments.has("prompt") || !arguments.has("schedule")) {
                return ToolResult.error("Missing required parameters: name, prompt, and schedule are all required");
            }

            String name = arguments.get("name").getAsString().trim();
            String prompt = arguments.get("prompt").getAsString().trim();
            String schedule = arguments.get("schedule").getAsString().trim();

            if (name.isEmpty() || prompt.isEmpty() || schedule.isEmpty()) {
                return ToolResult.error("Parameters cannot be empty");
            }

            if (!isValidSchedule(schedule)) {
                return ToolResult.error(
                        "Invalid schedule format: '" + schedule + "'. Valid formats: 'hourly', 'daily', 'weekly', " +
                                "'daily@HH:MM' (e.g., 'daily@08:00'), 'weekly@DAY@HH:MM' (e.g., 'weekly@MON@09:00'), " +
                                "'every_N_unit' (e.g., 'every_6_hours'), or milliseconds"
                );
            }

            CronJob job = new CronJob();
            job.setId(UUID.randomUUID().toString());
            job.setName(name);
            job.setPrompt(prompt);
            job.setSchedule(schedule);
            job.setEnabled(true);
            job.setPaused(false);
            job.setCreatedAt(System.currentTimeMillis());

            getTaskRepository().saveCronJob(job);
            getScheduler().scheduleJob(job);

            Log.d(TAG, "Created task: " + name + " with schedule: " + schedule);

            JsonObject result = new JsonObject();
            result.addProperty("status", "created");
            result.addProperty("task_id", job.getId());
            result.addProperty("name", job.getName());
            result.addProperty("schedule", job.getSchedule());
            result.addProperty("schedule_display", CronJobScheduler.formatScheduleForDisplay(schedule));
            result.addProperty("enabled", true);

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to create task", e);
            return ToolResult.error("Failed to create task: " + e.getMessage());
        }
    }

    private boolean isValidSchedule(String schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return false;
        }

        if (schedule.equalsIgnoreCase("hourly") ||
                schedule.equalsIgnoreCase("daily") ||
                schedule.equalsIgnoreCase("weekly")) {
            return true;
        }

        if (schedule.matches("daily@\\d{1,2}:\\d{2}")) {
            String[] parts = schedule.split("@");
            String[] timeParts = parts[1].split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        }

        if (schedule.matches("weekly@(MON|TUE|WED|THU|FRI|SAT|SUN)@\\d{1,2}:\\d{2}")) {
            String[] parts = schedule.split("@");
            String[] timeParts = parts[2].split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        }

        if (schedule.matches("every_\\d+_(hours?|minutes?|days?)")) {
            return true;
        }

        try {
            long ms = Long.parseLong(schedule);
            return ms > 0;
        } catch (NumberFormatException e) {
            // not a number — fall through
        }

        return false;
    }
}
