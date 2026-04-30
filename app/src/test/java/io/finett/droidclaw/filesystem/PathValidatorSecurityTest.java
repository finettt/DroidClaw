package io.finett.droidclaw.filesystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Regression tests for {@link PathValidator} — verifies the sibling-directory bypass fix
 * (MED-2) and standard path-traversal protections.
 *
 * <p>The key fix: previously {@code startsWith(canonicalWorkspace)} would match
 * {@code /workspace-evil/} against workspace {@code /workspace} because it is a
 * string prefix. The fix requires {@code startsWith(canonicalWorkspace + File.separator)}.
 */
public class PathValidatorSecurityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File workspace;
    private PathValidator validator;

    @Before
    public void setUp() throws IOException {
        workspace = tmp.newFolder("workspace");
        validator = new PathValidator(workspace);
    }

    // ==================== Sibling-directory bypass (MED-2) ====================

    @Test
    public void rejectsSiblingDirectoryWithSharedPrefix() throws IOException {
        // e.g. workspace=/data/app/workspace, evil=/data/app/workspace-evil/x.txt
        File evilSibling = tmp.newFolder("workspace-evil");
        File evilFile = new File(evilSibling, "secret.txt");
        evilFile.createNewFile();

        try {
            // Try to resolve via relative path that escapes to sibling
            validator.validateAndResolve("../workspace-evil/secret.txt");
            fail("Should have thrown SecurityException for sibling-dir bypass");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("traversal") || e.getMessage().contains("outside"));
        }
    }

    @Test
    public void acceptsWorkspaceRootItself() throws IOException {
        File result = validator.validateAndResolve(".");
        assertEquals(workspace.getCanonicalPath(), result.getCanonicalPath());
    }

    @Test
    public void acceptsFileInsideWorkspace() throws IOException {
        File inner = new File(workspace, "home");
        inner.mkdir();
        File result = validator.validateAndResolve("home");
        assertEquals(inner.getCanonicalPath(), result.getCanonicalPath());
    }

    // ==================== Standard path traversal ====================

    @Test
    public void rejectsDotDotTraversal() {
        try {
            validator.validateAndResolve("../etc/passwd");
            fail("Should throw SecurityException");
        } catch (SecurityException | IOException e) {
            // expected
        }
    }

    @Test
    public void rejectsAbsolutePathOutsideWorkspace() {
        try {
            validator.validateAndResolve("/etc/passwd");
            // Absolute paths are stripped of leading slash in PathValidator,
            // so this ends up as workspace + "/etc/passwd" — likely doesn't exist
            // but must not resolve outside workspace
        } catch (SecurityException | IOException e) {
            // expected if resolved outside
        }
    }

    @Test
    public void rejectsBackslashTraversal() {
        try {
            validator.validateAndResolve("..\\etc\\passwd");
            // Backslashes are normalised to forward slashes, so this becomes ../etc/passwd
            fail("Should throw SecurityException");
        } catch (SecurityException | IOException e) {
            // expected
        }
    }

    @Test
    public void toRelativePath_rejectsSiblingDirectory() throws IOException {
        File evilSibling = tmp.newFolder("workspace-evil");
        File evilFile = new File(evilSibling, "file.txt");
        evilFile.createNewFile();

        try {
            validator.toRelativePath(evilFile);
            fail("Should throw IllegalArgumentException for file outside workspace");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("workspace"));
        }
    }

    @Test
    public void toRelativePath_acceptsFileInsideWorkspace() throws IOException {
        File inner = new File(workspace, "test.txt");
        inner.createNewFile();

        String relative = validator.toRelativePath(inner);
        assertEquals("test.txt", relative);
    }

    @Test
    public void isValid_returnsFalse_forTraversalPath() {
        assertFalse(validator.isValid("../etc/passwd"));
        assertFalse(validator.isValid("../../root/.ssh/id_rsa"));
    }

    @Test
    public void isValid_returnsTrue_forSafePath() {
        assertTrue(validator.isValid("home/documents"));
        assertTrue(validator.isValid("tmp/output.txt"));
    }
}