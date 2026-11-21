package io.finett.droidclaw.shell;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * An immutable, normalised representation of what will actually be executed.
 *
 * <p>The approval UI shows the raw command verbatim (via
 * {@link #toApprovalDescription()}). The executor verifies {@link #getPlanHash()} before
 * running to close the substitution-attack vector: if any field changes between the
 * moment the user approves and the moment the process starts, the hash will not match
 * and execution is aborted.
 *
 * <p>The single execution path is the guest/remote shell: the {@code rawCommand} is passed
 * verbatim to {@code sh -c}, so full shell syntax (pipes, {@code &&}, redirects,
 * {@code $()}) is honoured inside the Linux environment. Construction is done by
 * {@link ExecPlanner}, which pins the working directory with a canonical-path guard
 * (workspace boundary) and derives a hash of everything the user approved.
 */
public class ExecPlan {

    private final String rawCommand;
    private final File cwd;
    private final String planHash;

    /**
     * Construct an ExecPlan. The {@code planHash} is computed immediately from the two
     * fields and is stable for the lifetime of this object.
     *
     * @param rawCommand the raw command string, passed verbatim to {@code sh -c}
     * @param cwd        working directory (an absolute, canonical path)
     */
    public ExecPlan(String rawCommand, File cwd) {
        this.rawCommand = rawCommand;
        this.cwd = cwd;
        this.planHash = computeHash(rawCommand, cwd);
    }

    // ==================== Hash ====================

    /**
     * Compute a stable SHA-256 hash of the plan's canonical representation.
     * NUL bytes (\0) are used as field separators to prevent argument-smuggling.
     */
    static String computeHash(String rawCommand, File cwd) {
        try {
            // Build: RAWCMD\0CWD
            StringBuilder repr = new StringBuilder();
            repr.append(rawCommand == null ? "" : rawCommand).append('\0');
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

    /** The raw command string, passed verbatim to {@code sh -c}. */
    public String getRawCommand() {
        return rawCommand;
    }

    /** Working directory for the process. */
    public File getCwd() {
        return cwd;
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
     * Shows the raw command verbatim and the resolved working directory, so the user sees
     * exactly what will be executed inside the Linux environment.
     */
    public String toApprovalDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Command: ").append(rawCommand).append('\n');
        sb.append("Working dir: ").append(cwd.getAbsolutePath()).append('\n');
        sb.append("Plan ID: ").append(planHash, 0, 16).append("\u2026");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ExecPlan{rawCommand='" + rawCommand + '\''
                + ", cwd=" + cwd
                + ", hash='" + planHash.substring(0, 8) + "...'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecPlan)) return false;
        ExecPlan that = (ExecPlan) o;
        return Objects.equals(rawCommand, that.rawCommand)
                && Objects.equals(cwd, that.cwd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawCommand, cwd);
    }
}
