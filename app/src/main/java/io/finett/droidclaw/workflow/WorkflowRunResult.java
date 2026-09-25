package io.finett.droidclaw.workflow;

import java.util.Collections;
import java.util.Map;

public final class WorkflowRunResult {

    public enum Status { SUCCESS, FAILED, CANCELLED }

    private final Status status;
    private final String output;
    private final String error;
    private final Map<String, TemplateResolver.NodeResult> nodeResults;
    private final int totalTokens;

    private WorkflowRunResult(Status status, String output, String error,
                              Map<String, TemplateResolver.NodeResult> nodeResults, int totalTokens) {
        this.status = status;
        this.output = output;
        this.error = error;
        this.nodeResults = nodeResults == null ? Collections.emptyMap() : Collections.unmodifiableMap(nodeResults);
        this.totalTokens = totalTokens;
    }

    public static WorkflowRunResult success(String output, Map<String, TemplateResolver.NodeResult> results, int totalTokens) {
        return new WorkflowRunResult(Status.SUCCESS, output, null, results, totalTokens);
    }

    public static WorkflowRunResult failed(String error, Map<String, TemplateResolver.NodeResult> results) {
        return new WorkflowRunResult(Status.FAILED, null, error, results, 0);
    }

    public static WorkflowRunResult cancelled(Map<String, TemplateResolver.NodeResult> results) {
        return new WorkflowRunResult(Status.CANCELLED, null, "Workflow cancelled", results, 0);
    }

    public Status getStatus() { return status; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public Map<String, TemplateResolver.NodeResult> getNodeResults() { return nodeResults; }
    public int getTotalTokens() { return totalTokens; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
}
