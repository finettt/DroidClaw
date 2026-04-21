package io.finett.droidclaw.shell;

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

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

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