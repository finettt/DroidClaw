package io.finett.droidclaw.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate of every {@link WorkflowIssue} found while loading a workflow. */
public final class WorkflowValidationResult {

    private final List<WorkflowIssue> issues = new ArrayList<>();

    public void add(WorkflowIssue issue) {
        if (issue != null) issues.add(issue);
    }

    public void addAll(WorkflowValidationResult other) {
        if (other != null) issues.addAll(other.issues);
    }

    public List<WorkflowIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<WorkflowIssue> getErrors() {
        List<WorkflowIssue> out = new ArrayList<>();
        for (WorkflowIssue i : issues) if (i.isError()) out.add(i);
        return out;
    }

    public List<WorkflowIssue> getWarnings() {
        List<WorkflowIssue> out = new ArrayList<>();
        for (WorkflowIssue i : issues) if (!i.isError()) out.add(i);
        return out;
    }

    public boolean hasErrors() {
        for (WorkflowIssue i : issues) if (i.isError()) return true;
        return false;
    }

    public boolean isValid() {
        return !hasErrors();
    }

    /** Human-readable dump of every issue, one per line. Empty string when clean. */
    public String describe() {
        if (issues.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (WorkflowIssue i : issues) sb.append(i).append('\n');
        return sb.toString();
    }
}
