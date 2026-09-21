package io.finett.droidclaw.workflow;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers load-pipeline stages 1 and 2: normalisation and strict structural parsing (spec §12). */
public class WorkflowParserTest {

    private static final String MINIMAL =
            "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"hi\"}}}}";

    private String errorsOf(String json) {
        WorkflowParser.Result r = WorkflowParser.parse(json);
        StringBuilder sb = new StringBuilder();
        for (WorkflowIssue i : r.getIssues().getErrors()) sb.append(i).append('\n');
        return sb.toString();
    }

    private void assertError(String json, String needle) {
        String e = errorsOf(json);
        assertTrue("expected an error containing '" + needle + "' but got:\n" + e, e.contains(needle));
    }

    private void assertNoErrors(String json) {
        WorkflowParser.Result r = WorkflowParser.parse(json);
        assertEquals("unexpected errors:\n" + r.getIssues().describe(), 0, r.getIssues().getErrors().size());
        assertNotNull(r.getWorkflow());
    }

    // ---------- happy path ----------

    @Test
    public void minimalWorkflowParses() {
        assertNoErrors(MINIMAL);
        Workflow wf = WorkflowParser.parse(MINIMAL).getWorkflow();
        assertEquals(1, wf.getVersion());
        assertEquals("g", wf.getGoal());
        assertEquals(1, wf.getAgentCount());
        assertEquals(WorkflowPrompt.Form.TEXT, wf.getAgent("a").getPrompt().getForm());
        // unset allowed_tools is null, meaning "inherit every tool" (§9 rule 1)
        assertNull(wf.getAgent("a").getAllowedTools());
        // runner-level defaults
        assertEquals(WorkflowDefaults.DEFAULT_MAX_PARALLEL, wf.getDefaults().getMaxParallel());
        assertEquals(WorkflowDefaults.DEFAULT_MAX_NODES, wf.getDefaults().getMaxNodes());
    }

