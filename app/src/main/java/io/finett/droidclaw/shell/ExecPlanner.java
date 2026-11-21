package io.finett.droidclaw.shell;

import java.io.File;
import java.io.IOException;

/**
 * Turns a raw command string into an immutable {@link ExecPlan}.
 *
 * <p>This is intentionally thin. It performs exactly two load-bearing steps:
 * <ol>
 *   <li><b>Working-directory guard</b> — the cwd is canonicalised and verified to be
 *       inside the workspace root (or equal to it). Canonical-path comparison also
 *       neutralises Android's {@code /data/user/0/<pkg>} / {@code /data/data/<pkg>}
 *       aliases, so a differently-spelled path cannot escape the boundary.</li>
 *   <li><b>Hash pinning</b> — the resulting plan carries a SHA-256 of {@code rawCommand}
 *       and {@code cwd} so the executor can detect substitution between approval time
 *       and run time.</li>
 * </ol>
 *
 * <p>There is no tokenisation, no shell-metacharacter policy, and no host-executable
 * resolution: the {@code rawCommand} is passed verbatim to {@code sh -c} by the shell
 * backends, so full shell syntax is honoured inside the Linux environment and the
 * approval modes (not argv curation) are the security gate.
 */
public class ExecPlanner {

    private final File workspaceRoot;

    public ExecPlanner(File workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Build an {@link ExecPlan} from a raw command string.
     *
     * @param rawCommand the command string, passed verbatim to {@code sh -c}
     * @param cwd        working directory; {@code null} means workspace root
     * @return normalised ExecPlan
     * @throws SecurityException if the cwd is outside the workspace, does not exist, or
     *                           is not a directory
     * @throws IOException       if canonical path resolution fails
     */
    public ExecPlan plan(String rawCommand, File cwd)
            throws SecurityException, IOException {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            throw new SecurityException("Command must not be empty");
        }

        File resolvedCwd = resolveCwd(cwd);
        return new ExecPlan(rawCommand, resolvedCwd);
    }

    /**
     * Validate and canonicalise the working directory. The cwd must be within the
     * workspace root (or equal to it) and must be an existing directory.
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
