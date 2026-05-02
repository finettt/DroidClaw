package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.service.BackgroundProcess;
import io.finett.droidclaw.service.BackgroundProcessManager;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Lists all background processes managed by the agent, optionally filtered by status.
 *
 * <p>Returns an array of process records including their ID, tool name, status,
 * timing info, and a preview of the result/error.
 */
public class ListBackgroundProcessesTool implements Tool {

    private static final String TOOL_NAME = "list_background_processes";
    private static final int RESULT_PREVIEW_LENGTH = 200;

    private final Context context;

    public ListBackgroundProcessesTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("status",
                        "Filter by status: 'running', 'completed', 'failed', 'killed', or 'all'. Default: 'all'",
                        false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "List background processes that were dispatched with background=true. "
                + "Returns each process's ID, tool name, status, timing info, and "
                + "a preview of the result. Use this to check on long-running tasks. "
                + "Optionally filter by status.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        String statusFilter = "all";
        if (arguments != null && arguments.has("status")
                && !arguments.get("status").isJsonNull()) {
            statusFilter = arguments.get("status").getAsString().trim().toLowerCase();
        }

        List<BackgroundProcess> processes;
        if ("all".equals(statusFilter)) {
            processes = manager.listAll();
        } else {
            processes = manager.listByStatus(statusFilter);
        }

        JsonArray array = new JsonArray();
        for (BackgroundProcess p : processes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("process_id", p.getId());
            obj.addProperty("tool_name", p.getToolName());
            obj.addProperty("status", p.getStatus());
            obj.addProperty("started_at", p.getStartedAt());

            if (p.getFinishedAt() > 0) {
                obj.addProperty("finished_at", p.getFinishedAt());
                obj.addProperty("duration_ms", p.getDurationMs());
            }

            if (p.getResult() != null) {
                String preview = p.getResult().length() > RESULT_PREVIEW_LENGTH
                        ? p.getResult().substring(0, RESULT_PREVIEW_LENGTH) + "..."
                        : p.getResult();
                obj.addProperty("result_preview", preview);
            }

            if (p.getError() != null) {
                obj.addProperty("error", p.getError());
            }

            array.add(obj);
        }

        JsonObject result = new JsonObject();
        result.addProperty("total", processes.size());
        result.addProperty("running_count", manager.runningCount());
        result.add("processes", array);

        return ToolResult.success(result);
    }
}