package io.finett.droidclaw.workflow;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests for the WorkflowRunner's pure logic (guard evaluation, retry computation)
 * that can be verified without Android or LLM dependencies.
 */
public class WorkflowRunnerLogicTest {

    private static final Pattern GUARD_PATTERN = Pattern.compile(
            "^\\{\\{\\s*([\\w.]+)\\s*\\}\\}\\s*(==|!=|contains)\\s*'([^']*)'$");

    // ==================== Guard parsing ====================

    @Test
    public void guardEqualsParsed() {
        Matcher m = GUARD_PATTERN.matcher("{{triage.output.severity}} == 'high'");
        assertTrue(m.matches());
        assertEquals("triage.output.severity", m.group(1));
        assertEquals("==", m.group(2));
        assertEquals("high", m.group(3));
    }

    @Test
    public void guardNotEqualsParsed() {
        Matcher m = GUARD_PATTERN.matcher("{{triage.output.severity}} != 'low'");
        assertTrue(m.matches());
        assertEquals("!=", m.group(2));
        assertEquals("low", m.group(3));
    }

    @Test
    public void guardContainsParsed() {
        Matcher m = GUARD_PATTERN.matcher("{{a.output}} contains 'error'");
        assertTrue(m.matches());
        assertEquals("contains", m.group(2));
        assertEquals("error", m.group(3));
    }

    @Test
    public void guardWithSpacesParsed() {
        Matcher m = GUARD_PATTERN.matcher("{{ triage.output.severity }} == 'high'");
        assertTrue(m.matches());
        assertEquals("triage.output.severity", m.group(1));
    }

    @Test
    public void guardWithAndOperatorRejected() {
        // v1 does not support boolean combinators (spec §13)
        Matcher m = GUARD_PATTERN.matcher("{{a.output}} == 'x' and {{b.output}} == 'y'");
        assertFalse(m.matches());
    }

    @Test
    public void guardWithDoubleQuotesRejected() {
        Matcher m = GUARD_PATTERN.matcher("{{a.output}} == \"high\"");
        assertFalse(m.matches());
    }

    // ==================== Guard evaluation ====================

    @Test
    public void guardEqualsEvaluation() {
        assertTrue(evaluateGuard("high", "==", "high"));
        assertFalse(evaluateGuard("low", "==", "high"));
    }

    @Test
    public void guardNotEqualsEvaluation() {
        assertTrue(evaluateGuard("high", "!=", "low"));
        assertFalse(evaluateGuard("low", "!=", "low"));
    }

    @Test
    public void guardContainsEvaluation() {
        assertTrue(evaluateGuard("this is an error message", "contains", "error"));
        assertFalse(evaluateGuard("all good", "contains", "error"));
    }

    @Test
    public void guardContainsNullValue() {
        assertFalse(evaluateGuard(null, "contains", "error"));
    }

    // ==================== Retry backoff ====================

    @Test
    public void retryBackoffDoubles() {
        WorkflowRetry retry = new WorkflowRetry(3, 1000);
        assertEquals(1000, retry.delayBeforeAttempt(1));
        assertEquals(2000, retry.delayBeforeAttempt(2));
        assertEquals(4000, retry.delayBeforeAttempt(3));
    }

    @Test
    public void retryBackoffCappedAt30s() {
        WorkflowRetry retry = new WorkflowRetry(5, 20000);
        assertEquals(20000, retry.delayBeforeAttempt(1));
        assertEquals(30000, retry.delayBeforeAttempt(2)); // 40000 capped to 30000
    }

    @Test
    public void retryNoBackoffWhenZero() {
        WorkflowRetry retry = new WorkflowRetry(3, 0);
        assertEquals(0, retry.delayBeforeAttempt(1));
        assertEquals(0, retry.delayBeforeAttempt(2));
    }

    @Test
    public void retryDefaultsNoRetry() {
        WorkflowRetry retry = WorkflowRetry.defaults();
        assertEquals(1, retry.getMaxAttempts());
        assertFalse(retry.willRetry());
    }

