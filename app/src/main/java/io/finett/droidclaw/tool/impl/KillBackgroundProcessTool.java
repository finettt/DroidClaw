package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonObject;

import io.finett.droidclaw.service.BackgroundProcess;
import io.finett.droidclaw.service.BackgroundProcessManager;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Kills a running background process by its process_id.
 *
 * <p>Only processes in the "running" state can be killed. Processes that have already
 * completed, failed, or been killed return a descriptive error.
 */
public class KillBackgroundProcessTool implements Tool {

    private static final String TOOL_NAME = "kill_background_process";

    private final Context context;

    public KillBackgroundProcessTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        String processId = arguments.has("process_id")
                ? arguments.get("process_id").getAsString() : "unknown";
        return "Kill background process: " + processId;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("process_id", "The ID of the background process to kill", true)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Kill a running background process by its process_id. "
                + "Only processes with status 'running' can be killed. "
                + "Returns whether the kill was successful.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("process_id")) {
            return ToolResult.error("Missing required parameter: process_id");
        }

        String processId = arguments.get("process_id").getAsString().trim();
        if (processId.isEmpty()) {
            return ToolResult.error("process_id must not be empty");
        }

        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);
        BackgroundProcess process = manager.get(processId);

        if (process == null) {
            return ToolResult.error("No background process found with id: " + processId);
        }

        if (!process.isRunning()) {
            JsonObject result = new JsonObject();
            result.addProperty("killed", false);
            result.addProperty("process_id", processId);
            result.addProperty("status", process.getStatus());
            result.addProperty("message", "Process is not running (status: " + process.getStatus() + ")");
            return ToolResult.success(result);
        }

        boolean killed = manager.kill(processId);

        JsonObject result = new JsonObject();
        result.addProperty("killed", killed);
        result.addProperty("process_id", processId);
        result.addProperty("status", killed
                ? BackgroundProcess.STATUS_KILLED
                : process.getStatus());
        if (!killed) {
            result.addProperty("message", "Kill request sent but process may have finished concurrently");
        }
        return ToolResult.success(result);
    }
}