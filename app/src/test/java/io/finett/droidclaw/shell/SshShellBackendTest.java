package io.finett.droidclaw.shell;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SshShellBackend}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Configuration validation</li>
 *   <li>Command execution flow (mocked JSch + ChannelExec)</li>
 *   <li>Error handling for connection failures</li>
 *   <li>Timeout handling</li>
 *   <li>Host key verification modes</li>
 *   <li>Command string building (no reflection needed)</li>
 *   <li>Thread safety (concurrent connection setup)</li>
 * </ul>
 */
public class SshShellBackendTest {

    @Mock
    private JSch mockJsch;

    @Mock
    private Session mockSession;

    @Mock
    private ChannelExec mockChannel;

    private SshShellBackend.JSchFactory mockJschFactory;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Stub JSch factory to return mocked JSch
        when(mockJsch.getSession(anyString(), anyString(), anyInt())).thenReturn(mockSession);
        when(mockSession.isConnected()).thenReturn(true);
        doNothing().when(mockSession).connect(anyInt());
        doNothing().when(mockSession).setServerAliveInterval(anyInt());
        doNothing().when(mockSession).setPassword(anyString());
        doNothing().when(mockSession).setConfig(anyString(), anyString());

        // Stub channel
        when(mockSession.openChannel(eq("exec"))).thenReturn(mockChannel);
        doNothing().when(mockChannel).setCommand(anyString());
        doNothing().when(mockChannel).setOutputStream(any());
        doNothing().when(mockChannel).setErrStream(any());
        doNothing().when(mockChannel).connect(anyInt());
        when(mockChannel.isConnected()).thenReturn(true);
        when(mockChannel.isClosed()).thenReturn(true); // immediately closed
        when(mockChannel.getExitStatus()).thenReturn(0);
        doNothing().when(mockChannel).disconnect();

