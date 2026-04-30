package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellExecutor} using the new ExecPlan-based API.
 *
 * <p>Executable paths are resolved at runtime via {@link #findExe(String)} so the
 * tests work on both Android (where binaries live in {@code /system/bin}) and on
 * Linux development / CI hosts (where they live in {@code /usr/bin} or {@code /bin}).
 */
public class ShellExecutorTest {

    private File cwd;

    /** Resolve an unqualified executable name through the trusted-dirs list. */
    private static String findExe(String name) throws Exception {
        ShellConfig cfg = ShellConfig.createFull();
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        ExecPlanner planner = new ExecPlanner(cfg, tmp);
        return planner.plan(name, tmp).getCanonicalExePath();
    }

    private static String ECHO;
    private static String PRINTF;
    private static String SLEEP;
    private static String SH;
    private static String PWD_EXE;

    @Before
    public void setUp() throws Exception {
        cwd = new File(System.getProperty("java.io.tmpdir"));
        ECHO    = findExe("echo");
        PRINTF  = findExe("printf");
        SLEEP   = findExe("sleep");
        SH      = findExe("sh");
        PWD_EXE = findExe("pwd");
    }

    private ExecPlan plan(String exe, String... argv) {
        return new ExecPlan(exe, Arrays.asList(argv), cwd, ExecPlan.ExecMode.DIRECT);
    }

    // ==================== Basic execution ====================

    @Test
    public void execute_simpleEchoCommand_succeeds() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan(ECHO, "Hello World");

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
        assertTrue(result.getStdout().contains("Hello World"));
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    public void execute_commandWithStderr_capturesStderr() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        // ls on a non-existent directory produces stderr
        ExecPlan p = plan(findExeSilent("ls"), "/nonexistent_directory_12345");

        ShellResult result = executor.execute(p);

        assertNotEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
        assertFalse(result.getStderr().isEmpty());
    }

    @Test
    public void execute_multilineOutput() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        // Use printf for predictable multiline output
        ExecPlan p = plan(PRINTF, "line1\\nline2\\nline3\\n");

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        String stdout = result.getStdout();
        assertTrue(stdout.contains("line1"));
        assertTrue(stdout.contains("line2"));
        assertTrue(stdout.contains("line3"));
    }

    // ==================== Timeout ====================

    @Test
    public void execute_commandTimesOut() {
        ShellConfig shortConfig = new ShellConfig.Builder()
                .policy(ExecPolicy.full())
                .timeoutSeconds(1)
                .build();
        ShellExecutor executor = new ShellExecutor(shortConfig);
        ExecPlan p = plan(SLEEP, "5");

        ShellResult result = executor.execute(p);

        assertTrue(result.isTimedOut());
        assertEquals(-1, result.getExitCode());
    }

    @Test
    public void execute_customTimeoutViaParameter() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan(ECHO, "test");

        ShellResult result = executor.execute(p, 60);

        assertEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
    }

    // ==================== Working directory ====================

    @Test
    public void execute_workingDirectoryRespected() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        File subDir = new File(cwd, "shell_exec_test_subdir");
        subDir.mkdirs();
        try {
            ExecPlan p = new ExecPlan(PWD_EXE, Collections.emptyList(),
                    subDir, ExecPlan.ExecMode.DIRECT);
            ShellResult result = executor.execute(p);

            assertEquals(0, result.getExitCode());
            String stdout = result.getStdout().trim();
            assertTrue("Output should contain directory name",
                    stdout.contains("shell_exec_test_subdir"));
        } finally {
            subDir.delete();
        }
    }

    // ==================== Exit code ====================

    @Test
    public void execute_exitCodeCaptured() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        // In SHELL mode, the executor wraps the plan as: sh -c "<exe> <argv...>".
        // So represent the shell built-in directly instead of nesting "sh -c" twice.
        ExecPlan p = new ExecPlan("exit",
                Arrays.asList("42"),
                cwd, ExecPlan.ExecMode.SHELL);

        ShellResult result = executor.execute(p);

        assertEquals(42, result.getExitCode());
    }

    // ==================== Policy enforcement ====================

    @Test(expected = SecurityException.class)
    public void execute_denyPolicy_rejectsAll() {
        ShellConfig config = ShellConfig.createDefault(); // DENY
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan(ECHO, "test");

        executor.execute(p);
    }

    @Test
    public void execute_allowlistPolicy_allowsListedCommand() {
        ShellConfig config = ShellConfig.createAllowlistDefault();
        ShellExecutor executor = new ShellExecutor(config);
        // Use the allowlisted echo path (resolved via planner in setUp)
        ExecPlan p = plan(ECHO, "allowlisted");

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("allowlisted"));
    }

    @Test(expected = SecurityException.class)
    public void execute_allowlistPolicy_rejectsUnlistedCommand() {
        ShellConfig config = ShellConfig.createAllowlistDefault();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan(findExeSilent("rm"), "-rf", "/");

        executor.execute(p);
    }

    // ==================== Plan hash / substitution attack ====================

    @Test(expected = SecurityException.class)
    public void execute_hashMismatch_throwsSubstitutionAttack() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);

        // Build a plan then tamper with its internal state by creating a forged plan
        // with a mismatched hash.
        ExecPlan original = plan(ECHO, "original");
        // Create a plan that claims the hash of a different plan
        ExecPlan forged = new ExecPlan(ECHO,
                Arrays.asList("tampered"), cwd, ExecPlan.ExecMode.DIRECT) {
            @Override
            public String getPlanHash() {
                return original.getPlanHash(); // wrong hash for these args
            }
        };

        executor.execute(forged);
    }

    // ==================== Null plan ====================

    @Test(expected = SecurityException.class)
    public void execute_nullPlan_throws() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        executor.execute(null);
    }

    // ==================== SHELL mode ====================

    @Test
    public void execute_shellMode_allowsPipes() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        // In SHELL mode, sh -c is used so pipes work
        ExecPlan p = new ExecPlan(ECHO,
                Arrays.asList("hello"),
                cwd, ExecPlan.ExecMode.SHELL);

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("hello"));
    }

    // ==================== Helper ====================

    /** Like {@link #findExe} but wraps checked exception (for use in lambda/field init). */
    private static String findExeSilent(String name) {
        try {
            return findExe(name);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve executable: " + name, e);
        }
    }
}