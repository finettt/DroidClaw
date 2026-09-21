package io.finett.droidclaw.workflow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 3 of the load pipeline (spec §12): the cross-node rules JSON Schema
 * cannot express — references resolve, the graph is acyclic, tool and model names
 * exist, and every {@code output.<field>} is backed by an {@code output_schema}.
 *
 * <p>All of this runs before any API call, so a malformed workflow never spends
 * a token.
 */
public final class WorkflowValidator {

    private WorkflowValidator() {}

    /**
     * Validates a parsed workflow against the host environment.
     *
     * @param wf  a workflow that already passed stages 1 and 2
     * @param env host capabilities; may be {@code null}, in which tool and model
     *            existence checks are skipped (useful for pure structural tests)
     */
    public static WorkflowValidationResult validate(Workflow wf, WorkflowEnvironment env) {
        WorkflowValidationResult res = new WorkflowValidationResult();
        if (wf == null) {
            res.add(WorkflowIssue.error("no workflow to validate"));
            return res;
        }
        WorkflowGraph graph = WorkflowGraph.build(wf);

        checkEntry(wf, graph, res);
        checkCycles(graph, res);
        checkAgentReferences(wf, graph, env, res);
        checkTools(wf, env, res);
        checkModels(wf, env, res);
        checkStructuredFields(wf, res);
        checkWorkflowOutput(wf, res);
        addWarnings(wf, graph, res);
        return res;
    }

    /** Convenience overload when no host environment is available. */
    public static WorkflowValidationResult validate(Workflow wf) {
        return validate(wf, null);
    }

    // ---------- rule 7: entry ----------

    private static void checkEntry(Workflow wf, WorkflowGraph graph, WorkflowValidationResult res) {
        for (String e : wf.getEntry()) {
            if (!wf.hasAgent(e)) {
                res.add(WorkflowIssue.error(null, "entry", "entry '" + e + "' is not a declared agent"));
            }
        }
        // An entry node must be a root: if it waits on something, that thing has
        // no trigger and the run deadlocks before it starts.
        for (String e : wf.getEntry()) {
            if (!wf.hasAgent(e)) continue;
            Set<String> deps = graph.dependenciesOf(e);
            if (!deps.isEmpty()) {
                res.add(WorkflowIssue.error(e, "entry",
                        "entry node depends on " + deps + "; an entry node must have no dependencies "
                                + "or the run can never start"));
            }
        }
        if (wf.getEntry().isEmpty()) {
            Set<String> roots = graph.roots();
            if (roots.isEmpty()) {
                res.add(WorkflowIssue.error(null, "entry",
                        "no entry declared and no dependency-free agent exists to start from"));
            }
        }
    }

    // ---------- rule 8: cycles ----------

    private static void checkCycles(WorkflowGraph graph, WorkflowValidationResult res) {
        List<String> cycle = graph.detectCycle();
        if (cycle != null) {
            res.add(WorkflowIssue.error(null, "agents",
                    "dependency cycle: " + String.join(" -> ", cycle)));
        }
    }

    // ---------- rule 6: agent references ----------

    private static void checkAgentReferences(Workflow wf, WorkflowGraph graph, WorkflowEnvironment env,
                                             WorkflowValidationResult res) {
        for (String key : wf.getAgentKeys()) {
            WorkflowAgent a = wf.getAgent(key);
            if (a == null) continue;

            for (String d : a.getDependsOn()) {
                if (d.equals(key)) {
                    res.add(WorkflowIssue.error(key, "depends_on", "an agent cannot depend on itself"));
                } else if (!wf.hasAgent(d)) {
                    res.add(WorkflowIssue.error(key, "depends_on",
                            "'" + d + "' is not a declared agent"));
                }
            }

            WorkflowPrompt p = a.getPrompt();
            if (p != null) {
                if (p.getFromAgent() != null && !wf.hasAgent(p.getFromAgent())) {
                    res.add(WorkflowIssue.error(key, "prompt.from_agent",
                            "'" + p.getFromAgent() + "' is not a declared agent"));
                }
                checkTemplateRefs(key, "prompt", p.effectiveTemplate(), wf, res);
                checkTemplateRefs(key, "prompt.system", p.getSystem(), wf, res);
            }

            for (java.util.Map.Entry<String, String> in : a.getInputs().entrySet()) {
                checkTemplateRefs(key, "inputs." + in.getKey(), in.getValue(), wf, res);
            }
            if (a.hasGuard()) {
                checkTemplateRefs(key, "when", a.getWhen(), wf, res);
            }
        }
    }

