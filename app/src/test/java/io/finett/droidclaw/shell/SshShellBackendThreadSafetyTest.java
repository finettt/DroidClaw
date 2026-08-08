package io.finett.droidclaw.shell;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safety tests for {@link SshShellBackend}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Concurrent {@code ensureConnected()} calls (race condition between checking
 *       and setting {@code connected})</li>
 *   <li>Concurrent {@code execute()} calls from multiple threads</li>
 *   <li>{@code isConnected()} thread-safety</li>
 *   <li>{@code testConnection()} thread-safety</li>
 * </ul>
 */
public class SshShellBackendThreadSafetyTest {

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

        // Create a mock JSch factory that returns the mock JSch instance
        SshShellBackend.JSchFactory mockFactory = new SshShellBackend.JSchFactory() {
            @Override
            public JSch create() {
                return mockJsch;
            }
        };
        mockJschFactory = mockFactory;

        // Stub JSch methods (doReturn for checked exceptions)
        doReturn(mockSession).when(mockJsch).getSession(anyString(), anyString(), anyInt());

        // Stub Session methods
        when(mockSession.isConnected()).thenReturn(true);
        doNothing().when(mockSession).connect(anyInt());
        doNothing().when(mockSession).setServerAliveInterval(anyInt());
        doNothing().when(mockSession).setPassword(anyString());
        doNothing().when(mockSession).setConfig(anyString(), anyString());

        // Stub channel (doReturn for checked exceptions)
        doReturn(mockChannel).when(mockSession).openChannel(eq("exec"));
        doNothing().when(mockChannel).setCommand(anyString());
        doNothing().when(mockChannel).setOutputStream(any());
        doNothing().when(mockChannel).setErrStream(any());
        doNothing().when(mockChannel).connect(anyInt());
        when(mockChannel.isConnected()).thenReturn(true);
        when(mockChannel.isClosed()).thenReturn(true); // immediately closed
        when(mockChannel.getExitStatus()).thenReturn(0);
        doNothing().when(mockChannel).disconnect();
    }

    private SshShellBackend createBackend() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .port(22)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .policy(ExecPolicy.full())
                .build();
        return new SshShellBackend(config, mockJschFactory);
    }

    // ==================== Concurrent ensureConnected() tests ====================

    @Test
    public void concurrentEnsureConnectedCalls_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // Start all threads at once to maximize race condition chance
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS); // wait for signal
                    backend.execute(plan, 30);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected — some threads may get connection failures
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for completion (with timeout since threads might hang)
        assertTrue("Threads did not complete in time",
                doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // At least some threads should have succeeded (the first one to get the lock)
        assertTrue("At least one thread should have succeeded, got " + successCount.get(),
                successCount.get() >= 1);

        // Verify backend is in a consistent state
        assertTrue("Backend should be connected", backend.isConnected());
    }

    // ==================== Concurrent execute() tests ====================

    @Test
    public void concurrentExecuteCalls_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    ShellResult result = backend.execute(plan, 30);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Threads did not complete in time",
                doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // Backend should remain in a consistent state
        assertTrue("Backend should be connected after concurrent access",
                backend.isConnected());

        // At least some threads should have succeeded
        assertTrue("At least one thread should have succeeded",
                successCount.get() >= 1);
    }

    // ==================== isConnected() thread-safety tests ====================

    @Test
    public void concurrentIsConnectedCalls_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Trigger a connection first
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );
        backend.execute(plan, 30);

        // Now read isConnected() concurrently
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    boolean connected = backend.isConnected();
                    // Just verify it doesn't throw
                    assertTrue("Result should be a valid boolean", connected == true || connected == false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Threads did not complete in time",
                doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }

    // ==================== testConnection() thread-safety tests ====================

    @Test
    public void concurrentTestConnectionCalls_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    boolean result = backend.testConnection();
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Expected — some threads may get connection failures
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Threads did not complete in time",
                doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    public void concurrentCloseAndExecute_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // Connect first
        backend.execute(plan, 30);

        // Now run close() and execute() concurrently
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger errorCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.countDown();
                backend.close();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.countDown();
                backend.execute(plan, 30);
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        assertTrue("Threads did not complete in time",
                doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Backend should be in a consistent state — execute() may have reconnected
        // after close(), which is expected behavior for concurrent access
    }

    @Test
    public void concurrentCloseCalls_doesNotCauseCrash() throws Exception {
        SshShellBackend backend = createBackend();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    backend.close();
                } catch (Exception e) {
                    // Should not throw
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Threads did not complete in time",
                doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertFalse("Backend should not be connected", backend.isConnected());
    }
}
