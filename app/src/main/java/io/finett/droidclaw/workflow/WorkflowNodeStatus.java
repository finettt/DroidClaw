package io.finett.droidclaw.workflow;

/** Terminal state of a workflow node, exposed to templates as {@code {{node.status}}. */
public enum WorkflowNodeStatus {
    OK("ok"),
    SKIPPED("skipped"),
    ERROR("error");

    private final String jsonValue;

    WorkflowNodeStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }
}
