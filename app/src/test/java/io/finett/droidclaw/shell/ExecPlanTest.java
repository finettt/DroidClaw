package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ExecPlan} — hash stability, approval description, immutability.
 */
public class ExecPlanTest {

    private static final File CWD = new File("/tmp");

    private ExecPlan plan(String exe, String... argv) {
        return new ExecPlan(exe, Arrays.asList(argv), CWD, ExecPlan.ExecMode.DIRECT);
    }

    @Test
    public void hash_isStableForIdenticalPlan() {
        ExecPlan p1 = plan("/system/bin/ls", "-l");
        ExecPlan p2 = plan("/system/bin/ls", "-l");
        assertEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenExeChanges() {
        ExecPlan p1 = plan("/system/bin/ls");
        ExecPlan p2 = plan("/system/bin/cat");
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenArgvChanges() {
        ExecPlan p1 = plan("/system/bin/ls", "-l");
        ExecPlan p2 = plan("/system/bin/ls", "-a");
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenArgvOrderChanges() {
        ExecPlan p1 = plan("/system/bin/ls", "-l", "-a");
        ExecPlan p2 = plan("/system/bin/ls", "-a", "-l");
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenCwdChanges() {
        ExecPlan p1 = new ExecPlan("/system/bin/ls", Collections.emptyList(),
                new File("/tmp/a"), ExecPlan.ExecMode.DIRECT);
        ExecPlan p2 = new ExecPlan("/system/bin/ls", Collections.emptyList(),
                new File("/tmp/b"), ExecPlan.ExecMode.DIRECT);
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_differsWhenModeChanges() {
        ExecPlan p1 = new ExecPlan("/system/bin/ls", Collections.emptyList(),
                CWD, ExecPlan.ExecMode.DIRECT);
        ExecPlan p2 = new ExecPlan("/system/bin/ls", Collections.emptyList(),
                CWD, ExecPlan.ExecMode.SHELL);
        assertNotEquals(p1.getPlanHash(), p2.getPlanHash());
    }

    @Test
    public void hash_isSha256Length() {
        ExecPlan p = plan("/system/bin/ls");
        assertEquals(64, p.getPlanHash().length());
    }

    @Test
    public void argv_isUnmodifiable() {
        ExecPlan p = plan("/system/bin/ls", "-l");
        try {
            p.getArgv().add("-a");
            fail("argv should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void approvalDescription_containsExeAndArgvAndCwd() {
        ExecPlan p = plan("/system/bin/ls", "-l", "/home");
        String desc = p.toApprovalDescription();

        assertTrue(desc.contains("/system/bin/ls"));
        assertTrue(desc.contains("-l"));
        assertTrue(desc.contains("/home"));
        assertTrue(desc.contains(CWD.getAbsolutePath()));
        assertTrue(desc.contains("DIRECT"));
    }

    @Test
    public void approvalDescription_containsPlanHashPrefix() {
        ExecPlan p = plan("/system/bin/ls");
        String desc = p.toApprovalDescription();

        assertTrue(desc.contains(p.getPlanHash().substring(0, 8)));
    }

    @Test
    public void computeHash_static_matchesInstanceHash() {
        ExecPlan p = plan("/system/bin/ls", "-l");
        String recomputed = ExecPlan.computeHash(
                p.getCanonicalExePath(),
                p.getArgv(),
                p.getCwd(),
                p.getMode()
        );
        assertEquals(p.getPlanHash(), recomputed);
    }
}