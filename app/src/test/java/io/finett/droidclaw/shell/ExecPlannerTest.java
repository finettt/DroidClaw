package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for ExecPlanner — verifies that common shell-injection
 * bypass vectors are rejected in DIRECT mode.
 */
public class ExecPlannerTest {

    private ShellConfig config;
    private ExecPlanner planner;
    private File workspace;

    /** Canonical path to {@code ls} resolved through the trusted-dirs list at test time. */
    private static String LS_PATH;

    @Before
    public void setUp() throws Exception {
        config = ShellConfig.createAllowlistDefault();
        workspace = new File(System.getProperty("java.io.tmpdir"));
        planner = new ExecPlanner(config, workspace);

        // Resolve ls once using the planner so tests don't hard-code Android-only paths.
        LS_PATH = planner.plan("ls", workspace).getCanonicalExePath();
    }

    private ExecPlan plan(String command) throws Exception {
        return planner.plan(command, workspace, ExecPlan.ExecMode.DIRECT);
    }

    @Test(expected = SecurityException.class)
    public void rejectsSemicolonInDirectMode() throws Exception {
        plan("ls; rm -rf /");
    }

    @Test(expected = SecurityException.class)
    public void rejectsPipeInDirectMode() throws Exception {
        plan("ls | grep test");
    }

    @Test(expected = SecurityException.class)
    public void rejectsAmpersandInDirectMode() throws Exception {
        plan("ls && echo done");
    }

    @Test(expected = SecurityException.class)
    public void rejectsRedirectionInDirectMode() throws Exception {
        plan("echo test > file.txt");
    }

    @Test(expected = SecurityException.class)
    public void rejectsDollarSubstitutionInDirectMode() throws Exception {
        plan("echo $(whoami)");
    }

    @Test(expected = SecurityException.class)
    public void rejectsBacktickSubstitutionInDirectMode() throws Exception {
        plan("echo `whoami`");
    }

    @Test(expected = SecurityException.class)
    public void rejectsUnterminatedQuote() throws Exception {
        planner.plan("echo \"unterminated", workspace);
    }

