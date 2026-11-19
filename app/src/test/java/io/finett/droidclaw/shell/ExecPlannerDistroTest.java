package io.finett.droidclaw.shell;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.finett.droidclaw.shell.distro.DistroSpec;

/**
 * Guest-path resolution tests for the distro planner. Host paths must be rejected even when
 * spelled through a symlink alias of the application directory: Android exposes the same
 * directory as both {@code /data/data/<pkg>} and {@code /data/user/0/<pkg>}.
 */
public class ExecPlannerDistroTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File realHome;
    private File workspace;
    private File distroRoot;
    private ExecPlanner planner;

    @Before
    public void setUp() throws Exception {
        realHome = temporaryFolder.newFolder("real");
        distroRoot = new File(realHome, "files/distro/ubuntu");
        assertTrue(distroRoot.mkdirs());
        assertTrue(new File(distroRoot, DistroSpec.MARKER_FILE).createNewFile());
        workspace = new File(realHome, "files/workspace");
        assertTrue(workspace.mkdirs());
        planner = new ExecPlanner(ShellConfig.createDefault(), workspace, distroRoot);
    }

    @Test
    public void rejectsHostPathInsideFilesDir() throws Exception {
        assertHostPathRejected(new File(realHome, "files/leak"));
    }

    @Test
    public void rejectsHostPathInsideWorkspace() throws Exception {
        assertHostPathRejected(new File(workspace, "leak"));
    }

    @Test
    public void rejectsHostPathSpelledThroughSymlinkAlias() throws Exception {
        File alias = new File(temporaryFolder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), realHome.toPath());
        assertHostPathRejected(new File(alias, "files/leak"));
    }

    private void assertHostPathRejected(File path) throws Exception {
        try {
            planner.plan(path.getAbsolutePath(), workspace, ExecPlan.ExecMode.DIRECT);
            fail("Expected a host path to be rejected: " + path);
        } catch (SecurityException expected) {
            assertTrue("Unexpected message: " + expected.getMessage(),
                    expected.getMessage().contains("Host path"));
        }
    }
}
