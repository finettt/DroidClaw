package io.finett.droidclaw.python;

/**
 * Configuration for Python execution.
 * Controls timeouts, output limits, and runtime settings.
 */
public class PythonConfig {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1MB

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

    /**
     * Creates a new Builder for constructing PythonConfig instances.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default PythonConfig with sensible settings.
     *
     * @return A PythonConfig instance with default values
     */
    public static PythonConfig createDefault() {
        return new Builder().build();
    }

    /**
     * @return Whether pip package installation is enabled
     */
    public boolean isPipEnabled() {
        return pipEnabled;
    }

    /**
     * @return Timeout in seconds for Python execution
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * @return Maximum output size in bytes
     */
    public int getMaxOutputSize() {
        return maxOutputSize;
    }

    /**
     * @return Custom Python path, or null for default
     */
    public String getPythonPath() {
        return pythonPath;
    }

    /**
     * Builder class for constructing PythonConfig instances.
     */
    public static class Builder {
        private boolean pipEnabled = true;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private String pythonPath = null;

        /**
         * Enable or disable pip package installation.
         *
         * @param enabled Whether pip should be enabled
         * @return This builder
         */
        public Builder enablePip(boolean enabled) {
            this.pipEnabled = enabled;
            return this;
        }

        /**
         * Set the execution timeout.
         *
         * @param seconds Timeout in seconds (must be positive)
         * @return This builder
         * @throws IllegalArgumentException if timeout is not positive
         */
        public Builder timeout(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeoutSeconds = seconds;
            return this;
        }

        /**
         * Set the maximum output size.
         *
         * @param bytes Maximum output size in bytes (must be positive)
         * @return This builder
         * @throws IllegalArgumentException if size is not positive
         */
        public Builder maxOutputSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("Max output size must be positive");
            }
            this.maxOutputSize = bytes;
            return this;
        }

        /**
         * Set a custom Python path.
         *
         * @param path Path to Python executable or directory
         * @return This builder
         */
        public Builder pythonPath(String path) {
            this.pythonPath = path;
            return this;
        }

        /**
         * Build the PythonConfig instance.
         *
         * @return A new PythonConfig with the configured settings
         */
        public PythonConfig build() {
            return new PythonConfig(this);
        }
    }
}