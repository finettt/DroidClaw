package io.finett.droidclaw.workflow;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The template workflows shipped in {@code app/src/main/assets/workflows/} and
 * seeded into the user workspace at {@code .agent/workflows/} by
 * {@code WorkspaceManager}. Each template must load through
 * {@link WorkflowLoader} (parser + normalizer + graph + validator) with zero
 * errors and zero warnings against the production tool list.
 */
public class WorkflowTemplateAssetsTest {

    private static final String[] TEMPLATES = {
            "morning-digest",
            "workspace-audit",
            "two-step-refactor"
    };

    /** Resolves the assets dir whether the test runs from the module or repo root. */
    private static File assetsDir() {
        File fromModule = new File("src/main/assets/workflows");
        if (fromModule.isDirectory()) return fromModule;
        File fromRoot = new File("app/src/main/assets/workflows");
        assertTrue("cannot locate assets/workflows from " + new File(".").getAbsolutePath(),
                fromRoot.isDirectory());
        return fromRoot;
    }

    private static String readTemplate(String name) throws IOException {
        File file = new File(assetsDir(), name + ".json");
        assertTrue("missing template asset: " + file, file.isFile());
        byte[] bytes = Files.readAllBytes(file.toPath());
        assertTrue(name + " must respect the runtime file cap",
                bytes.length <= Workflow.MAX_FILE_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static WorkflowLoader.Result loadClean(String name) throws IOException {
        // No model refs in templates: they follow the user's configured default
        // model, so an env with zero configured models must still validate them.
        WorkflowLoader.Result r = WorkflowLoader.load(readTemplate(name), new WorkflowTestEnv(20));
        assertEquals(name + " should have no errors: " + r.describeProblems(),
                0, r.getIssues().getErrors().size());
        assertEquals(name + " should have no warnings: " + r.describeProblems(),
                0, r.getIssues().getWarnings().size());
        assertTrue(name + " must be runnable", r.isRunnable());
        assertNotNull(name + " must produce a graph", r.getGraph());
        return r;
    }

    @Test
    public void allTemplatesLoadCleanAndAreValidWorkflowFileNames() throws IOException {
        for (String name : TEMPLATES) {
            loadClean(name);
            // Must be launchable via run_workflow's filename rules
            // (RunWorkflowTool.requireValidWorkflowName: [a-zA-Z0-9_-]{1,64}).
            assertTrue(name + " must be a valid run_workflow name",
                    name.matches("[a-zA-Z0-9_-]{1,64}"));
        }
    }

    @Test
    public void morningDigestIsALinearChainEndingInTheNote() throws IOException {
        WorkflowLoader.Result r = loadClean("morning-digest");
        assertEquals(java.util.Arrays.asList("collect", "summarize", "write_note"),
                r.getGraph().topologicalOrder());
        // Only the final node may write; upstream nodes are read-only or tool-less.
        assertTrue(r.getWorkflow().getAgent("summarize").getAllowedTools().isEmpty());
        assertEquals(java.util.Arrays.asList("write_file"),
                r.getWorkflow().getAgent("write_note").getAllowedTools());
    }

    @Test
    public void workspaceAuditFansIntoASingleReport() throws IOException {
        WorkflowLoader.Result r = loadClean("workspace-audit");
        assertTrue(r.getGraph().dependenciesOf("report").contains("scan"));
        assertTrue(r.getGraph().dependenciesOf("report").contains("inspect"));
        assertTrue(r.getGraph().dependenciesOf("inspect").contains("scan"));
    }

    @Test
    public void twoStepRefactorDemonstratesOnErrorAndRetry() throws IOException {
        WorkflowLoader.Result r = loadClean("two-step-refactor");
        Workflow wf = r.getWorkflow();
        // defaults.retry applies to 'analyze'; 'apply' overrides it.
        WorkflowRetry analyzeRetry = wf.getDefaults().resolveRetry(wf.getAgent("analyze").getRetry());
        assertEquals(2, analyzeRetry.getMaxAttempts());
        WorkflowRetry applyRetry = wf.getDefaults().resolveRetry(wf.getAgent("apply").getRetry());
        assertEquals(3, applyRetry.getMaxAttempts());
        // The verification step is optional: its failure must not kill the run.
        assertEquals(WorkflowErrorPolicy.CONTINUE, wf.getAgent("verify").getOnError());
        assertEquals(WorkflowErrorPolicy.FAIL,
                wf.getDefaults().resolveOnError(wf.getAgent("apply").getOnError()));
        // The analysis step must stay read-only; only 'apply' may edit.
        assertTrue(!wf.getAgent("analyze").getAllowedTools().contains("edit_file"));
        assertTrue(wf.getAgent("apply").getAllowedTools().contains("edit_file"));
    }
}
