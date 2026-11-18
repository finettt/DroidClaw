package io.finett.droidclaw.shell.distro;

import android.content.Context;
import android.util.Log;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives a Linux base-image bootstrap to completion.
 *
 * <p>The bootstrap runs as a single sequential pipeline on a dedicated IO-style
 * background executor (the Java equivalent of a {@code Dispatchers.IO}
 * coroutine: one worker thread, never the main thread, one step at a time). The
 * pipeline is
 * {@code NOT_INSTALLED -> DOWNLOADING -> VERIFYING -> UNPACKING -> FINALIZING ->
 * READY}, with any failure moving to {@code FAILED} and removing the partial
 * install.
 *
 * <p>Space limits are enforced host-side ({@link DistroDiskSpace}); PRoot runs
 * inside the image and cannot enforce a quota itself, so the limit is advisory
 * and checked here before and after install.
 */
public final class DistroManager {

    private static final String TAG = "DistroManager";

    /** Default advisory disk limit in megabytes when none is supplied. */
    private static final int DEFAULT_DISK_LIMIT_MB = 2048;

    /** Safety margin kept free above {@code limit + tarball size} at bootstrap. */
    private static final long BOOTSTRAP_MARGIN_MB = 256L;

    /** Pipeline state. {@link #getState} reports the current value. */
    public enum State {
        NOT_INSTALLED, DOWNLOADING, VERIFYING, UNPACKING, FINALIZING, READY, FAILED
    }

    /** Callbacks for a bootstrap run, always delivered off the main thread. */
    public interface Listener {
        void onStateChange(State state);

        void onProgress(long bytesSoFar, long total);

        void onError(DistroException error);
    }

