package io.finett.droidclaw.shell.distro;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Streams a downloaded tarball to disk.
 *
 * <p>Wraps an injectable {@link DistroFetcher} and owns the file output, so the
 * manager only deals with a {@code File} and a progress callback. This is the
 * download half of the bootstrap; {@link DistroVerifier} checks the bytes that
 * land here before {@link DistroUnpacker} extracts them.
 */
public final class DistroDownloader {

    private static final String TAG = "DistroDownloader";
    private static final int BUFFER_BYTES = 8192;

    private final DistroFetcher fetcher;

    public DistroDownloader(DistroFetcher fetcher) {
        if (fetcher == null) {
            throw new IllegalArgumentException("fetcher must not be null");
        }
        this.fetcher = fetcher;
    }

    /**
     * Download {@code url} into {@code dest}, reporting progress.
     *
     * @param url        the tarball URL
     * @param dest       destination file (created or overwritten)
     * @param listener   progress callback; may be {@code null}
     * @return the number of bytes written
     * @throws DistroException on a transport or HTTP failure
     * @throws IOException     if writing the file fails
     */
    public long download(String url, File dest, ProgressListener listener)
            throws DistroException, IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }
        try (OutputStream out = new FileOutputStream(dest)) {
            long bytes = fetcher.download(url, out, listener);
            Log.d(TAG, "Wrote " + bytes + " bytes to " + dest);
            return bytes;
        }
    }
}
