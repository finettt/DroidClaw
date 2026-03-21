package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

/**
 * Represents the result of a tool execution.
 * This is sent back to the LLM as a "tool" role message.
 */
public class ToolResult {
    private final boolean success;
    private final String content;
    private final String error;

    private ToolResult(boolean success, String content, String error) {
        this.success = success;
        this.content = content;
        this.error = error;
    }

    /**
     * Creates a successful tool result.
     * 
     * @param content Result content to send to the LLM
     * @return ToolResult instance
     */
    public static ToolResult success(String content) {
        return new ToolResult(true, content, null);
    }

    /**
     * Creates a failed tool result.
     * 
     * @param error Error message
     * @return ToolResult instance
     */
    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }

    /**
     * Creates a successful tool result from a JSON object.
     * 
     * @param json JSON content
     * @return ToolResult instance
     */
    public static ToolResult success(JsonObject json) {
        return new ToolResult(true, json.toString(), null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getError() {
        return error;
    }

    /**
     * Converts this result to a JSON string for the LLM.
     * 
     * @return JSON string representation
     */
    public String toJson() {
        if (success) {
            return content != null ? content : "";
        } else {
            JsonObject errorJson = new JsonObject();
            errorJson.addProperty("error", error != null ? error : "Unknown error");
            return errorJson.toString();
        }
    }

    @Override
    public String toString() {
        return success ? "Success: " + content : "Error: " + error;
    }
}