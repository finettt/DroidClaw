package io.finett.droidclaw.workflow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The three worked examples and the migration case from
 * {@code docs/features/workflow-schema.md} §15-§16. These files were also checked
 * against the §14 JSON Schema; this test asserts the Java loader agrees.
 */
public class WorkflowSpecExamplesTest {

    private static final String EX1_LINEAR =
            "{\n"
          +             "  \"version\": 1,\n"
          +             "  \"name\": \"Corrected sketch\",\n"
          +             "  \"goal\": \"Turn a raw note into a polished paragraph, then check it.\",\n"
          +             "  \"entry\": \"name\",\n"
          +             "  \"output\": \"{{name2.output}}\",\n"
          +             "  \"defaults\": {\n"
          +             "    \"model\": \"openrouter/anthropic/claude-sonnet-4\",\n"
          +             "    \"max_turns\": 6\n"
          +             "  },\n"
          +             "  \"agents\": {\n"
          +             "    \"name\": {\n"
          +             "      \"model\": \"openrouter/anthropic/claude-sonnet-4\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"text\": \"Rewrite this note as one clear paragraph:\\n{{workflow.input}}\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [],\n"
          +             "      \"max_turns\": 4\n"
          +             "    },\n"
          +             "    \"name2\": {\n"
          +             "      \"model\": \"openrouter/openai/gpt-4o-mini\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"from_agent\": \"name\"\n"
          +             "      }\n"
          +             "    }\n"
          +             "  }\n"
          +             "}";

    private static final String EX2_FANIN =
            "{\n"
          +             "  \"version\": 1,\n"
          +             "  \"name\": \"Bug triage\",\n"
          +             "  \"goal\": \"Classify an incoming bug report, investigate two angles in parallel, and produce one fix plan.\",\n"
          +             "  \"entry\": \"triage\",\n"
          +             "  \"output\": \"# {{triage.output.severity}} \\u2014 {{triage.output.area}}\\n\\n{{reviewer.output}}\",\n"
          +             "  \"defaults\": {\n"
          +             "    \"model\": \"openrouter/anthropic/claude-sonnet-4\",\n"
          +             "    \"max_turns\": 10,\n"
          +             "    \"approval\": \"deny_writes\",\n"
          +             "    \"timeout_ms\": 180000,\n"
          +             "    \"max_parallel\": 2\n"
          +             "  },\n"
          +             "  \"agents\": {\n"
          +             "    \"triage\": {\n"
          +             "      \"description\": \"Structured classification of the report\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Report:\\n{{workflow.input}}\\n\\nClassify it.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [],\n"
          +             "      \"max_turns\": 3,\n"
          +             "      \"output_schema\": {\n"
          +             "        \"type\": \"object\",\n"
          +             "        \"properties\": {\n"
          +             "          \"severity\": {\n"
          +             "            \"type\": \"string\",\n"
          +             "            \"enum\": [\n"
          +             "              \"low\",\n"
          +             "              \"medium\",\n"
          +             "              \"high\"\n"
          +             "            ]\n"
          +             "          },\n"
          +             "          \"area\": {\n"
          +             "            \"type\": \"string\"\n"
          +             "          },\n"
          +             "          \"summary\": {\n"
          +             "            \"type\": \"string\"\n"
          +             "          }\n"
          +             "        },\n"
          +             "        \"required\": [\n"
          +             "          \"severity\",\n"
          +             "          \"area\",\n"
          +             "          \"summary\"\n"
          +             "        ]\n"
          +             "      }\n"
          +             "    },\n"
          +             "    \"code_scan\": {\n"
          +             "      \"description\": \"Read the relevant source\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Area: {{triage.output.area}}\\nSummary: {{triage.output.summary}}\\n\\nFind the likely fault in the code.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [\n"
          +             "        \"read_file\",\n"
          +             "        \"search_files\",\n"
          +             "        \"list_files\",\n"
          +             "        \"file_info\"\n"
          +             "      ]\n"
          +             "    },\n"
          +             "    \"history_scan\": {\n"
          +             "      \"description\": \"Search prior reports\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Have we seen this before? Summary: {{triage.output.summary}}\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [\n"
          +             "        \"search_files\",\n"
          +             "        \"read_file\"\n"
          +             "      ],\n"
          +             "      \"retry\": {\n"
          +             "        \"max_attempts\": 2,\n"
          +             "        \"backoff_ms\": 1000\n"
          +             "      },\n"
          +             "      \"on_error\": \"skip\"\n"
          +             "    },\n"
          +             "    \"reviewer\": {\n"
          +             "      \"description\": \"Reconcile both angles into one plan\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Severity: {{triage.output.severity}}\\n\\nCode findings:\\n{{inputs.code}}\\n\\nHistory findings (status {{history_scan.status}}):\\n{{inputs.history}}\\n\\nWrite one fix plan.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [\n"
          +             "        \"read_file\"\n"
          +             "      ],\n"
          +             "      \"inputs\": {\n"
          +             "        \"code\": \"{{code_scan.output}}\",\n"
          +             "        \"history\": \"{{history_scan.output}}\"\n"
          +             "      },\n"
          +             "      \"when\": \"{{triage.output.severity}} != 'low'\"\n"
          +             "    }\n"
          +             "  }\n"
          +             "}";

