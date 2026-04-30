package io.finett.droidclaw.filesystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Regression tests for the protected-path enforcement in {@link VirtualFileSystem}
 * (MED-1 fix: prevents LLM-instructed overwrite of identity files).
 *
 * <p>Paths {@code .agent/soul.md}, {@code .agent/user.md}, and
 * {@code .agent/HEARTBEAT.md} must not be writable or deletable via the VFS,
 * regardless of how the path is supplied.
 */
public class VirtualFileSystemProtectedPathsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private VirtualFileSystem vfs;
    private File workspace;

    @Before
    public void setUp() throws IOException {
        workspace = tmp.newFolder("workspace");
        // Create the .agent directory so paths resolve
        new File(workspace, ".agent").mkdirs();
        PathValidator validator = new PathValidator(workspace);
        vfs = new VirtualFileSystem(validator);
    }

    // ==================== Write protection ====================

    @Test
    public void writeFile_throwsForSoulMd() {
        try {
            vfs.writeFile(".agent/soul.md", "malicious content", false);
            fail("Should throw SecurityException for soul.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    @Test
    public void writeFile_throwsForUserMd() {
        try {
            vfs.writeFile(".agent/user.md", "malicious content", false);
            fail("Should throw SecurityException for user.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    @Test
    public void writeFile_throwsForHeartbeatMd() {
        try {
            vfs.writeFile(".agent/HEARTBEAT.md", "malicious content", false);
            fail("Should throw SecurityException for HEARTBEAT.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    @Test
    public void writeFile_throwsForProtectedPathWithLeadingSlash() {
        try {
            vfs.writeFile("/.agent/soul.md", "malicious content", false);
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    @Test
    public void writeFile_throwsForProtectedPathWithBackslash() {
        try {
            vfs.writeFile(".agent\\soul.md", "malicious content", false);
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    @Test
    public void appendFile_throwsForSoulMd() {
        try {
            vfs.writeFile(".agent/soul.md", "appended content", true);
            fail("Should throw SecurityException for append to soul.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
        } catch (IOException e) {
            fail("Expected SecurityException, not IOException: " + e.getMessage());
        }
    }

    // ==================== Delete protection ====================

    @Test
    public void deleteFile_throwsForSoulMd() throws IOException {
        File soulFile = new File(workspace, ".agent/soul.md");
        soulFile.createNewFile();

        try {
            vfs.deleteFile(".agent/soul.md");
            fail("Should throw SecurityException for delete soul.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
            assertTrue("soul.md should still exist", soulFile.exists());
        }
    }

    @Test
    public void deleteFile_throwsForUserMd() throws IOException {
        File userFile = new File(workspace, ".agent/user.md");
        userFile.createNewFile();

        try {
            vfs.deleteFile(".agent/user.md");
            fail("Should throw SecurityException for delete user.md");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("protected"));
            assertTrue("user.md should still exist", userFile.exists());
        }
    }

    // ==================== Non-protected paths (regression: must still work) ====================

    @Test
    public void writeFile_succeedsForNonProtectedPath() throws IOException {
        File homeDir = new File(workspace, "home");
        homeDir.mkdirs();

        vfs.writeFile("home/notes.txt", "hello world", false);

        File written = new File(workspace, "home/notes.txt");
        assertTrue("File should have been written", written.exists());
    }

    @Test
    public void writeFile_succeedsForAgentMemory() throws IOException {
        // .agent/memory/ is NOT protected — only soul.md, user.md, HEARTBEAT.md
        File memoryDir = new File(workspace, ".agent/memory");
        memoryDir.mkdirs();

        vfs.writeFile(".agent/memory/notes.md", "memory content", false);

        File written = new File(workspace, ".agent/memory/notes.md");
        assertTrue("Memory file should have been written", written.exists());
    }

    @Test
    public void deleteFile_succeedsForNonProtectedPath() throws IOException {
        File tmpDir = new File(workspace, "tmp");
        tmpDir.mkdirs();
        File tmpFile = new File(tmpDir, "deleteme.txt");
        tmpFile.createNewFile();

        vfs.deleteFile("tmp/deleteme.txt");

        assertFalse("File should have been deleted", tmpFile.exists());
    }
}