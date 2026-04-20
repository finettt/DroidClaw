package io.finett.droidclaw.shell;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ShellConfig {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1MB

    private final int timeoutSeconds;
    private final int maxOutputSize;
    private final boolean enabled;
    private final boolean requireApproval;
    private final Set<String> blockedCommands;
    private final Set<String> allowedCommands;

    private ShellConfig(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputSize = builder.maxOutputSize;
        this.enabled = builder.enabled;
        this.requireApproval = builder.requireApproval;
        this.blockedCommands = Collections.unmodifiableSet(new HashSet<>(builder.blockedCommands));
        this.allowedCommands = builder.allowedCommands.isEmpty() 
            ? Collections.emptySet() 
            : Collections.unmodifiableSet(new HashSet<>(builder.allowedCommands));
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxOutputSize() {
        return maxOutputSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public Set<String> getBlockedCommands() {
        return blockedCommands;
    }

    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    public boolean isCommandAllowed(String command) {
        if (!enabled) {
            return false;
        }

        String trimmedCommand = command.trim();
        String firstToken = trimmedCommand.split("\\s+")[0];

        for (String blocked : blockedCommands) {
            String[] blockedTokens = blocked.split("\\s+");
            String firstBlockedToken = blockedTokens[0];
            if (firstToken.equals(firstBlockedToken)) {
                // Block entries with arguments (e.g. "rm -rf /") by matching argument prefix
                if (blockedTokens.length > 1) {
                    String blockedArgs = blocked.substring(firstBlockedToken.length()).trim();
                    String commandArgs = trimmedCommand.substring(firstToken.length()).trim();
                    if (blockedArgs.isEmpty() || commandArgs.startsWith(blockedArgs)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        // If an allowlist is defined, the command must be in it
        if (!allowedCommands.isEmpty()) {
            boolean allowed = false;
            for (String allowedCmd : allowedCommands) {
                if (trimmedCommand.startsWith(allowedCmd) || firstToken.equals(allowedCmd)) {
                    allowed = true;
                    break;
                }
            }
            return allowed;
        }

        return true;
    }

    public static ShellConfig createDefault() {
        return new Builder()
                .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                .maxOutputSize(MAX_OUTPUT_SIZE)
                .enabled(true)
                .requireApproval(false)
                .addBlockedCommands(getDefaultBlockedCommands())
                .build();
    }

    private static Set<String> getDefaultBlockedCommands() {
        return new HashSet<>(Arrays.asList(
            "rm -rf /",
            "mkfs",
            "dd if=/dev/zero",
            ":(){ :|:& };:", // Fork bomb
            "chmod -R 777 /",
            "chown -R",
            "> /dev/sda",
            "mv / /dev/null"
        ));
    }

    public static class Builder {
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxOutputSize = MAX_OUTPUT_SIZE;
        private boolean enabled = true;
        private boolean requireApproval = false;
        private Set<String> blockedCommands = new HashSet<>();
        private Set<String> allowedCommands = new HashSet<>();

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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder requireApproval(boolean requireApproval) {
            this.requireApproval = requireApproval;
            return this;
        }

        public Builder addBlockedCommand(String command) {
            this.blockedCommands.add(command);
            return this;
        }

        public Builder addBlockedCommands(Set<String> commands) {
            this.blockedCommands.addAll(commands);
            return this;
        }

        public Builder addAllowedCommand(String command) {
            this.allowedCommands.add(command);
            return this;
        }

        public Builder addAllowedCommands(Set<String> commands) {
            this.allowedCommands.addAll(commands);
            return this;
        }

        public ShellConfig build() {
            return new ShellConfig(this);
        }
    }
}