package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import io.finett.droidclaw.shell.distro.DistroManager.State;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * State-machine tests for {@link DistroManager} with an injected fake
 * {@link DistroFetcher} and a controlled free-space provider. Covers success,
 * size and checksum mismatches, network and space failures, post-install limit
 * enforcement, {@link #deleteAll()}, and the disk guard.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30) // API 30 (>= O): exercises the NIO filesystem path.
public class DistroManagerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long HUGE_FREE = 100L * 1024L * 1024L * 1024L; // 100 GB

    /** A {@link DistroFetcher} that streams canned bytes, throws, or reports a 404. */
    private static final class FakeFetcher implements DistroFetcher {
        final byte[] bytes;
        final DistroException error;

        FakeFetcher(byte[] bytes) {
            this.bytes = bytes;
            this.error = null;
        }

        FakeFetcher(DistroException error) {
            this.bytes = null;
            this.error = error;
        }

        @Override
        public long download(String url, OutputStream out, ProgressListener listener)
                throws DistroException, IOException {
            if (error != null) {
                throw error;
            }
            if (listener != null) {
                listener.onProgress(0, bytes.length);
            }
            out.write(bytes);
            return bytes.length;
        }
    }

    private DistroSpec.Entry entryFor(byte[] image) throws Exception {
        return new DistroSpec.Entry("http://fake/ubuntu.tar.gz", sha256Hex(image), image.length);
    }

    private DistroManager manager(File filesDir, DistroFetcher fetcher, int limitMb)
            throws Exception {
        return new DistroManager(
                (Context) null, filesDir, entryFor(TestTarballs.image()), fetcher,
                limitMb, dir -> HUGE_FREE);
    }

    /** Result of a bootstrap run, collected off the executor thread. */
    private static final class Outcome {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<State> state = new AtomicReference<>();
        final AtomicReference<DistroException> error = new AtomicReference<>();

        final DistroManager.Listener listener = new DistroManager.Listener() {
            @Override
            public void onStateChange(State s) {
                state.set(s);
                if (s == State.READY || s == State.FAILED) {
                    latch.countDown();
                }
            }

            @Override
            public void onProgress(long bytesSoFar, long total) {
                // ignored
            }

            @Override
            public void onError(DistroException e) {
                error.set(e);
            }
        };

        State await(long seconds) throws InterruptedException {
            assertTrue("bootstrap did not settle in " + seconds + "s",
                    latch.await(seconds, TimeUnit.SECONDS));
            return state.get();
        }
    }

    @Test
    public void bootstrap_success_reachesReadyAndWritesFinalize() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir, new FakeFetcher(TestTarballs.image()), 2048);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);
        assertEquals(State.READY, o.await(30));

        File image = new File(filesDir, "distro/ubuntu");
        assertTrue("marker file written", new File(image, DistroSpec.MARKER_FILE).isFile());
        assertTrue("resolv.conf written by finalize",
                new File(image, "etc/resolv.conf").isFile());
        assertTrue("sources file present",
                new File(image, "etc/apt/sources.list.d/ubuntu.sources").isFile());
        assertTrue("image reported installed", mgr.isInstalled());
    }

    @Test
    public void bootstrap_sizeMismatch_failsAndRemovesPartial() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = new DistroManager(
                (Context) null, filesDir,
                new DistroSpec.Entry("http://fake/ubuntu.tar.gz", sha256Hex(TestTarballs.image()),
                        TestTarballs.image().length + 1),
                new FakeFetcher(TestTarballs.image()), 2048, dir -> HUGE_FREE);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);

        assertEquals(State.FAILED, o.await(30));
        assertTrue("failure cites size mismatch",
                mgr.getError() != null && mgr.getError().contains("size mismatch"));
        assertFalse("size-mismatched install removed", new File(filesDir, "distro").exists());
    }

    @Test
    public void bootstrap_shaMismatch_failsAndRemovesPartial() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = new DistroManager(
                (Context) null, filesDir,
                new DistroSpec.Entry("http://fake/ubuntu.tar.gz", "0000", TestTarballs.image().length),
                new FakeFetcher(TestTarballs.image()), 2048, dir -> HUGE_FREE);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);
        assertEquals(State.FAILED, o.await(30));

        assertTrue("error surfaced", mgr.getError() != null);
        assertFalse("partial install removed", new File(filesDir, "distro").exists());
    }

    @Test
    public void bootstrap_networkFailure_failsAndRemovesPartial() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir,
                new FakeFetcher(new DistroException("connection refused")), 2048);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);
        assertEquals(State.FAILED, o.await(30));

        assertFalse("partial install removed on network failure",
                new File(filesDir, "distro").exists());
    }

    @Test
    public void bootstrap_insufficientSpace_failsBeforeInstall() throws Exception {
        File filesDir = tmp.getRoot();
        // Provider reports zero free space, so the bootstrap gate fails first.
        DistroManager mgr = new DistroManager(
                (Context) null, filesDir, entryFor(TestTarballs.image()),
                new FakeFetcher(TestTarballs.image()), 2048, dir -> 0L);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);
        assertEquals(State.FAILED, o.await(30));
        assertTrue("failure cites disk space",
                (mgr.getError() != null && mgr.getError().contains("disk space")));
    }

    @Test
    public void deleteAll_removesImageAndResetsState() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir, new FakeFetcher(TestTarballs.image()), 2048);
        Outcome o = new Outcome();
        mgr.bootstrap(o.listener);
        o.await(30);
        assertEquals(State.READY, mgr.getState());

        mgr.deleteAll();

        assertEquals(State.NOT_INSTALLED, mgr.getState());
        assertFalse("distro dir removed", new File(filesDir, "distro").exists());
    }

    @Test
    public void deleteAll_unlinksSymlinksWithoutDeletingTheirTargets() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir, new FakeFetcher(TestTarballs.image()), 2048);
        Outcome o = new Outcome();
        mgr.bootstrap(o.listener);
        assertEquals(State.READY, o.await(30));
        File outside = tmp.newFolder("outside");
        File kept = new File(outside, "keep.txt");
        Files.write(kept.toPath(), new byte[] {1});
        Files.createSymbolicLink(new File(filesDir, "distro/ubuntu/host-link").toPath(),
                outside.toPath());

        mgr.deleteAll();

        assertTrue("symlink target must survive distro cleanup", kept.isFile());
    }

    @Test
    public void bootstrap_overLimitAfterInstall_failsAndCleansUp() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir, new FakeFetcher(TestTarballs.image()), 0);
        Outcome o = new Outcome();

        mgr.bootstrap(o.listener);

        assertEquals(State.FAILED, o.await(30));
        assertTrue("failure cites disk limit",
                mgr.getError() != null && mgr.getError().contains("disk limit"));
        assertFalse("over-limit install removed", new File(filesDir, "distro").exists());
    }

    @Test
    public void guardWithinLimit_allowsUnderLimitAndFailsOver() throws Exception {
        File filesDir = tmp.getRoot();
        DistroManager mgr = manager(filesDir, new FakeFetcher(TestTarballs.image()), 2048);
        Outcome o = new Outcome();
        mgr.bootstrap(o.listener);
        o.await(30);

        // Image is well under the 2048 MB limit; guard must allow it.
        mgr.guardWithinLimit();

        // Grow the image past the limit, then the guard must reject it.
        File grown = new File(filesDir, "distro/ubuntu/usr/lib/grown.so");
        grown.getParentFile().mkdirs();
        growFile(grown, 2100L * 1024L * 1024L);
        try {
            mgr.guardWithinLimit();
            fail("expected DistroException when over the disk limit");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("disk limit"));
        }
    }

    private static void growFile(File f, long bytes) throws IOException {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            byte[] chunk = new byte[(int) (1024L * 1024L)];
            long written = 0;
            while (written < bytes) {
                fos.write(chunk);
                written += chunk.length;
            }
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
