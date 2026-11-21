package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ExecPlan} — hash stability, the raw command, and the
 * approval description. The plan pins everything the user approved: the raw command
 * (passed verbatim to {@code sh -c}) and the resolved working directory.
 */
public class ExecPlanTest {

    private static final File CWD = new File("/tmp");

    private static ExecPlan plan(String rawCommand) {
        return new ExecPlan(rawCommand, CWD);
    }

    @Test
    public void hash_isStableForIdenticalPlan() {
        ExecPlan p1 = plan("echo hello");
        ExecPlan p2 = plan("echo hello");
        assertEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenCommandChanges() {
        ExecPlan p1 = plan("echo hello");
        ExecPlan p2 = plan("echo world");
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenCwdChanges() {
        ExecPlan p1 = new ExecPlan("echo hello", new File("/tmp/a"));
        ExecPlan p2 = new ExecPlan("echo hello", new File("/tmp/b"));
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_isSha256Length() {
        ExecPlan p = plan("echo hello");
        assertEquals(64, p.getPlanHash().length());
    }

    @Test
    public void rawCommand_isExposedVerbatim() {
        ExecPlan p = plan("echo a && echo b");
        assertEquals("echo a && echo b", p.getRawCommand());
    }

    @Test
    public void approvalDescription_containsRawCommandAndCwdAndHashPrefix() {
        ExecPlan p = plan("echo a && echo b");
        String desc = p.toApprovalDescription();
        assertTrue(desc.contains("echo a && echo b"));
        assertTrue(desc.contains(CWD.getAbsolutePath()));
        assertTrue(desc.contains(p.getPlanHash().substring(0, 8)));
    }

    @Test
    public void computeHash_static_matchesInstanceHash() {
        ExecPlan p = plan("echo hello");
        String recomputed = ExecPlan.computeHash(p.getRawCommand(), p.getCwd());
        assertEquals(p.getPlanHash(), recomputed);
    }

    @Test
    public void equalsAndHashCode_areConsistent() {
        ExecPlan p1 = plan("echo hello");
        ExecPlan p2 = new ExecPlan("echo hello", new File("/tmp"));
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
