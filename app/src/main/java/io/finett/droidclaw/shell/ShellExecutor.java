package io.finett.droidclaw.shell;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands via normalised {@link ExecPlan} objects.
 *
 * <p>Security model:
 * <ul>
 *   <li>The primary API is {@link #execute(ExecPlan, int)} which accepts only a pre-validated,
 *       approved plan — never a raw string.</li>
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
public class ShellExecutor {

    private final ShellConfig config;

    public ShellExecutor(ShellConfig config) {
        this.config = config;
    }

    // ==================== Primary API ====================

    /**
     * Execute a pre-validated, approved {@link ExecPlan} with the config's default timeout.
     */
    public ShellResult execute(ExecPlan plan) throws SecurityException {
        return execute(plan, config.getTimeoutSeconds());
    }

    /**
     * Execute a pre-validated, approved {@link ExecPlan}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Re-verify the plan hash (substitution-attack prevention).</li>
     *   <li>Validate the plan against the policy/allowlist.</li>
     *   <li>Build the process command (DIRECT: argv[]; SHELL: sh -c ...).</li>
     *   <li>Start the process, capture stdout/stderr, enforce timeout.</li>
     * </ol>
     *
     * @param plan           the normalised, hash-locked execution plan
     * @param timeoutSeconds maximum execution time before forcible termination
     * @throws SecurityException if the plan hash has changed, the policy denies the command,
     *                           or the working directory is outside the workspace
     */
    public ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException {
        if (plan == null) {
            throw new SecurityException("ExecPlan must not be null");
        }

        // 1. Re-verify plan hash (TOCTOU / substitution-attack prevention)
        String recomputedHash = ExecPlan.computeHash(
                plan.getCanonicalExePath(),
                plan.getArgv(),
                plan.getCwd(),
                plan.getMode());
        if (!recomputedHash.equals(plan.getPlanHash())) {
            throw new SecurityException(
                "ExecPlan hash mismatch — plan may have been tampered with. "
                + "Expected: " + plan.getPlanHash().substring(0, 8) + "..."
                + "  Got: " + recomputedHash.substring(0, 8) + "...");
        }

        // 2. Policy/allowlist validation
        String denial = config.validatePlan(plan);
        if (denial != null) {
            throw new SecurityException(denial);
        }

        // 3. Build process command
        List<String> command = buildCommand(plan);

        // 4. Execute
        return runProcess(command, plan.getCwd(), timeoutSeconds);
    }

    // ==================== Command building ====================

    private List<String> buildCommand(ExecPlan plan) {
        List<String> command = new ArrayList<>();

        if (plan.getMode() == ExecPlan.ExecMode.DIRECT) {
            // No shell — exe + argv directly
            command.add(plan.getCanonicalExePath());
            command.addAll(plan.getArgv());
        } else {
            // SHELL mode — reconstruct command string for sh -c
            // (only reached when policy explicitly permits shell mode)
            StringBuilder sb = new StringBuilder(plan.getCanonicalExePath());
            for (String arg : plan.getArgv()) {
                sb.append(' ').append(arg);
            }
            command.add("sh");
            command.add("-c");
            command.add(sb.toString());
        }

        return command;
    }

    // ==================== Process runner ====================

    private ShellResult runProcess(List<String> command, java.io.File cwd, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        Process process = null;
        boolean timedOut = false;
        int exitCode = -1;
        String stdout = "";
        String stderr = "";

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd);
            builder.redirectErrorStream(false);
            process = builder.start();

            // Read stdout and stderr concurrently to avoid deadlock
            StreamGobbler stdoutGobbler = new StreamGobbler(
                    process.getInputStream(), config.getMaxOutputSize());
            StreamGobbler stderrGobbler = new StreamGobbler(
                    process.getErrorStream(), config.getMaxOutputSize());

            Thread stdoutThread = new Thread(stdoutGobbler, "shell-stdout");
            Thread stderrThread = new Thread(stderrGobbler, "shell-stderr");

            stdoutThread.start();
            stderrThread.start();

            boolean finished = waitForProcess(process, timeoutSeconds);

            if (!finished) {
                timedOut = true;
                destroyProcessForcibly(process);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            stdout = stdoutGobbler.getOutput();
            stderr = stderrGobbler.getOutput();

        } catch (IOException e) {
            stderr = "Failed to start process: " + e.getMessage();
            exitCode = -1;
        } catch (InterruptedException e) {
            stderr = "Process execution interrupted: " + e.getMessage();
            exitCode = -1;
            timedOut = true;
            destroyProcessForcibly(process);
            Thread.currentThread().interrupt();
        } finally {
            destroyProcessForcibly(process);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        return new ShellResult(stdout, stderr, exitCode, timedOut, executionTime);
    }

    // ==================== Process lifecycle helpers ====================

    private boolean waitForProcess(Process process, int timeoutSeconds)
            throws InterruptedException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            // Fallback for API < 26: poll from a worker thread
            final boolean[] finished = {false};
            Thread processThread = new Thread(() -> {
                try {
                    process.waitFor();
                    finished[0] = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "shell-wait");
            processThread.start();
            processThread.join(timeoutSeconds * 1000L);
            if (finished[0]) {
                return true;
            } else {
                processThread.interrupt();
                return false;
            }
        }
    }

    private void destroyProcessForcibly(Process process) {
        if (process == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            process.destroy();
        }
    }

    // ==================== Stream gobbler ====================

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final int maxSize;
        private final StringBuilder output = new StringBuilder();

        StreamGobbler(InputStream inputStream, int maxSize) {
            this.inputStream = inputStream;
            this.maxSize = maxSize;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > maxSize) {
                        output.append("\n[Output truncated — size limit reached]");
                        break;
                    }
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            } catch (IOException e) {
                // Stream closed when process terminated — expected
            }
        }

        String getOutput() {
            return output.toString();
        }
    }
}
