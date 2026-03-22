package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

/**
 * Interface for all agent tools.
 * Tools are capabilities that the agent can use to interact with the system.
 */
public interface Tool {
    /**
     * Gets the name of the tool.
     * This is used in the tool_calls from the LLM.
     * 
     * @return Tool name (e.g., "read_file", "execute_shell")
     */
    String getName();

    /**
     * Gets the tool definition for the OpenAI API.
     * This defines the tool's schema for function calling.
     * 
     * @return ToolDefinition object
     */
    ToolDefinition getDefinition();

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments JSON object containing tool arguments (typically includes tool_call_id from LLM)
     * @return ToolResult containing the execution result
     */
    ToolResult execute(JsonObject arguments);
}