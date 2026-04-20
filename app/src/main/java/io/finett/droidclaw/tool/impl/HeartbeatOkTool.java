package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

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
            null
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
        return ToolResult.success("{\"HEARTBEAT_OK\": true}");
    }
}
