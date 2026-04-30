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
     * the approval dialog — showing the user the <em>normalised</em> plan (canonical exe path,
     * tokenised argv, resolved cwd, plan hash) rather than the raw LLM-provided string.
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