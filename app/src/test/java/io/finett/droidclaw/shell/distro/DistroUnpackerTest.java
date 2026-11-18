package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link DistroUnpacker}: unix mode application, symlink
 * recreation, and the path-traversal guard. Payloads come from {@link TestTarballs}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30) // API 30 (>= O): exercises the NIO filesystem path.
public class DistroUnpackerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private DistroUnpacker unpacker() {
        return new DistroUnpacker();
    }

    @Test
    public void unpack_appliesModesCreatesFilesAndSymlinks() throws Exception {
        File dest = tmp.newFolder("ubuntu");
        byte[] image = TestTarballs.image();

        unpacker().unpack(new java.io.ByteArrayInputStream(image), dest);

        // Regular file (0644): readable, not executable.
        File lib = new File(dest, "usr/lib/libfoo.so");
        assertTrue("libfoo.so should exist", lib.isFile());
        assertFalse("0644 file must not be executable", lib.canExecute());
        assertEquals("LIBDATA", readAll(lib));

        // Executable file (0755): stays executable (ExecPlanner checks this).
        File echo = new File(dest, "bin/echo");
        assertTrue("bin/echo should exist", echo.isFile());
        assertTrue("0755 file must remain executable", echo.canExecute());

        // Symlink recreated as a link with the same target.
        Path link = new File(dest, "etc/os-release").toPath();
        assertTrue("os-release must be a symlink", Files.isSymbolicLink(link));
        assertEquals("../usr/lib/os-release", Files.readSymbolicLink(link).toString());
    }

    @Test
    public void unpack_rejectsTraversalEntry() throws Exception {
        File dest = tmp.newFolder("ubuntu");
        byte[] bad = TestTarballs.traversal(); // contains ../escape.txt

        try {
            unpacker().unpack(new java.io.ByteArrayInputStream(bad), dest);
            fail("expected DistroException on traversal entry");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("traversal"));
        }

        // The escaped file must not have been written outside the image dir.
        File escaped = new File(dest.getParent(), "escape.txt");
        assertFalse("escaped file must not be created", escaped.isFile());
    }

    @Test
    public void unpack_rejectsAbsoluteEntry() throws Exception {
        File dest = tmp.newFolder("ubuntu");
        byte[] bad = TestTarballs.absolute(); // contains /etc/evil

        try {
            unpacker().unpack(new java.io.ByteArrayInputStream(bad), dest);
            fail("expected DistroException on absolute entry");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("absolute"));
        }
    }

    @Test
    public void unpack_allowsImageAbsoluteSymlink() throws Exception {
        File dest = tmp.newFolder("absolute-symlink");

        unpacker().unpack(new java.io.ByteArrayInputStream(TestTarballs.absoluteSymlink()), dest);

        Path link = new File(dest, "etc/alternatives/awk").toPath();
        assertTrue("awk must be a symlink", Files.isSymbolicLink(link));
        assertEquals("/usr/bin/mawk", Files.readSymbolicLink(link).toString());
    }

    @Test
    public void unpack_createsHardLinks() throws Exception {
        File dest = tmp.newFolder("hardlink");

        unpacker().unpack(new java.io.ByteArrayInputStream(TestTarballs.hardLink()), dest);

        File perl = new File(dest, "usr/bin/perl");
        File perlVariant = new File(dest, "usr/bin/perl5.38.2");
        assertTrue("original must exist", perl.isFile());
        assertTrue("hard link must exist", perlVariant.isFile());
        // The key regression: a hard link must carry the target's content, not be empty.
        assertEquals("hard link shares the target's content", readAll(perl), readAll(perlVariant));
        // And they must share an inode, proving a real hard link rather than a copy.
        assertEquals("hard link shares an inode with its target",
                Files.getAttribute(perl.toPath(), "basic:fileKey"),
                Files.getAttribute(perlVariant.toPath(), "basic:fileKey"));
    }

    @Test
    public void unpack_rejectsRelativeSymlinkEscape() throws Exception {
        assertSymlinkEscapeRejected(TestTarballs.relativeSymlinkTraversal());
    }

    @Test
    public void unpack_rejectsAbsoluteSymlinkEscape() throws Exception {
        assertSymlinkEscapeRejected(TestTarballs.absoluteSymlinkTraversal());
    }

    @Test
    public void unpack_rejectsWriteThroughSymlinkParent() throws Exception {
        assertWriteThroughRejected(TestTarballs.symlinkParentWrite(),
                new File("/tmp", "droidclaw-distro-unpacker-parent-escape-7f3a9c"));
    }

    @Test
    public void unpack_rejectsWriteThroughSymlinkFile() throws Exception {
        assertWriteThroughRejected(TestTarballs.symlinkFileWrite(),
                new File("/tmp", "droidclaw-distro-unpacker-final-escape-7f3a9c"));
    }

    private void assertWriteThroughRejected(byte[] image, File hostFile) throws Exception {
        File dest = tmp.newFolder();
        assertFalse("test host path must not already exist", hostFile.exists());
        try {
            unpacker().unpack(new java.io.ByteArrayInputStream(image), dest);
            fail("expected DistroException on write through a symlink");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("symlink"));
        } finally {
            Files.deleteIfExists(hostFile.toPath());
        }
        assertFalse("archive must not write through a host symlink", hostFile.exists());
    }

    private void assertSymlinkEscapeRejected(byte[] image) throws Exception {
        File dest = tmp.newFolder();
        try {
            unpacker().unpack(new java.io.ByteArrayInputStream(image), dest);
            fail("expected DistroException on escaping symlink target");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("escapes the image"));
        }
        assertFalse(Files.isSymbolicLink(new File(dest, "etc/escape").toPath()));
    }

    private static String readAll(File file) throws IOException {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
