package io.finett.droidclaw.python;

/**
 * Configuration for Python code execution.
 *
 * <p>The {@link #isSafeMode()} flag controls whether a security preamble is injected
 * before user code:
 * <ul>
 *   <li><b>true (default)</b> — a fresh globals dict is used per execution (no cross-session
 *       contamination via {@code __main__}), dangerous built-in modules ({@code os},
 *       {@code subprocess}, {@code socket}, {@code ctypes}, etc.) are blocked via an
 *       {@code __import__} hook, and only a restricted set of built-ins is exposed.</li>
 *   <li><b>false</b> — full Python access (trusted-operator mode). Should only be set
 *       when {@code sandboxMode="full"} and the user has been explicitly warned.</li>
 * </ul>
 */
public class PythonConfig {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1 MB

    private final boolean pipEnabled;
    private final boolean safeMode;
    private final int timeoutSeconds;
    private final int maxOutputSize;
    private final String pythonPath;

    private PythonConfig(Builder builder) {
        this.pipEnabled = builder.pipEnabled;
        this.safeMode = builder.safeMode;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputSize = builder.maxOutputSize;
        this.pythonPath = builder.pythonPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Create a safe-mode config (recommended default). */
    public static PythonConfig createDefault() {
        return new Builder().build();
    }

    public boolean isPipEnabled() {
        return pipEnabled;
    }

    /**
     * When true, a security preamble is injected that:
     * <ul>
     *   <li>Uses a fresh {@code dict()} as globals per execution instead of
     *       {@code __main__.__dict__} — prevents cross-session state contamination.</li>
     *   <li>Replaces {@code __builtins__} with a restricted set.</li>
     *   <li>Installs an {@code __import__} hook that blocks dangerous modules:
     *       {@code os}, {@code subprocess}, {@code socket}, {@code ctypes},
     *       {@code importlib}, {@code shutil}, {@code tempfile}, {@code sys},
     *       {@code builtins}, {@code gc}, {@code signal}.</li>
     * </ul>
     */
    public boolean isSafeMode() {
        return safeMode;
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
        private boolean safeMode = true;  // secure by default
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private String pythonPath = null;

        public Builder enablePip(boolean enabled) {
            this.pipEnabled = enabled;
            return this;
        }

        /** Enable or disable safe-mode execution. Default is {@code true}. */
        public Builder safeMode(boolean safe) {
            this.safeMode = safe;
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