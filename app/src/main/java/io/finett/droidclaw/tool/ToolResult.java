package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

public class ToolResult {
    private final boolean success;
    private final String content;
    private final String error;

    private ToolResult(boolean success, String content, String error) {
        this.success = success;
        this.content = content;
        this.error = error;
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }

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