package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DistroShellBackend} command construction — the proot argument
 * vector, guest working-directory selection, the -R -> -r fallback, and bind-failure
 * detection. Real proot execution is covered by the instrumented e2e suite.
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

    private ExecPlan plan(String exe, ExecPlan.ExecMode mode, File cwd, String... argv) {
        return new ExecPlan(exe, Arrays.asList(argv), cwd, mode);
    }

    @Test
    public void directMode_buildsProotCommandWithResolvedExe() {
        File exe = new File(rootfs, "usr/bin/echo");
        String nativeLibDir = new File(filesDir, "native").getAbsolutePath();
        ExecPlan p = plan(exe.getAbsolutePath(), ExecPlan.ExecMode.DIRECT, workspace, "hello");

        List<String> cmd = backend.buildProotCommand(p, false);

        assertEquals(new File(nativeLibDir, "libproot.so").getAbsolutePath(), cmd.get(0));
        assertEquals("-0", cmd.get(1));
        assertEquals("--kill-on-exit", cmd.get(2));
        assertEquals("-R", cmd.get(3));
        assertEquals(rootfs.getAbsolutePath(), cmd.get(4));
        assertEquals("-b", cmd.get(5));
        assertEquals(workspace.getAbsolutePath() + ":/workspace", cmd.get(6));
        assertEquals("-w", cmd.get(7));
        assertEquals("/workspace", cmd.get(8));
        // The exe is the guest path (relative to the rootfs); libproot stops option
        // parsing at it, so no "--" separator is used (the packaged proot rejects it).
        assertEquals("/usr/bin/echo", cmd.get(9));
        assertEquals("hello", cmd.get(10));
        assertEquals(11, cmd.size());
    }

    @Test
    public void shellMode_runsGuestShWithJoinedCommand() {
        File sh = new File(rootfs, "bin/sh");
        ExecPlan p = plan(sh.getAbsolutePath(), ExecPlan.ExecMode.SHELL, workspace);

        List<String> cmd = backend.buildProotCommand(p, false);

        // -w /workspace /bin/sh -c "<joined guest shell + argv>" (no "--" separator)
        assertEquals("-w", cmd.get(7));
        assertEquals("/workspace", cmd.get(8));
        assertEquals("/bin/sh", cmd.get(9));
        assertEquals("-c", cmd.get(10));
        assertTrue(cmd.get(11).startsWith("/bin/sh"));
    }

    @Test
    public void guestCwd_isWorkspaceWhenInsideWorkspace() {
        ExecPlan p = plan("/usr/bin/echo", ExecPlan.ExecMode.DIRECT, workspace, "hi");
        assertEquals("/workspace", backend.guestCwdFor(p));
    }

    @Test
    public void guestCwd_isRootWhenOutsideWorkspace() {
        File outside = new File(filesDir, "somewhere-else");
        ExecPlan p = plan("/usr/bin/echo", ExecPlan.ExecMode.DIRECT, outside, "hi");
        assertEquals("/root", backend.guestCwdFor(p));
    }

    @Test
    public void chrootFallback_switchesTo_rFlagAndExplicitBinds() {
        ExecPlan p = plan("/usr/bin/echo", ExecPlan.ExecMode.DIRECT, workspace, "hi");

        List<String> cmd = backend.buildProotCommand(p, true);

        assertEquals("-r", cmd.get(3));
        assertEquals(rootfs.getAbsolutePath(), cmd.get(4));
        // Explicit guest binds for the fallback.
        assertTrue(cmd.contains("/dev"));
        assertTrue(cmd.contains("/proc"));
        assertTrue(cmd.contains("/sys"));
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