    private static final String EX3_PARALLEL_ROOTS =
            "{\n"
          +             "  \"version\": 1,\n"
          +             "  \"name\": \"Release notes\",\n"
          +             "  \"goal\": \"Draft user-facing release notes from the changelog, then fact-check them against the code.\",\n"
          +             "  \"entry\": [\n"
          +             "    \"collect\",\n"
          +             "    \"audience\"\n"
          +             "  ],\n"
          +             "  \"output\": \"{{editor.output}}\",\n"
          +             "  \"defaults\": {\n"
          +             "    \"model\": \"openrouter/openai/gpt-4o-mini\",\n"
          +             "    \"approval\": \"strict\",\n"
          +             "    \"max_turns\": 8\n"
          +             "  },\n"
          +             "  \"agents\": {\n"
          +             "    \"collect\": {\n"
          +             "      \"prompt\": {\n"
          +             "        \"text\": \"Read the changelog and list user-visible changes.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [\n"
          +             "        \"read_file\",\n"
          +             "        \"list_files\"\n"
          +             "      ]\n"
          +             "    },\n"
          +             "    \"audience\": {\n"
          +             "      \"prompt\": {\n"
          +             "        \"text\": \"Describe who reads these release notes and what they care about.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": []\n"
          +             "    },\n"
          +             "    \"drafter\": {\n"
          +             "      \"model\": \"openrouter/anthropic/claude-sonnet-4\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Changes:\\n{{inputs.changes}}\\n\\nAudience:\\n{{inputs.audience}}\\n\\nDraft the notes.\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [],\n"
          +             "      \"inputs\": {\n"
          +             "        \"changes\": \"{{collect.output}}\",\n"
          +             "        \"audience\": \"{{audience.output}}\"\n"
          +             "      }\n"
          +             "    },\n"
          +             "    \"factcheck\": {\n"
          +             "      \"description\": \"Optional verification; editor proceeds without it if this fails\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"from_agent\": \"drafter\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [\n"
          +             "        \"read_file\",\n"
          +             "        \"search_files\"\n"
          +             "      ],\n"
          +             "      \"on_error\": \"continue\",\n"
          +             "      \"retry\": {\n"
          +             "        \"max_attempts\": 3,\n"
          +             "        \"backoff_ms\": 2000\n"
          +             "      },\n"
          +             "      \"timeout_ms\": 60000\n"
          +             "    },\n"
          +             "    \"editor\": {\n"
          +             "      \"prompt\": {\n"
          +             "        \"template\": \"Draft:\\n{{drafter.output}}\\n\\nFact-check notes ({{factcheck.status}}):\\n{{inputs.checks}}\\n\\nProduce the final text.\"\n"
          +             "      },\n"
          +             "      \"depends_on\": [\n"
          +             "        \"factcheck\"\n"
          +             "      ],\n"
          +             "      \"inputs\": {\n"
          +             "        \"checks\": \"{{factcheck.output}}\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": []\n"
          +             "    }\n"
          +             "  }\n"
          +             "}";

    private static final String ORIGINAL_SKETCH =
            "{\n"
          +             "  \"agents\": {\n"
          +             "    \"name\": {\n"
          +             "      \"model\": \"model-id\",\n"
          +             "      \"prompt\": {\n"
          +             "        \"text\": \"prompt\"\n"
          +             "      },\n"
          +             "      \"allowed_tools\": [],\n"
          +             "      \"max_truns\": []\n"
          +             "    },\n"
          +             "    \"name2\": {\n"
          +             "      \"model\": \"model-id\",\n"
          +             "      \"propmt\": {\n"
          +             "        \"from_agent\": \"name\"\n"
          +             "      }\n"
          +             "    }\n"
          +             "  }\n"
          +             "}";

    private WorkflowLoader.Result load(String json) {
        return WorkflowLoader.load(json, WorkflowTestEnv.withExampleModels());
    }

    /** Asserts no errors. Warnings are checked per-example, since some are intentional. */
    private WorkflowLoader.Result assertLoads(String label, String json) {
        WorkflowLoader.Result r = load(json);
        assertEquals(label + " should have no errors: " + r.describeProblems(),
                0, r.getIssues().getErrors().size());
        assertTrue(label + " must be runnable", r.isRunnable());
        assertNotNull(label + " must produce a graph", r.getGraph());
        return r;
    }

    /** Asserts no errors and no warnings, then returns the loaded result. */
    private WorkflowLoader.Result assertClean(String label, String json) {
        WorkflowLoader.Result r = assertLoads(label, json);
        assertEquals(label + " should have no warnings: " + r.describeProblems(),
                0, r.getIssues().getWarnings().size());
        return r;
    }

