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

    @Before
    public void setUp() {
        config = ShellConfig.createAllowlistDefault();
        workspace = new File(System.getProperty("java.io.tmpdir"));
        planner = new ExecPlanner(config, workspace);
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
        // Use a trusted dir executable (ls)
        ExecPlan plan = planner.plan("/system/bin/ls", workspace);
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

        assertEquals("/system/bin/ls", plan.getCanonicalExePath());
        assertEquals(1, plan.getArgv().size());
        assertEquals("-l", plan.getArgv().get(0));
    }
}