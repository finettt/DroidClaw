package io.finett.droidclaw.workflow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Covers load-pipeline stage 3: the cross-node rules JSON Schema cannot express (spec §12). */
public class WorkflowValidatorTest {

    private static final WorkflowEnvironment ENV = WorkflowTestEnv.withExampleModels();

    private WorkflowLoader.Result load(String json) {
        return WorkflowLoader.load(json, ENV);
    }

    private String errors(String json) {
        WorkflowLoader.Result r = load(json);
        StringBuilder sb = new StringBuilder();
        for (WorkflowIssue i : r.getIssues().getErrors()) sb.append(i).append('\n');
        return sb.toString();
    }

    private void assertError(String json, String needle) {
        WorkflowLoader.Result r = load(json);
        assertFalse("expected failure", r.isRunnable());
        String e = errors(json);
        assertTrue("expected error containing '" + needle + "' but got:\n" + e, e.contains(needle));
    }

    private static String wf(String agents) {
        return "{\"version\":1,\"goal\":\"g\",\"agents\":{" + agents + "}}";
    }

    // ---------- rule 6: references ----------

    @Test
    public void unknownFromAgentRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"from_agent\":\"ghost\"}}"), "'ghost' is not a declared agent");
    }

    @Test
    public void unknownDependsOnRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"depends_on\":[\"nope\"]}"),
                "'nope' is not a declared agent");
    }

    @Test
    public void selfDependencyRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"depends_on\":[\"a\"]}"),
                "cannot depend on itself");
    }

    @Test
    public void unknownTemplateAgentRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"template\":\"{{ghost.output}}\"}}"),
                "references 'ghost', which is not a declared agent");
    }

    @Test
    public void unknownInputExpressionAgentRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{inputs.v}}\"},\"inputs\":{\"v\":\"{{ghost.output}}\"}}"),
                "references 'ghost', which is not a declared agent");
    }

    @Test
    public void unboundLocalInputRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{inputs.missing}}\"},\"inputs\":{\"other\":\"{{a.output}}\"}}"),
                "binds no such input");
    }

    @Test
    public void badWorkflowNamespaceFieldRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"template\":\"{{workflow.nope}}\"}}"), "is not valid");
    }

    // ---------- rule 7: entry ----------

    @Test
    public void unknownEntryRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"entry\":\"ghost\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}",
                "entry 'ghost' is not a declared agent");
    }

    @Test
    public void entryNodeWithDependenciesRejected() {
        // an entry node that waits on something can never be triggered
        assertError("{\"version\":1,\"goal\":\"g\",\"entry\":\"b\",\"agents\":{"
                        + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                        + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}}}}",
                "an entry node must have no dependencies");
    }

    // ---------- rule 8: cycles ----------

    @Test
    public void cycleRejectedWithPath() {
        String e = errors(wf("\"a\":{\"prompt\":{\"template\":\"{{b.output}}\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}}"));
        assertTrue(e, e.contains("dependency cycle"));
        assertTrue("cycle path should be named: " + e, e.contains("->"));
    }

    // ---------- rules 9 and 10: models and tools ----------

    @Test
    public void unknownToolRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"allowed_tools\":[\"read_file\",\"delete_everything\"]}"),
                "'delete_everything' is not a registered tool");
    }

    @Test
    public void knownToolsAccepted() {
        WorkflowLoader.Result r = load(wf(
                "\"a\":{\"prompt\":{\"text\":\"x\"},\"allowed_tools\":[\"read_file\",\"search_files\"]}"));
        assertEquals(r.describeProblems(), 0, r.getIssues().getErrors().size());
        assertTrue(r.isRunnable());
    }

    @Test
    public void unconfiguredModelRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"model\":\"acme/does-not-exist\"}"),
                "does not match any configured provider/model");
    }

    @Test
    public void configuredModelAccepted() {
        assertTrue(load(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"model\":\"openrouter/openai/gpt-4o-mini\"}"))
                .isRunnable());
    }

    @Test
    public void defaultsModelIsCheckedToo() {
        assertError("{\"version\":1,\"goal\":\"g\",\"defaults\":{\"model\":\"acme/nope\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}", "does not match any configured");
    }

    // ---------- rule 11: structured fields ----------

    @Test
    public void fieldAccessWithoutOutputSchemaRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"}},"
                        + "\"b\":{\"prompt\":{\"template\":\"{{a.output.sev}}\"}}"),
                "declares no output_schema");
    }

    @Test
    public void fieldNotInSchemaRejected() {
        assertError(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"output_schema\":{\"type\":\"object\","
                        + "\"properties\":{\"sev\":{\"type\":\"string\"}}}},"
                        + "\"b\":{\"prompt\":{\"template\":\"{{a.output.nope}}\"}}"),
                "not declared in a.output_schema.properties");
    }

    @Test
    public void fieldBackedBySchemaAccepted() {
        assertTrue(load(wf("\"a\":{\"prompt\":{\"text\":\"x\"},\"output_schema\":{\"type\":\"object\","
                + "\"properties\":{\"sev\":{\"type\":\"string\"}}}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output.sev}}\"}}")).isRunnable());
    }

    // ---------- workflow output ----------

    @Test
    public void workflowOutputUnknownAgentRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"output\":\"{{ghost.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}", "not a declared agent");
    }

    @Test
    public void workflowOutputCannotUseInputs() {
        assertError("{\"version\":1,\"goal\":\"g\",\"output\":\"{{inputs.x}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}", "per-node only");
    }

    // ---------- warnings ----------

    @Test
    public void unconsumedOutputWarns() {
        WorkflowLoader.Result r = load(wf("\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}}"));
        assertTrue("a is consumed by b, so no warning for a",
                !r.describeProblems().contains("[a] its output is consumed"));
        assertTrue("b's output goes nowhere: " + r.describeProblems(),
                r.describeProblems().contains("[b] its output is consumed by no other agent"));
    }

    @Test
    public void undeclaredRootWarns() {
        WorkflowLoader.Result r = load("{\"version\":1,\"goal\":\"g\",\"entry\":\"a\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"lonely\":{\"prompt\":{\"text\":\"y\"}}}}");
        assertTrue(r.describeProblems(), r.describeProblems().contains("[lonely] is a root"));
    }

    @Test
    public void warningDoesNotBlockRunning() {
        WorkflowLoader.Result r = load(wf("\"a\":{\"prompt\":{\"text\":\"x\"}}"));
        assertTrue("warnings must not prevent execution", r.isRunnable());
        assertNotNull(r.getWorkflow());
        assertFalse(r.getIssues().hasErrors());
        assertTrue(r.getIssues().getWarnings().size() > 0);
    }

    // ---------- environment ----------

    @Test
    public void validationWithoutEnvironmentSkipsHostChecks() {
        WorkflowLoader.Result r = WorkflowLoader.load(wf(
                "\"a\":{\"prompt\":{\"text\":\"x\"},\"model\":\"acme/unknown\",\"allowed_tools\":[\"not_a_tool\"]}"));
        assertTrue("no env => tool/model existence is not checked", r.isRunnable());
    }

    @Test
    public void defaultsResolutionPrecedence() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\","
                + "\"defaults\":{\"model\":\"openrouter/openai/gpt-4o-mini\",\"max_turns\":5,"
                + "\"approval\":\"strict\",\"on_error\":\"skip\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"max_turns\":9,"
                + "\"approval\":\"auto_approve\"}}}").getWorkflow();
        WorkflowAgent a = wf.getAgent("a");
        WorkflowDefaults d = wf.getDefaults();
        assertEquals("node beats defaults", Integer.valueOf(9), d.resolveMaxTurns(a.getMaxTurns()));
        assertEquals("defaults apply when the node omits the field",
                Integer.valueOf(5), d.resolveMaxTurns(null));
        assertEquals(WorkflowApprovalPolicy.STRICT, d.resolveApproval(null));
        assertEquals("node beats defaults", WorkflowApprovalPolicy.AUTO_APPROVE, d.resolveApproval(a.getApproval()));
        assertEquals(WorkflowErrorPolicy.SKIP, d.resolveOnError(a.getOnError()));
        assertEquals("openrouter/openai/gpt-4o-mini", d.resolveModel(a.getModel()));
    }
}
