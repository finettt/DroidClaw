package io.finett.droidclaw.shell;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One entry in the shell execution allowlist.
 *
 * <p>Matches a command by its canonical executable path and optionally constrains
 * the argument vector (argv) via:
 * <ul>
 *   <li>Allowed flag set — if non-empty, only listed flags are permitted.</li>
 *   <li>Denied flag set — flags that are always blocked regardless of allowedFlags.</li>
 *   <li>Inline-eval denial — blocks {@code -c}, {@code -e}, {@code --eval}, {@code -p}
 *       flags used by interpreters (python, node, sh, etc.).</li>
 *   <li>Max positional argument count — limits blast radius for file-operating commands.</li>
 * </ul>
 */
public class AllowlistEntry {

    /**
     * Canonical executable path to match, e.g. {@code "/system/bin/ls"}.
     * The special value {@code "*"} matches any executable (only valid in FULL policy context).
     */
    private final String canonicalExePath;

    /**
     * When true, flags commonly used for inline code evaluation
     * ({@code -c}, {@code -e}, {@code --eval}, {@code -p}, {@code --expression})
     * are always denied — even if they appear in {@code allowedFlags}.
     */
    private final boolean denyInlineEval;

    /**
     * If non-empty, only flags in this set are permitted.
     * Short and long forms must be listed separately (e.g. {@code "-l"} and {@code "--long"}).
     */
    private final Set<String> allowedFlags;

    /**
     * Flags that are always denied, checked before {@code allowedFlags}.
     */
    private final Set<String> deniedFlags;

    /**
     * Maximum number of non-flag (positional) arguments.
     * {@code -1} means unlimited.
     */
    private final int maxPositionalArgs;

    private AllowlistEntry(Builder b) {
        this.canonicalExePath = b.canonicalExePath;
        this.denyInlineEval = b.denyInlineEval;
        this.allowedFlags = Collections.unmodifiableSet(new HashSet<>(b.allowedFlags));
        this.deniedFlags = Collections.unmodifiableSet(new HashSet<>(b.deniedFlags));
        this.maxPositionalArgs = b.maxPositionalArgs;
    }

    /** Returns true if this entry matches the given canonical executable path. */
    public boolean matches(String canonicalPath) {
        return "*".equals(canonicalExePath) || canonicalExePath.equals(canonicalPath);
    }

    /**
     * Validate a parsed argv list against this entry's policy.
     *
     * @param argv the argument list (not including the executable itself)
     * @return {@code null} if the argv is allowed, or a human-readable denial reason
     */
    public String validateArgv(List<String> argv) {
        int positionalCount = 0;
        for (String arg : argv) {
            if (arg.startsWith("-")) {
                // Strip =value suffix for flag comparison (e.g. --format=long → --format)
                String flag = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;

                // Inline-eval protection (applies regardless of allowedFlags)
                if (denyInlineEval && isInlineEvalFlag(flag)) {
                    return "Inline-eval flag denied by policy: " + flag;
                }

                // Explicit deny list
                if (deniedFlags.contains(flag)) {
                    return "Flag denied by policy: " + flag;
                }

                // Allowlist check (only when allowedFlags is non-empty)
                if (!allowedFlags.isEmpty() && !allowedFlags.contains(flag)) {
                    return "Flag not in allowlist: " + flag;
                }
            } else {
                positionalCount++;
                if (maxPositionalArgs >= 0 && positionalCount > maxPositionalArgs) {
                    return "Too many positional arguments (max " + maxPositionalArgs + ")";
                }
            }
        }
        return null; // allowed
    }

    private static boolean isInlineEvalFlag(String flag) {
        switch (flag) {
            case "-c":
            case "-e":
            case "-p":
            case "--eval":
            case "--expression":
            case "--command":
                return true;
            default:
                return false;
        }
    }

    public String getCanonicalExePath() {
        return canonicalExePath;
    }

    public boolean isDenyInlineEval() {
        return denyInlineEval;
    }

    public Set<String> getAllowedFlags() {
        return allowedFlags;
    }

    public Set<String> getDeniedFlags() {
        return deniedFlags;
    }

    public int getMaxPositionalArgs() {
        return maxPositionalArgs;
    }

    // ==================== Builder ====================

    public static class Builder {
        private final String canonicalExePath;
        private boolean denyInlineEval = false;
        private Set<String> allowedFlags = new HashSet<>();
        private Set<String> deniedFlags = new HashSet<>();
        private int maxPositionalArgs = -1;

        public Builder(String canonicalExePath) {
            if (canonicalExePath == null || canonicalExePath.isEmpty()) {
                throw new IllegalArgumentException("canonicalExePath must not be null or empty");
            }
            this.canonicalExePath = canonicalExePath;
        }

        /** Block inline-eval flags (-c, -e, --eval, etc.) for this entry. */
        public Builder denyInlineEval() {
            this.denyInlineEval = true;
            return this;
        }

        /** Add a flag that is explicitly permitted (enables flag allowlist for this entry). */
        public Builder allowFlag(String flag) {
            this.allowedFlags.add(flag);
            return this;
        }

        /** Add multiple flags that are explicitly permitted. */
        public Builder allowFlags(String... flags) {
            for (String f : flags) {
                this.allowedFlags.add(f);
            }
            return this;
        }

        /** Add a flag that is always denied (checked before allowedFlags). */
        public Builder denyFlag(String flag) {
            this.deniedFlags.add(flag);
            return this;
        }

        /** Add multiple flags that are always denied. */
        public Builder denyFlags(String... flags) {
            for (String f : flags) {
                this.deniedFlags.add(f);
            }
            return this;
        }

        /** Set the maximum number of positional (non-flag) arguments. -1 = unlimited. */
        public Builder maxPositionalArgs(int max) {
            this.maxPositionalArgs = max;
            return this;
        }

        public AllowlistEntry build() {
            return new AllowlistEntry(this);
        }
    }
}