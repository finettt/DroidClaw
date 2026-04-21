package io.finett.droidclaw.filesystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class PathValidatorTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private PathValidator pathValidator;
    private File workspaceRoot;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = tempFolder.newFolder("workspace");
        pathValidator = new PathValidator(workspaceRoot);
    }

    @Test
    public void testValidateSimplePath() throws Exception {
        File result = pathValidator.validateAndResolve("test.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test
    public void testValidateNestedPath() throws Exception {
        File result = pathValidator.validateAndResolve("dir/subdir/file.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test
    public void testValidatePathWithLeadingSlash() throws Exception {
        File result = pathValidator.validateAndResolve("/test.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalAttack() throws Exception {
        pathValidator.validateAndResolve("../../etc/passwd");
    }

    @Test
    public void testAbsolutePathIsTreatedAsRelative() throws Exception {
        // Absolute paths are treated as relative by stripping the leading slash
        // So /etc/passwd becomes workspace/etc/passwd
        File result = pathValidator.validateAndResolve("/etc/passwd");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
        assertTrue(result.getPath().contains("etc"));
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalComplex() throws Exception {
        pathValidator.validateAndResolve("dir/../../outside/file.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPath() throws Exception {
        pathValidator.validateAndResolve(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPath() throws Exception {
        pathValidator.validateAndResolve("");
    }

    @Test
    public void testIsValidWithValidPath() {
        assertTrue(pathValidator.isValid("test.txt"));
        assertTrue(pathValidator.isValid("dir/file.txt"));
    }

    @Test
    public void testIsValidWithInvalidPath() {
        assertFalse(pathValidator.isValid("../../etc/passwd"));
        assertFalse(pathValidator.isValid(null));
        assertFalse(pathValidator.isValid(""));
    }

    @Test
    public void testToRelativePath() throws Exception {
        File testFile = new File(workspaceRoot, "test/file.txt");
        testFile.getParentFile().mkdirs();
        testFile.createNewFile();

        String relativePath = pathValidator.toRelativePath(testFile);
        assertEquals("test/file.txt", relativePath.replace('\\', '/'));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToRelativePathOutsideWorkspace() throws Exception {
        File outsideFile = tempFolder.newFile("outside.txt");
        pathValidator.toRelativePath(outsideFile);
    }

    @Test
    public void testGetWorkspaceRoot() {
        assertEquals(workspaceRoot, pathValidator.getWorkspaceRoot());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullWorkspace() {
        new PathValidator(null);
    }

    @Test
    public void testValidatePathWithBackslash() throws Exception {
        File result = pathValidator.validateAndResolve("\\test.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test
    public void testValidatePathWithDots() throws Exception {
        File result = pathValidator.validateAndResolve("./test.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test
    public void testToRelativePathForWorkspaceRoot() throws Exception {
        String relativePath = pathValidator.toRelativePath(workspaceRoot);
        assertEquals(".", relativePath);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPathWithWhitespace() throws Exception {
        pathValidator.validateAndResolve("   ");
    }

    @Test
    public void testValidatePathWithMultipleDots() throws Exception {
        File testDir = new File(workspaceRoot, "dir/subdir");
        testDir.mkdirs();

        File result = pathValidator.validateAndResolve("dir/subdir/../file.txt");
        assertNotNull(result);
        assertTrue(result.getCanonicalPath().startsWith(workspaceRoot.getCanonicalPath()));
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalWithMixedSeparators() throws Exception {
        pathValidator.validateAndResolve("dir\\..\\..\\etc/passwd");
    }
}