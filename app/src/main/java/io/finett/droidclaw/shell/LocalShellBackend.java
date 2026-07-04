package io.finett.droidclaw.shell;

/**
 * Local execution backend using {@code ProcessBuilder}.
 *
 * <p>This is the current implementation, wrapped behind the {@link ShellBackend} interface
 * so it can be swapped with {@link SshShellBackend} at runtime.
 */
public class LocalShellBackend implements ShellBackend {

    private final ShellConfig config;

    public LocalShellBackend(ShellConfig config) {
        this.config = config;
    }

    @Override
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
        java.util.List<String> command = buildCommand(plan);

        // 4. Execute
        return runProcess(command, plan.getCwd(), timeoutSeconds);
    }

    private java.util.List<String> buildCommand(ExecPlan plan) {
        java.util.List<String> command = new java.util.ArrayList<>();

        if (plan.getMode() == ExecPlan.ExecMode.DIRECT) {
            command.add(plan.getCanonicalExePath());
            command.addAll(plan.getArgv());
        } else {
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

    private ShellResult runProcess(java.util.List<String> command, java.io.File cwd, int timeoutSeconds) {
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

        } catch (java.io.IOException e) {
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

    private boolean waitForProcess(Process process, int timeoutSeconds)
            throws InterruptedException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } else {
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            process.destroy();
        }
    }

    @Override
    public void close() {
        // No persistent resources
    }

    // ==================== Stream gobbler ====================

    private static class StreamGobbler implements Runnable {
        private final java.io.InputStream inputStream;
        private final int maxSize;
        private static final int CHUNK_SIZE = 8192;
        private final StringBuilder output = new StringBuilder();
        private boolean truncated;

        StreamGobbler(java.io.InputStream inputStream, int maxSize) {
            this.inputStream = inputStream;
            this.maxSize = maxSize;
        }

        @Override
        public void run() {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(inputStream), CHUNK_SIZE)) {
                char[] buf = new char[CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = reader.read(buf)) != -1) {
                    // Check size limit before appending
                    if (output.length() + bytesRead > maxSize) {
                        int remaining = maxSize - output.length();
                        if (remaining > 0) {
                            output.append(buf, 0, remaining);
                        }
                        output.append("\n[Output truncated — size limit reached]");
                        truncated = true;
                        break;
                    }
                    // Add newline separator between chunks (except the first)
                    if (output.length() > 0 && !truncated) {
                        output.append('\n');
                    }
                    output.append(buf, 0, bytesRead);
                }
            } catch (java.io.IOException e) {
                // Stream closed when process terminated — expected
            }
        }

        String getOutput() {
            return output.toString();
        }
    }
}
