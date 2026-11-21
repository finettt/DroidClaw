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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SshShellBackend}.
 *
 * <p>Tests cover configuration validation, the command-execution flow (mocked JSch +
 * ChannelExec), connection/timeout/host-key handling, the {@code sh -c <raw command>}
 * remote command shape, and thread-safe session management.
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

        SshShellBackend.JSchFactory mockFactory = new SshShellBackend.JSchFactory() {
            @Override
            public JSch create() {
                return mockJsch;
            }
        };
        mockJschFactory = mockFactory;

        doReturn(mockSession).when(mockJsch).getSession(anyString(), anyString(), anyInt());

        when(mockSession.isConnected()).thenReturn(true);
        doNothing().when(mockSession).connect(anyInt());
        doNothing().when(mockSession).setServerAliveInterval(anyInt());
        doNothing().when(mockSession).setPassword(anyString());
        doNothing().when(mockSession).setConfig(anyString(), anyString());

        doReturn(mockChannel).when(mockSession).openChannel(eq("exec"));
        doNothing().when(mockChannel).setCommand(anyString());
        doNothing().when(mockChannel).setOutputStream(any());
        doNothing().when(mockChannel).setErrStream(any());
        doNothing().when(mockChannel).connect(anyInt());
        when(mockChannel.isConnected()).thenReturn(true);
        when(mockChannel.isClosed()).thenReturn(true);
        when(mockChannel.getExitStatus()).thenReturn(0);
        doNothing().when(mockChannel).disconnect();
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
                .policy(ExecPolicy.full())
                .build();
        return new SshShellBackend(config, mockJschFactory);
    }

    private ExecPlan plan(String rawCommand) {
        return new ExecPlan(rawCommand, new File(System.getProperty("java.io.tmpdir")));
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

        ExecPlan forged = new ExecPlan("ls -a", new File(System.getProperty("java.io.tmpdir"))) {
            @Override
            public String getPlanHash() {
                return "wrong_hash_" + plan("ls -l").getPlanHash();
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
        ShellConfig shellConfig = ShellConfig.createDefault();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .policy(shellConfig.getPolicy())
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        try {
            backend.execute(plan("ls -l"), 30);
            fail("Should have thrown SecurityException for denied policy");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("DENY"));
        }
    }

    @Test
    public void execute_planWithDenyPolicy_rejectsAll() {
        ShellConfig shellConfig = ShellConfig.createDefault();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .policy(shellConfig.getPolicy())
                .build();

        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);

        try {
            backend.execute(plan("ls -l"), 30);
            fail("Should have thrown SecurityException for DENY policy");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("DENY"));
        }
    }

    // ==================== Command building tests ====================

    @Test
    public void buildRemoteCommand_wrapsRawCommandInShC() {
        SshShellBackend backend = createBackend();
        ExecPlan plan = plan("/usr/bin/ls -l /home");
        assertEquals("sh -c \"/usr/bin/ls -l /home\"", backend.buildRemoteCommand(plan));
    }

    @Test
    public void buildRemoteCommand_singleQuotesPassThrough() {
        SshShellBackend backend = createBackend();
        ExecPlan plan = plan("echo hello 'world'");

        String command = backend.buildRemoteCommand(plan);
        assertTrue(command.startsWith("sh -c \""));
        assertTrue(command.contains("hello 'world'"));
    }

    @Test
    public void buildRemoteCommand_escapesDollarAndBacktick() {
        SshShellBackend backend = createBackend();

        // The review's exact case: the outer remote login shell must not expand $(id)
        // before the inner sh -c sees it, so the $ is escaped.
        ExecPlan plan = plan("echo $(id)");
        assertEquals("sh -c \"echo \\$(id)\"", backend.buildRemoteCommand(plan));

        // Backticks are escaped the same way so the inner sh -c performs the expansion.
        ExecPlan tick = plan("`id`");
        assertEquals("sh -c \"\\`id\\`\"", backend.buildRemoteCommand(tick));
    }

    // ==================== Execution flow tests (with mocks) ====================

    @Test
    public void execute_successReturnsResult() throws Exception {
        SshShellBackend backend = createBackend();
        ShellResult result = backend.execute(plan("ls -l"), 30);

        assertEquals(0, result.getExitCode());
        assertFalse(result.isTimedOut());
        assertTrue(result.isSuccess());
    }

    @Test
    public void execute_timeoutReturnsTimedOutResult() throws Exception {
        when(mockChannel.isClosed()).thenReturn(false);

        SshShellBackend backend = createBackend();
        ShellResult result = backend.execute(plan("sleep 3600"), 1);

        assertTrue(result.isTimedOut());
        assertTrue(result.getStderr().contains("timed out"));
        assertEquals(-1, result.getExitCode());
    }

    @Test
    public void execute_jschException_setsConnectedFalse() throws Exception {
        doThrow(new JSchException("connection refused"))
                .when(mockSession).openChannel(eq("exec"));

        SshShellBackend backend = createBackend();
        ShellResult result = backend.execute(plan("ls -l"), 30);

        assertFalse(result.isSuccess());
        assertTrue(result.getStderr().contains("SSH execution failed"));
        assertEquals(-1, result.getExitCode());
        assertFalse(backend.isConnected());
    }

    @Test
    public void ensureConnected_usesInjectedFactory() throws Exception {
        SshShellBackend backend = createBackend();
        backend.execute(plan("ls -l"), 30);
        verify(mockSession).connect(10_000);
    }

    @Test
    public void ensureConnected_passesAuthTypeConfig() throws Exception {
        SshShellBackend backend = createBackend();
        backend.execute(plan("ls -l"), 30);
        verify(mockSession).setPassword("testpass");
    }

    @Test
    public void ensureConnected_setsHostKeyVerification() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(true)
                .policy(ExecPolicy.full())
                .build();
        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);
        backend.execute(plan("ls -l"), 30);
        verify(mockSession).setConfig("StrictHostKeyChecking", "yes");
    }

    @Test
    public void ensureConnected_disablesHostKeyVerification() throws Exception {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .policy(ExecPolicy.full())
                .build();
        SshShellBackend backend = new SshShellBackend(config, mockJschFactory);
        backend.execute(plan("ls -l"), 30);
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
        backend.execute(plan("ls -l"), 30);
        assertTrue(backend.isConnected());
    }

    @Test
    public void close_disconnectsSession() throws Exception {
        SshShellBackend backend = createBackend();
        backend.execute(plan("ls -l"), 30);
        assertTrue(backend.isConnected());
        backend.close();
        assertFalse(backend.isConnected());
        verify(mockSession).disconnect();
    }

    @Test
    public void close_multipleTimes_safe() {
        SshShellBackend backend = createBackend();
        backend.close();
        backend.close();
        backend.close();
        assertFalse(backend.isConnected());
    }

    @Test
    public void testConnection_success() throws Exception {
        SshShellBackend backend = createBackend();
        assertTrue(backend.testConnection());
        verify(mockSession, atLeastOnce()).openChannel(eq("exec"));
    }

    @Test
    public void testConnection_failureSetsConnectedFalse() throws Exception {
        doThrow(new JSchException("refused"))
                .when(mockSession).openChannel(eq("exec"));
        SshShellBackend backend = createBackend();
        assertFalse(backend.testConnection());
        assertFalse(backend.isConnected());
    }

    // ==================== Thread safety tests ====================

    @Test
    public void close_isThreadSafe() throws Exception {
        SshShellBackend backend = createBackend();
        backend.execute(plan("ls -l"), 30);

        Thread t1 = new Thread(backend::close);
        Thread t2 = new Thread(backend::close);
        Thread t3 = new Thread(backend::close);
        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();
        assertFalse(backend.isConnected());
    }
}
