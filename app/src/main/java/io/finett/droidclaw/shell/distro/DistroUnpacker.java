package io.finett.droidclaw.shell.distro;

import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts a base-image tarball into the image directory.
 *
 * <p>The stream is a gzip-compressed tar: {@link GzipCompressorInputStream}
 * decompresses, and {@link TarArchiveInputStream} walks the entries. For each
 * entry this applies the stored unix permission bits (so executables stay
 * executable, which {@code ExecPlanner} relies on), recreates symlinks and hard
 * links with {@link DistroFileSystem}, and rejects any entry whose path would
 * escape the image directory.
 *
 * <p>All filesystem work goes through {@link DistroFileSystem} and {@link File},
 * so the unpacker runs on every supported API level without the API 26+
 * {@code java.nio.file} API. The containment check is lexical: it resolves
 * {@code ..} and {@code .} components against the archive-supplied names without
 * following symlinks, so a hostile {@code ../} path is rejected on the declared
 * name alone (the classic tar-slip attack). Symlink and hard-link targets are
 * likewise resolved lexically against the link's own directory (symlinks) or the
 * archive root (hard links) and rejected if they point outside the image.
 *
 * <p>Extraction is write-through safe: {@link #createRealDirectories} refuses to
 * descend through a symlinked or non-directory path, and regular-file extraction
 * rejects a pre-existing symlink at the destination, so archive entries cannot
 * overwrite host files reached through a symlinked ancestor.
 */
public final class DistroUnpacker {

    private static final String TAG = "DistroUnpacker";
    private static final int BUFFER_BYTES = 8192;

    /**
     * Extract a gzip-compressed tar from {@code gzipIn} into {@code destDir}.
     *
     * @param gzipIn  open stream over the {@code .tar.gz} body; closed by this method
     * @param destDir the image directory (created if absent)
     * @throws DistroException on a path-traversal attempt
     * @throws IOException     on a read/write failure
     */
    public void unpack(InputStream gzipIn, File destDir) throws IOException, DistroException {
        try (TarArchiveInputStream tar =
                new TarArchiveInputStream(new GzipCompressorInputStream(gzipIn))) {
            unpack(tar, destDir);
        }
    }

    private void unpack(TarArchiveInputStream tar, File destDir) throws IOException, DistroException {
        File root = destDir.getCanonicalFile();
        if (DistroFileSystem.isSymlink(destDir)) {
            throw new DistroException(
                    "Refusing to unpack into a symlinked directory: " + destDir);
        }
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            throw new IOException("Could not create image directory: " + destDir);
        }
        if (!root.isDirectory()) {
            throw new DistroException("Image directory is not a directory: " + destDir);
        }

        // Hard links may reference entries that appear later in the archive, so
        // record their targets in the first pass and create the links afterwards.
        Map<File, Deque<String>> pendingHardLinks = new HashMap<>();

        TarArchiveEntry entry;
        while ((entry = tar.getNextTarEntry()) != null) {
            String name = entry.getName();
            if (name.isEmpty()) {
                continue;
            }
            File dest = resolve(root, name);
            if (entry.isDirectory()) {
                createRealDirectories(root, dest);
                DistroFileSystem.applyMode(dest, entry.getMode());
            } else if (entry.isSymbolicLink()) {
                applySymlink(dest, entry.getLinkName(), root);
            } else if (entry.isLink()) {
                pendingHardLinks.computeIfAbsent(dest, k -> new ArrayDeque<>())
                        .add(entry.getLinkName());
            } else {
                createRealDirectories(root, dest.getParentFile());
                if (DistroFileSystem.isSymlink(dest)) {
                    throw new DistroException(
                            "Refusing to write through a symlink at: " + dest);
                }
                try (OutputStream out = new FileOutputStream(dest)) {
                    copy(tar, out);
                }
                DistroFileSystem.applyMode(dest, entry.getMode());
            }
        }
        createHardLinks(root, pendingHardLinks);

        Log.d(TAG, "Unpacked into " + destDir);
    }

    /**
     * Create hard links now that every regular file is on disk. Each recorded
     * link is created against its lexically-resolved target; a link whose target
     * is missing, escaping, or itself a symlink is skipped.
     */
    private void createHardLinks(File root, Map<File, Deque<String>> pendingHardLinks)
            throws DistroException {
        for (Map.Entry<File, Deque<String>> pending : pendingHardLinks.entrySet()) {
            File link = pending.getKey();
            for (String targetName : pending.getValue()) {
                File target = resolveFromRoot(root, targetName);
                if (!within(root, target) || !target.isFile()
                        || DistroFileSystem.isSymlink(target) || target.equals(link)) {
                    continue;
                }
                try {
                    DistroFileSystem.createHardLink(link, target);
                } catch (IOException e) {
                    // A hard link whose target vanished is not fatal; skip it.
                }
            }
        }
    }

    /** Resolve {@code name} under {@code root}, rejecting absolute and traversal paths. */
    private File resolve(File root, String name) throws DistroException {
        if (name.startsWith("/")) {
            throw new DistroException("Refusing absolute path in archive: " + name);
        }
        File dest = normalize(new File(root, name));
        if (!within(root, dest)) {
            throw new DistroException("Refusing path traversal in archive: " + name);
        }
        return dest;
    }

    /**
     * Resolve a hard-link target, which is stored relative to the archive root
     * (unlike symlink targets, which are relative to the link's own directory).
     */
    private File resolveFromRoot(File root, String name) throws DistroException {
        String target = name.startsWith("/") ? name.substring(1) : name;
        File dest = normalize(new File(root, target));
        if (!within(root, dest)) {
            throw new DistroException("Refusing hard link path traversal in archive: " + name);
        }
        return dest;
    }

    /** Recreate a symlink, checking its lexically-resolved target stays inside {@code root}. */
    private void applySymlink(File dest, String linkName, File root)
            throws IOException, DistroException {
        createRealDirectories(root, dest.getParentFile());
        File resolvedTarget;
        if (linkName.startsWith("/")) {
            // Absolute links in a distro image are rooted at the image, not the host.
            resolvedTarget = normalize(new File(root, linkName.substring(1)));
        } else {
            resolvedTarget = normalize(new File(dest.getParentFile(), linkName));
        }
        if (!within(root, resolvedTarget)) {
            throw new DistroException(
                    "Refusing symlink that escapes the image directory: " + linkName);
        }
        DistroFileSystem.createSymlink(dest, linkName);
    }

    /**
     * Create {@code directory} and every parent up to (but not through)
     * {@code root}, refusing to descend through a symlink or a non-directory.
     *
     * <p>Because {@link DistroFileSystem#isSymlink} is checked for each component
     * as it is created, an archive that lists a symlinked directory before a
     * regular-file entry cannot cause that file to be written through the symlink
     * to its host target.
     */
    private static void createRealDirectories(File root, File directory)
            throws IOException, DistroException {
        if (directory == null || directory.equals(root) || !within(root, directory)) {
            return;
        }
        String relative = directory.getPath().substring(root.getPath().length() + 1);
        File current = root;
        for (String component : relative.split(Pattern.quote(File.separator))) {
            // Refuse to descend through a symlinked or non-directory ancestor.
            if (DistroFileSystem.isSymlink(current)) {
                throw new DistroException(
                        "Refusing to create directories through a symlink at: " + current);
            }
            if (current.exists() && !current.isDirectory()) {
                throw new DistroException(
                        "Refusing to create directories through a file at: " + current);
            }
            File next = new File(current, component);
            if (!next.isDirectory() && !next.mkdirs()) {
                throw new IOException("Could not create directory: " + next);
            }
            current = next;
        }
        // The final `current` equals `directory`; refuse if it is a symlink so a
        // directory entry cannot create its contents through a host symlink.
        if (DistroFileSystem.isSymlink(current)) {
            throw new DistroException(
                    "Refusing to create directories through a symlink at: " + current);
        }
    }

    private static void copy(TarArchiveInputStream tar, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = tar.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /** Lexically normalize {@code ..} and {@code .} without following symlinks. */
    private static File normalize(File file) {
        String path = file.getAbsoluteFile().getPath();
        boolean absolute = path.startsWith(File.separator);
        Deque<String> stack = new ArrayDeque<>();
        for (String part : path.split(Pattern.quote(File.separator))) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.pollLast();
                }
                continue;
            }
            stack.addLast(part);
        }
        File result = absolute ? new File(File.separator, stack.pollFirst())
                : new File(stack.pollFirst());
        for (String part : stack) {
            result = new File(result, part);
        }
        return result;
    }

    /** Whether {@code path} equals {@code root} or lives beneath it (lexical). */
    private static boolean within(File root, File path) {
        String rootAbs = root.getPath();
        String pAbs = path.getPath();
        return pAbs.equals(rootAbs) || pAbs.startsWith(rootAbs + File.separator);
    }
}
