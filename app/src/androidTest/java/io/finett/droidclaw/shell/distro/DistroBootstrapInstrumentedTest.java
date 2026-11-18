package io.finett.droidclaw.shell.distro;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.util.Flaky;
import io.finett.droidclaw.util.FlakyTestRule;

/**
 * Instrumented test for the full distro bootstrap pipeline on a real device or
 * emulator. It uses the production {@link OkHttpDistroFetcher} to download the
 * pinned Ubuntu base image, verifies its SHA-256, unpacks it, and finalizes the
 * image, then asserts the on-disk layout matches what {@link DistroManager}
 * promises: the ready marker, {@code etc/os-release}, {@code etc/resolv.conf},
 * and the apt sources file.
 *
 * <p>The download, checksum verification, and unpack depend on network and disk,
 * so the test is marked {@link Flaky} and retried: a transient network hiccup
 * must not fail CI. The emulator matrix is {@code x86_64}, so
 * {@link DistroSpec#detect} resolves the {@code amd64} image.
 */
@RunWith(AndroidJUnit4.class)
public class DistroBootstrapInstrumentedTest {

    private static final String TAG = "DistroBootstrapTest";
    // Production default; fresh CI emulators provide enough /data for the ~2.3 GB install.
    private static final int DISK_LIMIT_MB = 2048;
    private static final long BOOTSTRAP_TIMEOUT_MINUTES = 5;

    @Rule
    public FlakyTestRule flakyTestRule = new FlakyTestRule();

    private Context context;

    @Before
    public void setUp() {
        context = getApplicationContext();
    }

    @After
    public void tearDown() {
        deleteRecursively(new File(context.getFilesDir(), "distro"));
    }

    @Flaky(maxAttempts = 3)
    @Test
    public void bootstrap_reachesReadyAndInstallsImage() throws Exception {
        // Start from a clean slate so a crashed prior attempt cannot leave a
        // partial install that confuses the next attempt.
        deleteRecursively(new File(context.getFilesDir(), "distro"));

        DistroManager manager = new DistroManager(context, new OkHttpDistroFetcher(),
                DISK_LIMIT_MB);

        CountDownLatch done = new CountDownLatch(1);
        DistroManager.Listener listener = new DistroManager.Listener() {
            @Override
            public void onStateChange(DistroManager.State state) {
                Log.i(TAG, "state -> " + state);
                System.out.println(TAG + ": state -> " + state);
                if (state == DistroManager.State.READY
                        || state == DistroManager.State.FAILED) {
                    done.countDown();
                }
            }

            @Override
            public void onProgress(long bytesSoFar, long total) {
                if (total > 0) {
                    Log.d(TAG, "progress " + bytesSoFar + "/" + total);
                }
            }

            @Override
            public void onError(DistroException error) {
                Log.e(TAG, "bootstrap error", error);
                System.out.println(TAG + ": error -> " + error.getMessage());
            }
        };

        manager.bootstrap(listener);

        assertTrue("bootstrap must reach a terminal state in time",
                done.await(BOOTSTRAP_TIMEOUT_MINUTES, TimeUnit.MINUTES));

        DistroManager.State state = manager.getState();
        Log.i(TAG, "final state: " + state + " error=" + manager.getError());
        System.out.println(TAG + ": final state=" + state + " error=" + manager.getError());
        assertEquals("bootstrap should reach READY (last error: " + manager.getError() + ")",
                DistroManager.State.READY, state);
        assertNull("no error expected on success", manager.getError());

        File imageDir = new File(new File(context.getFilesDir(), "distro"),
                DistroSpec.IMAGE_DIR);
        assertTrue(".droidclaw-ready marker must exist",
                new File(imageDir, DistroSpec.MARKER_FILE).isFile());
        assertTrue("etc/os-release must exist",
                new File(imageDir, "etc/os-release").isFile());
        assertTrue("etc/resolv.conf must exist",
                new File(imageDir, "etc/resolv.conf").isFile());
        assertTrue("apt sources file must exist",
                new File(imageDir, "etc/apt/sources.list.d/ubuntu.sources").isFile());
    }

    @Flaky(maxAttempts = 3)
    @Test
    public void detectsImageForDeviceAbi() throws Exception {
        DistroSpec.Entry entry = DistroSpec.detect(context);
        assertNotNull("detect() must resolve an entry", entry);
        boolean isAmd64 = entry.url.contains("amd64");
        boolean isArm64 = entry.url.contains("arm64");
        assertTrue("resolved image should be amd64 or arm64, got: " + entry.url,
                isAmd64 || isArm64);
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        if (Files.isSymbolicLink(file.toPath())) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
