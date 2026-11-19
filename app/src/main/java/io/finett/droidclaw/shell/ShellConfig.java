package io.finett.droidclaw.shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for shell execution.
 *
 * <p>The security model is a single {@link ExecPolicy} security level plus an
 * {@link ExecPolicy.AskMode} approval gate:
 * <ul>
 *   <li>By default ({@link #createDefault()}) the level is {@link ExecPolicy.SecurityLevel#DENY},
 *       so no command runs until the user explicitly enables shell access.</li>
 *   <li>Once shell access is enabled, {@link ExecPolicy#blind()} grants full execution and
 *       lets the shell-approval MODE (ask_all / auto / full_access) decide prompting.</li>
 * </ul>
 *
 * <p>Validation is intentionally narrow: {@link #validatePlan(ExecPlan)} only rejects the
 * {@link ExecPolicy.SecurityLevel#DENY} case (shell disabled). The historical command
 * allowlist was removed — with the distro backend every resolved command runs and the
 * approval flow is the single gate.
 */
public class ShellConfig {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1 MB

    /**
     * Directories searched (in order) when resolving unqualified executable names in the
     * host (non-distro) case — i.e. the SSH backend, where the plan exe lives on the remote
     * host. The distro backend resolves names inside the guest rootfs instead and does not
     * use this list.
     *
     * <p>Android paths come first; Linux/desktop paths are included so unit tests can run on
     * development workstations. Non-existent directories are silently skipped at resolution
     * time.
     */
    static final List<String> DEFAULT_TRUSTED_DIRS = Collections.unmodifiableList(Arrays.asList(
        "/system/bin",
        "/system/xbin",
        "/usr/bin",
        "/bin",
        // NixOS: user-installed packages live here (e.g. /run/current-system/sw/bin/ls)
        "/run/current-system/sw/bin"
    ));

    private final int timeoutSeconds;
    private final int maxOutputSize;
    private final ExecPolicy policy;
    private final List<String> trustedDirs;
    private final ExecPlan.ExecMode defaultMode;
    /** Shell-approval mode governing prompt vs. silent execution (ask_all/auto/full_access). */
    private final String shellApprovalMode;

    private ShellConfig(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputSize = builder.maxOutputSize;
        this.policy = builder.policy;
        this.trustedDirs = Collections.unmodifiableList(new ArrayList<>(builder.trustedDirs));
        this.defaultMode = builder.defaultMode;
        this.shellApprovalMode = builder.shellApprovalMode;
    }

    // ==================== Factory methods ====================

    /**
     * Deny-everything default. Shell is off unless the user explicitly enables it.
     * This matches {@link io.finett.droidclaw.model.AgentConfig#getDefaults()} which
     * sets {@code shellAccess = false}.
     */
    public static ShellConfig createDefault() {
        return new Builder()
                .policy(ExecPolicy.deny())
                .defaultMode(ExecPlan.ExecMode.DIRECT)
                .build();
    }

    /**
     * Full-access config — any command may run but approval is always required.
     * Suitable only for explicitly trusted / developer setups.
     */
    public static ShellConfig createFull() {
        return new Builder()
                .policy(ExecPolicy.full())
                .defaultMode(ExecPlan.ExecMode.DIRECT)
                .build();
    }

    // ==================== Plan validation ====================

    /**
     * Validate a normalised {@link ExecPlan} against this config's policy.
     *
     * <p>The check is intentionally narrow: only the {@link ExecPolicy.SecurityLevel#DENY}
     * level (shell disabled) is rejected. Once shell access is enabled the approval flow is
     * the single gate, so any plan is allowed here.
     *
     * @return {@code null} if the plan is allowed, or a human-readable denial reason string.
     */
    public String validatePlan(ExecPlan plan) {
        // DENY — reject immediately; the approval flow is the gate once shell is enabled.
        if (policy.getSecurity() == ExecPolicy.SecurityLevel.DENY) {
            return "Shell execution policy is DENY — enable shell access in settings";
        }
        return null;
    }

    /**
     * Returns true if, according to the ask policy, the user must be prompted
     * even when the command is otherwise allowed.
     *
     * @param planAllowed true if {@link #validatePlan} returned null
     */
    public boolean requiresApproval(boolean planAllowed) {
        switch (policy.getAsk()) {
            case ALWAYS:
                return true;
            case ON_MISS:
                return !planAllowed;
            case OFF:
            default:
                return false;
        }
    }

    // ==================== Getters ====================

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxOutputSize() {
        return maxOutputSize;
    }

    public ExecPolicy getPolicy() {
        return policy;
    }

    public List<String> getTrustedDirs() {
        return trustedDirs;
    }

    public ExecPlan.ExecMode getDefaultMode() {
        return defaultMode;
    }

    /** The shell-approval mode governing prompt vs. silent execution. */
    public String getShellApprovalMode() {
        return shellApprovalMode;
    }

    // ==================== Builder ====================

    public static class Builder {
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private ExecPolicy policy = ExecPolicy.deny();
        private List<String> trustedDirs = new ArrayList<>(DEFAULT_TRUSTED_DIRS);
        private ExecPlan.ExecMode defaultMode = ExecPlan.ExecMode.DIRECT;
        private String shellApprovalMode = "ask_all";

        public Builder timeoutSeconds(int timeoutSeconds) {
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxOutputSize(int maxOutputSize) {
            if (maxOutputSize <= 0) {
                throw new IllegalArgumentException("Max output size must be positive");
            }
            this.maxOutputSize = maxOutputSize;
            return this;
        }

        public Builder policy(ExecPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder trustedDirs(List<String> dirs) {
            this.trustedDirs = new ArrayList<>(dirs);
            return this;
        }

        public Builder defaultMode(ExecPlan.ExecMode mode) {
            this.defaultMode = mode;
            return this;
        }

        /**
         * Set the shell-approval mode (ask_all/auto/full_access) that gates whether a
         * command is prompted for, judged, or run silently. Default: ask_all.
         */
        public Builder shellApprovalMode(String shellApprovalMode) {
            this.shellApprovalMode = shellApprovalMode;
            return this;
        }

        public ShellConfig build() {
            return new ShellConfig(this);
        }
    }
}
