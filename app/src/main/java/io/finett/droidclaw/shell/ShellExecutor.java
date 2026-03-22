package io.finett.droidclaw.shell;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.filesystem.PathValidator;

/**
 * Executes shell commands on Android using ProcessBuilder.
 * Provides timeout mechanism and security checks.
 * Integrates with VirtualFileSystem for sandboxed working directory validation.
 */
public class ShellExecutor {
    private final ShellConfig config;
    private final PathValidator pathValidator;

    /**
     * Creates a ShellExecutor with the given configuration.
     * Without PathValidator, working directories must be absolute paths.
     *
     * @param config Shell execution configuration
     */
    public ShellExecutor(ShellConfig config) {
        this.config = config;
        this.pathValidator = null;
    }

    /**
     * Creates a ShellExecutor with configuration and PathValidator.
     * When PathValidator is provided, working directories are validated against
     * the virtual filesystem sandbox.
     *
     * @param config Shell execution configuration
     * @param pathValidator PathValidator for working directory validation (may be null)
     */
    public ShellExecutor(ShellConfig config, PathValidator pathValidator) {
        this.config = config;
        this.pathValidator = pathValidator;
    }

    /**
     * Execute a shell command with the default working directory.
     *
     * @param command The command to execute
     * @return ShellResult containing stdout, stderr, exit code, and execution metadata
     * @throws SecurityException if the command is not allowed by the configuration
     */
    public ShellResult execute(String command) throws SecurityException {
        return execute(command, null);
    }

    /**
     * Execute a shell command with a specific working directory.
     * If PathValidator is configured, the working directory path will be validated
     * against the virtual filesystem sandbox.
     *
     * @param command The command to execute
     * @param workingDirectory The working directory for the command (null for default)
     * @return ShellResult containing stdout, stderr, exit code, and execution metadata
     * @throws SecurityException if the command is not allowed or path is outside sandbox
     */
    public ShellResult execute(String command, File workingDirectory) throws SecurityException {
        return execute(command, workingDirectory, config.getTimeoutSeconds());
    }

    /**
     * Execute a shell command with a working directory specified as a relative path.
     * The path is resolved and validated using the PathValidator.
     *
     * @param command The command to execute
     * @param relativeWorkingDir Relative path to working directory (validated against sandbox)
     * @return ShellResult containing stdout, stderr, exit code, and execution metadata
     * @throws SecurityException if the command is not allowed or path is outside sandbox
     * @throws IllegalStateException if no PathValidator is configured
     */
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

    /**
     * Execute a shell command with a specific timeout.
     *
     * @param command The command to execute
     * @param workingDirectory The working directory for the command (null for default)
     * @param timeoutSeconds Timeout in seconds
     * @return ShellResult containing stdout, stderr, exit code, and execution metadata
     * @throws SecurityException if the command is not allowed by the configuration
     */
    public ShellResult execute(String command, File workingDirectory, int timeoutSeconds)
            throws SecurityException {
        if (!config.isEnabled()) {
            throw new SecurityException("Shell execution is disabled");
        }

        if (!config.isCommandAllowed(command)) {
            throw new SecurityException("Command is not allowed: " + command);
        }

        // Validate working directory against virtual filesystem sandbox
        if (workingDirectory != null && pathValidator != null) {
            try {
                // Verify the working directory is within the workspace
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
            // Create ProcessBuilder with sh -c to execute the command
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            
            // Set working directory if provided
            if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
                builder.directory(workingDirectory);
            }

            // Redirect error stream to separate stream (not merged with stdout)
            builder.redirectErrorStream(false);

            // Start the process
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

            // Wait for process to complete with timeout (API level compatible)
            boolean finished = waitForProcess(process, timeoutSeconds);

            if (!finished) {
                // Process timed out
                timedOut = true;
                destroyProcessForcibly(process);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            // Wait for output threads to complete
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

    /**
     * Wait for a process to complete with timeout, compatible with all API levels.
     * Uses the modern API on API 26+ and a fallback implementation for older versions.
     *
     * @param process The process to wait for
     * @param timeoutSeconds Timeout in seconds
     * @return true if the process finished, false if it timed out
     */
    private boolean waitForProcess(Process process, int timeoutSeconds) throws InterruptedException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the modern API on API 26+
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            // Fallback for older API levels: use a worker thread to wait for the process
            final boolean[] finished = {false};
            Thread processThread = new Thread(() -> {
                try {
                    process.waitFor();
                    finished[0] = true;
                } catch (InterruptedException e) {
                    // Thread was interrupted, timeout occurred
                }
            });
            processThread.start();

            // Wait for either the process to finish or timeout
            processThread.join(timeoutSeconds * 1000L);

            if (finished[0]) {
                // Process finished within timeout
                return true;
            } else {
                // Timeout occurred - interrupt the process thread
                processThread.interrupt();
                return false;
            }
        }
    }

    /**
     * Destroy a process forcibly, compatible with all API levels.
     *
     * @param process The process to destroy (may be null)
     */
    private void destroyProcessForcibly(Process process) {
        if (process == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            // On older API levels, destroy() is the only option
            process.destroy();
        }
    }

    /**
     * Helper class to read process output streams without blocking.
     */
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
                // Ignore - stream was likely closed
            }
        }

        public String getOutput() {
            return output.toString();
        }
    }
}