    @Test
    public void linearChainExampleLoadsClean() {
        WorkflowLoader.Result r = assertClean("ex1 linear", EX1_LINEAR);
        Workflow wf = r.getWorkflow();
        assertEquals(2, wf.getAgentCount());
        // from_agent desugars into a dependency edge
        assertTrue(r.getGraph().dependenciesOf("name2").contains("name"));
        assertEquals(java.util.Arrays.asList("name", "name2"), r.getGraph().topologicalOrder());
        // [] means "no tools", which is distinct from unset
        assertNotNull(wf.getAgent("name").getAllowedTools());
        assertTrue(wf.getAgent("name").getAllowedTools().isEmpty());
    }

    @Test
    public void fanInExampleLoadsWithOnlyTheDocumentedGuardWarning() {
        WorkflowLoader.Result r = assertLoads("ex2 fan-in", EX2_FANIN);
        // 'reviewer' is conditional AND feeds the workflow output, so the loader
        // warns that a false guard substitutes an empty string (spec §10.3).
        // That warning is intentional, not a defect in the example.
        assertEquals("expected exactly one warning: " + r.describeProblems(),
                1, r.getIssues().getWarnings().size());
        String w = r.getIssues().getWarnings().get(0).toString();
        assertTrue(w, w.contains("reviewer") && w.contains("conditional"));
        WorkflowGraph g = r.getGraph();
        // triage -> {code_scan, history_scan} -> reviewer
        assertEquals(java.util.Collections.singleton("triage"), g.roots());
        assertTrue(g.dependenciesOf("reviewer").contains("code_scan"));
        assertTrue(g.dependenciesOf("reviewer").contains("history_scan"));
        assertTrue(g.dependenciesOf("reviewer").contains("triage"));
        // the two scans are independent, so they may run in parallel
        assertTrue(g.dependenciesOf("code_scan").isEmpty()
                || !g.dependenciesOf("code_scan").contains("history_scan"));
        assertFalseDep(g, "code_scan", "history_scan");
        // a bare template reference is an ordering edge (spec §8.2)
        assertTrue("template refs must create edges", g.dependenciesOf("code_scan").contains("triage"));
        // structured field access is backed by output_schema
        assertTrue(r.getWorkflow().getAgent("triage").hasOutputSchema());
    }

    private static void assertFalseDep(WorkflowGraph g, String a, String b) {
        assertTrue(a + " must not depend on " + b, !g.dependenciesOf(a).contains(b));
    }

    @Test
    public void parallelRootsExampleLoadsClean() {
        assertClean("ex3 parallel roots", EX3_PARALLEL_ROOTS);
        WorkflowLoader.Result r = load(EX3_PARALLEL_ROOTS);
        assertEquals(2, r.getWorkflow().getEntry().size());
        assertTrue(r.getGraph().roots().contains("collect"));
        assertTrue(r.getGraph().roots().contains("audience"));
        // on_error=continue is what makes factcheck optional without killing editor
        assertEquals(WorkflowErrorPolicy.CONTINUE, r.getWorkflow().getAgent("factcheck").getOnError());
    }

    @Test
    public void topologicalOrderIsDeterministic() {
        WorkflowLoader.Result r = load(EX2_FANIN);
        java.util.List<String> first = r.getGraph().topologicalOrder();
        for (int i = 0; i < 5; i++) {
            assertEquals(first, WorkflowGraph.build(r.getWorkflow()).topologicalOrder());
        }
        assertEquals("triage", first.get(0));
        assertEquals("reviewer", first.get(first.size() - 1));
    }

    // ---------- §16 migration ----------

    @Test
    public void originalSketchIsRejectedWithExactlyTheDocumentedErrors() {
        WorkflowLoader.Result r = load(ORIGINAL_SKETCH);
        assertTrue("sketch must not be runnable", !r.isRunnable());
        String all = r.describeProblems();
        // the two misspellings are auto-repaired, so they must NOT appear as errors
        assertTrue("propmt should be normalised, not an error: " + all, !all.contains("unknown property 'propmt'"));
        assertTrue("max_truns should be normalised, not an error: " + all, !all.contains("unknown property 'max_truns'"));
        // and they must be reported as warnings
        assertTrue("expected a propmt warning: " + all, all.contains("'propmt' is a misspelling"));
        assertTrue("expected a max_truns warning: " + all, all.contains("'max_truns' is a misspelling"));
        assertTrue("expected a coercion warning: " + all, all.contains("is not an integer"));
        // the four errors the author must fix
        assertTrue(all.contains("'version' is required"));
        assertTrue(all.contains("'goal' is required"));
        assertEquals("exactly 2 bad model refs expected", 2, countOccurrences(all, "is not a model reference"));
    }

    @Test
    public void repairedSketchLoadsClean() {
        String repaired = ORIGINAL_SKETCH
                .replace("\"agents\": {", "\"version\": 1,\n  \"goal\": \"Rewrite and check a note.\",\n  \"agents\": {")
                .replace("model-id", "openrouter/openai/gpt-4o-mini");
        WorkflowLoader.Result r = load(repaired);
        assertTrue("repaired sketch should load: " + r.describeProblems(), r.isRunnable());
        // normalisation warnings remain, which is the point: files should get fixed
        assertTrue(r.getIssues().getWarnings().size() >= 2);
        assertEquals(2, r.getWorkflow().getAgentCount());
    }

    private static int countOccurrences(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
