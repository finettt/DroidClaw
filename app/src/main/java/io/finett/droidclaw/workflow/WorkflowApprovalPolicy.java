package io.finett.droidclaw.workflow;

/**
 * Per-node tool approval policy for a workflow run.
 *
 * <p>Workflows frequently run unattended (background service, cron), where a
 * blocking approval dialog has nobody to answer it. The default is therefore
 * {@link #DENY_WRITES}, the only policy that is safe in every launch context.
 *
 * <p>See {@code docs/features/workflow-schema.md} §10.1.
 */
public enum WorkflowApprovalPolicy {
    /** Follow the global {@code AgentConfig} settings. Foreground runs only. */
    INHERIT("inherit"),
    /** Auto-approve every allowed tool. Requires explicit opt-in. */
    AUTO_APPROVE("auto_approve"),
    /** Read-only tools run freely; approval-requiring tools are auto-rejected. Default. */
    DENY_WRITES("deny_writes"),
    /** Reject all tool calls; the node is text-only reasoning. */
    STRICT("strict");

    private final String jsonValue;

    WorkflowApprovalPolicy(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    /** Parses a JSON value, or returns {@code null} when it is not a valid policy. */
    public static WorkflowApprovalPolicy fromJson(String value) {
        if (value == null) return null;
        for (WorkflowApprovalPolicy p : values()) {
            if (p.jsonValue.equals(value)) return p;
        }
        return null;
    }

    public static WorkflowApprovalPolicy defaultValue() {
        return DENY_WRITES;
    }
}
