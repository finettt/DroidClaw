package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.PathValidator;

import static org.junit.Assert.*;

/**
 * Unit tests for ShellExecutor.
 */
public class ShellExecutorTest {
    private ShellExecutor executor;
    private ShellConfig config;
    private File workspaceRoot;
    private PathValidator pathValidator;

    @Before
    public void setUp() throws IOException {
        config = ShellConfig.createDefault();
        workspaceRoot = new File(System.getProperty("java.io.tmpdir"), "shell_executor_test_" + System.currentTimeMillis());
        workspaceRoot.mkdirs();
        pathValidator = new PathValidator(workspaceRoot);
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
        // Create a subdirectory in workspace
        File subDir = new File(workspaceRoot, "subdir");
        subDir.mkdirs();
        
        try {
            ShellResult result = executor.execute("pwd", subDir);
            
            assertTrue("Command should succeed", result.isSuccess());
            assertTrue("Output should contain directory name",
                       result.getStdout().contains("subdir") ||
                       result.getStdout().contains(workspaceRoot.getName()));
        } finally {
            subDir.delete();
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

    // === Tests for PathValidator integration ===

    @Test
    public void testExecutorWithPathValidator() {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        ShellResult result = vfsExecutor.execute("echo test");
        assertTrue("Command should succeed with PathValidator", result.isSuccess());
    }

    @Test
    public void testWorkingDirectoryValidationWithinWorkspace() throws IOException {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        // Create a subdirectory within workspace
        File validDir = new File(workspaceRoot, "valid_dir");
        validDir.mkdirs();
        
        try {
            ShellResult result = vfsExecutor.execute("pwd", validDir);
            assertTrue("Command should succeed within workspace", result.isSuccess());
        } finally {
            validDir.delete();
        }
    }

    @Test
    public void testWorkingDirectoryValidationOutsideWorkspace() throws IOException {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        // Create a directory outside workspace
        File outsideDir = new File(System.getProperty("java.io.tmpdir"), "outside_workspace_" + System.currentTimeMillis());
        outsideDir.mkdirs();
        
        try {
            // This should throw SecurityException because directory is outside workspace
            try {
                vfsExecutor.execute("pwd", outsideDir);
                fail("Should have thrown SecurityException for directory outside workspace");
            } catch (SecurityException e) {
                assertTrue("Error should mention sandbox or workspace",
                           e.getMessage().contains("sandbox") ||
                           e.getMessage().contains("workspace") ||
                           e.getMessage().contains("outside"));
            }
        } finally {
            outsideDir.delete();
        }
    }

    @Test
    public void testExecuteWithRelativeDir() throws IOException {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        // Create a subdirectory
        File subDir = new File(workspaceRoot, "relative_test_dir");
        subDir.mkdirs();
        
        try {
            // Test the new executeWithRelativeDir method
            ShellResult result = vfsExecutor.executeWithRelativeDir("pwd", "relative_test_dir");
            assertTrue("Command should succeed with relative path", result.isSuccess());
        } finally {
            subDir.delete();
        }
    }

    @Test
    public void testExecuteWithRelativeDirNoPathValidator() {
        // Create executor without PathValidator
        ShellExecutor noVfsExecutor = new ShellExecutor(config);
        
        try {
            noVfsExecutor.executeWithRelativeDir("pwd", "some_dir");
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention PathValidator",
                       e.getMessage().contains("PathValidator"));
        }
    }

    @Test
    public void testExecuteWithRelativeDirPathTraversal() throws IOException {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        // Try path traversal
        try {
            vfsExecutor.executeWithRelativeDir("pwd", "../../../tmp");
            fail("Should have thrown SecurityException for path traversal");
        } catch (SecurityException e) {
            assertTrue("Error should mention security or path",
                       e.getMessage().contains("Security") ||
                       e.getMessage().contains("Invalid") ||
                       e.getMessage().contains("outside"));
        }
    }

    @Test
    public void testNullWorkingDirectoryWithNullPathValidator() {
        // Executor without PathValidator should work with null working directory
        ShellExecutor noVfsExecutor = new ShellExecutor(config);
        
        ShellResult result = noVfsExecutor.execute("echo test", null);
        assertTrue("Should work with null working directory", result.isSuccess());
    }

    @Test
    public void testNullWorkingDirectoryWithPathValidator() {
        // Executor with PathValidator should work with null working directory
        // (uses process's default working directory)
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        ShellResult result = vfsExecutor.execute("echo test", null);
        assertTrue("Should work with null working directory", result.isSuccess());
    }

    @Test
    public void testNestedDirectoryInWorkspace() throws IOException {
        // Create executor with PathValidator
        ShellExecutor vfsExecutor = new ShellExecutor(config, pathValidator);
        
        // Create nested directory structure
        File nestedDir = new File(workspaceRoot, "level1/level2/level3");
        nestedDir.mkdirs();
        
        try {
            ShellResult result = vfsExecutor.execute("pwd", nestedDir);
            assertTrue("Should work with nested directory within workspace", result.isSuccess());
        } finally {
            // Cleanup
            nestedDir.delete();
            new File(workspaceRoot, "level1/level2").delete();
            new File(workspaceRoot, "level1").delete();
        }
    }
}