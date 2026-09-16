package io.finett.droidclaw.workflow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Covers the {{ expr }} grammar of spec §8.2 and the two resolution modes of §10.3. */
public class TemplateResolverTest {

    private TemplateResolver.Context base() {
        return new TemplateResolver.Context()
                .workflowName("wf").workflowGoal("ship it").workflowInput("RAW")
                .node("a", TemplateResolver.NodeResult.ok("A-TEXT"))
                .node("b", TemplateResolver.NodeResult.skipped())
                .node("c", TemplateResolver.NodeResult.error("partial"));
    }

    @Test
    public void resolvesWorkflowNamespace() throws Exception {
        assertEquals("ship it", TemplateResolver.resolve("{{workflow.goal}}", base()));
        assertEquals("RAW", TemplateResolver.resolve("{{ workflow.input }}", base()));
        assertEquals("wf", TemplateResolver.resolve("{{workflow.name}}", base()));
    }

    @Test
    public void resolvesAgentOutputAndStatus() throws Exception {
        assertEquals("A-TEXT", TemplateResolver.resolve("{{a.output}}", base()));
        assertEquals("ok", TemplateResolver.resolve("{{a.status}}", base()));
        assertEquals("skipped", TemplateResolver.resolve("{{b.status}}", base()));
        assertEquals("error", TemplateResolver.resolve("{{c.status}}", base()));
    }

    @Test
    public void multipleExpressionsInOneTemplate() throws Exception {
        assertEquals("[A-TEXT|ship it]", TemplateResolver.resolve("[{{a.output}}|{{workflow.goal}}]", base()));
    }

    @Test
    public void strictModeRaisesOnMissingOutput() {
        for (String expr : new String[]{"{{b.output}}", "{{c.output}}"}) {
            try {
                TemplateResolver.resolve(expr, base());
                fail(expr + " should raise under STRICT");
            } catch (TemplateResolver.TemplateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("has no output"));
            }
        }
    }

    @Test
    public void strictModeToleratesOnlyContinueNodes() throws Exception {
        // 'c' declared on_error:continue => degrades to empty string
        TemplateResolver.Context ctx = base().tolerable("c");
        assertEquals("", TemplateResolver.resolve("{{c.output}}", ctx));
        // 'b' skipped is still not tolerable under STRICT
        try {
            TemplateResolver.resolve("{{b.output}}", ctx);
            fail("skipped node must still raise under STRICT");
        } catch (TemplateResolver.TemplateException expected) { /* ok */ }
    }

    @Test
    public void finalModeToleratesSkippedButNotError() throws Exception {
        // §10.3: a conditional branch that did not fire must not fail the run
        assertEquals("", TemplateResolver.resolve("{{b.output}}", base(), TemplateResolver.Mode.FINAL));
        try {
            TemplateResolver.resolve("{{c.output}}", base(), TemplateResolver.Mode.FINAL);
            fail("an errored node must still fail FINAL output");
        } catch (TemplateResolver.TemplateException expected) { /* ok */ }
    }

    @Test
    public void unknownAgentRaises() {
        try {
            TemplateResolver.resolve("{{ghost.output}}", base());
            fail("unknown agent should raise");
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not run"));
        }
    }

    @Test
    public void unboundInputRaises() {
        try {
            TemplateResolver.resolve("{{inputs.nope}}", base());
            fail("unbound input should raise");
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not bound"));
        }
    }

    @Test
    public void boundInputsResolve() throws Exception {
        TemplateResolver.Context ctx = base().input("sec", "S").input("perf", "P");
        assertEquals("S/P", TemplateResolver.resolve("{{inputs.sec}}/{{inputs.perf}}", ctx));
    }

    @Test
    public void structuredFieldAccess() throws Exception {
        JsonObject s = JsonParser.parseString("{\"severity\":\"high\",\"tags\":[\"a\"]}").getAsJsonObject();
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .node("t", TemplateResolver.NodeResult.ok("raw", s));
        assertEquals("high", TemplateResolver.resolve("{{t.output.severity}}", ctx));
        assertEquals("[\"a\"]", TemplateResolver.resolve("{{t.output.tags}}", ctx));
    }

    @Test
    public void structuredFieldMissingRaises() {
        JsonObject s = JsonParser.parseString("{\"severity\":\"high\"}").getAsJsonObject();
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .node("t", TemplateResolver.NodeResult.ok("raw", s));
        try {
            TemplateResolver.resolve("{{t.output.nope}}", ctx);
            fail("missing field should raise");
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("absent"));
        }
    }

    @Test
    public void fieldAccessWithoutSchemaRaises() {
        try {
            TemplateResolver.resolve("{{a.output.severity}}", base());
            fail("field access on a text-only node should raise");
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("output_schema"));
        }
    }

    @Test
    public void badWorkflowFieldRaises() {
        try {
            TemplateResolver.resolve("{{workflow.nope}}", base());
            fail();
        } catch (TemplateResolver.TemplateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a workflow field"));
        }
    }

    @Test
    public void literalTextIsUntouched() throws Exception {
        assertEquals("no expressions here", TemplateResolver.resolve("no expressions here", base()));
        assertEquals("", TemplateResolver.resolve(null, base()));
    }

    // ---------- static analysis ----------

    @Test
    public void referencedAgentsExcludesReservedRoots() {
        Set<String> refs = TemplateResolver.referencedAgents(
                "{{workflow.goal}} {{inputs.x}} {{a.output}} {{b.output.f}} {{a.status}}");
        assertEquals(2, refs.size());
        assertTrue(refs.contains("a"));
        assertTrue(refs.contains("b"));
        assertFalse(refs.contains("workflow"));
        assertFalse(refs.contains("inputs"));
    }

    @Test
    public void referencedFieldsOnlyPicksOutputPaths() {
        Set<String> f = TemplateResolver.referencedFields("{{t.output.sev}} {{t.status}} {{u.output.x}}", "t");
        assertEquals(1, f.size());
        assertTrue(f.contains("sev"));
    }

    @Test
    public void referencedInputsListsBindings() {
        Set<String> in = TemplateResolver.referencedInputs("{{inputs.a}} and {{inputs.b}} and {{c.output}}");
        assertEquals(2, in.size());
        assertTrue(in.contains("a") && in.contains("b"));
    }

    @Test
    public void unbalancedBracesDetected() {
        assertTrue(TemplateResolver.hasUnbalancedBraces("open {{ but never closed"));
        assertTrue(TemplateResolver.hasUnbalancedBraces("}} stray"));
        assertFalse(TemplateResolver.hasUnbalancedBraces("{{a.output}} fine"));
        assertFalse(TemplateResolver.hasUnbalancedBraces("no braces at all"));
        assertFalse(TemplateResolver.hasUnbalancedBraces(null));
    }
}
