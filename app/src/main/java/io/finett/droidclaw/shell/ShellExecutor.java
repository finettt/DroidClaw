package io.finett.droidclaw.shell;

import android.os.Looper;
import android.util.Log;

import io.finett.droidclaw.model.AgentConfig;

/**
 * Executes shell commands via normalised {@link ExecPlan} objects.
 *
 * <p>Security model:
 * <ul>
 *   <li>The primary API is {@link #execute(ExecPlan, int)} which accepts only a pre-validated,
 *       approved plan — never a raw string.</li>
 *   <li>Supports multiple backends: local (ProcessBuilder) and SSH (remote server).</li>
 *   <li>The normalised {@link ExecPlan} carries the raw command, which every backend runs
 *       through {@code sh -c} inside its environment (guest rootfs or remote host), so full
 *       shell syntax is honoured.</li>
 *   <li>The plan's SHA-256 hash is re-verified immediately before the process starts.
 *       If any field was mutated between approval and execution, the hash will not match
 *       and execution is aborted (substitution-attack prevention).</li>
 * </ul>
 *
 * <p>The old {@code execute(String command, ...)} API that accepted raw shell strings
 * is removed. Callers must use {@link ExecPlanner} to build an {@link ExecPlan} first.
 *
 * <p>Threading: {@link #execute} blocks for the full command duration (waitFor + stream
 * pumping + timeout). It must be called from a background thread, never the main thread.
 * The agent loop dispatches shell execution off main; direct callers must do the same.
 */
public class ShellExecutor implements AutoCloseable {

    private static final String TAG = "ShellExecutor";

    private final ShellBackend backend;
    private int defaultTimeoutSeconds;

    /**
     * Create a ShellExecutor with a specific backend.
     *
     * @param backend            the execution backend (DistroShellBackend or SshShellBackend)
     * @param defaultTimeoutSec  default timeout in seconds when no explicit timeout is given
     */
    public ShellExecutor(ShellBackend backend, int defaultTimeoutSec) {
        this.backend = backend;
        this.defaultTimeoutSeconds = defaultTimeoutSec > 0 ? defaultTimeoutSec : 30;
    }

    // ==================== Primary API ====================

    /**
     * Execute a pre-validated, approved {@link ExecPlan} with the configured default timeout.
     *
     * @return the execution result
     * @throws SecurityException if the plan hash has changed, the policy denies the command,
     *                           or the working directory is outside the workspace
     */
    public ShellResult execute(ExecPlan plan) throws SecurityException {
        warnIfOnMainThread();
        return backend.execute(plan, defaultTimeoutSeconds);
    }

    /**
     * Execute a pre-validated, approved {@link ExecPlan}.
     *
     * @param plan           the normalised, hash-locked execution plan
     * @param timeoutSeconds maximum execution time before forcible termination
     * @throws SecurityException if the plan hash has changed, the policy denies the command,
     *                           or the working directory is outside the workspace
     */
    public ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException {
        warnIfOnMainThread();
        return backend.execute(plan, timeoutSeconds);
    }

    /** Log a warning when process execution is about to block the main thread. */
    private static void warnIfOnMainThread() {
        try {
            Looper main = Looper.getMainLooper();
            if (main != null && Looper.myLooper() == main) {
                Log.w(TAG, "Shell execution on main thread — this blocks the UI and can ANR. "
                        + "Dispatch to a background thread.");
            }
        } catch (Exception ignored) {
            // No looper available (plain JVM tests): nothing to warn about.
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void close() {
        backend.close();
    }
}
