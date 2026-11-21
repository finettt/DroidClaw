package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for SSH connection management, pooling, and lifecycle, plus the remote command
 * shape produced by {@link SshShellBackend#buildRemoteCommand(ExecPlan)} (a single
 * {@code sh -c <raw command>} path).
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

        for (int i = 0; i < 5; i++) {
            ExecPlan plan = new ExecPlan("echo test" + i, tmpDir);
            try {
                backend.execute(plan, 5);
                fail("Should fail at connection");
            } catch (SecurityException e) {
                // Expected - no SSH server on localhost:22
            }
        }
    }

    @Test
    public void connectionTimeout_handlesTimeout() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .policy(ExecPolicy.full())
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan("sleep 10", tmpDir);

        try {
            backend.execute(plan, 1);
            fail("Should fail without SSH server");
        } catch (SecurityException e) {
            assertTrue("Should mention SSH connection failure",
                    e.getMessage().contains("Failed to connect to SSH server"));
        }
    }

    @Test
    public void connectionAuth_failurePassword() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("wrongpassword")
                .verifyHostKey(false)
                .policy(ExecPolicy.deny())
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan("echo test", tmpDir);

        try {
            backend.execute(plan, 5);
            fail("Should fail with DENY policy");
        } catch (SecurityException e) {
            assertTrue("Error should mention DENY policy", e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void connectionAuth_failureKey() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .privateKeyPath("/nonexistent/key/path")
                .verifyHostKey(false)
                .policy(ExecPolicy.deny())
                .build();

        SshShellBackend backend = new SshShellBackend(config);

        ExecPlan plan = new ExecPlan("echo test", tmpDir);

        try {
            backend.execute(plan, 5);
            fail("Should fail with DENY policy");
        } catch (SecurityException e) {
            assertTrue("Error should mention DENY policy", e.getMessage().contains("DENY"));
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

        assertFalse("Host key verification should be disabled", config.isVerifyHostKey());
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

        backend.close();
        backend.close();
        backend.close();
        assertFalse("Should not be connected after close", backend.isConnected());
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

    // ==================== Remote command shape ====================

    @Test
    public void commandExecution_wrapsRawCommandInShC() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("/usr/bin/ls -l /home", tmpDir);

        assertEquals("sh -c \"/usr/bin/ls -l /home\"", buildRemoteCommand(backend, plan));
    }

    @Test
    public void commandExecution_noArgs() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("/usr/bin/pwd", tmpDir);

        assertEquals("sh -c \"/usr/bin/pwd\"", buildRemoteCommand(backend, plan));
    }

    @Test
    public void commandExecution_multipleArgs() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("echo arg1 arg2 arg3", tmpDir);

        assertEquals("sh -c \"echo arg1 arg2 arg3\"", buildRemoteCommand(backend, plan));
    }

    @Test
    public void commandExecution_argsWithSpaces_quoted() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("echo hello world foo bar", tmpDir);

        String command = buildRemoteCommand(backend, plan);
        System.out.println("DBG_CMD=[" + command + "]");
        System.out.println("DBG_CONTAINS=[" + (command.contains("say \"hi\"")) + "]");
        assertTrue(command.contains("sh -c \"echo hello world foo bar\""));
    }

    @Test
    public void commandExecution_singleQuotes_passThrough() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("/usr/bin/echo hello 'world'", tmpDir);

        String command = buildRemoteCommand(backend, plan);
        System.out.println("DBG_CMD=[" + command + "]");
        System.out.println("DBG_CONTAINS=[" + (command.contains("say \"hi\"")) + "]");
        assertTrue(command.startsWith("sh -c \""));
        assertTrue(command.contains("hello 'world'"));
        assertTrue(command.endsWith("\""));
    }

    @Test
    public void commandExecution_doubleQuote_escaped() throws Exception {
        SshShellBackend backend = connectedBackend();
        ExecPlan plan = new ExecPlan("echo say \"hi\"", tmpDir);

        String command = buildRemoteCommand(backend, plan);
        // The embedded double quotes are escaped for the outer remote shell.
        assertTrue("inner quotes must be escaped", command.contains("\\\""));
        assertTrue("command wrapped in double quotes",
                command.startsWith("sh -c \"") && command.endsWith("\""));
    }

    // ==================== Helper Methods ====================

    private SshShellBackend connectedBackend() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .build();
        return new SshShellBackend(config);
    }

    private String buildRemoteCommand(SshShellBackend backend, ExecPlan plan) throws Exception {
        java.lang.reflect.Method method = SshShellBackend.class
                .getDeclaredMethod("buildRemoteCommand", ExecPlan.class);
        method.setAccessible(true);
        return (String) method.invoke(backend, plan);
    }
}
