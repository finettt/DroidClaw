package io.finett.droidclaw.shell;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shell backend that runs commands inside the installed Linux image through PRoot.
 *
 * <p>The contract mirrors the removed local backend: re-verify the plan hash, run
 * {@link ShellConfig#validatePlan}, then start the process. Instead of running the
 * executable directly, it launches the packaged PRoot binary with the rootfs bound, the
 * workspace mounted at {@code /workspace}, and the resolved guest command as the trailing
 * {@code exe} ...argv.
 *
 * <p>Proot invocation:
 * <pre>
 *   &lt;nativeLibraryDir&gt;/libproot.so -0 --kill-on-exit -R &lt;rootfs&gt;
 *       -b &lt;workspace&gt;:/workspace -w &lt;guestCwd&gt; &lt;exe&gt; ...argv
 * </pre>
 *
 * <p>{@code guestCwd} is {@code /workspace} when the plan cwd is inside the workspace, else
 * {@code /root}. If {@code -R} fails because a host bind path is missing, it falls back to
 * {@code -r &lt;rootfs&gt;} with explicit {@code -b /dev -b /proc -b /sys}.
 */
public class DistroShellBackend implements ShellBackend {

    private static final String TAG = "DistroShellBackend";
    private static final String PROOT_BINARY = "libproot.so";
    private static final String PROOT_LOADER = "libproot-loader.so";
    private static final String PROOT_LOADER32 = "libproot-loader32.so";
    private static final String PROOT_TMP_DIR_NAME = "proot-tmp";
    private static final String SHELL_IN_GUEST = "bin/sh";

    private static final String[] GUEST_BIND_FALLBACK = {"/dev", "/proc", "/sys"};

    private static final String PATH_GUEST =
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    private final ShellConfig config;
    private final File rootfsHost;
    private final File canonicalRootfsHost;
    private final File workspaceHost;
    private final String nativeLibraryDir;
    private final File prootTmpDir;
    private final boolean seccompDisabled;

    public DistroShellBackend(ShellConfig config,
                              File rootfsHost,
                              File workspaceHost,
                              String nativeLibraryDir,
                              File filesDir,
                              boolean seccompDisabled) {
        this.config = config;
        this.rootfsHost = rootfsHost;
        this.workspaceHost = workspaceHost;
        this.nativeLibraryDir = nativeLibraryDir;
        this.prootTmpDir = new File(filesDir, PROOT_TMP_DIR_NAME);
        this.seccompDisabled = seccompDisabled;
        this.canonicalRootfsHost = canonicalHost(rootfsHost);
    }

    /** Canonicalise a host path, tolerating failures by returning the original. */
    private static File canonicalHost(File file) {
        try {
            return file.getCanonicalFile();
        } catch (java.io.IOException e) {
            return file;
        }
    }

    /**
     * Map a host path inside the guest rootfs to its guest path relative to the chroot.
     * libproot resolves the executable relative to the chroot, so the full host path
     * (e.g. {@code /data/data/app/.../distro/ubuntu/usr/bin/echo}) must be stripped of the
     * canonical rootfs prefix to become the guest path ({@code /usr/bin/echo}).
     */
    private String guestPath(String hostPath) {
        String root = canonicalRootfsHost.getAbsolutePath();
        if (hostPath.equals(root)) {
            return "/";
        }
        if (hostPath.startsWith(root + java.io.File.separator)) {
            return "/" + hostPath.substring(root.length() + 1);
        }
        return hostPath;
    }

    @Override
    public ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException {
        if (plan == null) {
            throw new SecurityException("ExecPlan must not be null");
        }

        // 1. Re-verify plan hash (TOCTOU / substitution-attack prevention)
        String recomputedHash = ExecPlan.computeHash(
                plan.getCanonicalExePath(),
                plan.getArgv(),
                plan.getCwd(),
                plan.getMode());
        if (!recomputedHash.equals(plan.getPlanHash())) {
            throw new SecurityException(
                "ExecPlan hash mismatch — plan may have been tampered with. "
                + "Expected: " + plan.getPlanHash().substring(0, 8) + "..."
                + "  Got: " + recomputedHash.substring(0, 8) + "...");
        }

        // 2. Policy validation (DENY-only; the approval flow is the gate once shell is enabled)
        String denial = config.validatePlan(plan);
        if (denial != null) {
            throw new SecurityException(denial);
        }

        // 3a. Ensure the proot temp directory exists (libproot needs it for the glue rootfs).
        if (!prootTmpDir.exists() && !prootTmpDir.mkdirs()) {
            android.util.Log.w(TAG, "Could not create proot temp dir: " + prootTmpDir);
        }

        // 3. Build the PRoot command and run it, falling back to -r if -R bind fails.
        boolean chrootFallback = false;
        List<String> command = buildProotCommand(plan, chrootFallback);
        ShellResult result = runProcess(command, plan.getCwd(), timeoutSeconds);

        if (!chrootFallback && looksLikeBindFailure(result)) {
            android.util.Log.d(TAG, "FIRST(-R) exit=" + result.getExitCode() + " stderr=" + result.getStderr());
            android.util.Log.d(TAG, "FALLBACK to -r");
            chrootFallback = true;
            command = buildProotCommand(plan, chrootFallback);
            result = runProcess(command, plan.getCwd(), timeoutSeconds);
        }

        return result;
    }

    // ==================== Command building ====================

    /**
     * Assemble the PRoot command line for the given plan.
     *
     * <p>DIRECT mode runs the resolved guest executable with its argv. SHELL mode runs the
     * guest shell ({@code /bin/sh}) with {@code -c} and the plan reconstructed as a single
     * command string, mirroring the removed local backend's shell handling.
     */
    List<String> buildProotCommand(ExecPlan plan, boolean chrootFallback) {
        String exeForProot;
        List<String> exeArgv;

        if (plan.getMode() == ExecPlan.ExecMode.DIRECT) {
            exeForProot = guestPath(plan.getCanonicalExePath());
            exeArgv = new ArrayList<>(plan.getArgv());
        } else {
            exeForProot = guestPath(new File(rootfsHost, SHELL_IN_GUEST).getAbsolutePath());
            StringBuilder sb = new StringBuilder(exeForProot);
            for (String arg : plan.getArgv()) {
                sb.append(' ').append(arg);
            }
            exeArgv = Arrays.asList("-c", sb.toString());
        }

        List<String> command = new ArrayList<>();
        command.add(new File(nativeLibraryDir, PROOT_BINARY).getAbsolutePath());
        command.add("-0");
        command.add("--kill-on-exit");
        command.add(chrootFallback ? "-r" : "-R");
        command.add(rootfsHost.getAbsolutePath());
        command.add("-b");
        command.add(workspaceHost.getAbsolutePath() + ":/workspace");
        if (chrootFallback) {
            for (String mount : GUEST_BIND_FALLBACK) {
                command.add("-b");
                command.add(mount);
            }
        }
        command.add("-w");
        command.add(guestCwdFor(plan));
        // The executable is always an absolute guest path (DIRECT) or the guest
        // shell (SHELL mode), so option parsing stops there on its own; no "--"
        // separator is needed. The packaged Termux "proot" build rejects "--".
        command.add(exeForProot);
        command.addAll(exeArgv);
        android.util.Log.d("DistroShellBackend", "PROOT_CMD=" + command.toString());
        return command;
    }

    /** {@code /workspace} when the plan cwd is inside the workspace, otherwise {@code /root}. */
    String guestCwdFor(ExecPlan plan) {
        File cwd = plan.getCwd();
        if (isInside(cwd, workspaceHost)) {
            return "/workspace";
        }
        return "/root";
    }

    private boolean isInside(File file, File base) {
        if (file == null || base == null) {
            return false;
        }
        try {
            String abs = file.getCanonicalFile().getAbsolutePath();
            String root = base.getCanonicalFile().getAbsolutePath();
            return abs.equals(root) || abs.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Heuristic: PRoot exits before running the command when a {@code -R} bind source is
     * missing. A normal command failure produces its own output and a real exit code, so we
     * only retry with {@code -r} when stderr points at a missing host path.
     */
    boolean looksLikeBindFailure(ShellResult result) {
        if (result == null || result.isSuccess()) {
            return false;
        }
        String err = result.getStderr();
        if (err == null) {
            return false;
        }
        String lowered = err.toLowerCase();
        return lowered.contains("such file or directory")
                || lowered.contains("bind(")
                || lowered.contains("cannot bind");
    }

    // ==================== Process execution ====================

    private ShellResult runProcess(List<String> command, File cwd, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        Process process = null;
        boolean timedOut = false;
        int exitCode = -1;
        String stdout = "";
        String stderr = "";

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd);
            builder.redirectErrorStream(false);
            environment(builder);

            process = builder.start();

            StreamGobbler stdoutGobbler = new StreamGobbler(
                    process.getInputStream(), config.getMaxOutputSize());
            StreamGobbler stderrGobbler = new StreamGobbler(
                    process.getErrorStream(), config.getMaxOutputSize());

            Thread stdoutThread = new Thread(stdoutGobbler, "distro-stdout");
            Thread stderrThread = new Thread(stderrGobbler, "distro-stderr");

            stdoutThread.start();
            stderrThread.start();

            boolean finished = waitForProcess(process, timeoutSeconds);
            if (!finished) {
                timedOut = true;
                destroyProcessForcibly(process);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            stdout = stdoutGobbler.getOutput();
            stderr = stderrGobbler.getOutput();

        } catch (IOException e) {
            stderr = "Failed to start process: " + e.getMessage();
            exitCode = -1;
        } catch (InterruptedException e) {
            stderr = "Process execution interrupted: " + e.getMessage();
            exitCode = -1;
            timedOut = true;
            destroyProcessForcibly(process);
            Thread.currentThread().interrupt();
        } finally {
            destroyProcessForcibly(process);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        return new ShellResult(stdout, stderr, exitCode, timedOut, executionTime);
    }

    /** Inject the guest environment that PRoot and the commands inside it rely on. */
    void environment(ProcessBuilder builder) {
        java.util.Map<String, String> env = builder.environment();
        env.put("LD_LIBRARY_PATH", nativeLibraryDir);
        env.put("HOME", "/root");
        env.put("PATH", PATH_GUEST);
        env.put("LANG", "C.UTF-8");
        env.put("TERM", "dumb");
        env.put("TMPDIR", "/tmp");
        env.put("PROOT_TMP_DIR", prootTmpDir.getAbsolutePath());
        // PRoot cannot execve anything without its loader: the compiled-in default points
        // at the Termux installation, which does not exist here. Both loaders are packaged
        // next to libproot.so and extracted into nativeLibraryDir by Android.
        env.put("PROOT_LOADER", new File(nativeLibraryDir, PROOT_LOADER).getAbsolutePath());
        env.put("PROOT_LOADER_32", new File(nativeLibraryDir, PROOT_LOADER32).getAbsolutePath());
        if (seccompDisabled) {
            env.put("PROOT_NO_SECCOMP", "1");
        }
    }

    private boolean waitForProcess(Process process, int timeoutSeconds)
            throws InterruptedException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } else {
            final boolean[] finished = {false};
            Thread processThread = new Thread(() -> {
                try {
                    process.waitFor();
                    finished[0] = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "distro-wait");
            processThread.start();
            processThread.join(timeoutSeconds * 1000L);
            if (finished[0]) {
                return true;
            } else {
                processThread.interrupt();
                return false;
            }
        }
    }

    private void destroyProcessForcibly(Process process) {
        if (process == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            process.destroy();
        }
    }

    @Override
    public void close() {
        // No persistent resources
    }

    // ==================== Stream gobbler ====================

    private static class StreamGobbler implements Runnable {
        private final java.io.InputStream inputStream;
        private final int maxSize;
        private static final int CHUNK_SIZE = 8192;
        private final StringBuilder output = new StringBuilder();
        private boolean truncated;

        StreamGobbler(java.io.InputStream inputStream, int maxSize) {
            this.inputStream = inputStream;
            this.maxSize = maxSize;
        }

        @Override
        public void run() {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(inputStream), CHUNK_SIZE)) {
                char[] buf = new char[CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = reader.read(buf)) != -1) {
                    if (output.length() + bytesRead > maxSize) {
                        int remaining = maxSize - output.length();
                        if (remaining > 0) {
                            output.append(buf, 0, remaining);
                        }
                        output.append("\n[Output truncated — size limit reached]");
                        truncated = true;
                        break;
                    }
                    if (output.length() > 0 && !truncated) {
                        output.append('\n');
                    }
                    output.append(buf, 0, bytesRead);
                }
            } catch (java.io.IOException e) {
                // Stream closed when process terminated — expected
            }
        }

        String getOutput() {
            return output.toString();
        }
    }
}
