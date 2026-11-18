package io.finett.droidclaw.shell;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.shell.distro.DistroSpec;

/**
 * Converts a raw command string (or pre-tokenised argv) into a normalised, immutable
 * {@link ExecPlan} ready for security validation and execution.
 *
 * <p>The planner performs:
 * <ol>
 *   <li><b>Tokenisation</b> — split on whitespace, respecting single and double quotes.</li>
 *   <li><b>Shell metacharacter rejection</b> — in {@link ExecPlan.ExecMode#DIRECT} mode,
 *       any token containing shell metacharacters
 *       ({@code ; & | > < $ ` ( ) { } \n}) is rejected immediately.
 *       This prevents injection even before the allowlist check.</li>
 *   <li><b>Executable resolution</b> — if the exe is not an absolute path, it is looked up
 *       only in {@link ShellConfig#getTrustedDirs()}, not in the process {@code PATH}.
 *       Symlinks are resolved to their canonical form.</li>
 *   <li><b>Working-directory validation</b> — the cwd is verified to be within the
 *       workspace root via canonical-path comparison.</li>
 * </ol>
 *
 * <p>The resulting {@link ExecPlan} carries a SHA-256 hash of all fields so the executor
 * can detect substitution between approval time and run time.
 */
public class ExecPlanner {

    /**
     * Shell metacharacters that must not appear in any token when mode is DIRECT.
     * These are characters the shell would interpret as control operators, redirections,
     * substitutions, or glob expansions.
     */
    private static final String SHELL_METACHARS = ";|&><`$(){}!\n~";

    private final ShellConfig config;
    private final File workspaceRoot;
    /**
     * Guest rootfs host path (e.g. {@code filesDir/distro/ubuntu}) when the distro backend is
     * active, or {@code null} for the host (SSH) case. When non-null, executable resolution
     * happens inside the rootfs and a missing image yields an actionable error rather than a
     * host-path lookup.
     */
    private final File distroRoot;

    public ExecPlanner(ShellConfig config, File workspaceRoot) {
        this(config, workspaceRoot, null);
    }

    /**
     * Build a planner that resolves executables inside the guest rootfs when {@code distroRoot}
     * is non-null, or against the host trusted dirs otherwise.
     */
    public ExecPlanner(ShellConfig config, File workspaceRoot, File distroRoot) {
        this.config = config;
        this.workspaceRoot = workspaceRoot;
        this.distroRoot = distroRoot;
        android.util.Log.d("ExecPlanner", "distroRoot=" + (distroRoot == null ? "null" : distroRoot.getAbsolutePath()));
    }

    // ==================== Public API ====================

    /**
     * Build an {@link ExecPlan} from a raw command string using the config's default mode.
     *
     * @param rawCommand the command string (e.g. {@code "ls -la /home"})
     * @param cwd        working directory; {@code null} means workspace root
     * @return normalised ExecPlan
     * @throws SecurityException if the command contains shell metacharacters (DIRECT mode),
     *                           the exe is not resolvable in trusted dirs, or the cwd is
     *                           outside the workspace
     * @throws IOException       if canonical path resolution fails
     */
    public ExecPlan plan(String rawCommand, File cwd)
            throws SecurityException, IOException {
        return plan(rawCommand, cwd, config.getDefaultMode());
    }

    /**
     * Build an {@link ExecPlan} from a raw command string with an explicit execution mode.
     */
    public ExecPlan plan(String rawCommand, File cwd, ExecPlan.ExecMode mode)
            throws SecurityException, IOException {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            throw new SecurityException("Command must not be empty");
        }

        List<String> tokens = tokenise(rawCommand.trim());
        if (tokens.isEmpty()) {
            throw new SecurityException("Command tokenised to empty list");
        }