    @Test
    public void tokenise_respectsQuotes() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("echo \"hello world\" 'test 123'");
        assertEquals(3, tokens.size());
        assertEquals("echo", tokens.get(0));
        assertEquals("hello world", tokens.get(1));
        assertEquals("test 123", tokens.get(2));
    }

    @Test
    public void planHash_changesWhenArgvChanges() throws Exception {
        ExecPlan p1 = planner.plan("ls -l", workspace);
        ExecPlan p2 = planner.plan("ls -a", workspace);

        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void planHash_changesWhenCwdChanges() throws Exception {
        ExecPlan p1 = planner.plan("ls", workspace);

        File otherDir = new File(workspace, "subdir");
        otherDir.mkdirs();

        ExecPlan p2 = planner.plan("ls", otherDir);

        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void resolveAbsoluteExeCanonicalisesPath() throws Exception {
        // Resolve ls by name first, then use that absolute path to test absolute resolution.
        ExecPlan plan = planner.plan(LS_PATH, workspace);
        assertTrue(plan.getCanonicalExePath().endsWith("ls"));
    }

    @Test(expected = SecurityException.class)
    public void rejectsExeOutsideTrustedDirs() throws Exception {
        planner.plan("nonexistent_executable", workspace);
    }

    @Test
    public void planFromTokens_buildsCorrectly() throws Exception {
        ExecPlan plan = planner.planFromTokens(
                Arrays.asList("ls", "-l"),
                workspace,
                ExecPlan.ExecMode.DIRECT
        );

        assertEquals(LS_PATH, plan.getCanonicalExePath());
        assertEquals(1, plan.getArgv().size());
        assertEquals("-l", plan.getArgv().get(0));
    }

    @Test(expected = SecurityException.class)
    public void rejectsEmptyCommand() throws Exception {
        planner.plan("", workspace);
    }

    @Test(expected = SecurityException.class)
    public void rejectsWhitespaceOnlyCommand() throws Exception {
        planner.plan("   \n\t  ", workspace);
    }

    @Test(expected = SecurityException.class)
    public void rejectsNullCommand() throws Exception {
        planner.plan(null, workspace);
    }

    @Test
    public void tokenise_emptyString_returnsEmpty() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("");
        assertTrue("Empty string should tokenise to empty list", tokens.isEmpty());
    }

    @Test
    public void tokenise_whitespaceOnly_returnsEmpty() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("   \n\t  ");
        assertTrue("Whitespace-only should tokenise to empty list", tokens.isEmpty());
    }

    @Test
    public void tokenise_singleToken_noQuotes() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("ls");
        assertEquals(1, tokens.size());
        assertEquals("ls", tokens.get(0));
    }

    @Test
    public void tokenise_multipleTokens_noQuotes() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("ls -la /home");
        assertEquals(3, tokens.size());
        assertEquals("ls", tokens.get(0));
        assertEquals("-la", tokens.get(1));
        assertEquals("/home", tokens.get(2));
    }

    @Test
    public void tokenise_mixedQuotes() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("echo \"hello world\" 'foo bar' baz");
        assertEquals(4, tokens.size());
        assertEquals("echo", tokens.get(0));
        assertEquals("hello world", tokens.get(1));
        assertEquals("foo bar", tokens.get(2));
        assertEquals("baz", tokens.get(3));
    }

    @Test
    public void tokenise_nestedQuotes() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("echo \"'inner single'\"");
        assertEquals(2, tokens.size());
        assertEquals("echo", tokens.get(0));
        assertEquals("'inner single'", tokens.get(1));
    }

    @Test(expected = SecurityException.class)
    public void tokenise_unterminatedDoubleQuote() throws Exception {
        ExecPlanner.tokenise("echo \"unterminated");
    }

    @Test(expected = SecurityException.class)
    public void tokenise_unterminatedSingleQuote() throws Exception {
        ExecPlanner.tokenise("echo 'unterminated");
    }

    @Test
    public void tokenise_escapedInQuotes_preserved() throws Exception {
        List<String> tokens = ExecPlanner.tokenise("echo \"hello\\nworld\"");
        assertEquals(2, tokens.size());
        assertEquals("hello\\nworld", tokens.get(1));
    }

    @Test
    public void planHash_sameInput_sameHash() throws Exception {
        ExecPlan p1 = planner.plan("ls -l", workspace);
        ExecPlan p2 = planner.plan("ls -l", workspace);

        assertEquals("Same input should produce same hash", p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void planHash_differentExe_differentHash() throws Exception {
        ExecPlan p1 = planner.plan("ls", workspace);
        ExecPlan p2 = planner.plan("ls -l", workspace);

        assertNotEquals("Different argv should produce different hash", p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void planHash_differentMode_differentHash() throws Exception {
        ExecPlan p1 = planner.planFromTokens(
                Arrays.asList("ls"), workspace, ExecPlan.ExecMode.DIRECT);
        ExecPlan p2 = planner.planFromTokens(
                Arrays.asList("ls"), workspace, ExecPlan.ExecMode.SHELL);

        assertNotEquals("Different mode should produce different hash", p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void plan_containsCorrectFields() throws Exception {
        ExecPlan plan = planner.plan("ls -la", workspace);

        assertNotNull("Exe path should not be null", plan.getCanonicalExePath());
        assertTrue("Exe path should be absolute", plan.getCanonicalExePath().startsWith("/"));
        assertEquals("Should have 1 arg", 1, plan.getArgv().size());
        assertEquals("-la", plan.getArgv().get(0));
        assertNotNull("CWD should not be null", plan.getCwd());
        assertEquals("Mode should be DIRECT", ExecPlan.ExecMode.DIRECT, plan.getMode());
        assertNotNull("Hash should not be null", plan.getPlanHash());
        assertEquals("Hash should be 64 hex chars", 64, plan.getPlanHash().length());
    }

    @Test
    public void plan_approvalDescriptionContainsFields() throws Exception {
        ExecPlan plan = planner.plan("ls -la", workspace);

        String desc = plan.toApprovalDescription();
        assertTrue("Should contain mode", desc.contains("DIRECT"));
        assertTrue("Should contain executable", desc.contains("ls"));
        assertTrue("Should contain arguments", desc.contains("-la"));
        assertTrue("Should contain working dir", desc.contains("Working dir"));
        assertTrue("Should contain plan ID", desc.contains("Plan ID"));
    }

    @Test
    public void plan_toStringContainsHash() throws Exception {
        ExecPlan plan = planner.plan("ls", workspace);

        String str = plan.toString();
        assertTrue("Should contain mode", str.contains("DIRECT"));
        assertTrue("Should contain exe", str.contains("ls"));
        assertTrue("Should contain truncated hash", str.contains("..."));
    }

    @Test(expected = SecurityException.class)
    public void rejectsLeftAngleBracket() throws Exception {
        plan("cat < file.txt");
    }

    @Test(expected = SecurityException.class)
    public void rejectsRightAngleBracket() throws Exception {
        plan("echo test > file.txt");
    }

    @Test(expected = SecurityException.class)
    public void rejectsParentheses() throws Exception {
        plan("echo $(date)");
    }

    @Test(expected = SecurityException.class)
    public void rejectsCurlyBraces() throws Exception {
        plan("echo {a,b}");
    }

    @Test(expected = SecurityException.class)
    public void rejectsExclamation() throws Exception {
        plan("echo hello!");
    }

    @Test(expected = SecurityException.class)
    public void rejectsTilde() throws Exception {
        plan("ls ~");
    }

    @Test
    public void resolveCwd_workspaceRoot_allowed() throws Exception {
        ExecPlan plan = planner.plan("ls", workspace);
        assertEquals(workspace.getCanonicalFile(), plan.getCwd());
    }

    @Test(expected = SecurityException.class)
    public void resolveCwd_outsideWorkspace_rejected() throws Exception {
        File outside = new File("/");
        planner.plan("ls", outside);
    }

    @Test(expected = SecurityException.class)
    public void resolveCwd_nonexistent_rejected() throws Exception {
        File nonexistent = new File(workspace, "does_not_exist_12345");
        planner.plan("ls", nonexistent);
    }

    @Test
    public void planFromTokens_emptyArgv() throws Exception {
        ExecPlan plan = planner.planFromTokens(
                Arrays.asList("ls"), workspace, ExecPlan.ExecMode.DIRECT);

        assertEquals(LS_PATH, plan.getCanonicalExePath());
        assertTrue("Argv should be empty", plan.getArgv().isEmpty());
    }

    @Test
    public void planFromTokens_multipleArgv() throws Exception {
        ExecPlan plan = planner.planFromTokens(
                Arrays.asList("ls", "-la", "/tmp"), workspace, ExecPlan.ExecMode.DIRECT);

        assertEquals(2, plan.getArgv().size());
        assertEquals("-la", plan.getArgv().get(0));
        assertEquals("/tmp", plan.getArgv().get(1));
    }

    @Test(expected = SecurityException.class)
    public void planFromTokens_nullTokens() throws Exception {
        planner.planFromTokens(null, workspace, ExecPlan.ExecMode.DIRECT);
    }

    @Test(expected = SecurityException.class)
    public void planFromTokens_emptyTokens() throws Exception {
        planner.planFromTokens(Arrays.asList(), workspace, ExecPlan.ExecMode.DIRECT);
    }

    @Test
    public void computeHash_stableAcrossCalls() {
        String h1 = ExecPlan.computeHash("/bin/ls", Arrays.asList("-la"), workspace, ExecPlan.ExecMode.DIRECT);
        String h2 = ExecPlan.computeHash("/bin/ls", Arrays.asList("-la"), workspace, ExecPlan.ExecMode.DIRECT);

        assertEquals("Hash should be deterministic", h1, h2);
        assertEquals("Should be 64 hex chars", 64, h1.length());
    }

    @Test
    public void computeHash_differentFields_differentHash() {
        String h1 = ExecPlan.computeHash("/bin/ls", Arrays.asList("-la"), workspace, ExecPlan.ExecMode.DIRECT);
        String h2 = ExecPlan.computeHash("/bin/ls", Arrays.asList("-la"), workspace, ExecPlan.ExecMode.SHELL);

        assertNotEquals("Different mode should change hash", h1, h2);
    }
}
