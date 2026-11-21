package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DistroShellBackend} command construction: the single
 * {@code ... -w guestCwd -- /bin/sh -c <rawCommand>} proot argv (raw command verbatim, no
 * tokenisation artefacts), guest working-directory selection, the {@code -R} -> {@code -r}
 * fallback, and bind-failure detection. Real proot execution lives in the instrumented suite.
 */
public class DistroShellBackendTest {

    private File rootfs;
    private File workspace;
    private File filesDir;
    private DistroShellBackend backend;

    @Before
    public void setUp() {
        filesDir = new File(System.getProperty("java.io.tmpdir"), "distro_files_" + System.currentTimeMillis());
        filesDir.mkdirs();
        rootfs = new File(filesDir, "distro/ubuntu");
        workspace = new File(filesDir, "workspace");
        workspace.mkdirs();
        ShellConfig config = ShellConfig.createFull();
        backend = new DistroShellBackend(config, rootfs, workspace,
                new File(filesDir, "native").getAbsolutePath(), filesDir, true);
    }

    private ExecPlan plan(String rawCommand, File cwd) {
        return new ExecPlan(rawCommand, cwd);
    }

    @Test
    public void prootCommand_runsShCWithRawCommandVerbatim() {
        ExecPlan p = plan("echo a && echo b", workspace);

        List<String> cmd = backend.buildProotCommand(p, false);

        assertEquals(new File(filesDir, "native/libproot.so").getAbsolutePath(), cmd.get(0));
        assertEquals("-0", cmd.get(1));
        assertEquals("--kill-on-exit", cmd.get(2));
        assertEquals("-R", cmd.get(3));
        assertEquals(rootfs.getAbsolutePath(), cmd.get(4));
        assertEquals("-b", cmd.get(5));
        assertEquals(workspace.getAbsolutePath() + ":/workspace", cmd.get(6));
        assertEquals("-w", cmd.get(7));
        assertEquals("/workspace", cmd.get(8));
        // PRoot option parsing ends at "--", then the raw command runs through the guest shell.
        assertEquals("--", cmd.get(9));
        assertEquals("/bin/sh", cmd.get(10));
        assertEquals("-c", cmd.get(11));
        assertEquals("echo a && echo b", cmd.get(12));
        assertEquals(13, cmd.size());
    }

    @Test
    public void prootCommand_metacharsAreNotTokenised() {
        ExecPlan p = plan("printf 'x\ny' | grep y", workspace);

        List<String> cmd = backend.buildProotCommand(p, false);

        // The whole command is a single -c argument, metacharacters intact.
        int i = cmd.indexOf("-c");
        assertEquals("printf 'x\ny' | grep y", cmd.get(i + 1));
    }

    @Test
    public void environment_pointsProotAtPackagedLoaders() {
        ProcessBuilder builder = new ProcessBuilder("true");
        backend.environment(builder);

        String nativeLibDir = new File(filesDir, "native").getAbsolutePath();
        assertEquals(new File(nativeLibDir, "libproot-loader.so").getAbsolutePath(),
                builder.environment().get("PROOT_LOADER"));
        assertEquals(new File(nativeLibDir, "libproot-loader32.so").getAbsolutePath(),
                builder.environment().get("PROOT_LOADER_32"));
        assertEquals(nativeLibDir, builder.environment().get("LD_LIBRARY_PATH"));
    }

    @Test
    public void guestCwd_isWorkspaceWhenInsideWorkspace() {
        ExecPlan p = plan("echo hi", workspace);
        assertEquals("/workspace", backend.guestCwdFor(p));
    }

    @Test
    public void guestCwd_isRootWhenOutsideWorkspace() {
        File outside = new File(filesDir, "somewhere-else");
        ExecPlan p = plan("echo hi", outside);
        assertEquals("/root", backend.guestCwdFor(p));
    }

    @Test
    public void chrootFallback_switchesTo_rFlagAndExplicitBinds() {
        ExecPlan p = plan("echo hi", workspace);

        List<String> cmd = backend.buildProotCommand(p, true);

        assertEquals("-r", cmd.get(3));
        assertEquals(rootfs.getAbsolutePath(), cmd.get(4));
        // Explicit guest binds for the fallback, then the same sh -c tail.
        assertTrue(cmd.contains("/dev"));
        assertTrue(cmd.contains("/proc"));
        assertTrue(cmd.contains("/sys"));
        int shIdx = cmd.indexOf("/bin/sh");
        assertEquals("--", cmd.get(shIdx - 1));
        assertEquals("-c", cmd.get(shIdx + 1));
    }

    @Test
    public void looksLikeBindFailure_detectsMissingHostPath() {
        ShellResult bindError = new ShellResult("",
                "proot: bind(\"/workspace\"): No such file or directory", -1, false, 1);
        assertTrue(backend.looksLikeBindFailure(bindError));
    }

    @Test
    public void looksLikeBindFailure_ignoresNormalCommandFailure() {
        ShellResult normalFail = new ShellResult("", "ls: cannot access '/nope'", 2, false, 1);
        assertFalse(backend.looksLikeBindFailure(normalFail));
    }

    @Test
    public void looksLikeBindFailure_ignoresSuccess() {
        ShellResult ok = new ShellResult("out", "", 0, false, 1);
        assertFalse(backend.looksLikeBindFailure(ok));
    }
}
