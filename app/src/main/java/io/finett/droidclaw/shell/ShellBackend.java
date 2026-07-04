package io.finett.droidclaw.shell;

/**
 * Abstraction over shell command execution backends.
 *
 * <p>Implementations may execute commands locally (via {@code ProcessBuilder}) or
 * remotely (e.g. via SSH). The contract is identical: accept an approved {@link ExecPlan}
 * and return a {@link ShellResult}.
 *
 * <p>Implementations are expected to be thread-safe (or used from a single thread) and
 * must close any connections/resources they hold when {@link #close()} is called.
 */
public interface ShellBackend {

    /**
     * Execute a pre-validated, approved {@link ExecPlan}.
     *
     * @param plan the normalised execution plan
     * @param timeoutSeconds maximum execution time before forcible termination
     * @return result with stdout, stderr, exit code, and timing info
     * @throws SecurityException if execution is denied or the plan is invalid
     */
    ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException;

    /**
     * Execute with the backend's default timeout.
     */
    default ShellResult execute(ExecPlan plan) throws SecurityException {
        return execute(plan, 30);
    }

    /**
     * Close any resources held by this backend (e.g. SSH sessions).
     * Safe to call multiple times.
     */
    void close();
}
