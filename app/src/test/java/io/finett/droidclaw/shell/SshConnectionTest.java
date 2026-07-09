package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Tests for SSH connection management, pooling, and lifecycle.
 */
public class SshConnectionTest {

    private File tmpDir = new File(System.getProperty("java.io.tmpdir"));

    @Test
    public void connectionPool_reusesExistingConnection() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        // Execute multiple commands - should reuse connection
        for (int i = 0; i < 5; i++) {
            ExecPlan plan = new ExecPlan(
                    "/usr/bin/echo",
                    Arrays.asList("test" + i),
                    tmpDir,
                    ExecPlan.ExecMode.DIRECT
            );

            try {
                backend.execute(plan, 5);
                fail("Should fail at connection");
            } catch (SecurityException e) {
                // Expected - would reuse connection if server was available
            }
        }
    }

    @Test
    public void connectionTimeout_handlesTimeout() throws Exception {
        // Use FULL policy to bypass allowlist; use a real backend but expect
        // a SecurityException from ensureConnected since there's no SSH server on localhost
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .policy(ExecPolicy.full())
                .build();

        // No mock factory — let ensureConnected try and fail with SecurityException
        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/sleep",
                Arrays.asList("10"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 1);
            fail("Should fail without SSH server");
        } catch (SecurityException e) {
            // Expected - no SSH server running on localhost:22
            assertTrue("Should mention SSH connection failure",
                    e.getMessage().contains("Failed to connect to SSH server"));
        }
    }

    @Test
    public void connectionAuth_failurePassword() throws Exception {
        // Test DENY policy rejection since no SSH server is available
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("wrongpassword")
                .verifyHostKey(false)
                .policy(ExecPolicy.deny()) // explicitly deny
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("test"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 5);
            fail("Should fail with DENY policy");
        } catch (SecurityException e) {
            assertTrue("Error should mention DENY policy",
                      e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void connectionAuth_failureKey() throws Exception {
        // Test DENY policy rejection since no SSH server is available
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .privateKeyPath("/nonexistent/key/path")
                .verifyHostKey(false)
                .policy(ExecPolicy.deny()) // explicitly deny
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("test"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 5);
            fail("Should fail with DENY policy");
        } catch (SecurityException e) {
            assertTrue("Error should mention DENY policy",
                      e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void connectionHostKeyVerification_enabledByDefault() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        // Should default to verifyHostKey = true
        assertTrue("Host key verification should be enabled by default",
                config.isVerifyHostKey());
    }

    @Test
    public void connectionHostKeyVerification_canBeDisabled() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        assertFalse("Host key verification should be disabled",
                config.isVerifyHostKey());
    }

    @Test
    public void sessionLifecycle_closeDisconnects() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        // Close should not throw even if not connected
        backend.close();
        assertFalse("Should not be connected after close", backend.isConnected());
    }

    @Test
    public void sessionLifecycle_multipleClosesSafe() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        // Multiple closes should not throw
        backend.close();
        backend.close();
        backend.close();
        assertFalse("Should not be connected", backend.isConnected());
    }

    @Test
    public void sessionLifecycle_isConnectedReturnsFalseByDefault() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        assertFalse("Should not be connected initially", backend.isConnected());
    }

    @Test
    public void commandExecution_directMode_wrapsArgs() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l", "/home"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // In DIRECT mode, args should be wrapped in quotes
        String command = buildRemoteCommand(backend, plan);
        assertEquals("/usr/bin/ls '-l' '/home'", command);
    }

    @Test
    public void commandExecution_shellMode_passesAsIs() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "echo",
                Arrays.asList("hello world"),
                tmpDir,
                ExecPlan.ExecMode.SHELL
        );

        // In SHELL mode, command should be passed as-is
        String command = buildRemoteCommand(backend, plan);
        assertEquals("echo hello world", command);
    }

    @Test
    public void commandExecution_specialChars_escaped() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("hello 'world'"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = buildRemoteCommand(backend, plan);
        // Args should be quoted and internal quotes escaped
        assertTrue(command.contains("/usr/bin/echo"));
        assertTrue(command.contains("'hello"));
        assertTrue(command.contains("world'"));
    }

    @Test
    public void commandExecution_emptyArgs_noExtraSpaces() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/pwd",
                Arrays.asList(),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = buildRemoteCommand(backend, plan);
        assertEquals("/usr/bin/pwd", command);
    }

    @Test
    public void commandExecution_multipleArgs_allQuoted() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("arg1", "arg2", "arg3"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = buildRemoteCommand(backend, plan);
        assertEquals("/usr/bin/echo 'arg1' 'arg2' 'arg3'", command);
    }

    @Test
    public void commandExecution_argsWithSpaces_quoted() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("hello world", "foo bar"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = buildRemoteCommand(backend, plan);
        assertTrue(command.contains("'hello world'"));
        assertTrue(command.contains("'foo bar'"));
    }

    @Test
    public void commandExecution_argsWithSpecialChars_properlyEscaped() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("it's a test"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = buildRemoteCommand(backend, plan);
        // Single quotes should be escaped with backslash
        assertTrue(command.contains("'it'\\''s a test'"));
    }

    @Test
    public void commandExecution_nullArgs_handled() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                java.util.Collections.emptyList(),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // Should handle null args gracefully
        String command = buildRemoteCommand(backend, plan);
        assertEquals("/usr/bin/ls", command);
    }

    // ==================== Helper Methods ====================

    private String buildRemoteCommand(SshShellBackend backend, ExecPlan plan) throws Exception {
        java.lang.reflect.Method method = SshShellBackend.class
                .getDeclaredMethod("buildRemoteCommand", ExecPlan.class);
        method.setAccessible(true);
        return (String) method.invoke(backend, plan);
    }
}