    /**
     * Checks every {@code {{...}}} in one template: unknown agent roots, and
     * {@code {{inputs.x}}} names that this node never binds.
     */
    private static void checkTemplateRefs(String key, String field, String template, Workflow wf,
                                          WorkflowValidationResult res) {
        if (template == null) return;
        if (TemplateResolver.hasUnbalancedBraces(template)) {
            res.add(WorkflowIssue.error(key, field, "unbalanced {{ }} in template"));
        }
        WorkflowAgent self = wf.getAgent(key);
        Set<String> bound = self == null ? new LinkedHashSet<>() : self.getInputs().keySet();

        for (String expr : TemplateResolver.expressions(template)) {
            String[] parts = expr.split("\\.");
            String root = parts[0].trim();
            if (root.isEmpty()) {
                res.add(WorkflowIssue.error(key, field, "empty expression '{{}}'"));
                continue;
            }
            if ("workflow".equals(root)) {
                if (parts.length != 2 || !TemplateResolver.workflowFields().contains(parts[1].trim())) {
                    res.add(WorkflowIssue.error(key, field,
                            "'" + expr + "' is not valid; use workflow.goal, workflow.input or workflow.name"));
                }
                continue;
            }
            if ("inputs".equals(root)) {
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    res.add(WorkflowIssue.error(key, field, "'" + expr + "' must name a bound input"));
                } else if (!bound.contains(parts[1].trim())) {
                    res.add(WorkflowIssue.error(key, field,
                            "template uses inputs." + parts[1].trim() + " but this node binds no such input"));
                }
                continue;
            }
            if (!wf.hasAgent(root)) {
                res.add(WorkflowIssue.error(key, field,
                        "template references '" + root + "', which is not a declared agent"));
            }
        }
    }

    // ---------- rule 10: tools ----------

    private static void checkTools(Workflow wf, WorkflowEnvironment env, WorkflowValidationResult res) {
        if (env == null) return;
        List<String> defaultsAllowed = wf.getDefaults().getAllowedTools();
        checkToolList(null, "defaults.allowed_tools", defaultsAllowed, env, res);
        checkToolList(null, "defaults.denied_tools", wf.getDefaults().getDeniedTools(), env, res);
        for (String key : wf.getAgentKeys()) {
            WorkflowAgent a = wf.getAgent(key);
            checkToolList(key, "allowed_tools", a.getAllowedTools(), env, res);
            checkToolList(key, "denied_tools", a.getDeniedTools(), env, res);
        }
    }

    private static void checkToolList(String key, String field, List<String> tools,
                                      WorkflowEnvironment env, WorkflowValidationResult res) {
        if (tools == null) return;
        for (String t : tools) {
            if (!env.hasTool(t)) {
                res.add(WorkflowIssue.error(key, field,
                        "'" + t + "' is not a registered tool"));
            }
        }
    }

    // ---------- rule 9: models ----------

    private static void checkModels(Workflow wf, WorkflowEnvironment env, WorkflowValidationResult res) {
        if (env == null) return;
        checkModel(null, "defaults.model", wf.getDefaults().getModel(), env, res);
        for (String key : wf.getAgentKeys()) {
            WorkflowAgent a = wf.getAgent(key);
            String ref = wf.getDefaults().resolveModel(a.getModel());
            if (ref != null) checkModel(key, "model", ref, env, res);
        }
    }

    private static void checkModel(String key, String field, String ref,
                                   WorkflowEnvironment env, WorkflowValidationResult res) {
        if (ref == null) return;
        int i = ref.indexOf('/');
        if (i < 0) {
            res.add(WorkflowIssue.error(key, field,
                    "'" + ref + "' is not a model reference; use 'providerId/modelId'"));
            return;
        }
        String providerId = ref.substring(0, i);
        String modelId = ref.substring(i + 1);
        if (!env.hasModel(providerId, modelId)) {
            res.add(WorkflowIssue.error(key, field,
                    "'" + ref + "' does not match any configured provider/model"));
        }
    }

    // ---------- rule 11: structured fields ----------

    private static void checkStructuredFields(Workflow wf, WorkflowValidationResult res) {
        for (String key : wf.getAgentKeys()) {
            WorkflowAgent a = wf.getAgent(key);
            List<String> templates = new ArrayList<>();
            if (a.getPrompt() != null) {
                templates.add(a.getPrompt().effectiveTemplate());
                templates.add(a.getPrompt().getSystem());
            }
            templates.addAll(a.getInputs().values());
            if (a.hasGuard()) templates.add(a.getWhen());
            for (String t : templates) {
                if (t == null) continue;
                for (String producer : TemplateResolver.referencedAgents(t)) {
                    for (String fieldName : TemplateResolver.referencedFields(t, producer)) {
                        checkFieldBacked(key, producer, fieldName, wf, res);
                    }
                }
            }
        }
        // the workflow's own output template may read structured fields too
        if (wf.getOutput() != null) {
            for (String producer : TemplateResolver.referencedAgents(wf.getOutput())) {
                for (String fieldName : TemplateResolver.referencedFields(wf.getOutput(), producer)) {
                    checkFieldBacked(null, producer, fieldName, wf, res);
                }
            }
        }
    }

    private static void checkFieldBacked(String consumer, String producer, String fieldName,
                                         Workflow wf, WorkflowValidationResult res) {
        if (!wf.hasAgent(producer)) return;   // already reported as an unknown reference
        WorkflowAgent p = wf.getAgent(producer);
        String where = consumer == null ? "workflow output" : consumer;
        if (!p.hasOutputSchema()) {
            res.add(WorkflowIssue.error(consumer, null,
                    where + " reads '" + producer + ".output." + fieldName + "' but '" + producer
                            + "' declares no output_schema, so it produces plain text only"));
            return;
        }
        JsonObject schema = p.getOutputSchema();
        JsonElement props = schema.get("properties");
        if (props == null || !props.isJsonObject() || !props.getAsJsonObject().has(fieldName)) {
            res.add(WorkflowIssue.error(consumer, null,
                    where + " reads '" + producer + ".output." + fieldName
                            + "', which is not declared in " + producer + ".output_schema.properties"));
        }
    }

    // ---------- workflow output ----------

    private static void checkWorkflowOutput(Workflow wf, WorkflowValidationResult res) {
        if (wf.getOutput() == null) return;
        if (TemplateResolver.hasUnbalancedBraces(wf.getOutput())) {
            res.add(WorkflowIssue.error(null, "output", "unbalanced {{ }} in workflow output"));
        }
        for (String root : TemplateResolver.referencedAgents(wf.getOutput())) {
            if (!wf.hasAgent(root)) {
                res.add(WorkflowIssue.error(null, "output",
                        "workflow output references '" + root + "', which is not a declared agent"));
            }
        }
        for (String name : TemplateResolver.referencedInputs(wf.getOutput())) {
            res.add(WorkflowIssue.error(null, "output",
                    "workflow output uses inputs." + name + ", but 'inputs' bindings are per-node only"));
        }
    }

    // ---------- soft warnings ----------

    private static void addWarnings(Workflow wf, WorkflowGraph graph, WorkflowValidationResult res) {
        Set<String> entrySet = new LinkedHashSet<>(wf.getEntry());
        if (entrySet.isEmpty()) entrySet.addAll(graph.roots());

        for (String key : wf.getAgentKeys()) {
            WorkflowAgent a = wf.getAgent(key);
            if (graph.dependenciesOf(key).isEmpty() && !entrySet.contains(key)) {
                res.add(WorkflowIssue.warning(key, null,
                        "is a root (nothing upstream) but is not listed in 'entry'; it will still run first, "
                                + "which is often unintended"));
            }
            boolean consumed = !graph.dependentsOf(key).isEmpty();
            boolean inOutput = wf.getOutput() != null
                    && TemplateResolver.referencedAgents(wf.getOutput()).contains(key);
            if (!consumed && !inOutput) {
                res.add(WorkflowIssue.warning(key, null,
                        "its output is consumed by no other agent and by no workflow 'output'; "
                                + "it runs but its result is discarded"));
            }
            if (a.getAllowedTools() != null && a.getAllowedTools().isEmpty()
                    && WorkflowApprovalPolicy.defaultValue() != wf.getDefaults().resolveApproval(a.getApproval())
                    && wf.getDefaults().resolveApproval(a.getApproval()) == WorkflowApprovalPolicy.AUTO_APPROVE) {
                res.add(WorkflowIssue.warning(key, "approval",
                        "auto_approve is set but the node has no tools, so there is nothing to approve"));
            }
            if (a.hasGuard() && graph.dependentsOf(key).isEmpty() && wf.getOutput() != null
                    && TemplateResolver.referencedAgents(wf.getOutput()).contains(key)) {
                res.add(WorkflowIssue.warning(key, "when",
                        "is conditional and feeds the workflow 'output'; when the guard is false the output "
                                + "will substitute an empty string for this node"));
            }
        }
    }
}
