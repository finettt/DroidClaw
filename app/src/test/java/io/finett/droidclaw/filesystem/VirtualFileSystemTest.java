package io.finett.droidclaw.filesystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class VirtualFileSystemTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private VirtualFileSystem vfs;
    private PathValidator pathValidator;
    private File workspaceRoot;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = tempFolder.newFolder("workspace");
        pathValidator = new PathValidator(workspaceRoot);
        vfs = new VirtualFileSystem(pathValidator);
    }

    @Test
    public void testWriteAndReadFile() throws Exception {
        String path = "test.txt";
        String content = "Hello, World!";

        // Write file
        assertTrue(vfs.writeFile(path, content, false));

        // Read file
        VirtualFileSystem.FileReadResult result = vfs.readFile(path, null, null);
        assertEquals(content, result.getContent());
        assertEquals(1, result.getTotalLines());
        assertEquals(1, result.getLinesRead());
        assertFalse(result.isTruncated());
    }

    @Test
    public void testWriteMultilineFile() throws Exception {
        String path = "multiline.txt";
        String content = "Line 1\nLine 2\nLine 3";

        vfs.writeFile(path, content, false);

        VirtualFileSystem.FileReadResult result = vfs.readFile(path, null, null);
        assertEquals(content, result.getContent());
        assertEquals(3, result.getTotalLines());
    }

    @Test
    public void testAppendToFile() throws Exception {
        String path = "append.txt";
        
        vfs.writeFile(path, "First line", false);
        vfs.writeFile(path, "\nSecond line", true);

        VirtualFileSystem.FileReadResult result = vfs.readFile(path, null, null);
        assertEquals("First line\nSecond line", result.getContent());
    }

    @Test
    public void testReadFileWithOffset() throws Exception {
        String path = "offset.txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        
        vfs.writeFile(path, content, false);

        VirtualFileSystem.FileReadResult result = vfs.readFile(path, 2, null);
        assertEquals("Line 3\nLine 4\nLine 5", result.getContent());
        assertEquals(5, result.getTotalLines());
        assertEquals(3, result.getLinesRead());
    }

    @Test
    public void testReadFileWithLimit() throws Exception {
        String path = "limit.txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        
        vfs.writeFile(path, content, false);

        VirtualFileSystem.FileReadResult result = vfs.readFile(path, 0, 3);
        assertEquals("Line 1\nLine 2\nLine 3", result.getContent());
        assertEquals(5, result.getTotalLines());
        assertEquals(3, result.getLinesRead());
        assertTrue(result.isTruncated());
    }

    @Test(expected = IOException.class)
    public void testReadNonexistentFile() throws Exception {
        vfs.readFile("nonexistent.txt", null, null);
    }

    @Test
    public void testListFiles() throws Exception {
        // Create some files
        vfs.writeFile("file1.txt", "content1", false);
        vfs.writeFile("file2.txt", "content2", false);
        vfs.writeFile("dir/file3.txt", "content3", false);

        // List files in root
        VirtualFileSystem.FileListResult result = vfs.listFiles(".", false);
        assertTrue(result.getFiles().size() >= 2);

        // Verify we can find our files
        boolean foundFile1 = false;
        boolean foundFile2 = false;
        for (VirtualFileSystem.FileInfo info : result.getFiles()) {
            if (info.getPath().endsWith("file1.txt")) foundFile1 = true;
            if (info.getPath().endsWith("file2.txt")) foundFile2 = true;
        }
        assertTrue(foundFile1);
        assertTrue(foundFile2);
    }

    @Test
    public void testListFilesRecursive() throws Exception {
        vfs.writeFile("file1.txt", "content1", false);
        vfs.writeFile("dir/file2.txt", "content2", false);
        vfs.writeFile("dir/subdir/file3.txt", "content3", false);

        VirtualFileSystem.FileListResult result = vfs.listFiles(".", true);
        
        // Should find all files recursively
        boolean foundFile1 = false;
        boolean foundFile3 = false;
        for (VirtualFileSystem.FileInfo info : result.getFiles()) {
            if (info.getPath().endsWith("file1.txt")) foundFile1 = true;
            if (info.getPath().endsWith("file3.txt")) foundFile3 = true;
        }
        assertTrue(foundFile1);
        assertTrue(foundFile3);
    }

    @Test
    public void testDeleteFile() throws Exception {
        String path = "delete.txt";
        vfs.writeFile(path, "content", false);

        assertTrue(vfs.deleteFile(path));

        try {
            vfs.readFile(path, null, null);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test(expected = IOException.class)
    public void testDeleteNonexistentFile() throws Exception {
        vfs.deleteFile("nonexistent.txt");
    }

    @Test(expected = IOException.class)
    public void testDeleteNonEmptyDirectory() throws Exception {
        vfs.writeFile("dir/file.txt", "content", false);
        vfs.deleteFile("dir");
    }

    @Test
    public void testSearchFiles() throws Exception {
        vfs.writeFile("file1.txt", "Hello World\nFoo Bar", false);
        vfs.writeFile("file2.txt", "Test Hello\nAnother line", false);
        vfs.writeFile("file3.log", "No match here", false);

        // Search for "Hello"
        VirtualFileSystem.FileSearchResult result = vfs.searchFiles(".", "Hello", "*.txt");
        
        assertEquals(2, result.getMatches().size());
        
        // Verify matches
        for (VirtualFileSystem.SearchMatch match : result.getMatches()) {
            assertTrue(match.getLine().contains("Hello"));
            assertTrue(match.getFile().endsWith(".txt"));
        }
    }

    @Test
    public void testSearchFilesWithPattern() throws Exception {
        vfs.writeFile("test.txt", "Line with pattern", false);
        vfs.writeFile("test.log", "Line with pattern", false);

        VirtualFileSystem.FileSearchResult result = vfs.searchFiles(".", "pattern", "*.txt");
        
        // Should only match .txt file
        assertEquals(1, result.getMatches().size());
        assertTrue(result.getMatches().get(0).getFile().endsWith(".txt"));
    }

    @Test
    public void testGetFileInfo() throws Exception {
        String path = "info.txt";
        String content = "Test content";
        vfs.writeFile(path, content, false);

        VirtualFileSystem.FileInfo info = vfs.getFileInfo(path);
        
        assertNotNull(info);
        assertEquals(path, info.getPath());
        assertFalse(info.isDirectory());
        assertTrue(info.getSize() > 0);
        assertTrue(info.getLastModified() > 0);
    }

    @Test
    public void testGetFileInfoForDirectory() throws Exception {
        vfs.writeFile("dir/file.txt", "content", false);

        VirtualFileSystem.FileInfo info = vfs.getFileInfo("dir");
        
        assertNotNull(info);
        assertTrue(info.isDirectory());
    }

    @Test(expected = IOException.class)
    public void testGetFileInfoNonexistent() throws Exception {
        vfs.getFileInfo("nonexistent.txt");
    }

    @Test
    public void testWriteCreatesParentDirectories() throws Exception {
        String path = "deep/nested/dir/file.txt";
        vfs.writeFile(path, "content", false);

        VirtualFileSystem.FileReadResult result = vfs.readFile(path, null, null);
        assertEquals("content", result.getContent());
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalInWrite() throws Exception {
        vfs.writeFile("../../etc/passwd", "malicious", false);
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalInRead() throws Exception {
        vfs.readFile("../../etc/passwd", null, null);
    }

    @Test(expected = SecurityException.class)
    public void testPathTraversalInDelete() throws Exception {
        vfs.deleteFile("../../etc/passwd");
    }

    @Test(expected = IOException.class)
    public void testReadFileTooLarge() throws Exception {
        // Create a file larger than MAX_FILE_SIZE (10MB)
        String path = "large.txt";
        StringBuilder largeContent = new StringBuilder();
        // Create content > 10MB
        for (int i = 0; i < 11 * 1024 * 1024; i++) {
            largeContent.append("a");
        }
        
        vfs.writeFile(path, largeContent.toString(), false);
        vfs.readFile(path, null, null);
    }

    @Test(expected = IOException.class)
    public void testWriteFileTooLarge() throws Exception {
        // Try to write content larger than MAX_FILE_SIZE
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 11 * 1024 * 1024; i++) {
            largeContent.append("a");
        }
        
        vfs.writeFile("large.txt", largeContent.toString(), false);
    }

    @Test
    public void testDeleteEmptyDirectory() throws Exception {
        // Create an empty directory by creating and then deleting a file
        vfs.writeFile("emptydir/temp.txt", "temp", false);
        vfs.deleteFile("emptydir/temp.txt");
        
        // Now delete the empty directory
        assertTrue(vfs.deleteFile("emptydir"));
    }

    @Test(expected = IOException.class)
    public void testReadDirectory() throws Exception {
        vfs.writeFile("dir/file.txt", "content", false);
        vfs.readFile("dir", null, null);
    }

    @Test(expected = IOException.class)
    public void testListFilesOnFile() throws Exception {
        vfs.writeFile("file.txt", "content", false);
        vfs.listFiles("file.txt", false);
    }

    @Test(expected = IOException.class)
    public void testListNonexistentDirectory() throws Exception {
        vfs.listFiles("nonexistent", false);
    }

    @Test(expected = IOException.class)
    public void testSearchNonexistentDirectory() throws Exception {
        vfs.searchFiles("nonexistent", "pattern", null);
    }

    @Test(expected = IOException.class)
    public void testSearchOnFile() throws Exception {
        vfs.writeFile("file.txt", "content", false);
        vfs.searchFiles("file.txt", "pattern", null);
    }

    @Test
    public void testSearchWithInvalidRegex() {
        try {
            vfs.writeFile("test.txt", "content", false);
            vfs.searchFiles(".", "[invalid(", null);
            fail("Should have thrown exception for invalid regex");
        } catch (Exception e) {
            // Expected - invalid regex pattern
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testGlobPatternMatching() throws Exception {
        vfs.writeFile("test.txt", "match", false);
        vfs.writeFile("test.log", "match", false);
        vfs.writeFile("data.txt", "match", false);

        // Search only .txt files
        VirtualFileSystem.FileSearchResult result = vfs.searchFiles(".", "match", "*.txt");
        
        assertEquals(2, result.getMatches().size());
        for (VirtualFileSystem.SearchMatch match : result.getMatches()) {
            assertTrue(match.getFile().endsWith(".txt"));
        }
    }

    @Test
    public void testGlobPatternWithQuestionMark() throws Exception {
        vfs.writeFile("test1.txt", "match", false);
        vfs.writeFile("test2.txt", "match", false);
        vfs.writeFile("test10.txt", "match", false);

        // Search with ? wildcard (single character)
        VirtualFileSystem.FileSearchResult result = vfs.searchFiles(".", "match", "test?.txt");
        
        assertEquals(2, result.getMatches().size());
    }

    @Test
    public void testReadFileWithOffsetBeyondEnd() throws Exception {
        vfs.writeFile("small.txt", "Line 1\nLine 2", false);

        VirtualFileSystem.FileReadResult result = vfs.readFile("small.txt", 100, null);
        
        assertEquals("", result.getContent());
        assertEquals(2, result.getTotalLines());
        assertEquals(0, result.getLinesRead());
        assertFalse(result.isTruncated());
    }

    @Test
    public void testReadFileWithZeroLimit() throws Exception {
        vfs.writeFile("test.txt", "Line 1\nLine 2\nLine 3", false);

        VirtualFileSystem.FileReadResult result = vfs.readFile("test.txt", 0, 0);
        
        assertEquals("", result.getContent());
        assertEquals(3, result.getTotalLines());
        assertEquals(0, result.getLinesRead());
        assertTrue(result.isTruncated());
    }

    @Test
    public void testListFilesNullPath() throws Exception {
        // Null path should default to current directory
        VirtualFileSystem.FileListResult result = vfs.listFiles(null, false);
        assertNotNull(result);
        assertNotNull(result.getFiles());
    }

    @Test
    public void testSearchFilesNullPath() throws Exception {
        vfs.writeFile("test.txt", "content", false);
        
        // Null path should default to current directory
        VirtualFileSystem.FileSearchResult result = vfs.searchFiles(null, "content", null);
        assertNotNull(result);
        assertTrue(result.getMatches().size() >= 1);
    }
}