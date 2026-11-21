package io.finett.droidclaw.shell;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Unit tests for the thin {@link ExecPlanner}: raw command passthrough, hash pinning, and
 * the canonical-path working-directory guard (workspace boundary + symlink-alias resolution).
 */
public class ExecPlannerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File workspace;
    private ExecPlanner planner;

    @Before
    public void setUp() throws Exception {
        workspace = temporaryFolder.newFolder("workspace");
        planner = new ExecPlanner(workspace);
    }

    @Test
    public void rawCommand_passedThroughVerbatim() throws Exception {
        ExecPlan p = planner.plan("echo a && echo b", workspace);
        assertEquals("echo a && echo b", p.getRawCommand());
        assertEquals(workspace.getCanonicalFile(), p.getCwd());
    }

    @Test
    public void hash_stableForSameInput() throws Exception {
        ExecPlan p1 = planner.plan("ls", workspace);
        ExecPlan p2 = planner.plan("ls", workspace);
        assertEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_changesWhenCommandChanges() throws Exception {
        ExecPlan p1 = planner.plan("ls", workspace);
        ExecPlan p2 = planner.plan("pwd", workspace);
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_changesWhenCwdChanges() throws Exception {
        File sub = new File(workspace, "sub");
        sub.mkdirs();
        ExecPlan p1 = planner.plan("ls", workspace);
        ExecPlan p2 = planner.plan("ls", sub);
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void resolveCwd_workspaceRootAllowed_whenCwdIsNull() throws Exception {
        ExecPlan p = planner.plan("ls", null);
        assertEquals(workspace.getCanonicalFile(), p.getCwd());
    }

    @Test
    public void resolveCwd_insideWorkspaceAllowed() throws Exception {
        File sub = new File(workspace, "sub");
        sub.mkdirs();
        ExecPlan p = planner.plan("ls", sub);
        assertEquals(sub.getCanonicalFile(), p.getCwd());
    }

    @Test(expected = SecurityException.class)
    public void resolveCwd_outsideWorkspaceRejected() throws Exception {
        File outside = new File(temporaryFolder.getRoot(), "outside");
        outside.mkdirs();
        planner.plan("ls", outside);
    }

    @Test(expected = SecurityException.class)
    public void resolveCwd_nonexistentRejected() throws Exception {
        planner.plan("ls", new File(workspace, "does_not_exist_12345"));
    }

    /**
     * Canonicalise a cwd spelled through a symlink alias of the workspace root. The guard
     * compares canonical paths, so the aliased spelling must resolve inside the workspace
     * (the same mechanism that neutralises Android's /data/user/0 vs /data/data aliases).
     */
    @Test
    public void resolveCwd_canonicalAliasInsideWorkspace_accepted() throws Exception {
        File alias = new File(temporaryFolder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), temporaryFolder.getRoot().toPath());
        File cwdViaAlias = new File(alias, "workspace/sub");
        cwdViaAlias.mkdirs();

        ExecPlan p = planner.plan("ls", cwdViaAlias);

        assertEquals("alias spelling must canonicalise inside the workspace",
                workspace.getCanonicalFile().getAbsolutePath() + File.separator + "sub",
                p.getCwd().getCanonicalFile().getAbsolutePath());
    }

    @Test(expected = SecurityException.class)
    public void resolveCwd_canonicalAliasOutsideWorkspace_rejected() throws Exception {
        File alias = new File(temporaryFolder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), temporaryFolder.getRoot().toPath());
        File cwdViaAlias = new File(alias, "elsewhere");
        cwdViaAlias.mkdirs();

        planner.plan("ls", cwdViaAlias);
    }

    @Test(expected = SecurityException.class)
    public void rejectsEmptyCommand() throws Exception {
        planner.plan("", workspace);
    }

    @Test(expected = SecurityException.class)
    public void rejectsWhitespaceOnlyCommand() throws Exception {
        planner.plan("   \n\t  ", workspace);
    }

    @Test(expected = SecurityException.class)
    public void rejectsNullCommand() throws Exception {
        planner.plan(null, workspace);
    }
}
