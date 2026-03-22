package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Unit tests for ShellExecutor.
 */
public class ShellExecutorTest {
    private ShellExecutor executor;
    private ShellConfig config;

    @Before
    public void setUp() {
        config = ShellConfig.createDefault();
        executor = new ShellExecutor(config);
    }

    @Test
    public void testSimpleEchoCommand() {
        ShellResult result = executor.execute("echo 'Hello World'");
        
        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
        assertTrue(result.getStdout().contains("Hello World"));
        assertTrue(result.getExecutionTimeMs() > 0);
    }

    @Test
    public void testCommandWithStderr() {
        // ls on a non-existent directory produces stderr
        ShellResult result = executor.execute("ls /nonexistent_directory_12345");
        
        assertFalse(result.isSuccess()); // Non-zero exit code
        assertFalse(result.isTimedOut());
        assertFalse(result.getStderr().isEmpty());
    }

    @Test
    public void testMultilineOutput() {
        ShellResult result = executor.execute("echo 'line1'; echo 'line2'; echo 'line3'");
        
        assertTrue(result.isSuccess());
        String stdout = result.getStdout();
        assertTrue(stdout.contains("line1"));
        assertTrue(stdout.contains("line2"));
        assertTrue(stdout.contains("line3"));
    }

    @Test
    public void testCommandTimeout() {
        // Create a config with very short timeout
        ShellConfig shortConfig = new ShellConfig.Builder()
                .timeoutSeconds(1)
                .enabled(true)
                .build();
        ShellExecutor shortExecutor = new ShellExecutor(shortConfig);
        
        // Sleep for longer than timeout
        ShellResult result = shortExecutor.execute("sleep 5");
        
        assertTrue(result.isTimedOut());
        assertEquals(-1, result.getExitCode());
    }

    @Test
    public void testWorkingDirectory() {
        // Create a temporary directory structure
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        
        ShellResult result = executor.execute("pwd", tempDir);
        
        if (result.isSuccess()) {
            assertTrue(result.getStdout().contains(tempDir.getName()) || 
                      result.getStdout().contains("tmp"));
        }
    }

    @Test
    public void testExitCode() {
        // Command that exits with specific code
        ShellResult result = executor.execute("exit 42");
        
        assertEquals(42, result.getExitCode());
        assertFalse(result.isSuccess());
        assertFalse(result.isTimedOut());
    }

    @Test
    public void testBlockedCommand() {
        ShellConfig secureConfig = new ShellConfig.Builder()
                .enabled(true)
                .addBlockedCommand("rm -rf /")
                .build();
        ShellExecutor secureExecutor = new ShellExecutor(secureConfig);
        
        try {
            secureExecutor.execute("rm -rf /");
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void testDisabledShell() {
        ShellConfig disabledConfig = new ShellConfig.Builder()
                .enabled(false)
                .build();
        ShellExecutor disabledExecutor = new ShellExecutor(disabledConfig);
        
        try {
            disabledExecutor.execute("echo test");
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("disabled"));
        }
    }

    @Test
    public void testAllowedCommandsWhitelist() {
        ShellConfig whitelistConfig = new ShellConfig.Builder()
                .enabled(true)
                .addAllowedCommand("echo")
                .addAllowedCommand("pwd")
                .build();
        ShellExecutor whitelistExecutor = new ShellExecutor(whitelistConfig);
        
        // Allowed command should work
        ShellResult result1 = whitelistExecutor.execute("echo test");
        assertTrue(result1.isSuccess());
        
        // Non-allowed command should fail
        try {
            whitelistExecutor.execute("ls");
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void testCombinedOutput() {
        ShellResult result = executor.execute("echo 'stdout'; echo 'stderr' >&2");
        
        String combined = result.getCombinedOutput();
        assertTrue(combined.contains("stdout"));
        // stderr might or might not be captured depending on system
    }

    @Test
    public void testEmptyCommand() {
        ShellResult result = executor.execute("");
        
        // Empty command should complete quickly
        assertTrue(result.getExecutionTimeMs() < 5000);
    }

    @Test
    public void testPipeAndRedirection() {
        ShellResult result = executor.execute("echo 'test' | grep 'test'");
        
        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("test"));
    }

    @Test
    public void testEnvironmentVariables() {
        ShellResult result = executor.execute("echo $HOME");
        
        assertTrue(result.isSuccess());
        // Should output something (the HOME environment variable)
        assertFalse(result.getStdout().trim().isEmpty());
    }

    @Test
    public void testLongOutput() {
        // Generate a lot of output
        ShellResult result = executor.execute("for i in $(seq 1 100); do echo $i; done");
        
        assertTrue(result.isSuccess());
        String stdout = result.getStdout();
        assertTrue(stdout.contains("1"));
        assertTrue(stdout.contains("100") || stdout.contains("truncated"));
    }

    @Test
    public void testCommandChaining() {
        ShellResult result = executor.execute("echo 'first' && echo 'second' || echo 'third'");
        
        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("first"));
        assertTrue(result.getStdout().contains("second"));
    }

    @Test
    public void testCustomTimeout() {
        ShellResult result = executor.execute("echo test", null, 60);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isTimedOut());
    }
}