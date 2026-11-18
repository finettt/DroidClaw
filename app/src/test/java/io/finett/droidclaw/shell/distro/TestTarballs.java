package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Fixture helpers that supply pre-built {@code .tar.gz} payloads for the distro
 * tests. The payloads are committed as test resources
 * ({@code io/finett/droidclaw/shell/distro/*.tar.gz}) rather than assembled at
 * runtime so the byte layout is stable and the synthetic image faithfully
 * mirrors the real Ubuntu-base layout:
 *
 * <ul>
 *   <li>{@link #image()} — a regular file ({@code usr/lib/libfoo.so}, 0644), an
 *       executable ({@code bin/echo}, 0755), the {@code etc/os-release} symlink
 *       ({@code ../usr/lib/os-release}), plus {@code etc/resolv.conf} and the
 *       {@code ubuntu.sources} deb822 file that the manager finalizes/verifies;</li>
 *   <li>{@link #traversal()} — a {@code ../escape.txt} entry that exercises the
 *       tar-slip guard;</li>
 *   <li>{@link #absolute()} — an {@code /etc/evil} entry that exercises the
 *       absolute-path guard;</li>
 *   <li>{@link #absoluteSymlink()} — an image-absolute symlink matching the real
 *       Ubuntu-base {@code etc/alternatives/awk} entry;</li>
 *   <li>{@link #relativeSymlinkTraversal()} and {@link #absoluteSymlinkTraversal()}
 *       — symlink targets that exercise the escape guard;</li>
 *   <li>{@link #symlinkParentWrite()} and {@link #symlinkFileWrite()} — archive
 *       entries that attempt to write through an absolute symlink to the host.</li>
 * </ul>
 */
public final class TestTarballs {

    private TestTarballs() {}

    /** The canonical synthetic Ubuntu-base image (regular + executable + symlink). */
    public static byte[] image() throws IOException {
        return read("image.tar.gz");
    }

    /** A payload that contains an absolute-path entry ({@code /etc/evil}). */
    public static byte[] absolute() throws IOException {
        return read("absolute.tar.gz");
    }

    /** A payload that contains a traversal entry ({@code ../escape.txt}). */
    public static byte[] traversal() throws IOException {
        return read("traversal.tar.gz");
    }

    /** A payload with {@code etc/alternatives/awk -> /usr/bin/mawk}. */
    public static byte[] absoluteSymlink() throws IOException {
        return read("absolute-symlink.tar.gz");
    }

    /** A payload with a relative symlink target that escapes the image. */
    public static byte[] relativeSymlinkTraversal() throws IOException {
        return read("relative-symlink-traversal.tar.gz");
    }

    /** A payload with an image-absolute symlink target that escapes the image. */
    public static byte[] absoluteSymlinkTraversal() throws IOException {
        return read("absolute-symlink-traversal.tar.gz");
    }

    /** A payload that writes a file through a symlinked parent directory. */
    public static byte[] symlinkParentWrite() throws IOException {
        return read("symlink-parent-write.tar.gz");
    }

    /**
     * A payload with a regular file ({@code usr/bin/perl}) and a hard-link entry
     * ({@code usr/bin/perl5.38.2}) pointing at it, mirroring the real
     * Ubuntu-base layout so hard links are exercised end to end.
     */
    public static byte[] hardLink() throws IOException {
        return read("hard-link.tar.gz");
    }

    /** A payload that replaces a symlink with a regular-file entry. */
    public static byte[] symlinkFileWrite() throws IOException {
        return read("symlink-file-write.tar.gz");
    }

    /** Assert {@code data} starts with the gzip magic bytes (1f 8b). */
    public static void assertGzip(byte[] data) {
        assertArrayEquals(new byte[]{(byte) 0x1f, (byte) 0x8b}, Arrays.copyOf(data, 2));
    }

    /** Decompress {@code gz} back to the raw TAR bytes for byte-level inspection. */
    public static byte[] decompress(byte[] gz) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GzipCompressorInputStream in =
                new GzipCompressorInputStream(new ByteArrayInputStream(gz))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    /** Load a committed fixture from the test classpath. */
    private static byte[] read(String name) {
        ClassLoader cl = TestTarballs.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("io/finett/droidclaw/shell/distro/" + name)) {
            if (in == null) {
                throw new java.io.FileNotFoundException(
                        "missing test fixture: io/finett/droidclaw/shell/distro/" + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