    private final File rootDir;
    private final File imageDir;
    private final File filesDir;
    private final File tarball;
    private final File marker;
    private final DistroSpec.Entry entry;
    private final DistroDownloader downloader;
    private final DistroVerifier verifier;
    private final DistroUnpacker unpacker;
    private volatile long limitBytes;
    private final ExecutorService io;
    private final DiskFreeProvider freeProvider;
    // Guards the single-flight reservation and the deletion guard together so
    // a bootstrap and a delete cannot interleave and corrupt the install.
    private final Object opLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Reads the free bytes on a directory's partition. Injected so tests can
     * drive the bootstrap space gate deterministically; production passes the
     * real {@link DistroDiskSpace#freeBytes} measurement.
     */
    interface DiskFreeProvider {
        long freeBytes(File dir);
    }

    private volatile State state = State.NOT_INSTALLED;
    private volatile String error;
    private volatile Listener listener;

    /** Production entry point: resolve the image for this device, default limit. */
    public DistroManager(Context context, DistroFetcher fetcher) {
        this(context, context.getApplicationContext().getFilesDir(),
                detectForDevice(context), fetcher, DEFAULT_DISK_LIMIT_MB,
                DistroDiskSpace::freeBytes);
    }

    private static DistroSpec.Entry detectForDevice(Context context) {
        try {
            return DistroSpec.detect(context);
        } catch (DistroException e) {
            // No supported ABI or no image for it: a fatal runtime configuration error.
            throw new IllegalStateException(
                    "Cannot determine Ubuntu base image for this device", e);
        }
    }

    /** Public entry point with an explicit disk limit in megabytes. */
    public DistroManager(Context context, DistroFetcher fetcher, int diskLimitMb) {
        this(context, context.getApplicationContext().getFilesDir(),
                detectForDevice(context), fetcher, diskLimitMb,
                DistroDiskSpace::freeBytes);
    }

    /**
     * Canonical constructor. Package-private so unit tests can pin the entry,
     * the on-disk base directory, the fetcher, and the limit without a device.
     */
    DistroManager(Context context, File filesDir, DistroSpec.Entry entry,
                  DistroFetcher fetcher, int diskLimitMb, DiskFreeProvider freeProvider) {
        this.filesDir = filesDir;
        this.rootDir = new File(filesDir, "distro");
        this.imageDir = new File(rootDir, DistroSpec.IMAGE_DIR);
        this.tarball = new File(rootDir, DistroSpec.TARBALL_NAME);
        this.marker = new File(imageDir, DistroSpec.MARKER_FILE);
        this.entry = entry;
        this.downloader = new DistroDownloader(fetcher);
        this.verifier = new DistroVerifier();
        this.unpacker = new DistroUnpacker();
        this.limitBytes = (long) diskLimitMb * 1024L * 1024L;
        this.freeProvider = freeProvider;
        this.io = Executors.newSingleThreadExecutor(daemonFactory());
    }

    /**
     * Update the advisory disk limit (megabytes) before a bootstrap. Kept
     * volatile because it is read from the bootstrap worker thread.
     */
    public void setDiskLimitMb(int diskLimitMb) {
        this.limitBytes = (long) diskLimitMb * 1024L * 1024L;
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread thread = new Thread(r, "distro-bootstrap-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Stop the background executor. A bootstrap already running finishes on its
     * daemon thread; the closed executor then accepts no new work. Call this
     * when the owning view is destroyed to avoid leaking the executor.
     */
    public void shutdown() {
        io.shutdown();
    }

    /** Current pipeline state. */
    public State getState() {
        return state;
    }

    /** Error message from the last failed run, or {@code null}. */
    public String getError() {
        return error;
    }

    /** Whether the marker file exists on disk (image ready, even after a restart). */
    public boolean isInstalled() {
        return marker.isFile();
    }

    /**
     * Session guard for (future) distro commands: fail fast while the installed
     * image exceeds the limit, with an actionable error.
     *
     * @throws DistroException when the image is over the limit
     * @throws IOException on a filesystem error while measuring the image
     */
    public void guardWithinLimit() throws DistroException, IOException {
        long used = DistroDiskSpace.usedBytes(rootDir);
        DistroDiskSpace.assertWithinLimit(used, limitBytes);
    }

    /**
     * Start the bootstrap unless one is already in flight or the image is ready.
     *
     * <p>The reservation is atomic: the first call claims the single-flight slot
     * and publishes {@code DOWNLOADING} before enqueuing, so a concurrent call
     * cannot start a second pipeline. A re-entrant call re-registers its listener
     * and lets the running pipeline drive it to completion.
     *
     * @param listener callbacks for progress and completion
     */
    public void bootstrap(Listener listener) {
        synchronized (opLock) {
            if (listener != null) {
                this.listener = listener;
            }
            if (!running.compareAndSet(false, true)) {
                // Already in flight: keep the new listener and let the running
                // pipeline drive it to completion.
                return;
            }
            // Reserve the operation and publish the first state before enqueueing.
            this.error = null;
            setState(State.DOWNLOADING);
        }
        io.execute(this::runBootstrap);
    }

    private void runBootstrap() {
        boolean failed = false;
        String failureMessage = null;
        try {
            // Recheck readiness: a concurrent deleteAll() or a completed prior run
            // may have installed the image between reservation and execution.
            if (!isInstalled()) {
                // Measure the parent: rootDir does not exist yet at this point and
                // File.getFreeSpace() reports 0 for a non-existent path on Android.
                // filesDir and rootDir share one partition, so the reading is valid.
                DistroDiskSpace.assertBootstrapFree(
                        freeProvider.freeBytes(filesDir), limitBytes, entry.size,
                        BOOTSTRAP_MARGIN_MB * 1024L * 1024L);
                if (!rootDir.isDirectory() && !rootDir.mkdirs()) {
                    throw new IOException("Could not create image directory: " + rootDir);
                }

                long downloaded = downloader.download(entry.url, tarball, this::notifyProgress);
                if (downloaded != entry.size) {
                    throw new DistroException("Downloaded image size mismatch: expected "
                            + entry.size + " bytes, got " + downloaded);
                }

                setState(State.VERIFYING);
                verifier.verify(tarball, entry.sha256);

                setState(State.UNPACKING);
                try (InputStream in = new FileInputStream(tarball)) {
                    unpacker.unpack(in, imageDir);
                }

                setState(State.FINALIZING);
                finalize(imageDir);
                guardWithinLimit();
            }
        } catch (Throwable t) {
            failureMessage = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Log.e(TAG, "Bootstrap failed", t);
            // Remove the partial install before notifying listeners so observers
            // never see a FAILED state with leftover files on disk.
            cleanup();
            failed = true;
        } finally {
            // Release the single-flight slot BEFORE publishing the terminal state
            // so a concurrent deleteAll() or bootstrap() cannot race the latch.
            running.set(false);
        }
        if (failed) {
            this.error = failureMessage;
            setState(State.FAILED);
            if (listener != null) {
                listener.onError(new DistroException(failureMessage));
            }
        } else {
            setState(State.READY);
            Log.i(TAG, "Bootstrap complete: " + imageDir);
        }
    }

    /** Write resolv.conf, ensure a sources file exists, and drop the ready marker. */
    private void finalize(File dir) throws IOException {
        writeBytes(new File(dir, "etc/resolv.conf"), resolvConfContent());
        File sources = new File(dir, "etc/apt/sources.list.d/ubuntu.sources");
        if (!sources.isFile()) {
            writeUtf8(sources, nobleDeb822());
        }
        writeUtf8(new File(dir, DistroSpec.MARKER_FILE), "ready\n");
    }

    /**
     * resolv.conf contents: copy the host resolver config when present and
     * non-empty, otherwise fall back to public DNS servers.
     */
    private static byte[] resolvConfContent() throws IOException {
        File host = new File("/system/etc/resolv.conf");
        if (host.isFile() && host.length() > 0) {
            return readAll(host);
        }
        return ("nameserver 8.8.8.8\nnameserver 1.1.1.1\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String nobleDeb822() {
        return String.join("\n",
                "Types: deb",
                "URIs: https://archive.ubuntu.com/ubuntu/",
                "Suites: noble noble-updates noble-backports",
                "Components: main universe restricted multiverse",
                "Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg",
                "");
    }

    /**
     * Remove the whole distro directory (tarball, image, marker).
     *
     * <p>Deletion is rejected while a bootstrap is in flight so the running
     * pipeline cannot recreate the tree it is about to remove.
     *
     * @return {@code true} if the image was removed, {@code false} if a bootstrap
     *         is already running and deletion was refused
     */
    public boolean deleteAll() {
        synchronized (opLock) {
            if (running.get()) {
                return false;
            }
            deleteRecursively(rootDir);
            this.error = null;
            setState(State.NOT_INSTALLED);
            return true;
        }
    }

    private void cleanup() {
        deleteRecursively(rootDir);
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        if (DistroFileSystem.isSymlink(file)) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        // Best-effort cleanup after a failed bootstrap.
        file.delete();
    }

    private void setState(State next) {
        this.state = next;
        if (listener != null) {
            listener.onStateChange(next);
        }
    }

    private void notifyProgress(long bytes, long total) {
        Listener l = listener;
        if (l != null) {
            l.onProgress(bytes, total);
        }
    }

    private static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    private static void writeBytes(File file, byte[] content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }

    private static void writeUtf8(File file, String text) throws IOException {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
