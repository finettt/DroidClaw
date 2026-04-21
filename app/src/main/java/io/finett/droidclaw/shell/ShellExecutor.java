package io.finett.droidclaw.shell;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.filesystem.PathValidator;

public class ShellExecutor {
    private final ShellConfig config;
    private final PathValidator pathValidator;

    public ShellExecutor(ShellConfig config) {
        this.config = config;
        this.pathValidator = null;
    }

    public ShellExecutor(ShellConfig config, PathValidator pathValidator) {
        this.config = config;
        this.pathValidator = pathValidator;
    }

    public ShellResult execute(String command) throws SecurityException {
        return execute(command, null);
    }

    public ShellResult execute(String command, File workingDirectory) throws SecurityException {
        return execute(command, workingDirectory, config.getTimeoutSeconds());
    }

    public ShellResult executeWithRelativeDir(String command, String relativeWorkingDir)
            throws SecurityException, IllegalStateException {
        if (pathValidator == null) {
            throw new IllegalStateException(
                "PathValidator not configured. Use execute() with absolute File path instead."
            );
        }
        
        File workingDir;
        try {
            workingDir = pathValidator.validateAndResolve(relativeWorkingDir);
        } catch (IOException e) {
            throw new SecurityException("Invalid working directory: " + relativeWorkingDir +
                " - " + e.getMessage());
        }
        
        return execute(command, workingDir, config.getTimeoutSeconds());
    }

    public ShellResult execute(String command, File workingDirectory, int timeoutSeconds)
            throws SecurityException {
        if (!config.isEnabled()) {
            throw new SecurityException("Shell execution is disabled");
        }

        if (!config.isCommandAllowed(command)) {
            throw new SecurityException("Command is not allowed: " + command);
        }

        // Validate working directory is within the workspace sandbox
        if (workingDirectory != null && pathValidator != null) {
            try {
                String canonicalWorkspace = pathValidator.getWorkspaceRoot().getCanonicalPath();
                String canonicalDir = workingDirectory.getCanonicalPath();
                
                if (!canonicalDir.startsWith(canonicalWorkspace)) {
                    throw new SecurityException(
                        "Working directory is outside workspace sandbox: " + workingDirectory
                    );
                }
            } catch (IOException e) {
                throw new SecurityException(
                    "Cannot validate working directory: " + e.getMessage()
                );
            }
        }

        long startTime = System.currentTimeMillis();
        Process process = null;
        boolean timedOut = false;
        int exitCode = -1;
        String stdout = "";
        String stderr = "";

        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
                builder.directory(workingDirectory);
            }

            builder.redirectErrorStream(false);
            process = builder.start();

            // Read stdout and stderr in separate threads to avoid deadlock
            StreamGobbler stdoutGobbler = new StreamGobbler(
                process.getInputStream(), 
                config.getMaxOutputSize()
            );
            StreamGobbler stderrGobbler = new StreamGobbler(
                process.getErrorStream(), 
                config.getMaxOutputSize()
            );

            Thread stdoutThread = new Thread(stdoutGobbler);
            Thread stderrThread = new Thread(stderrGobbler);
            
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
            stderr = "Failed to execute command: " + e.getMessage();
            exitCode = -1;
        } catch (InterruptedException e) {
            stderr = "Command execution interrupted: " + e.getMessage();
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

    private boolean waitForProcess(Process process, int timeoutSeconds) throws InterruptedException {
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
                    // interrupted by timeout
                }
            });
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
        if (process == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            process.destroy();
        }
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final int maxSize;
        private final StringBuilder output;

        public StreamGobbler(InputStream inputStream, int maxSize) {
            this.inputStream = inputStream;
            this.maxSize = maxSize;
            this.output = new StringBuilder();
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > maxSize) {
                        output.append("\n[Output truncated - size limit reached]");
                        break;
                    }
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(line);
                }
            } catch (IOException e) {
                // stream was closed when the process terminated
            }
        }

        public String getOutput() {
            return output.toString();
        }
    }
}