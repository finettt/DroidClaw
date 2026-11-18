package io.finett.droidclaw.shell.distro;

/**
 * Download progress, in bytes.
 *
 * <p>{@code total} is the content length when the server reports one, and
 * {@code -1} when it does not (for example a chunked response).
 */
@FunctionalInterface
public interface ProgressListener {
    void onProgress(long bytesSoFar, long total);
}
