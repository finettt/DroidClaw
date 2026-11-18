package io.finett.droidclaw.shell.distro;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Injectable transport for a single base-image download.
 *
 * <p>This is the seam that mirrors the UpdateChecker fetcher (#170): production
 * code uses {@link OkHttpDistroFetcher}, and tests inject a fake that returns
 * canned bytes without opening a socket. A single method performs a GET and
 * streams the body into {@code out}, reporting progress through
 * {@link ProgressListener}.
 *
 * <p>On a transport failure or a non-success HTTP status (including 404) the
 * implementation throws {@link DistroException} so callers never have to
 * distinguish network errors from HTTP errors.
 */
@FunctionalInterface
public interface DistroFetcher {
    /**
     * Fetch {@code url} and stream its body into {@code out}.
     *
     * @param url        the tarball URL
     * @param out        destination stream; the caller owns and closes it
     * @param listener   progress callback; may be {@code null}
     * @return the number of bytes written to {@code out}
     * @throws DistroException on a transport failure or a non-success HTTP status
     * @throws IOException     if streaming the body fails midway
     */
    long download(String url, OutputStream out, ProgressListener listener)
            throws DistroException, IOException;
}
