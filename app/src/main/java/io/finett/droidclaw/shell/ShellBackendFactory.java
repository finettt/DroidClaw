package io.finett.droidclaw.shell;

import android.content.Context;
import android.util.Log;

import io.finett.droidclaw.model.AgentConfig;

import java.io.File;

/**
 * Factory for creating the appropriate {@link ShellBackend} based on agent configuration.
 *
 * <p>Supports:
 * <ul>
 *   <li>the distro backend (default) — execute commands inside the installed Linux image
 *       via {@link DistroShellBackend}. The values {@code null}, {@code "local"}, and
 *       {@code "distro"} all select it; a stored {@code "local"} value is migrated to this
 *       default.</li>
 *   <li>{@code "ssh"} — execute commands on a remote Linux server via {@link SshShellBackend}.</li>
 * </ul>
 */
public class ShellBackendFactory {

    private static final String TAG = "ShellBackendFactory";

    /**
     * Create a shell backend based on the agent configuration.
     *
     * @param agentConfig  the agent configuration containing backend selection and SSH settings
     * @param shellConfig  the shell execution policy configuration
     * @param context      application context (for the PRoot native library and temp dir)
     * @param distroRoot   the guest rootfs host path when the distro backend is selected, or
     *                     {@code null} for SSH
     * @param workspaceRoot the workspace host root, mounted at {@code /workspace} in the guest
     * @return the appropriate {@link ShellBackend} implementation
     */
    public static ShellBackend create(AgentConfig agentConfig, ShellConfig shellConfig,
                                      Context context, File distroRoot, File workspaceRoot) {
        String backend = agentConfig.getShellBackend();

        if ("ssh".equalsIgnoreCase(backend)) {
            return createSshBackend(agentConfig, shellConfig);
        }

        // Default (null / "local" / "distro") — run inside the installed Linux image.
        return createDistroBackend(agentConfig, shellConfig, context, distroRoot, workspaceRoot);
    }

    /**
     * Resolve the guest rootfs host path for this application: {@code filesDir/distro/ubuntu}.
     *
     * <p>This path exists regardless of whether the image is installed; {@link DistroShellBackend}
     * and {@link ExecPlanner} surface an actionable error when the ready marker is absent.
     */
    public static File distroRootFor(Context context) {
        File filesDir = context.getApplicationContext().getFilesDir();
        return new File(new File(filesDir, "distro"), io.finett.droidclaw.shell.distro.DistroSpec.IMAGE_DIR);
    }

    /**
     * Create the distro shell backend.
     */
    private static DistroShellBackend createDistroBackend(AgentConfig agentConfig, ShellConfig shellConfig,
                                                          Context context, File distroRoot, File workspaceRoot) {
        Context applicationContext = context.getApplicationContext();
        String nativeLibraryDir = applicationContext.getApplicationInfo().nativeLibraryDir;
        boolean seccompDisabled = agentConfig.isDistroNoSeccomp();
        return new DistroShellBackend(shellConfig, distroRoot, workspaceRoot,
                nativeLibraryDir, applicationContext.getFilesDir(), seccompDisabled);
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

        SshConfig sshConfig = builder.build();
        Log.i(TAG, "Created SSH backend for " + agentConfig.getSshHost() + ":" + agentConfig.getSshPort());

        return new SshShellBackend(sshConfig);
    }

    /**
     * Check if the current agent configuration specifies the SSH backend.
     */
    public static boolean isSshBackend(AgentConfig agentConfig) {
        return "ssh".equalsIgnoreCase(agentConfig.getShellBackend());
    }
}
