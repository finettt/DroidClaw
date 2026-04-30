package io.finett.droidclaw.shell;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, normalised representation of what will actually be executed.
 *
 * <p>The approval UI shows <em>this</em> object (via {@link #toApprovalDescription()}),
 * not the raw LLM-provided string. The executor verifies {@link #getPlanHash()} before
 * running to close the substitution-attack vector: if any field changes between the
 * moment the user approves and the moment the process starts, the hash will not match
 * and execution is aborted.
 *
 * <p>Construction is done by {@link ExecPlanner}, which resolves the canonical exe path,
 * tokenises argv, and validates the working directory.
 */
public class ExecPlan {

    /** Whether to run directly via argv array (no shell) or via {@code sh -c}. */
    public enum ExecMode {
        /**
         * Safe default: execute via {@code ProcessBuilder(argv[])} with no shell.
         * Shell metacharacters (;, &, |, >, <, $, `, (, ), \n) in any token are
         * rejected at plan-build time.
         */
        DIRECT,
        /**
         * Shell mode: execute via {@code sh -c "..."}. Requires explicit opt-in
         * and is only permitted when {@link ExecPolicy.SecurityLevel} is ALLOWLIST
         * or FULL with {@link ExecPolicy.AskMode#ALWAYS}.
         */
        SHELL
    }

    private final String canonicalExePath;
    private final List<String> argv;
    private final File cwd;
    private final ExecMode mode;
    private final String planHash;

    /**
     * Construct an ExecPlan. The {@code planHash} is computed immediately from the
     * four fields and is stable for the lifetime of this object.
     *
     * @param canonicalExePath canonical absolute path to the executable
     * @param argv             argument list (not including the executable)
     * @param cwd              working directory (must be an absolute, canonical path)
     * @param mode             execution mode
     */
    public ExecPlan(String canonicalExePath, List<String> argv, File cwd, ExecMode mode) {
        this.canonicalExePath = canonicalExePath;
        this.argv = Collections.unmodifiableList(argv);
        this.cwd = cwd;
        this.mode = mode;
        this.planHash = computeHash(canonicalExePath, argv, cwd, mode);
    }

    // ==================== Hash ====================

    /**
     * Compute a stable SHA-256 hash of the plan's canonical representation.
     * NUL bytes (\0) are used as field separators to prevent argument-smuggling.
     */
    static String computeHash(String exe, List<String> argv, File cwd, ExecMode mode) {
        try {
            // Build: MODE\0EXE\0ARG0\0ARG1\0...\0CWD
            StringBuilder repr = new StringBuilder();
            repr.append(mode.name()).append('\0');
            repr.append(exe).append('\0');
            for (String arg : argv) {
                repr.append(arg).append('\0');
            }
            repr.append(cwd.getAbsolutePath());

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(repr.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec; this should never happen.
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ==================== Accessors ====================

    /** Canonical absolute path to the executable, e.g. {@code /system/bin/ls}. */
    public String getCanonicalExePath() {
        return canonicalExePath;
    }

    /** Argument list (not including the executable itself). Unmodifiable. */
    public List<String> getArgv() {
        return argv;
    }

    /** Working directory for the process. */
    public File getCwd() {
        return cwd;
    }

    /** Execution mode (DIRECT or SHELL). */
    public ExecMode getMode() {
        return mode;
    }

    /**
     * SHA-256 hex hash of the canonical plan representation.
     * The executor re-computes this before starting the process and aborts if it differs,
     * preventing TOCTOU / substitution attacks.
     */
    public String getPlanHash() {
        return planHash;
    }

    // ==================== Display ====================

    /**
     * Human-readable summary shown in the approval dialog.
     * Deliberately shows the normalised plan, not the raw LLM-provided string,
     * so the user sees exactly what will be executed.
     */
    public String toApprovalDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Mode: ").append(mode.name()).append('\n');
        sb.append("Executable: ").append(canonicalExePath).append('\n');
        if (!argv.isEmpty()) {
            sb.append("Arguments:");
            for (String arg : argv) {
                sb.append("\n  ").append(arg);
            }
            sb.append('\n');
        }
        sb.append("Working dir: ").append(cwd.getAbsolutePath()).append('\n');
        sb.append("Plan ID: ").append(planHash, 0, 16).append("…");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ExecPlan{mode=" + mode
                + ", exe='" + canonicalExePath + '\''
                + ", argv=" + argv
                + ", cwd=" + cwd
                + ", hash='" + planHash.substring(0, 8) + "...'}";
    }
}