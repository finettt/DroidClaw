package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

public interface Tool {
    String getName();

    ToolDefinition getDefinition();

    ToolResult execute(JsonObject arguments);

    /**
     * Destructive operations (shell execution, file deletion, file overwriting) should return true.
     */
    default boolean requiresApproval() {
        return false;
    }

    /** Returns a human-readable description shown in approval dialogs. */
    default String getApprovalDescription(JsonObject arguments) {
        return "Execute " + getName() + " tool";
    }
}