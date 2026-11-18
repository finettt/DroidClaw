package io.finett.droidclaw.shell.distro;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Filesystem helpers for the distro image: symlink creation, hard-link creation,
 * permission application, and symlink detection.
 *
 * <p>Below API 26 the JDK {@code java.nio.file} API is not available on the
 * device, so symlink creation, hard-link creation, and exact chmod go through
 * {@link android.system.Os}. On API 26+ the NIO API is used. Symlink detection
 * ({@link #isSymlink}) uses only {@link File} and works on every supported API
 * level.
 */
public final class DistroFileSystem {

    private DistroFileSystem() {
    }

    /**
     * Whether {@code file} is a symbolic link.
     *
     * <p>On API 26+ this uses {@link Files#isSymbolicLink}, which reports the
     * link state of {@code file} itself without following it. On API 24-25, where
     * that NIO API is unavailable, it falls back to {@link File#getCanonicalFile}:
     * a path whose canonical form differs from its declared name is a symlink (or
     * sits under one). Paths that cannot be canonicalized are conservatively
     * treated as symlinks so callers skip traversal rather than follow them.
     */
    public static boolean isSymlink(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return Nio.isSymlink(file);
            } catch (RuntimeException e) {
                // fall through to the File API fallback
            }
        }
        try {
            File canonicalParent = file.getParentFile() == null
                    ? file
                    : file.getParentFile().getCanonicalFile();
            File canonicalFile = new File(canonicalParent, file.getName());
            return !canonicalFile.getCanonicalFile().equals(canonicalFile);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Create a symbolic link at {@code link} whose target text is {@code target}.
     *
     * <p>On API 26+ this delegates to {@link Files#createSymbolicLink}; on
     * API 24-25 it uses {@link Os#symlink}.
     *
     * @throws IOException on a link-creation failure
     */
    public static void createSymlink(File link, String target) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Nio.createSymlink(link, target);
        } else {
            try {
                Os.symlink(target, link.getAbsolutePath());
            } catch (ErrnoException e) {
                throw new IOException("Failed to create symlink " + link + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Create a hard link at {@code link} pointing at {@code target}.
     *
     * <p>On API 26+ this delegates to {@link Files#createLink}; on API 24-25 it
     * shells out to {@code ln}, which is provided by the Android system image.
     *
     * @throws IOException on a link-creation failure
     */
    public static void createHardLink(File link, File target) throws IOException {
        if (link.exists() && !link.delete()) {
            throw new IOException("Could not replace existing entry before hard link: " + link);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Nio.createHardLink(link, target);
        } else {
            OsImpl.createHardLink(link, target);
        }
    }

    /**
     * Apply the unix permission bits from {@code mode} to {@code file}.
     *
     * <p>Owner/group/other read, write, and execute bits are mapped;
     * setuid/setgid/sticky are ignored. On API 26+ exact bits are written with
     * {@link Files#setPosixFilePermissions}; on API 24-25 with {@link Os#chmod}.
     * When neither the host store nor the OS supports the requested bits, the
     * execute bits are set via the {@link File} API as a fallback so executables
     * remain executable.
     *
     * @throws IOException on a permission-application failure (when no fallback applies)
     */
    public static void applyMode(File file, int mode) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Nio.applyMode(file, mode);
                return;
            } catch (UnsupportedOperationException | NoSuchFileException e) {
                // fall through to the File API fallback
            }
        } else {
            try {
                Os.chmod(file.getAbsolutePath(), mode & 0777);
                return;
            } catch (ErrnoException | UnsupportedOperationException e) {
                // fall through to the File API fallback
            }
        }
        file.setReadable((mode & 0444) != 0, false);
        file.setWritable((mode & 0222) != 0, false);
        file.setExecutable((mode & 0111) != 0, false);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static final class Nio {
        private Nio() {
        }

        private static void createSymlink(File link, String target) throws IOException {
            Files.createSymbolicLink(link.toPath(), Paths.get(target));
        }

        private static boolean isSymlink(File file) {
            return Files.isSymbolicLink(file.toPath());
        }

        private static void createHardLink(File link, File target) throws IOException {
            Files.createLink(link.toPath(), target.toPath());
        }

        private static void applyMode(File file, int mode) throws IOException {
            StringBuilder bits = new StringBuilder(9);
            bits.append((mode & 0400) != 0 ? 'r' : '-');
            bits.append((mode & 0200) != 0 ? 'w' : '-');
            bits.append((mode & 0100) != 0 ? 'x' : '-');
            bits.append((mode & 0040) != 0 ? 'r' : '-');
            bits.append((mode & 0020) != 0 ? 'w' : '-');
            bits.append((mode & 0010) != 0 ? 'x' : '-');
            bits.append((mode & 0004) != 0 ? 'r' : '-');
            bits.append((mode & 0002) != 0 ? 'w' : '-');
            bits.append((mode & 0001) != 0 ? 'x' : '-');
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(bits.toString());
            Files.setPosixFilePermissions(file.toPath(), perms);
        }
    }

    /** API 24-25 fallbacks backed by {@link android.system.Os} and the {@code ln} binary. */
    private static final class OsImpl {
        private OsImpl() {
        }

        private static void createSymlink(File link, String target) throws IOException {
            try {
                Os.symlink(target, link.getAbsolutePath());
            } catch (ErrnoException e) {
                throw new IOException("Failed to create symlink " + link + ": " + e.getMessage(), e);
            }
        }

        private static void createHardLink(File link, File target) throws IOException {
            // Os.link is API 26+, so shell out to ln, which the system image provides.
            shellLink(target, link);
        }

        private static void applyMode(File file, int mode) throws IOException {
            try {
                Os.chmod(file.getAbsolutePath(), mode & 0777);
            } catch (ErrnoException e) {
                throw new IOException("Failed to chmod " + file + ": " + e.getMessage(), e);
            }
        }

        private static void shellLink(File target, File link) throws IOException {
            try {
                Process process = new ProcessBuilder("ln", target.getAbsolutePath(),
                        link.getAbsolutePath()).redirectErrorStream(true).start();
                drain(process.getInputStream());
                int exit = process.waitFor();
                if (exit != 0) {
                    throw new IOException("ln failed with exit code " + exit);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted creating hard link via ln");
            }
        }

        private static void drain(java.io.InputStream in) throws IOException {
            byte[] buffer = new byte[4096];
            while (in.read(buffer) != -1) {
                // discard
            }
        }
    }
}
