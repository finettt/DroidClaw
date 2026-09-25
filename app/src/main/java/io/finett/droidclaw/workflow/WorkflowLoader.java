package io.finett.droidclaw.workflow;

/**
 * Runs the full three-stage load pipeline of spec §12 and returns a workflow
 * together with its dependency graph and every issue found.
 *
 * <pre>
 *   raw JSON -> NORMALISE -> SCHEMA/STRUCTURE -> SEMANTIC -> runnable Workflow
 * </pre>
 *
 * <p>Callers must check {@link Result#isRunnable()} before executing: a workflow
 * with errors never reaches the LLM.
 */
public final class WorkflowLoader {

    private WorkflowLoader() {}

    public static final class Result {
        private final Workflow workflow;
        private final WorkflowGraph graph;
        private final WorkflowValidationResult issues;

        Result(Workflow workflow, WorkflowGraph graph, WorkflowValidationResult issues) {
            this.workflow = workflow;
            this.graph = graph;
            this.issues = issues;
        }

        /** The parsed workflow, or {@code null} if stage 1/2 failed. */
        public Workflow getWorkflow() { return workflow; }

        /** The dependency graph, or {@code null} if the workflow did not parse. */
        public WorkflowGraph getGraph() { return graph; }

        public WorkflowValidationResult getIssues() { return issues; }

        public boolean isRunnable() { return workflow != null && graph != null && issues.isValid(); }

        public String describeProblems() { return issues.describe(); }
    }

    public static Result load(String json, WorkflowEnvironment env) {
        WorkflowParser.Result parsed = WorkflowParser.parse(json);
        WorkflowValidationResult all = new WorkflowValidationResult();
        all.addAll(parsed.getIssues());
        if (parsed.getWorkflow() == null || all.hasErrors()) {
            return new Result(null, null, all);
        }
        Workflow wf = parsed.getWorkflow();
        all.addAll(WorkflowValidator.validate(wf, env));
        WorkflowGraph graph = all.hasErrors() ? null : WorkflowGraph.build(wf);
        return new Result(all.hasErrors() ? null : wf, graph, all);
    }

    /** Loads without host-environment checks (tool/model existence are skipped). */
    public static Result load(String json) {
        return load(json, null);
    }
}
