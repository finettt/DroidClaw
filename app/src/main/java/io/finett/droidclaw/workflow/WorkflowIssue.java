package io.finett.droidclaw.workflow;

/**
 * A single validation or normalisation finding.
 *
 * <p>{@code agentKey} and {@code field} may be {@code null} when the finding is
 * file-level rather than node-level. Messages are written to name the offending
 * key and field precisely, per the spec's "fail loud" principle.
 */
public final class WorkflowIssue {

    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String agentKey;
    private final String field;
    private final String message;

    public WorkflowIssue(Severity severity, String agentKey, String field, String message) {
        this.severity = severity;
        this.agentKey = agentKey;
        this.field = field;
        this.message = message;
    }

    public static WorkflowIssue error(String agentKey, String field, String message) {
        return new WorkflowIssue(Severity.ERROR, agentKey, field, message);
    }

    public static WorkflowIssue error(String message) {
        return new WorkflowIssue(Severity.ERROR, null, null, message);
    }

    public static WorkflowIssue warning(String agentKey, String field, String message) {
        return new WorkflowIssue(Severity.WARNING, agentKey, field, message);
    }

    public static WorkflowIssue warning(String message) {
        return new WorkflowIssue(Severity.WARNING, null, null, message);
    }

    public Severity getSeverity() { return severity; }
    public String getAgentKey() { return agentKey; }
    public String getField() { return field; }
    public String getMessage() { return message; }

    public boolean isError() { return severity == Severity.ERROR; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(severity == Severity.ERROR ? "ERROR" : "WARN ");
        if (agentKey != null) {
            sb.append('[').append(agentKey);
            if (field != null) sb.append('.').append(field);
            sb.append("] ");
        } else if (field != null) {
            sb.append('[').append(field).append("] ");
        }
        return sb.append(message).toString();
    }
}
