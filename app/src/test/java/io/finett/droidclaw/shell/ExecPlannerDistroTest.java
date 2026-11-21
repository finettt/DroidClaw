package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Working-directory guard tests that mirror Android exposing the app directory through two
 * aliases (the production {@code /data/user/0/<pkg>} and {@code /data/data/<pkg>}). A cwd
 * spelled through the alias must canonicalise to the same path as the canonical spelling.
 */
public class ExecPlannerDistroTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File realHome;
    private File workspace;
    private ExecPlanner planner;

    @Before
    public void setUp() throws Exception {
        realHome = temporaryFolder.newFolder("real");
        workspace = new File(realHome, "files/workspace");
        assertTrue(workspace.mkdirs());
        planner = new ExecPlanner(workspace);
    }

    @Test
    public void cwdSpelledThroughSymlinkAlias_canonicalisesInsideWorkspace() throws Exception {
        File alias = new File(temporaryFolder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), realHome.toPath());
        File cwdViaAlias = new File(alias, "files/workspace/sub");
        cwdViaAlias.mkdirs();

        ExecPlan p = planner.plan("ls", cwdViaAlias);

        assertEquals(workspace.getCanonicalFile().getAbsolutePath() + File.separator + "sub",
                p.getCwd().getCanonicalFile().getAbsolutePath());
    }

    @Test
    public void cwdSpelledThroughSymlinkAliasOutsideWorkspace_rejected() throws Exception {
        File alias = new File(temporaryFolder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), realHome.toPath());
        File cwdViaAlias = new File(alias, "files/elsewhere");
        cwdViaAlias.mkdirs();

        try {
            planner.plan("ls", cwdViaAlias);
            fail("Expected the outside-workspace cwd to be rejected");
        } catch (SecurityException expected) {
            assertTrue("Unexpected message: " + expected.getMessage(),
                    expected.getMessage().contains("outside workspace"));
        }
    }
}
