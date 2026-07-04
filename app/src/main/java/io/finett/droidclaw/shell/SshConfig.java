package io.finett.droidclaw.shell;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for SSH shell backend.
 *
 * <p>Holds connection parameters and security settings for remote shell execution.
 */
public class SshConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;  // null if using key-based auth
    private final String privateKeyPath;  // null if using password auth
    private final boolean verifyHostKey;
    private final ExecPolicy policy;
    private final List<AllowlistEntry> allowlist;

    private SshConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.privateKeyPath = builder.privateKeyPath;
        this.verifyHostKey = builder.verifyHostKey;
        this.policy = builder.policy;
        this.allowlist = java.util.Collections.unmodifiableList(new ArrayList<>(builder.allowlist));
    }

    // ==================== Getters ====================

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public boolean isVerifyHostKey() {
        return verifyHostKey;
    }

    public ExecPolicy getPolicy() {
        return policy;
    }

    public List<AllowlistEntry> getAllowlist() {
        return allowlist;
    }

    /**
     * Validate an {@link ExecPlan} against this SSH config's policy and allowlist.
     *
     * @return {@code null} if allowed, or a denial reason string
     */
    public String validatePlan(ExecPlan plan) {
        if (policy.getSecurity() == ExecPolicy.SecurityLevel.DENY) {
            return "SSH execution policy is DENY — enable shell access in settings";
        }

        if (policy.getSecurity() == ExecPolicy.SecurityLevel.FULL) {
            return null;
        }

        for (AllowlistEntry entry : allowlist) {
            if (entry.matches(plan.getCanonicalExePath())) {
                String argvDenial = entry.validateArgv(plan.getArgv());
                if (argvDenial != null) {
                    return "Command argument denied: " + argvDenial;
                }
                return null;
            }
        }

        return "Command not in SSH allowlist: " + plan.getCanonicalExePath();
    }

    // ==================== Builder ====================

    public static class Builder {
        private String host = "";
        private int port = 22;
        private String username = "";
        private String password = null;
        private String privateKeyPath = null;
        private boolean verifyHostKey = true;
        private ExecPolicy policy = ExecPolicy.deny();
        private List<AllowlistEntry> allowlist = new ArrayList<>();

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder privateKeyPath(String path) {
            this.privateKeyPath = path;
            return this;
        }

        public Builder verifyHostKey(boolean verify) {
            this.verifyHostKey = verify;
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

        public SshConfig build() {
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("SSH host is required");
            }
            if (username == null || username.isEmpty()) {
                throw new IllegalArgumentException("SSH username is required");
            }
            return new SshConfig(this);
        }
    }
}
