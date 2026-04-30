package io.finett.droidclaw.shell;

/**
 * Describes the execution security policy for shell commands.
 *
 * <p>Modelled after the OpenClaw policy+allowlist+ask architecture:
 * <ul>
 *   <li>{@link SecurityLevel#DENY} — no shell execution permitted (default).</li>
 *   <li>{@link SecurityLevel#ALLOWLIST} — only commands matching the allowlist are run.</li>
 *   <li>{@link SecurityLevel#FULL} — any command may run (trusted-operator mode).</li>
 * </ul>
 *
 * <p>{@link AskMode} controls when the user is prompted for approval:
 * <ul>
 *   <li>{@link AskMode#OFF} — no prompt (only safe with DENY).</li>
 *   <li>{@link AskMode#ON_MISS} — prompt when the allowlist does not match.</li>
 *   <li>{@link AskMode#ALWAYS} — prompt for every execution (mandatory for FULL mode).</li>
 * </ul>
 */
public class ExecPolicy {

    public enum SecurityLevel {
        /** Reject all exec attempts. */
        DENY,
        /** Permit only allowlisted executables+argv profiles. */
        ALLOWLIST,
        /** Permit everything; user approval is still required (ask=ALWAYS enforced). */
        FULL
    }

    public enum AskMode {
        /** Never ask — only valid when security=DENY. */
        OFF,
        /** Ask when the command is not in the allowlist. */
        ON_MISS,
        /** Always ask before execution. */
        ALWAYS
    }

    private final SecurityLevel security;
    private final AskMode ask;

    public ExecPolicy(SecurityLevel security, AskMode ask) {
        if (security == SecurityLevel.FULL && ask != AskMode.ALWAYS) {
            // Enforce: FULL mode must always ask — prevent silent full-access execution.
            ask = AskMode.ALWAYS;
        }
        this.security = security;
        this.ask = ask;
    }

    /** Deny all exec; no approval prompt needed. */
    public static ExecPolicy deny() {
        return new ExecPolicy(SecurityLevel.DENY, AskMode.OFF);
    }

    /**
     * Allowlist mode: only allowlisted commands run; prompt if not matched.
     * This is the recommended default when shell access is explicitly enabled.
     */
    public static ExecPolicy allowlist() {
        return new ExecPolicy(SecurityLevel.ALLOWLIST, AskMode.ON_MISS);
    }

    /**
     * Allowlist mode with mandatory approval for every command, even if matched.
     * Suitable for security-sensitive environments.
     */
    public static ExecPolicy allowlistAlwaysAsk() {
        return new ExecPolicy(SecurityLevel.ALLOWLIST, AskMode.ALWAYS);
    }

    /**
     * Full (trusted-operator) mode: any command may run, but approval is always required.
     */
    public static ExecPolicy full() {
        return new ExecPolicy(SecurityLevel.FULL, AskMode.ALWAYS);
    }

    public SecurityLevel getSecurity() {
        return security;
    }

    public AskMode getAsk() {
        return ask;
    }

    @Override
    public String toString() {
        return "ExecPolicy{security=" + security + ", ask=" + ask + '}';
    }
}