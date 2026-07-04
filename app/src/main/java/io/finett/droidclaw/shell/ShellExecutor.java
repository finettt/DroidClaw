package io.finett.droidclaw.shell;

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
 *   <li>In {@link ExecPlan.ExecMode#DIRECT} mode, the process is started via
 *       {@code ProcessBuilder(argv[])} with no shell interpreter. Shell metacharacters
 *       (already rejected by {@link ExecPlanner}) cannot be interpreted.</li>
 *   <li>In {@link ExecPlan.ExecMode#SHELL} mode, {@code sh -c} is used. This mode
 *       requires explicit opt-in and the policy must mandate {@code ask=ALWAYS}.</li>
 *   <li>The plan's SHA-256 hash is re-verified immediately before the process starts.
 *       If any field was mutated between approval and execution, the hash will not match
 *       and execution is aborted (substitution-attack prevention).</li>
 * </ul>
 *
 * <p>The old {@code execute(String command, ...)} API that accepted raw shell strings
 * is removed. Callers must use {@link ExecPlanner} to build an {@link ExecPlan} first.
 */
public class ShellExecutor implements AutoCloseable {

    private static final String TAG = "ShellExecutor";

    private final ShellBackend backend;
    private int defaultTimeoutSeconds;

    /**
     * Create a ShellExecutor with a specific backend.
     *
     * @param backend            the execution backend (LocalShellBackend or SshShellBackend)
     * @param defaultTimeoutSec  default timeout in seconds when no explicit timeout is given
     */
    public ShellExecutor(ShellBackend backend, int defaultTimeoutSec) {
        this.backend = backend;
        this.defaultTimeoutSeconds = defaultTimeoutSec > 0 ? defaultTimeoutSec : 30;
    }

    /**
     * Create a ShellExecutor with a local backend (default for backward compatibility).
     *
     * @param config the shell configuration
     * @deprecated Use {@link ShellBackendFactory#create(AgentConfig, ShellConfig)} instead.
     */
    @Deprecated
    public ShellExecutor(ShellConfig config) {
        this(new LocalShellBackend(config), config.getTimeoutSeconds());
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
        return backend.execute(plan, timeoutSeconds);
    }

    // ==================== Lifecycle ====================

    @Override
    public void close() {
        backend.close();
    }
}
