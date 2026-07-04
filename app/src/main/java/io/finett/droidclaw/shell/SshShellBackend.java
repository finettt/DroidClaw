package io.finett.droidclaw.shell;

import android.util.Log;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * SSH-based shell backend that executes commands on a remote Linux server.
 *
 * <p>Features:
 * <ul>
 *   <li>Persistent SSH session with automatic reconnection on timeout</li>
 *   <li>Password and key-based authentication</li>
 *   <li>Host key verification (configurable for development)</li>
 *   <li>Command timeout enforcement</li>
 *   <li>Thread-safe session management</li>
 * </ul>
 *
 * <p>Security considerations:
 * <ul>
 *   <li>Credentials are stored via {@code EncryptedSharedPreferences}</li>
 *   <li>JSch is injected for testability — use {@link #SshShellBackend(SshConfig, JSchFactory)} for
 *       custom JSch instances</li>
 *   <li>Host key verification should be enabled in production</li>
 *   <li>Channel connect and session connect both have timeouts to prevent indefinite blocking</li>
 * </ul>
 */
public class SshShellBackend implements ShellBackend {

    private static final String TAG = "SshShellBackend";
    private static final int SESSION_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 30_000;
    private static final int CHANNEL_CONNECT_TIMEOUT_MS = 15_000;

    private final SshConfig config;
    private final JSchFactory jschFactory;
    private Session session;
    private boolean connected;

    /**
     * Functional interface for creating JSch instances.
     * Allows dependency injection for testing.
     */
    @FunctionalInterface
    public interface JSchFactory {
        JSch create() throws IOException;
    }

    /**
     * Create an SSH shell backend with a default JSch instance.
     */
    public SshShellBackend(SshConfig config) {
        this(config, DefaultJSchFactory.INSTANCE);
    }

    /**
     * Create an SSH shell backend with an injected JSch factory.
     * Used for testing to inject mocked JSch instances.
     */
    public SshShellBackend(SshConfig config, JSchFactory jschFactory) {
        this.config = config;
        this.jschFactory = jschFactory != null ? jschFactory : DefaultJSchFactory.INSTANCE;
        this.connected = false;
    }

    @Override
    public ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException {
        if (plan == null) {
            throw new SecurityException("ExecPlan must not be null");
        }

        // Re-verify plan hash
        String recomputedHash = ExecPlan.computeHash(
                plan.getCanonicalExePath(),
                plan.getArgv(),
                plan.getCwd(),
                plan.getMode());
        if (!recomputedHash.equals(plan.getPlanHash())) {
            throw new SecurityException(
                "ExecPlan hash mismatch — plan may have been tampered with.");
        }

        // Validate policy
        String denial = config.validatePlan(plan);
        if (denial != null) {
            throw new SecurityException(denial);
        }

        // Build command string for remote execution
        String remoteCommand = buildRemoteCommand(plan);

        // Execute on remote server
        return executeRemote(remoteCommand, timeoutSeconds);
    }

    // ==================== Command building ====================

    /**
     * Build the remote command string from an ExecPlan.
     *
     * @param plan the execution plan
     * @return the command string to execute on the remote server
     */
    String buildRemoteCommand(ExecPlan plan) {
        if (plan.getMode() == ExecPlan.ExecMode.DIRECT) {
            // For DIRECT mode, wrap each argument in single quotes
            StringBuilder sb = new StringBuilder();
            sb.append(plan.getCanonicalExePath());
            for (String arg : plan.getArgv()) {
                sb.append(' ');
                sb.append(escapeShellArg(arg));
            }
            return sb.toString();
        } else {
            // For SHELL mode, pass as-is to remote sh
            StringBuilder sb = new StringBuilder(plan.getCanonicalExePath());
            for (String arg : plan.getArgv()) {
                sb.append(' ').append(arg);
            }
            return sb.toString();
        }
    }

    /**
     * Escape a single shell argument for use in a single-quoted bash command.
     *
     * @param arg the argument to escape
     * @return the escaped argument wrapped in single quotes
     */
    String escapeShellArg(String arg) {
        // Escape single quotes by ending the string, adding escaped quote, restarting
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    // ==================== Remote execution ====================

    private ShellResult executeRemote(String command, int timeoutSeconds) throws SecurityException {
        long startTime = System.currentTimeMillis();
        ChannelExec channel = null;
        int exitCode = -1;
        String stdout = "";
        String stderr = "";
        boolean timedOut = false;

        try {
            // Ensure connected
            ensureConnected();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            // Set up output streams
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();

            channel.setOutputStream(outStream);
            channel.setErrStream(errStream);

            // Use timeout on channel.connect to prevent indefinite blocking
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MS);

            // Wait for completion with timeout
            long timeoutMs = timeoutSeconds * 1000L;
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (channel.isConnected() && System.currentTimeMillis() < deadline) {
                if (channel.isClosed()) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SecurityException("Command execution interrupted", e);
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                timedOut = true;
                channel.disconnect();
                exitCode = -1;
                stderr = "Command timed out after " + timeoutSeconds + " seconds";
            } else {
                exitCode = channel.getExitStatus();
                try {
                    stdout = outStream.toString("UTF-8");
                    stderr = errStream.toString("UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    // Fallback to default encoding if UTF-8 not supported
                    stdout = outStream.toString();
                    stderr = errStream.toString();
                }
            }

        } catch (JSchException e) {
            stderr = "SSH execution failed: " + e.getMessage();
            exitCode = -1;
            connected = false;
            Log.e(TAG, "SSH execution error", e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        return new ShellResult(stdout, stderr, exitCode, timedOut, executionTime);
    }

    // ==================== Connection management (thread-safe) ====================

    private void ensureConnected() throws SecurityException {
        synchronized (this) {
            if (connected && session != null && session.isConnected()) {
                return;
            }

            // Close any existing session
            closeSessionLocked();
        }

        // Create new session outside of lock (JSch construction can be slow)
        Session localSession = null;
        try {
            JSch jsch = jschFactory.create();

            // Load private key if provided
            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                try {
                    jsch.addIdentity(config.getPrivateKeyPath());
                } catch (JSchException e) {
                    throw new SecurityException("Failed to load SSH private key: " + e.getMessage(), e);
                }
            }

            // Create session
            localSession = jsch.getSession(
                config.getUsername(),
                config.getHost(),
                config.getPort());

            // Set authentication
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                localSession.setPassword(config.getPassword());
            }

            // Set session timeouts
            localSession.setConfig("StrictHostKeyChecking",
                config.isVerifyHostKey() ? "yes" : "no");
            localSession.setConfig("PreferredAuthentications",
                config.getPrivateKeyPath() != null ? "publickey,keyboard-interactive,password"
                                                    : "password");

            // Connect with timeout
            localSession.connect(SESSION_TIMEOUT_MS);
            localSession.setServerAliveInterval(SOCKET_TIMEOUT_MS);

            Log.i(TAG, "Connected to " + config.getHost() + ":" + config.getPort());

        } catch (JSchException e) {
            throw new SecurityException("Failed to connect to SSH server: " + e.getMessage(), e);
        }

        // Atomically publish the new session
        synchronized (this) {
            this.session = localSession;
            this.connected = true;
        }
    }

    private void closeSessionLocked() {
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Error closing SSH session", e);
            }
        }
        session = null;
        connected = false;
    }

    @Override
    public void close() {
        synchronized (this) {
            closeSessionLocked();
        }
    }

    /**
     * Check if currently connected to the SSH server.
     */
    public boolean isConnected() {
        synchronized (this) {
            return connected && session != null && session.isConnected();
        }
    }

    /**
     * Test the SSH connection without executing a command.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        try {
            ensureConnected();
            // Try a simple command to verify the connection works
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("echo test");
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MS);
            channel.disconnect();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "SSH connection test failed", e);
            connected = false;
            return false;
        }
    }

    /**
     * Default JSch factory that creates new JSch instances.
     */
    private static class DefaultJSchFactory implements JSchFactory {
        static final DefaultJSchFactory INSTANCE = new DefaultJSchFactory();

        @Override
        public JSch create() throws IOException {
            return new JSch();
        }
    }
}