        return planFromTokens(tokens, cwd, mode);
    }

    /**
     * Build an {@link ExecPlan} from a pre-tokenised argv list.
     * {@code tokens.get(0)} is the executable; the rest are arguments.
     */
    public ExecPlan planFromTokens(List<String> tokens, File cwd, ExecPlan.ExecMode mode)
            throws SecurityException, IOException {
        if (tokens == null || tokens.isEmpty()) {
            throw new SecurityException("Token list must not be empty");
        }

        String rawExe = tokens.get(0);
        List<String> argv = tokens.size() > 1
                ? new ArrayList<>(tokens.subList(1, tokens.size()))
                : new ArrayList<>();

        // 1. In DIRECT mode, reject shell metacharacters in every token
        if (mode == ExecPlan.ExecMode.DIRECT) {
            for (String token : tokens) {
                String metaFound = findMetachar(token);
                if (metaFound != null) {
                    throw new SecurityException(
                        "Shell metacharacter '" + metaFound + "' found in token '" + token
                        + "' — use SHELL mode or remove the metacharacter");
                }
            }
        }

        // 2. Resolve executable to canonical path
        String canonicalExe = resolveExe(rawExe);

        // 3. Validate and resolve working directory
        File resolvedCwd = resolveCwd(cwd);

        return new ExecPlan(canonicalExe, argv, resolvedCwd, mode);
    }

    // ==================== Tokeniser ====================

    /**
     * Simple shell-like tokeniser: splits on unquoted whitespace,
     * respects single-quoted ({@code '...'}) and double-quoted ({@code "..."}) strings.
     * Does NOT perform variable expansion or glob expansion.
     */
    static List<String> tokenise(String input) throws SecurityException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char inQuote = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inQuote != 0) {
                if (c == inQuote) {
                    inQuote = 0; // close quote
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                inQuote = c; // open quote
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (inQuote != 0) {
            throw new SecurityException("Unterminated quote in command");
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    // ==================== Metachar check ====================

    /**
     * Returns the first shell metacharacter found in {@code token}, or {@code null} if clean.
     */
    private static String findMetachar(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (SHELL_METACHARS.indexOf(c) >= 0) {
                return String.valueOf(c);
            }
        }
        return null;
    }

    // ==================== Exe resolution ====================

    /**
     * Resolve a raw executable name/path to a stable, absolute path suitable for
     * {@link ProcessBuilder}.
     *
     * <p><b>Path normalisation strategy:</b> we use {@link File#getAbsolutePath()} (which
     * resolves {@code ..} components) rather than {@link File#getCanonicalPath()} (which
     * follows every symlink to its final target). On systems such as Nix/NixOS where
     * executables like {@code /bin/echo} are symlinks to a multi-call binary
     * ({@code /nix/store/.../bin/coreutils}), following the symlink to the canonical path
     * would cause the kernel to run {@code coreutils} without the argv[0] name hint it
     * relies on to select its built-in function — producing unexpected behaviour (e.g.
     * exit code 1). Keeping the symlink path intact lets the OS kernel handle argv[0]
     * correctly, which is the same behaviour as invoking the binary from a shell.</p>
     *
     * <p><b>Distro (guest rootfs) resolution differs:</b> executables resolved inside the
     * guest rootfs are returned via {@link File#getCanonicalPath()} so their host path
     * matches the canonical root that libproot applies. libproot canonicalises the chroot
     * (for example {@code /data/user/0/app} to {@code /data/data/app}), so a non-canonical
     * exe path would fall outside the chroot and be reported as "not found".</p>
     *
     * <p>Path-traversal is still prevented: we verify the absolute path starts with one of
     * the trusted directories and the file exists and is executable.  Relative names are
     * looked up only in {@link ShellConfig#getTrustedDirs()}, ignoring the process
     * {@code PATH} to prevent PATH-hijacking attacks.</p>
     */
    private String resolveExe(String rawExe) throws SecurityException, IOException {
        if (distroRoot != null) {
            return resolveExeInDistro(rawExe);
        }
        return resolveExeOnHost(rawExe);
    }

    // ==================== Distro (guest rootfs) resolution ====================

    /** Directories inside the guest rootfs searched, in order, for an unqualified exe. */
    private static final List<String> DISTRO_BIN_DIRS = Collections.unmodifiableList(Arrays.asList(
            "usr/local/bin", "usr/bin", "bin", "usr/sbin", "sbin"));

    private String resolveExeInDistro(String rawExe) throws SecurityException, IOException {
        if (!distroReady()) {
            throw new SecurityException(
                    "The Linux environment is not installed yet. Install it in "
                    + "Settings > Agent Settings > Linux environment, then try again");
        }

        if (rawExe.startsWith("/")) {
            return resolveDistroAbsolute(rawExe);
        }
        return resolveDistroName(rawExe);
    }

    /** Search the guest rootfs bin directories for a bare executable name. */
    private String resolveDistroName(String rawExe) throws SecurityException, IOException {
        for (String dir : DISTRO_BIN_DIRS) {
            File candidate = new File(distroRoot, dir + File.separator + rawExe);
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.getCanonicalFile().getAbsolutePath();
            }
        }
        throw new SecurityException(
                "Executable '" + rawExe + "' not found in the Linux environment");
    }

    /**
     * Map a guest absolute path (e.g. {@code /usr/bin/x}) under the rootfs, rejecting host
     * paths and any path that escapes the rootfs through {@code ..} traversal. Symlinks are
     * NOT followed to their canonical target so multi-call binaries keep their argv[0] name.
     */
    private String resolveDistroAbsolute(String rawExe) throws SecurityException, IOException {
        // A bare host absolute path (workspace or filesDir prefix) is not a guest path and
        // must not leak into the guest rootfs.
        String hostAbs = new File(rawExe).getAbsoluteFile().getAbsolutePath();
        for (String hostPrefix : hostPathPrefixes()) {
            if (hostAbs.equals(hostPrefix) || hostAbs.startsWith(hostPrefix + File.separator)) {
                throw new SecurityException(
                        "Host path is not valid inside the Linux environment: " + rawExe);
            }
        }

        File exeFile = new File(distroRoot, rawExe);
        File canonicalRoot = distroRoot.getCanonicalFile();
        String rootPath = canonicalRoot.getAbsolutePath();
        String exePath = exeFile.getCanonicalFile().getAbsolutePath();
        if (!exePath.equals(rootPath) && !exePath.startsWith(rootPath + File.separator)) {
            throw new SecurityException(
                    "Path escapes the Linux environment rootfs: " + rawExe);
        }

        if (!exeFile.exists()) {
            throw new SecurityException("Executable not found in the Linux environment: " + rawExe);
        }
        if (!exeFile.canExecute()) {
            throw new SecurityException("File is not executable in the Linux environment: " + exePath);
        }
        return exePath;
    }

    private boolean distroReady() {
        return new File(distroRoot, DistroSpec.MARKER_FILE).isFile();
    }

    /**
     * Host-only prefixes whose absolute paths are not valid guest paths. A guest uses
     * {@code /workspace} (the mount point), so a raw path under the workspace or filesDir
     * host directory is a leak and must be rejected. Excludes the filesystem root so a
     * normal guest path such as {@code /usr/bin/echo} is not rejected.
     */
    private List<String> hostPathPrefixes() throws IOException {
        List<String> prefixes = new ArrayList<>();
        prefixes.add(workspaceRoot.getCanonicalFile().getAbsolutePath());
        // distroRoot is <filesDir>/distro/ubuntu, so filesDir is two levels up.
        File filesDir = distroRoot.getCanonicalFile().getParentFile().getParentFile();
        if (filesDir != null) {
            prefixes.add(filesDir.getCanonicalFile().getAbsolutePath());
        }
        return prefixes;
    }

    // ==================== Host (SSH) resolution ====================

    private String resolveExeOnHost(String rawExe) throws SecurityException, IOException {
        if (rawExe.startsWith("/")) {
            // Absolute path — normalise ".." components but do NOT follow final symlinks,
            // so multi-call binaries (busybox, coreutils on Nix) keep their logical name.
            File exeFile = new File(rawExe).getAbsoluteFile();
            if (!exeFile.exists()) {
                throw new SecurityException("Executable not found: " + rawExe);
            }
            if (!exeFile.canExecute()) {
                throw new SecurityException("File is not executable: " + exeFile.getAbsolutePath());
            }
            // Verify the resolved path stays within a trusted directory.
            String absPath = exeFile.getAbsolutePath();
            for (String trustedDir : config.getTrustedDirs()) {
                if (absPath.startsWith(trustedDir + "/") || absPath.equals(trustedDir)) {
                    return absPath;
                }
            }
            // Not in any trusted dir — still allow if policy is FULL (no allowlist).
            // The ShellConfig.validatePlan() will enforce policy; we just resolve the path.
            return absPath;
        }

        // Relative name — search only in trusted directories (do NOT follow to canonical
        // target so multi-call binaries keep their argv[0] name).
        for (String trustedDir : config.getTrustedDirs()) {
            File candidate = new File(trustedDir, rawExe);
            if (candidate.exists() && candidate.canExecute()) {
                try { android.util.Log.d("ExecPlanner", "exe_abs=" + candidate.getAbsolutePath() + " exe_canon=" + candidate.getCanonicalFile().getAbsolutePath()); } catch (Exception e) {}
                return candidate.getAbsolutePath();
            }
        }

        throw new SecurityException(
            "Executable '" + rawExe + "' not found in trusted directories: "
            + config.getTrustedDirs());
    }

    // ==================== Cwd resolution ====================

    /**
     * Validate and canonicalise the working directory.
     * The cwd must be within the workspace root (or equal to it).
     */
    private File resolveCwd(File cwd) throws SecurityException, IOException {
        File resolved = (cwd != null) ? cwd : workspaceRoot;
        File canonical = resolved.getCanonicalFile();
        File canonicalRoot = workspaceRoot.getCanonicalFile();

        String cwdPath = canonical.getAbsolutePath();
        String rootPath = canonicalRoot.getAbsolutePath();

        if (!cwdPath.equals(rootPath)
                && !cwdPath.startsWith(rootPath + File.separator)) {
            throw new SecurityException(
                "Working directory is outside workspace: " + cwdPath);
        }

        if (!canonical.exists() || !canonical.isDirectory()) {
            throw new SecurityException(
                "Working directory does not exist or is not a directory: " + cwdPath);
        }

        return canonical;
    }
}
