package io.finett.droidclaw.shell;

/**
 * Represents the result of a shell command execution.
 */
public class ShellResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final boolean timedOut;
    private final long executionTimeMs;

    public ShellResult(String stdout, String stderr, int exitCode, boolean timedOut, long executionTimeMs) {
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * @return Standard output from the command
     */
    public String getStdout() {
        return stdout;
    }

    /**
     * @return Standard error from the command
     */
    public String getStderr() {
        return stderr;
    }

    /**
     * @return Exit code of the command (0 typically means success)
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * @return Whether the command timed out
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * @return Execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * @return Whether the command was successful (exit code 0 and not timed out)
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * @return Combined stdout and stderr output
     */
    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (!stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (!stderr.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(stderr);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ShellResult{" +
                "exitCode=" + exitCode +
                ", timedOut=" + timedOut +
                ", executionTimeMs=" + executionTimeMs +
                ", stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                '}';
    }
}