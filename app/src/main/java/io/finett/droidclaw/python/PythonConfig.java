package io.finett.droidclaw.python;

public class PythonConfig {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024;

    private final boolean pipEnabled;
    private final int timeoutSeconds;
    private final int maxOutputSize;
    private final String pythonPath;

    private PythonConfig(Builder builder) {
        this.pipEnabled = builder.pipEnabled;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputSize = builder.maxOutputSize;
        this.pythonPath = builder.pythonPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PythonConfig createDefault() {
        return new Builder().build();
    }

    public boolean isPipEnabled() {
        return pipEnabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxOutputSize() {
        return maxOutputSize;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public static class Builder {
        private boolean pipEnabled = true;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private String pythonPath = null;

        public Builder enablePip(boolean enabled) {
            this.pipEnabled = enabled;
            return this;
        }

        public Builder timeout(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeoutSeconds = seconds;
            return this;
        }

        public Builder maxOutputSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("Max output size must be positive");
            }
            this.maxOutputSize = bytes;
            return this;
        }

        public Builder pythonPath(String path) {
            this.pythonPath = path;
            return this;
        }

        public PythonConfig build() {
            return new PythonConfig(this);
        }
    }
}