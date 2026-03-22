package io.finett.droidclaw.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands on Android using ProcessBuilder.
 * Provides timeout mechanism and security checks.
 */
public class ShellExecutor {
    private final ShellConfig config;

    public ShellExecutor(ShellConfig config) {
        this.config = config;
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
     *
     * @param command The command to execute
     * @param workingDirectory The working directory for the command (null for default)
     * @return ShellResult containing stdout, stderr, exit code, and execution metadata
     * @throws SecurityException if the command is not allowed by the configuration
     */
    public ShellResult execute(String command, File workingDirectory) throws SecurityException {
        return execute(command, workingDirectory, config.getTimeoutSeconds());
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

            // Wait for process to complete with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                // Process timed out
                timedOut = true;
                process.destroyForcibly();
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
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        return new ShellResult(stdout, stderr, exitCode, timedOut, executionTime);
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