package io.finett.droidclaw.shell;

import android.util.Log;

import io.finett.droidclaw.model.AgentConfig;

/**
 * Factory for creating the appropriate {@link ShellBackend} based on agent configuration.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code "local"} — execute commands on the Android device via ProcessBuilder</li>
 *   <li>{@code "ssh"} — execute commands on a remote Linux server via SSH</li>
 * </ul>
 */
public class ShellBackendFactory {

    private static final String TAG = "ShellBackendFactory";

    /**
     * Create a shell backend based on the agent configuration.
     *
     * @param agentConfig the agent configuration containing backend selection and SSH settings
     * @param shellConfig the shell execution policy/allowlist configuration
     * @return the appropriate ShellBackend implementation
     */
    public static ShellBackend create(AgentConfig agentConfig, ShellConfig shellConfig) {
        String backend = agentConfig.getShellBackend();

        if ("ssh".equalsIgnoreCase(backend)) {
            return createSshBackend(agentConfig, shellConfig);
        }

        // Default to local backend
        return new LocalShellBackend(shellConfig);
    }

    /**
     * Create an SSH shell backend with the provided configuration.
     */
    private static SshShellBackend createSshBackend(AgentConfig agentConfig, ShellConfig shellConfig) {
        SshConfig.Builder builder = new SshConfig.Builder()
                .host(agentConfig.getSshHost())
                .port(agentConfig.getSshPort())
                .username(agentConfig.getSshUser())
                .verifyHostKey(agentConfig.isSshVerifyHostKey())
                .policy(shellConfig.getPolicy());

        // Set authentication based on type
        String authType = agentConfig.getSshAuthType();
        if ("key".equalsIgnoreCase(authType)) {
            String privateKeyPath = agentConfig.getSshPrivateKeyPath();
            if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                builder.privateKeyPath(privateKeyPath);
            }
        } else {
            String password = agentConfig.getSshPassword();
            if (password != null && !password.isEmpty()) {
                builder.password(password);
            }
        }

        // Copy allowlist from shell config
        builder.allowlistEntries(shellConfig.getAllowlist());

        SshConfig sshConfig = builder.build();
        Log.i(TAG, "Created SSH backend for " + agentConfig.getSshHost() + ":" + agentConfig.getSshPort());

        return new SshShellBackend(sshConfig);
    }

    /**
     * Check if the current agent configuration specifies SSH backend.
     */
    public static boolean isSshBackend(AgentConfig agentConfig) {
        return "ssh".equalsIgnoreCase(agentConfig.getShellBackend());
    }
}