    @Test
    public void emptyAllowedToolsMeansNoTools() {
        Workflow wf = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"hi\"},"
                        + "\"allowed_tools\":[]}}}").getWorkflow();
        List<String> tools = wf.getAgent("a").getAllowedTools();
        assertNotNull("[] must be preserved, not treated as unset (§9 rule 2)", tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    public void entryAcceptsStringOrArray() {
        assertNotNull(WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"entry\":\"a\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}")
                .getWorkflow());
        Workflow wf = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"entry\":[\"a\",\"b\"],"
                        + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}},\"b\":{\"prompt\":{\"text\":\"y\"}}}}")
                .getWorkflow();
        assertEquals(2, wf.getEntry().size());
    }

    @Test
    public void outputSchemaIsRetained() {
        Workflow wf = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                        + "\"output_schema\":{\"type\":\"object\",\"properties\":{\"sev\":{\"type\":\"string\"}}}}}}")
                .getWorkflow();
        assertTrue(wf.getAgent("a").hasOutputSchema());
        assertTrue(wf.getAgent("a").getOutputSchema().has("properties"));
    }

    // ---------- stage 1: normalisation ----------

    @Test
    public void propmtIsRenamedWithAWarning() {
        WorkflowParser.Result r = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"propmt\":{\"text\":\"hi\"}}}}");
        assertEquals(0, r.getIssues().getErrors().size());
        assertNotNull(r.getWorkflow().getAgent("a").getPrompt());
        assertTrue(r.getIssues().describe().contains("'propmt' is a misspelling"));
    }

    @Test
    public void maxTrunsIsRenamedWithAWarning() {
        WorkflowParser.Result r = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"hi\"},\"max_truns\":7}}}");
        assertEquals(Integer.valueOf(7), r.getWorkflow().getAgent("a").getMaxTurns());
        assertTrue(r.getIssues().describe().contains("'max_truns' is a misspelling"));
    }

    @Test
    public void wronglyTypedMaxTurnsIsDroppedNotFatal() {
        WorkflowParser.Result r = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"hi\"},\"max_truns\":[]}}}");
        assertEquals("coercion must not be an error", 0, r.getIssues().getErrors().size());
        assertNull("field should be dropped so the default applies", r.getWorkflow().getAgent("a").getMaxTurns());
        assertTrue(r.getIssues().describe().contains("is not an integer"));
    }

    @Test
    public void bothSpellingsPresentIsAnError() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"propmt\":{\"text\":\"y\"}}}}", "both 'prompt' and the misspelled 'propmt'");
    }

    // ---------- stage 2: strict structure ----------

    @Test
    public void unknownTopLevelPropertyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}},\"oops\":1}",
                "unknown property 'oops'");
    }

    @Test
    public void unknownAgentPropertyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"max_trun\":5}}}",
                "unknown property 'max_trun'");
    }

    @Test
    public void unknownPromptPropertyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\",\"extra\":1}}}}",
                "unknown property 'extra'");
    }

    @Test
    public void versionIsRequired() {
        assertError("{\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}", "'version' is required");
    }

    @Test
    public void unsupportedVersionRejected() {
        assertError("{\"version\":2,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}",
                "unsupported workflow version 2");
    }

    @Test
    public void goalIsRequired() {
        assertError("{\"version\":1,\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}", "'goal' is required");
    }

    @Test
    public void emptyGoalRejected() {
        assertError("{\"version\":1,\"goal\":\"   \",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}",
                "'goal' is required");
    }

    @Test
    public void agentsIsRequiredAndNonEmpty() {
        assertError("{\"version\":1,\"goal\":\"g\"}", "'agents' is required");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{}}", "at least one agent");
    }

    @Test
    public void promptIsRequired() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{}}}", "'prompt' is required");
    }

    @Test
    public void exactlyOnePromptFormRequired() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{}}}}",
                "exactly one of 'text', 'template' or 'from_agent'");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\",\"template\":\"y\"}}}}",
                "exactly one of 'text', 'template' or 'from_agent'");
    }

    @Test
    public void emptyPromptBodyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"   \"}}}}",
                "must not be empty");
    }

    @Test
    public void unbalancedTemplateBracesRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"template\":\"open {{ here\"}}}}",
                "unbalanced");
    }

    @Test
    public void badAgentKeyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"BadName\":{\"prompt\":{\"text\":\"x\"}}}}",
                "agent key must match");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"1abc\":{\"prompt\":{\"text\":\"x\"}}}}",
                "agent key must match");
    }

    @Test
    public void reservedAgentKeyRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"workflow\":{\"prompt\":{\"text\":\"x\"}}}}",
                "reserved template namespace");
    }

    @Test
    public void bareModelIdRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"model\":\"gpt-4o\"}}}", "is not a model reference");
    }

    @Test
    public void modelReferenceWithSlashesInModelIdIsAccepted() {
        // split on the FIRST slash only: providerId=openrouter, modelId=anthropic/claude-sonnet-4
        Workflow wf = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                        + "\"model\":\"openrouter/anthropic/claude-sonnet-4\"}}}").getWorkflow();
        assertEquals("openrouter", wf.getAgent("a").getProviderId());
        assertEquals("anthropic/claude-sonnet-4", wf.getAgent("a").getModelId());
    }

    @Test
    public void numericBoundsEnforced() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"max_turns\":0}}}",
                "out of range");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"max_turns\":999}}}",
                "out of range");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"timeout_ms\":10}}}",
                "below the 1000ms minimum");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"retry\":{\"max_attempts\":99}}}}", "out of range");
        assertError("{\"version\":1,\"goal\":\"g\",\"defaults\":{\"max_parallel\":99}}", "out of range");
    }

    @Test
    public void nonIntegerNumberRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},\"max_turns\":2.5}}}",
                "must be an integer");
    }

    @Test
    public void invalidEnumValuesRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"on_error\":\"explode\"}}}", "not a valid on_error");
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"approval\":\"yolo\"}}}", "not a valid approval");
    }

    @Test
    public void invalidGuardGrammarRejected() {
        assertError("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                + "\"when\":\"{{b.output}} and {{c.output}}\"}}}", "not a valid guard");
        Workflow wf = WorkflowParser.parse(
                "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"},"
                        + "\"when\":\"{{b.output}} == 'high'\"}}}").getWorkflow();
        assertEquals("{{b.output}} == 'high'", wf.getAgent("a").getWhen());
    }

    @Test
    public void malformedJsonAndWrongRootTypeRejected() {
        assertError("{not json", "not valid JSON");
        assertError("[1,2,3]", "top level must be a JSON object");
        assertError("", "empty");
        assertError(null, "empty");
    }

    @Test
    public void oversizedFileRejected() {
        StringBuilder sb = new StringBuilder("{\"version\":1,\"goal\":\"");
        for (int i = 0; i < Workflow.MAX_FILE_BYTES; i++) sb.append('x');
        sb.append("\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"x\"}}}}");
        assertError(sb.toString(), "byte cap");
    }

    @Test
    public void enumsRoundTripThroughJsonValues() {
        assertEquals(WorkflowApprovalPolicy.DENY_WRITES, WorkflowApprovalPolicy.fromJson("deny_writes"));
        assertEquals(WorkflowApprovalPolicy.AUTO_APPROVE, WorkflowApprovalPolicy.fromJson("auto_approve"));
        assertEquals(WorkflowErrorPolicy.CONTINUE, WorkflowErrorPolicy.fromJson("continue"));
        assertNull(WorkflowApprovalPolicy.fromJson("nope"));
        assertNull(WorkflowErrorPolicy.fromJson(null));
        assertEquals(WorkflowApprovalPolicy.DENY_WRITES, WorkflowApprovalPolicy.defaultValue());
        assertEquals(WorkflowErrorPolicy.FAIL, WorkflowErrorPolicy.defaultValue());
        assertFalse(WorkflowApprovalPolicy.defaultValue() == WorkflowApprovalPolicy.STRICT);
    }

    @Test
    public void retryBackoffDoublesAndCaps() {
        WorkflowRetry r = new WorkflowRetry(4, 1000);
        assertEquals(1000L, r.delayBeforeAttempt(1));
        assertEquals(2000L, r.delayBeforeAttempt(2));
        assertEquals(4000L, r.delayBeforeAttempt(3));
        assertEquals("n < 1 means no delay", 0L, r.delayBeforeAttempt(0));
        assertEquals("zero backoff never delays", 0L, new WorkflowRetry(3, 0).delayBeforeAttempt(2));
        assertTrue(r.willRetry());
        assertFalse(WorkflowRetry.defaults().willRetry());
        assertEquals(WorkflowRetry.MAX_BACKOFF_MS, new WorkflowRetry(5, 20000).delayBeforeAttempt(4));
    }
}
