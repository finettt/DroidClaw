package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link DistroDiskSpace}: byte<->MB math and the two space
 * gates used to protect the advisory install quota.
 */
public class DistroDiskSpaceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long MB = 1024L * 1024L;

    @Test
    public void megabytes_roundsDown() {
        assertEquals(1500, DistroDiskSpace.megabytes(1500L * MB));
        assertEquals(1499, DistroDiskSpace.megabytes(1500L * MB - 1));
    }

    @Test
    public void assertBootstrapFree_allowsEnoughSpace() throws Exception {
        // limit 2048 MB + tarball 30 MB + margin 256 MB ~ 2334 MB needed; 3000 MB free ok.
        DistroDiskSpace.assertBootstrapFree(3000 * MB, 2048 * MB, 30 * MB, 256 * MB);
    }

    @Test
    public void assertBootstrapFree_failsWhenInsufficient() {
        try {
            DistroDiskSpace.assertBootstrapFree(100 * MB, 2048 * MB, 30 * MB, 256 * MB);
            fail("expected DistroException on insufficient space");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("disk space"));
            assertTrue(expected.getMessage().contains("lower the Linux disk limit"));
        }
    }

    @Test
    public void assertWithinLimit_allowsUnderLimit() throws Exception {
        DistroDiskSpace.assertWithinLimit(1000 * MB, 2048 * MB);
    }

    @Test
    public void assertWithinLimit_failsOverLimit() {
        try {
            DistroDiskSpace.assertWithinLimit(4096 * MB, 2048 * MB);
            fail("expected DistroException on over-limit image");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("disk limit"));
        }
    }

    @Test
    public void usedBytes_sumsFilesRecursively() throws IOException {
        File root = tmp.newFolder("distro");
        File sub = new File(root, "ubuntu");
        assertTrue(sub.mkdir());
        File big = new File(sub, "usr/lib/big.so");
        assertTrue(big.getParentFile().mkdirs());
        Files.write(big.toPath(), new byte[(int) (5 * MB)]);
        File small = new File(sub, "usr/bin/small");
        assertTrue(small.getParentFile().mkdirs());
        Files.write(small.toPath(), "hi".getBytes(StandardCharsets.UTF_8));

        long used = DistroDiskSpace.usedBytes(root);

        assertEquals(5 * MB + 2, used);
    }

    @Test
    public void usedBytes_doesNotFollowSymlinks() throws Exception {
        File outside = tmp.newFolder("outside");
        File outsideFile = new File(outside, "large");
        Files.write(outsideFile.toPath(), new byte[(int) (5 * MB)]);
        File root = tmp.newFolder("distro");
        Files.write(new File(root, "real").toPath(), "hi".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(root, "outside-file").toPath(), outsideFile.toPath());
        Files.createSymbolicLink(new File(root, "outside-dir").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "duplicate").toPath(), Paths.get("real"));

        assertEquals(2L, DistroDiskSpace.usedBytes(root));
    }

    @Test
    public void usedBytes_zeroWhenMissing() throws Exception {
        assertEquals(0L, DistroDiskSpace.usedBytes(new File("/no/such/dir")));
    }

    @Test
    public void freeBytes_neverNegative() {
        assertTrue(DistroDiskSpace.freeBytes(tmp.getRoot()) >= 0L);
    }
}
