package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

import io.finett.droidclaw.shell.ExecPlan;

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

    /**
     * For tools that perform host execution (shell commands), build and return a normalised
     * {@link ExecPlan} from the LLM-provided arguments.
     *
     * <p>When non-null, the agent loop displays {@link ExecPlan#toApprovalDescription()} in
     * the approval dialog — showing the user the raw command verbatim (plus the resolved cwd
     * and plan hash) rather than some other rendering of the LLM-provided string.
     * This prevents prompt-injection attacks where the approval description differs from what
     * is actually executed.
     *
     * <p>Default implementation returns {@code null} — non-exec tools keep using
     * {@link #getApprovalDescription(JsonObject)}.
     */
    default ExecPlan buildExecPlan(JsonObject arguments) {
        return null;
    }

    /** Returns a human-readable description shown in approval dialogs for non-exec tools. */
    default String getApprovalDescription(JsonObject arguments) {
        return "Execute " + getName() + " tool";
    }
}