package io.finett.droidclaw.shell.distro;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Host-side free/used space checks for the distro directory.
 *
 * <p>These are advisory: PRoot runs inside the image and cannot enforce a quota,
 * so the limit is checked by the host before and after install. Symlinks are
 * never followed, so a link to a host file cannot inflate the image size. Values
 * come straight from {@link File} APIs and are therefore easy to drive from unit
 * tests.
 */
public final class DistroDiskSpace {

    private DistroDiskSpace() {
    }

    /** Free bytes on the partition that holds {@code dir}. */
    public static long freeBytes(File dir) {
        return Math.max(0L, dir.getFreeSpace());
    }

    /**
     * Total size of every file under {@code dir}, recursing depth-first.
     * Directories themselves contribute zero; a missing directory is zero.
     */
    public static long usedBytes(File dir) throws IOException {
        if (dir == null || !dir.isDirectory()) {
            return 0L;
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            // Never follow symlinks during measurement: a link to a host file
            // would inflate the image size and could double-count shared data.
            if (DistroFileSystem.isSymlink(child)) {
                continue;
            }
            if (child.isDirectory()) {
                total += usedBytes(child);
            } else if (child.isFile()) {
                try (InputStream in = new FileInputStream(child)) {
                    total += skipped(in);
                }
            }
        }
        return total;
    }

    private static long skipped(InputStream in) throws IOException {
        long n = 0;
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            n += read;
        }
        return n;
    }

    /** Millabytes, rounded down. */
    public static long megabytes(long bytes) {
        return bytes / (1024L * 1024L);
    }

    /**
     * Gate the bootstrap: free space must cover the limit, the tarball, and a
     * safety margin, otherwise abort with an actionable error.
     *
     * @param freeBytes   free bytes on the partition
     * @param limitBytes  the configured distro limit in bytes
     * @param tarballSize downloaded tarball size in bytes
     * @param marginBytes extra headroom kept free after install
     * @throws DistroException when there is not enough free space
     */
    public static void assertBootstrapFree(
            long freeBytes, long limitBytes, long tarballSize, long marginBytes)
            throws DistroException {
        long required = limitBytes + tarballSize + marginBytes;
        if (freeBytes < required) {
            throw new DistroException(
                    "Not enough disk space to install: need "
                            + megabytes(required) + " MB free (limit + tarball + margin), "
                            + "but only " + megabytes(freeBytes) + " MB is free. "
                            + "Free disk space or lower the Linux disk limit in settings.");
        }
    }

    /**
     * Session guard: block distro commands while the image exceeds the limit,
     * with an actionable error.
     *
     * @param usedBytes   current size of the installed image in bytes
     * @param limitBytes  the configured distro limit in bytes
     * @throws DistroException when the image is over the limit
     */
    public static void assertWithinLimit(long usedBytes, long limitBytes) throws DistroException {
        if (usedBytes > limitBytes) {
            throw new DistroException(
                    "Linux image exceeds the disk limit (" + megabytes(limitBytes)
                            + " MB). Run 'apt clean', remove packages, or raise the disk limit "
                            + "in settings.");
        }
    }
}
