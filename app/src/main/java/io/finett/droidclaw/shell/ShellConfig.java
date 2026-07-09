package io.finett.droidclaw.shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for shell execution.
 *
 * <p>Replaces the old blocklist model with an allowlist + {@link ExecPolicy} model:
 * <ul>
 *   <li>By default ({@link #createDefault()}) all execution is denied.</li>
 *   <li>When shell access is explicitly enabled, use {@link #createAllowlistDefault()}
 *       which permits a minimal, safe set of read-only commands.</li>
 *   <li>Full/trusted-operator access is available via {@link #createFull()} but always
 *       requires user approval for every command.</li>
 * </ul>
 *
 * <p>The old {@code blockedCommands} / {@code isCommandAllowed(String)} API is removed.
 * Validation now operates on a normalised {@link ExecPlan} via {@link #validatePlan(ExecPlan)}.
 */
public class ShellConfig {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1 MB

    /**
     * Directories searched (in order) when resolving unqualified executable names.
     * Only these directories are trusted; PATH from the environment is ignored.
     *
     * <p>Android paths come first; Linux/desktop paths are included so unit tests
     * can run on development workstations. Non-existent directories are silently
     * skipped at resolution time.
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
    private final List<AllowlistEntry> allowlist;
    private final List<String> trustedDirs;
    private final ExecPlan.ExecMode defaultMode;

    private ShellConfig(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputSize = builder.maxOutputSize;
        this.policy = builder.policy;
        this.allowlist = Collections.unmodifiableList(new ArrayList<>(builder.allowlist));
        this.trustedDirs = Collections.unmodifiableList(new ArrayList<>(builder.trustedDirs));
        this.defaultMode = builder.defaultMode;
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
     * Allowlist-based config with a minimal, read-focused safe command set.
     * Use this when the user has explicitly enabled shell access.
     *
     * <p>Safe commands included: {@code ls}, {@code cat}, {@code echo}, {@code pwd},
     * {@code date}, {@code grep}, {@code find} (with {@code -exec} denied),
     * {@code wc}, {@code head}, {@code tail}.
     *
     * <p>Destructive commands ({@code rm}, {@code mv}, {@code chmod}, {@code chown},
     * {@code dd}, {@code mkfs}, {@code sh}, {@code bash}) are NOT in this allowlist.
     */
    public static ShellConfig createAllowlistDefault() {
        return createAllowlistDefault(java.util.Collections.emptyList());
    }

    /**
     * Allowlist-based config with the safe default set plus user-provided extra entries.
     *
     * @param extraEntries additional allowlist entries (e.g. custom executables the user
     *                     explicitly trusts); may be empty but not null
     */
    public static ShellConfig createAllowlistDefault(List<AllowlistEntry> extraEntries) {
        Builder builder = new Builder()
                .policy(ExecPolicy.allowlist())
                .defaultMode(ExecPlan.ExecMode.DIRECT)
                .allowlistEntries(buildSafeAllowlist());
        for (AllowlistEntry entry : extraEntries) {
            builder.allowlistEntry(entry);
        }
        return builder.build();
    }

    /**
     * Full access config — any command may run but approval is always required.
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
     * Validate a normalised {@link ExecPlan} against this config's policy and allowlist.
     *
     * @return {@code null} if the plan is allowed, or a human-readable denial reason string.
     */
    public String validatePlan(ExecPlan plan) {
        // DENY — reject immediately
        if (policy.getSecurity() == ExecPolicy.SecurityLevel.DENY) {
            return "Shell execution policy is DENY — enable shell access in settings";
        }

        // FULL — no allowlist check; approval is enforced separately by the ask=ALWAYS policy
        if (policy.getSecurity() == ExecPolicy.SecurityLevel.FULL) {
            return null;
        }

        // ALLOWLIST — find matching entry and validate argv
        for (AllowlistEntry entry : allowlist) {
            if (entry.matches(plan.getCanonicalExePath())) {
                String argvDenial = entry.validateArgv(plan.getArgv());
                if (argvDenial != null) {
                    return "Command argument denied: " + argvDenial;
                }
                return null; // allowed
            }
        }

        // No allowlist match
        return "Command not in allowlist: " + plan.getCanonicalExePath();
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

    public List<AllowlistEntry> getAllowlist() {
        return allowlist;
    }

    public List<String> getTrustedDirs() {
        return trustedDirs;
    }

    public ExecPlan.ExecMode getDefaultMode() {
        return defaultMode;
    }

    // ==================== Default allowlist ====================

    /**
     * Build the minimal safe allowlist for {@link #createAllowlistDefault()}.
     *
     * <p>Design principles:
     * <ul>
     *   <li>Only read-only or append-only commands are included.</li>
     *   <li>{@code find} is included but {@code -exec} and {@code -delete} are denied.</li>
     *   <li>No interpreter ({@code sh}, {@code python}, {@code node}) is included here;
     *       those are added explicitly by the caller if needed.</li>
     * </ul>
     */
    private static List<AllowlistEntry> buildSafeAllowlist() {
        List<AllowlistEntry> list = new ArrayList<>();

        // ls — directory listing; only safe display flags
        for (String path : resolveAllPaths("ls")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-l", "-a", "-h", "-1", "-A", "-p", "-la", "-lah", "-lh", "--help")
                    .build());
        }

        // cat — file content; limit to 1 file at a time to reduce exfil surface
        for (String path : resolveAllPaths("cat")) {
            list.add(new AllowlistEntry.Builder(path)
                    .maxPositionalArgs(1)
                    .build());
        }

        // echo — output only; no flags that write to files
        for (String path : resolveAllPaths("echo")) {
            list.add(new AllowlistEntry.Builder(path)
                    .denyFlags("-e")   // -e enables escape interpretation (potential injection)
                    .build());
        }

        // pwd — current directory
        for (String path : resolveAllPaths("pwd")) {
            list.add(new AllowlistEntry.Builder(path)
                    .maxPositionalArgs(0)
                    .build());
        }

        // date — current date/time
        for (String path : resolveAllPaths("date")) {
            list.add(new AllowlistEntry.Builder(path)
                    .maxPositionalArgs(1)
                    .build());
        }

        // grep — text search; read-only
        for (String path : resolveAllPaths("grep")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-r", "-l", "-n", "-i", "-E", "-F", "-c", "-v",
                                "--include", "--exclude", "-m", "--help")
                    .build());
        }

        // find — file search; -exec, -delete, -execdir are denied
        for (String path : resolveAllPaths("find")) {
            list.add(new AllowlistEntry.Builder(path)
                    .denyFlags("-exec", "-execdir", "-delete", "-ok", "-okdir")
                    .build());
        }

        // wc — word/line/byte count
        for (String path : resolveAllPaths("wc")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-l", "-c", "-w", "-m", "--help")
                    .build());
        }

        // head — first N lines
        for (String path : resolveAllPaths("head")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-n", "--help")
                    .maxPositionalArgs(1)
                    .build());
        }

        // tail — last N lines (no -f to prevent hang)
        for (String path : resolveAllPaths("tail")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-n", "--help")
                    .denyFlags("-f", "--follow")
                    .maxPositionalArgs(1)
                    .build());
        }

        // uname — system info
        for (String path : resolveAllPaths("uname")) {
            list.add(new AllowlistEntry.Builder(path)
                    .allowFlags("-a", "-r", "-m", "-s", "--help")
                    .maxPositionalArgs(0)
                    .build());
        }

        // id — current user/group info
        for (String path : resolveAllPaths("id")) {
            list.add(new AllowlistEntry.Builder(path)
                    .maxPositionalArgs(0)
                    .build());
        }

        // env — list environment variables (read-only; no set/unset)
        for (String path : resolveAllPaths("env")) {
            list.add(new AllowlistEntry.Builder(path)
                    .maxPositionalArgs(0)
                    .build());
        }

        return list;
    }

    /**
     * Resolve all canonical paths for {@code exeName} across every trusted directory.
     *
     * <p>On Android the binary lives in {@code /system/bin}; on Linux CI it may be in
     * {@code /usr/bin} or {@code /bin} (or a Nix store path reached via a symlink).
     * We add an entry for every path found so the allowlist works on both platforms
     * without hardcoding OS-specific locations.
     *
     * <p>If no trusted directory contains {@code exeName}, the list will be empty and
     * the command simply won't appear in the allowlist — which is the safe default.
     */
    private static List<String> resolveAllPaths(String exeName) {
        List<String> paths = new ArrayList<>();
        for (String dir : DEFAULT_TRUSTED_DIRS) {
            File candidate = new File(dir, exeName);
            if (candidate.exists() && candidate.canExecute()) {
                String absolute = candidate.getAbsolutePath();
                if (!paths.contains(absolute)) {
                    paths.add(absolute);
                }
            }
        }
        return paths;
    }

    // ==================== Builder ====================

    public static class Builder {
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private ExecPolicy policy = ExecPolicy.deny();
        private List<AllowlistEntry> allowlist = new ArrayList<>();
        private List<String> trustedDirs = new ArrayList<>(DEFAULT_TRUSTED_DIRS);
        private ExecPlan.ExecMode defaultMode = ExecPlan.ExecMode.DIRECT;

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

        public Builder allowlistEntry(AllowlistEntry entry) {
            this.allowlist.add(entry);
            return this;
        }

        public Builder allowlistEntries(List<AllowlistEntry> entries) {
            this.allowlist.addAll(entries);
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

        public ShellConfig build() {
            return new ShellConfig(this);
        }
    }
}
