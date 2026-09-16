package io.finett.droidclaw.workflow;

/**
 * What happens when a workflow node fails after all retry attempts.
 *
 * <p>{@link #SKIP} and {@link #CONTINUE} are not interchangeable: {@code SKIP}
 * abandons the node <em>and every transitive dependent</em>, while
 * {@code CONTINUE} degrades in place so dependents still run and observe
 * {@code {{node.status}} == "error"} with an empty output.
 *
 * <p>See {@code docs/features/workflow-schema.md} §10.3.
 */
public enum WorkflowErrorPolicy {
    /** Cancel the whole workflow. Default. */
    FAIL("fail"),
    /** Mark this node and all transitive dependents skipped; keep unrelated branches running. */
    SKIP("skip"),
    /** Mark this node errored; dependents still run and can degrade gracefully. */
    CONTINUE("continue");

    private final String jsonValue;

    WorkflowErrorPolicy(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static WorkflowErrorPolicy fromJson(String value) {
        if (value == null) return null;
        for (WorkflowErrorPolicy p : values()) {
            if (p.jsonValue.equals(value)) return p;
        }
        return null;
    }

    public static WorkflowErrorPolicy defaultValue() {
        return FAIL;
    }
}
