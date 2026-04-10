package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for explicitly marking a heartbeat check as healthy.
 * When the agent determines all systems are normal, it can call this tool
 * to explicitly set the HEARTBEAT_OK status.
 *
 * This provides an alternative to including HEARTBEAT_OK in the text response.
 */
public class HeartbeatOkTool implements Tool {

    private static final String NAME = "heartbeat_ok";

    private final ToolDefinition definition;

    public HeartbeatOkTool() {
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        return new ToolDefinition(
            NAME,
            "Mark the current heartbeat check as healthy. Call this when all system checks " +
            "are normal and nothing requires immediate attention. This silently records the " +
            "heartbeat as successful without triggering a notification.",
            null // No parameters required
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
        // Return success - the HeartbeatWorker will detect this tool result
        // and mark the heartbeat as healthy
        return ToolResult.success("HEARTBEAT_OK: System health check passed. All systems normal.");
    }
}
