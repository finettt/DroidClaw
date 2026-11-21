package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellExecutor} — the thin wrapper that supplies a default timeout
 * and delegates to the configured {@link ShellBackend}. Real command execution lives in the
 * backend (see {@link DistroShellBackendTest} and the instrumented suite).
 */
public class ShellExecutorTest {

    /** Records the plan and timeout it was invoked with, without running anything. */
    private static final class RecordingBackend implements ShellBackend {
        ExecPlan seenPlan;
        int seenTimeout;
        boolean closed;

        @Override public ShellResult execute(ExecPlan plan, int timeoutSeconds) {
            seenPlan = plan;
            seenTimeout = timeoutSeconds;
            return new ShellResult("ok", "", 0, false, 1);
        }

        @Override public void close() { closed = true; }
    }

    private static ExecPlan samplePlan() {
        return new ExecPlan("echo hi",
                new File(System.getProperty("java.io.tmpdir")));
    }

    @Test
    public void execute_usesConfiguredDefaultTimeout() {
        RecordingBackend backend = new RecordingBackend();
        ShellExecutor executor = new ShellExecutor(backend, 45);

        executor.execute(samplePlan());

        assertEquals(45, backend.seenTimeout);
        assertNotNull(backend.seenPlan);
    }

    @Test
    public void execute_explicitTimeoutOverridesDefault() {
        RecordingBackend backend = new RecordingBackend();
        ShellExecutor executor = new ShellExecutor(backend, 45);

        executor.execute(samplePlan(), 10);

        assertEquals(10, backend.seenTimeout);
    }

    @Test
    public void constructor_fallsBackToDefaultTimeoutWhenNonPositive() {
        RecordingBackend backend = new RecordingBackend();
        ShellExecutor executor = new ShellExecutor(backend, 0);

        executor.execute(samplePlan());

        assertEquals(30, backend.seenTimeout);
    }

    @Test
    public void close_delegatesToBackend() {
        RecordingBackend backend = new RecordingBackend();
        ShellExecutor executor = new ShellExecutor(backend, 30);

        executor.close();

        assertTrue(backend.closed);
    }
}
