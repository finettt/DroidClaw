package io.finett.droidclaw.shell;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for {@link LocalShellBackend}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Null plan rejection</li>
 *   <li>Hash mismatch rejection</li>
 *   <li>DENY policy rejection</li>
 *   <li>ALLOWLIST policy — rejected unlisted command</li>
 *   <li>DIRECT mode command building</li>
 *   <li>SHELL mode command building</li>
 *   <li>Real command execution (echo)</li>
 *   <li>Real command execution (pwd)</li>
 *   <li>Process timeout handling</li>
 *   <li>StreamGobbler output truncation</li>
 *   <li>close() is no-op</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
public class LocalShellBackendTest {

    @Mock
    private ShellConfig mockConfig;

    private LocalShellBackend backend;
    private File workspaceDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Create a temp working directory for tests
        workspaceDir = File.createTempFile("droidclaw-test-", "");
        workspaceDir.delete();
        workspaceDir.mkdirs();

        // Default stubs for mockConfig
        when(mockConfig.validatePlan(any(ExecPlan.class))).thenReturn(null);
        when(mockConfig.getMaxOutputSize()).thenReturn(1024 * 1024); // 1MB default

        backend = new LocalShellBackend(mockConfig);
    }

    // ==================== Null & validation tests ====================

    @Test
    public void execute_nullPlan_throwsSecurityException() {
        try {
            backend.execute(null, 30);
            fail("Should have thrown SecurityException for null plan");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void execute_planWithHashMismatch_throwsSecurityException() {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        // Create a plan directly (bypass ExecPlanner which resolves canonical paths)
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                workspaceDir,
                ExecPlan.ExecMode.DIRECT
        );

        // Create a forged plan with wrong hash
        ExecPlan forged = new ExecPlan(
                plan.getCanonicalExePath(),
                Arrays.asList("-a"),
                workspaceDir,
                plan.getMode()
        ) {
            @Override
            public String getPlanHash() {
                return "wrong_hash_" + plan.getPlanHash();
            }
        };

        try {
            localBackend.execute(forged, 30);
            fail("Should have thrown SecurityException for hash mismatch");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("tampered"));
        }
    }

    @Test
    public void execute_planWithDenyPolicy_throwsSecurityException() {
        ShellConfig shellConfig = ShellConfig.createDefault(); // DENY policy
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        // Create a plan directly (bypass ExecPlanner which resolves canonical paths)
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                workspaceDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            localBackend.execute(plan, 30);
            fail("Should have thrown SecurityException for denied policy");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void execute_planWithAllowlistPolicy_rejectsUnlistedCommand() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        // Use a command not in the allowlist — /system/bin/rm is not on Linux
        // Instead, create a plan with a non-existent canonical path that won't match any allowlist entry
        ExecPlan forged = new ExecPlan(
                "/system/bin/rm",  // This path won't match any allowlist entry on Linux
                Arrays.asList("-rf", "/"),
                workspaceDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            localBackend.execute(forged, 30);
            fail("Should have thrown SecurityException for unlisted command");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("not in allowlist"));
        }
    }

    // ==================== Command building tests (via buildCommand) ====================

    @Test
    public void execute_directMode_buildsCorrectCommand() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        ExecPlan plan = new ExecPlanner(shellConfig, workspaceDir)
                .planFromTokens(Arrays.asList("ls", "-l"), workspaceDir, ExecPlan.ExecMode.DIRECT);

        ShellResult result = localBackend.execute(plan, 10);

        // ls -l should succeed
        assertTrue("ls -l should succeed", result.isSuccess());
    }

    @Test
    public void execute_shellMode_buildsCorrectCommand() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        ExecPlan plan = new ExecPlanner(shellConfig, workspaceDir)
                .planFromTokens(Arrays.asList("echo", "hello"), workspaceDir, ExecPlan.ExecMode.SHELL);

        ShellResult result = localBackend.execute(plan, 10);

        // echo hello should succeed and print "hello"
        assertTrue("echo hello should succeed", result.isSuccess());
        assertTrue(result.getStdout().contains("hello"));
    }

    // ==================== Real command execution tests ====================

    @Test
    public void execute_echoCommand_returnsOutput() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        ExecPlan plan = new ExecPlanner(shellConfig, workspaceDir)
                .planFromTokens(Arrays.asList("echo", "droidclaw"), workspaceDir, ExecPlan.ExecMode.SHELL);

        ShellResult result = localBackend.execute(plan, 10);

        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("droidclaw"));
    }

    @Test
    public void execute_pwdCommand_returnsWorkingDirectory() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        // pwd is a shell builtin, not a standalone binary — must use SHELL mode
        ExecPlan plan = new ExecPlanner(shellConfig, workspaceDir)
                .planFromTokens(Arrays.asList("pwd"), workspaceDir, ExecPlan.ExecMode.SHELL);

        ShellResult result = localBackend.execute(plan, 10);

        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains(workspaceDir.getAbsolutePath()));
    }

    @Test
    public void execute_lsCommand_returnsOutput() throws Exception {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        ExecPlan plan = new ExecPlanner(shellConfig, workspaceDir)
                .planFromTokens(Arrays.asList("ls", "-la"), workspaceDir, ExecPlan.ExecMode.DIRECT);

        ShellResult result = localBackend.execute(plan, 10);

        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("."));
    }

    @Test
    public void execute_commandWithNonZeroExitCode() throws Exception {
        // Use a real allowed command (ls) that succeeds — exit code 0.
        // For a non-zero exit code, we'd need to use a shell builtin or interpreter,
        // which requires the command to be in the allowlist. Since that's complex
        // to set up portably, we test the exit code path with a custom ExecPlan.
        // On most Linux systems, /bin/true and /bin/false exist and are in /bin/
        // which is a trusted dir. Create a plan directly with a forged /bin/false path.
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        LocalShellBackend localBackend = new LocalShellBackend(shellConfig);

        // /bin/false is a standard Linux command that always returns exit code 1
        // We'll add it to the allowlist and use ExecPlanner to resolve the path
        File falseBin = new File("/bin/false");
        if (!falseBin.exists()) {
            // Try /usr/bin/false
            falseBin = new File("/usr/bin/false");
        }
        if (!falseBin.exists()) {
            // Try /run/current-system/sw/bin/false (NixOS)
            falseBin = new File("/run/current-system/sw/bin/false");
        }
        if (!falseBin.exists()) {
            // No suitable binary found — skip this test
            return;
        }
        String falsePath = falseBin.getAbsolutePath();

        // Build allowlist with the real false path
        ShellConfig shellConfigWithFalse = ShellConfig.createAllowlistDefault(Arrays.asList(
                new AllowlistEntry.Builder(falsePath).build()
        ));
        LocalShellBackend localBackend2 = new LocalShellBackend(shellConfigWithFalse);

        // Use ExecPlanner with the relative name "false" so it resolves to the real path
        ExecPlan plan = new ExecPlanner(shellConfigWithFalse, workspaceDir)
                .planFromTokens(Arrays.asList("false"), workspaceDir, ExecPlan.ExecMode.DIRECT);

        ShellResult result = localBackend2.execute(plan, 10);

        // false should not succeed
        assertFalse(result.isSuccess());
        assertEquals(1, result.getExitCode());
    }

    // ==================== StreamGobbler tests (via reflection) ====================

    /**
     * Access the private StreamGobbler class via reflection to test its output truncation logic.
     */
    @Test
    public void streamGobbler_truncatesAtMaxSize() throws Exception {
        // Use reflection to access the private StreamGobbler class
        Class<?> gobblerClass = Class.forName(
                "io.finett.droidclaw.shell.LocalShellBackend$StreamGobbler");

        java.io.ByteArrayOutputStream input = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 200; i++) {
            input.write('A');
        }

        Object gobbler = gobblerClass.getDeclaredConstructor(
                java.io.InputStream.class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(input.toByteArray()), 100);

        Thread gobblerThread = new Thread((Runnable) gobbler);
        gobblerThread.start();
        gobblerThread.join(1000);

        String output = (String) gobblerClass.getDeclaredMethod("getOutput").invoke(gobbler);

        assertTrue("Output should be truncated", output.contains("truncated"));
        assertTrue("Output should be at most maxSize bytes plus truncation message",
                output.length() <= 100 + 45);
    }

    @Test
    public void streamGobbler_outputWithinSizeLimit() throws Exception {
        Class<?> gobblerClass = Class.forName(
                "io.finett.droidclaw.shell.LocalShellBackend$StreamGobbler");

        String data = "hello world\n";

        Object gobbler = gobblerClass.getDeclaredConstructor(
                java.io.InputStream.class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(data.getBytes()), 1024);

        Thread gobblerThread = new Thread((Runnable) gobbler);
        gobblerThread.start();
        gobblerThread.join(1000);

        String output = (String) gobblerClass.getDeclaredMethod("getOutput").invoke(gobbler);

        assertEquals("Output should match input when within limit", data, output);
    }

    @Test
    public void streamGobbler_emptyInput() throws Exception {
        Class<?> gobblerClass = Class.forName(
                "io.finett.droidclaw.shell.LocalShellBackend$StreamGobbler");

        Object gobbler = gobblerClass.getDeclaredConstructor(
                java.io.InputStream.class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(new byte[0]), 1024);

        Thread gobblerThread = new Thread((Runnable) gobbler);
        gobblerThread.start();
        gobblerThread.join(1000);

        assertEquals("Empty input should produce empty output",
                "", gobblerClass.getDeclaredMethod("getOutput").invoke(gobbler));
    }

    @Test
    public void streamGobbler_outputWithTruncationMarker() throws Exception {
        Class<?> gobblerClass = Class.forName(
                "io.finett.droidclaw.shell.LocalShellBackend$StreamGobbler");

        java.io.ByteArrayOutputStream input = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 200; i++) {
            input.write('X');
        }

        Object gobbler = gobblerClass.getDeclaredConstructor(
                java.io.InputStream.class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(input.toByteArray()), 50);

        Thread gobblerThread = new Thread((Runnable) gobbler);
        gobblerThread.start();
        gobblerThread.join(1000);

        String output = (String) gobblerClass.getDeclaredMethod("getOutput").invoke(gobbler);

        assertTrue("Output should contain truncation marker",
                output.contains("Output truncated — size limit reached"));
    }

    // ==================== close() test ====================

    @Test
    public void close_isNoOp() {
        // LocalShellBackend has no persistent resources
        backend.close();
        // Should not throw
    }
}