    // ==================== Error policy semantics ====================

    @Test
    public void errorPolicyDefaults() {
        assertEquals(WorkflowErrorPolicy.FAIL, WorkflowErrorPolicy.defaultValue());
    }

    @Test
    public void approvalPolicyDefaults() {
        assertEquals(WorkflowApprovalPolicy.DENY_WRITES, WorkflowApprovalPolicy.defaultValue());
    }

    @Test
    public void errorPolicyParsing() {
        assertEquals(WorkflowErrorPolicy.FAIL, WorkflowErrorPolicy.fromJson("fail"));
        assertEquals(WorkflowErrorPolicy.SKIP, WorkflowErrorPolicy.fromJson("skip"));
        assertEquals(WorkflowErrorPolicy.CONTINUE, WorkflowErrorPolicy.fromJson("continue"));
        assertNull(WorkflowErrorPolicy.fromJson("invalid"));
    }

    @Test
    public void approvalPolicyParsing() {
        assertEquals(WorkflowApprovalPolicy.INHERIT, WorkflowApprovalPolicy.fromJson("inherit"));
        assertEquals(WorkflowApprovalPolicy.AUTO_APPROVE, WorkflowApprovalPolicy.fromJson("auto_approve"));
        assertEquals(WorkflowApprovalPolicy.DENY_WRITES, WorkflowApprovalPolicy.fromJson("deny_writes"));
        assertEquals(WorkflowApprovalPolicy.STRICT, WorkflowApprovalPolicy.fromJson("strict"));
        assertNull(WorkflowApprovalPolicy.fromJson("invalid"));
    }

    // ==================== Node status ====================

    @Test
    public void nodeStatusJsonValues() {
        assertEquals("ok", WorkflowNodeStatus.OK.jsonValue());
        assertEquals("skipped", WorkflowNodeStatus.SKIPPED.jsonValue());
        assertEquals("error", WorkflowNodeStatus.ERROR.jsonValue());
    }

    // ==================== Template resolution for workflow output ====================

    @Test
    public void templateResolverFinalModeSkippedNodeReturnsEmpty() throws Exception {
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowGoal("test goal")
                .workflowInput("input")
                .node("a", TemplateResolver.NodeResult.skipped());

        // FINAL mode: skipped node substitutes empty string
        String result = TemplateResolver.resolve("{{a.output}}", ctx, TemplateResolver.Mode.FINAL);
        assertEquals("", result);
    }

    @Test
    public void templateResolverStrictModeSkippedNodeThrows() {
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowGoal("test goal")
                .node("a", TemplateResolver.NodeResult.skipped());

        try {
            TemplateResolver.resolve("{{a.output}}", ctx, TemplateResolver.Mode.STRICT);
            fail("Expected TemplateException for skipped node in STRICT mode");
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage().contains("skipped"));
        }
    }

    @Test
    public void templateResolverTolerableNodeReturnsEmpty() throws Exception {
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowGoal("test goal")
                .node("a", TemplateResolver.NodeResult.error(null))
                .tolerable("a");

        // STRICT mode with tolerable: error node degrades to empty string
        String result = TemplateResolver.resolve("{{a.output}}", ctx, TemplateResolver.Mode.STRICT);
        assertEquals("", result);
    }

    @Test
    public void templateResolverStatusField() throws Exception {
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowGoal("test goal")
                .node("a", TemplateResolver.NodeResult.error(null));

        String result = TemplateResolver.resolve("{{a.status}}", ctx, TemplateResolver.Mode.STRICT);
        assertEquals("error", result);
    }

    // ==================== Helpers ====================

    private boolean evaluateGuard(String value, String op, String literal) {
        switch (op) {
            case "==": return literal.equals(value);
            case "!=": return !literal.equals(value);
            case "contains": return value != null && value.contains(literal);
            default: return true;
        }
    }
}