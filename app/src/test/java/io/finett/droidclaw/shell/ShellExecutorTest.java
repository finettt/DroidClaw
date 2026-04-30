package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellExecutor} using the new ExecPlan-based API.
 */
public class ShellExecutorTest {

    private File cwd;

    @Before
    public void setUp() {
        cwd = new File(System.getProperty("java.io.tmpdir"));
    }

    private ExecPlan plan(String exe, String... argv) {
        return new ExecPlan(exe, Arrays.asList(argv), cwd, ExecPlan.ExecMode.DIRECT);
    }

    // ==================== Basic execution ====================

    @Test
    public void execute_simpleEchoCommand_succeeds() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan("/system/bin/echo", "Hello World");

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
        ExecPlan p = plan("/system/bin/ls", "/nonexistent_directory_12345");

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
        ExecPlan p = plan("/system/bin/printf", "line1\\nline2\\nline3\\n");

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
        ExecPlan p = plan("/system/bin/sleep", "5");

        ShellResult result = executor.execute(p);

        assertTrue(result.isTimedOut());
        assertEquals(-1, result.getExitCode());
    }

    @Test
    public void execute_customTimeoutViaParameter() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan("/system/bin/echo", "test");

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
            ExecPlan p = new ExecPlan("/system/bin/pwd", Collections.emptyList(),
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
        // Use sh -c to run exit 42, but we need SHELL mode for that
        ExecPlan p = new ExecPlan("/system/bin/sh",
                Arrays.asList("-c", "exit 42"),
                cwd, ExecPlan.ExecMode.SHELL);

        ShellResult result = executor.execute(p);

        assertEquals(42, result.getExitCode());
    }

    // ==================== Policy enforcement ====================

    @Test(expected = SecurityException.class)
    public void execute_denyPolicy_rejectsAll() {
        ShellConfig config = ShellConfig.createDefault(); // DENY
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan("/system/bin/echo", "test");

        executor.execute(p);
    }

    @Test
    public void execute_allowlistPolicy_allowsListedCommand() {
        ShellConfig config = ShellConfig.createAllowlistDefault();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan("/system/bin/echo", "allowlisted");

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("allowlisted"));
    }

    @Test(expected = SecurityException.class)
    public void execute_allowlistPolicy_rejectsUnlistedCommand() {
        ShellConfig config = ShellConfig.createAllowlistDefault();
        ShellExecutor executor = new ShellExecutor(config);
        ExecPlan p = plan("/system/bin/rm", "-rf", "/");

        executor.execute(p);
    }

    // ==================== Plan hash / substitution attack ====================

    @Test(expected = SecurityException.class)
    public void execute_hashMismatch_throwsSubstitutionAttack() {
        ShellConfig config = ShellConfig.createFull();
        ShellExecutor executor = new ShellExecutor(config);

        // Build a plan then tamper with its internal state by creating a forged plan
        // with a mismatched hash.
        ExecPlan original = plan("/system/bin/echo", "original");
        // Create a plan that claims the hash of a different plan
        ExecPlan forged = new ExecPlan("/system/bin/echo",
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
        ExecPlan p = new ExecPlan("/system/bin/echo",
                Arrays.asList("hello"),
                cwd, ExecPlan.ExecMode.SHELL);

        ShellResult result = executor.execute(p);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("hello"));
    }
}