        // Create a factory that returns the mocked JSch
        mockJschFactory = () -> mockJsch;
    }

    private SshShellBackend createBackend() {
        return createBackend("localhost");
    }

    private SshShellBackend createBackend(String host) {
        SshConfig config = new SshConfig.Builder()
                .host(host)
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();
        return new SshShellBackend(config, mockJschFactory);
    }

    // ==================== Null & validation tests ====================

    @Test
    public void execute_nullPlan_throwsSecurityException() {
        SshShellBackend backend = createBackend();

        try {
            backend.execute(null, 30);
            fail("Should have thrown SecurityException for null plan");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void execute_planWithHashMismatch_throwsSecurityException() {
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // Create a forged plan with wrong hash
        ExecPlan forged = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-a"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        ) {
            @Override
            public String getPlanHash() {
                return "wrong_hash_" + plan.getPlanHash();
            }
        };

        try {
            backend.execute(forged, 30);
            fail("Should have thrown SecurityException for hash mismatch");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("tampered"));
        }
    }

    @Test
    public void execute_planWithDenyPolicy_throwsSecurityException() throws Exception {
        ShellConfig shellConfig = ShellConfig.createDefault(); // DENY policy
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .policy(shellConfig.getPolicy())
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 30);
            fail("Should have thrown SecurityException for denied policy");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void execute_planWithAllowlistPolicy_rejectsUnlistedCommand() {
        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .policy(shellConfig.getPolicy())
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/system/bin/rm",
                Arrays.asList("-rf", "/"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 30);
            fail("Should have thrown SecurityException for unlisted command");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("not in SSH allowlist"));
        }
    }

    // ==================== Command building tests (no reflection) ====================

    @Test
    public void buildRemoteCommand_directMode_escapesArgs() {
        SshShellBackend backend = createBackend();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l", "/home"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = backend.buildRemoteCommand(plan);

        assertEquals("/usr/bin/ls '-l' '/home'", command);
    }

    @Test
    public void buildRemoteCommand_shellMode_passesAsIs() {
        SshShellBackend backend = createBackend();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "echo",
                Arrays.asList("hello world"),
                tmpDir,
                ExecPlan.ExecMode.SHELL
        );

        String command = backend.buildRemoteCommand(plan);

        assertEquals("echo hello world", command);
    }

    @Test
    public void buildRemoteCommand_withSpecialChars_escapesQuotes() {
        SshShellBackend backend = createBackend();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/echo",
                Arrays.asList("hello 'world'"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String command = backend.buildRemoteCommand(plan);

        assertTrue(command.contains("/usr/bin/echo"));
        // Args are wrapped in single quotes
        assertTrue(command.contains("'hello"));
        assertTrue(command.contains("world'"));
    }

    @Test
    public void escapeShellArg_simpleArg() {
        SshShellBackend backend = createBackend();
        assertEquals("'hello'", backend.escapeShellArg("hello"));
    }

    @Test
    public void escapeShellArg_withSingleQuote() {
        SshShellBackend backend = createBackend();
        assertEquals("'it'\\''s a test'", backend.escapeShellArg("it's a test"));
    }

    @Test
    public void escapeShellArg_withMultipleQuotes() {
        SshShellBackend backend = createBackend();
        assertEquals("'a'\\''b'\\''c'", backend.escapeShellArg("a'b'c"));
    }

    @Test
    public void escapeShellArg_emptyString() {
        SshShellBackend backend = createBackend();
        assertEquals("''", backend.escapeShellArg(""));
    }

    @Test
    public void escapeShellArg_withSpaces() {
        SshShellBackend backend = createBackend();
        assertEquals("'hello world'", backend.escapeShellArg("hello world"));
    }

    // ==================== Execution flow tests (with mocks) ====================

    @Test
    public void execute_successReturnsResult() throws Exception {
        // When channel.isClosed() returns true immediately (already set in setUp),
        // the loop exits, exitCode = 0
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        ShellResult result = backend.execute(plan, 30);

        assertEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
        assertTrue(result.isSuccess());
    }

    @Test
    public void execute_timeoutReturnsTimedOutResult() throws Exception {
        // Make the channel never close so the timeout kicks in
        when(mockChannel.isClosed()).thenReturn(false);

        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/sleep",
                Arrays.asList("3600"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        ShellResult result = backend.execute(plan, 1); // 1 second timeout

        assertTrue(result.isTimedOut());
        assertTrue(result.getStderr().contains("timed out"));
        assertEquals(-1, result.getExitCode());
    }

    @Test
    public void execute_jschException_setsConnectedFalse() throws Exception {
        // Make openChannel throw
        doThrow(new JSchException("connection refused"))
                .when(mockSession).openChannel(eq("exec"));

        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        try {
            backend.execute(plan, 30);
            fail("Should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("SSH execution failed"));
        }

        assertFalse(backend.isConnected());
    }

    @Test
    public void ensureConnected_usesInjectedFactory() throws Exception {
        SshShellBackend backend = createBackend();

        // Trigger connection
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        // Verify session.connect was called
        verify(mockSession).connect(10_000);
    }

    @Test
    public void ensureConnected_passesAuthTypeConfig() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        // Verify password was set on session
        verify(mockSession).setPassword("testpass");
    }

    @Test
    public void ensureConnected_setsHostKeyVerification() throws Exception {
        // Test with host key verification enabled
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(true) // explicitly enabled
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        verify(mockSession).setConfig("StrictHostKeyChecking", "yes");
    }

    @Test
    public void ensureConnected_disablesHostKeyVerification() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false) // explicitly disabled
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        verify(mockSession).setConfig("StrictHostKeyChecking", "no");
    }

    // ==================== Connection lifecycle tests ====================

    @Test
    public void isConnected_initiallyFalse() {
        SshShellBackend backend = createBackend();
        assertFalse(backend.isConnected());
    }

    @Test
    public void isConnected_trueAfterConnected() throws Exception {
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        assertTrue(backend.isConnected());
    }

    @Test
    public void close_disconnectsSession() throws Exception {
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);
        assertTrue(backend.isConnected());

        backend.close();
        assertFalse(backend.isConnected());

        // Verify session.disconnect was called
        verify(mockSession).disconnect();
    }

    @Test
    public void close_multipleTimes_safe() {
        SshShellBackend backend = createBackend();

        // Multiple close calls should not throw
        backend.close();
        backend.close();
        backend.close();
        assertFalse(backend.isConnected());
    }

    @Test
    public void testConnection_success() throws Exception {
        SshShellBackend backend = createBackend();

        boolean result = backend.testConnection();

        assertTrue(result);
        verify(mockSession, atLeastOnce()).openChannel(eq("exec"));
    }

    @Test
    public void testConnection_failureSetsConnectedFalse() throws Exception {
        // Make the connection fail
        doThrow(new JSchException("refused"))
                .when(mockSession).openChannel(eq("exec"));

        SshShellBackend backend = createBackend();

        boolean result = backend.testConnection();

        assertFalse(result);
        assertFalse(backend.isConnected());
    }

    // ==================== Thread safety tests ====================

    @Test
    public void close_isThreadSafe() throws Exception {
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        // Run multiple closes in parallel
        Thread t1 = new Thread(backend::close);
        Thread t2 = new Thread(backend::close);
        Thread t3 = new Thread(backend::close);

        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();

        assertFalse(backend.isConnected());
    }
}
