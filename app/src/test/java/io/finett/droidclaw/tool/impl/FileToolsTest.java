package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;

public class FileToolsTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private VirtualFileSystem vfs;
    private PathValidator pathValidator;
    private File workspaceRoot;
    private FileReadTool fileReadTool;
    private FileWriteTool fileWriteTool;
    private FileListTool fileListTool;
    private FileDeleteTool fileDeleteTool;
    private FileSearchTool fileSearchTool;
    private FileEditTool fileEditTool;
    private FileInfoTool fileInfoTool;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = tempFolder.newFolder("workspace");
        pathValidator = new PathValidator(workspaceRoot);
        vfs = new VirtualFileSystem(pathValidator);

        // Initialize all tools
        fileReadTool = new FileReadTool(vfs);
        fileWriteTool = new FileWriteTool(vfs);
        fileListTool = new FileListTool(vfs);
        fileDeleteTool = new FileDeleteTool(vfs);
        fileSearchTool = new FileSearchTool(vfs);
        fileEditTool = new FileEditTool(vfs, pathValidator);
        fileInfoTool = new FileInfoTool(vfs);
    }

    @Test
    public void testFileWriteToolSuccess() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");
        args.addProperty("content", "Hello, World!");

        ToolResult result = fileWriteTool.execute(args);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("test.txt"));
    }

    @Test
    public void testFileWriteToolMissingPath() {
        JsonObject args = new JsonObject();
        args.addProperty("content", "Hello");

        ToolResult result = fileWriteTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: path"));
    }

    @Test
    public void testFileReadToolSuccess() {
        // First write a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "read_test.txt");
        writeArgs.addProperty("content", "Test content");
        fileWriteTool.execute(writeArgs);

        // Then read it
        JsonObject readArgs = new JsonObject();
        readArgs.addProperty("path", "read_test.txt");

        ToolResult result = fileReadTool.execute(readArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Test content"));
    }

    @Test
    public void testFileReadToolWithOffset() {
        // Write multiline file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "multiline.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2\nLine 3");
        fileWriteTool.execute(writeArgs);

        // Read with offset
        JsonObject readArgs = new JsonObject();
        readArgs.addProperty("path", "multiline.txt");
        readArgs.addProperty("offset", 1);

        ToolResult result = fileReadTool.execute(readArgs);

        assertTrue(result.isSuccess());
        JsonObject resultJson = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue(resultJson.get("content").getAsString().contains("Line 2"));
    }

    @Test
    public void testFileListToolSuccess() {
        // Create some files
        JsonObject writeArgs1 = new JsonObject();
        writeArgs1.addProperty("path", "file1.txt");
        writeArgs1.addProperty("content", "content1");
        fileWriteTool.execute(writeArgs1);

        JsonObject writeArgs2 = new JsonObject();
        writeArgs2.addProperty("path", "file2.txt");
        writeArgs2.addProperty("content", "content2");
        fileWriteTool.execute(writeArgs2);

        // List files
        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("path", ".");

        ToolResult result = fileListTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("file1.txt") || result.getContent().contains("file2.txt"));
    }

    @Test
    public void testFileListToolRecursive() {
        // Create nested files
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "dir/subdir/file.txt");
        writeArgs.addProperty("content", "nested content");
        fileWriteTool.execute(writeArgs);

        // List recursively
        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("path", ".");
        listArgs.addProperty("recursive", true);

        ToolResult result = fileListTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("file.txt"));
    }

    @Test
    public void testFileDeleteToolSuccess() {
        // Create a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "delete_me.txt");
        writeArgs.addProperty("content", "content");
        fileWriteTool.execute(writeArgs);

        // Delete it
        JsonObject deleteArgs = new JsonObject();
        deleteArgs.addProperty("path", "delete_me.txt");

        ToolResult result = fileDeleteTool.execute(deleteArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("deleted"));
    }

    @Test
    public void testFileSearchToolSuccess() {
        // Create files with searchable content
        JsonObject writeArgs1 = new JsonObject();
        writeArgs1.addProperty("path", "search1.txt");
        writeArgs1.addProperty("content", "Find this keyword");
        fileWriteTool.execute(writeArgs1);

        JsonObject writeArgs2 = new JsonObject();
        writeArgs2.addProperty("path", "search2.txt");
        writeArgs2.addProperty("content", "Another keyword here");
        fileWriteTool.execute(writeArgs2);

        // Search for pattern
        JsonObject searchArgs = new JsonObject();
        searchArgs.addProperty("pattern", "keyword");
        searchArgs.addProperty("file_pattern", "*.txt");

        ToolResult result = fileSearchTool.execute(searchArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("keyword"));
    }

    @Test
    public void testFileEditToolReplace() throws Exception {
        // Create a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "edit.txt");
        writeArgs.addProperty("content", "Hello World\nHello Everyone");
        fileWriteTool.execute(writeArgs);

        // Edit with replace
        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "edit.txt");
        editArgs.addProperty("operation", "replace");
        editArgs.addProperty("search", "Hello");
        editArgs.addProperty("replacement", "Hi");

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());
        
        // Verify the replacement
        JsonObject readArgs = new JsonObject();
        readArgs.addProperty("path", "edit.txt");
        ToolResult readResult = fileReadTool.execute(readArgs);
        assertTrue(readResult.getContent().contains("Hi World"));
    }

    @Test
    public void testFileEditToolInsert() throws Exception {
        // Create a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "insert.txt");
        writeArgs.addProperty("content", "Line 1\nLine 3");
        fileWriteTool.execute(writeArgs);

        // Insert a line
        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "insert.txt");
        editArgs.addProperty("operation", "insert");
        editArgs.addProperty("line_number", 2);
        editArgs.addProperty("content", "Line 2");

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());
    }

    @Test
    public void testFileEditToolDeleteLines() throws Exception {
        // Create a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "delete_lines.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2\nLine 3\nLine 4");
        fileWriteTool.execute(writeArgs);

        // Delete lines
        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "delete_lines.txt");
        editArgs.addProperty("operation", "delete_lines");
        editArgs.addProperty("line_number", 2);
        editArgs.addProperty("count", 2);

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());
    }

    @Test
    public void testFileInfoToolSuccess() {
        // Create a file
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "info.txt");
        writeArgs.addProperty("content", "Test content");
        fileWriteTool.execute(writeArgs);

        // Get file info
        JsonObject infoArgs = new JsonObject();
        infoArgs.addProperty("path", "info.txt");

        ToolResult result = fileInfoTool.execute(infoArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("info.txt"));
        assertTrue(result.getContent().contains("\"type\":\"file\""));
    }

    @Test
    public void testToolDefinitions() {
        // Verify all tools have proper definitions
        assertNotNull(fileReadTool.getDefinition());
        assertNotNull(fileWriteTool.getDefinition());
        assertNotNull(fileListTool.getDefinition());
        assertNotNull(fileDeleteTool.getDefinition());
        assertNotNull(fileSearchTool.getDefinition());
        assertNotNull(fileEditTool.getDefinition());
        assertNotNull(fileInfoTool.getDefinition());

        assertEquals("read_file", fileReadTool.getName());
        assertEquals("write_file", fileWriteTool.getName());
        assertEquals("list_files", fileListTool.getName());
        assertEquals("delete_file", fileDeleteTool.getName());
        assertEquals("search_files", fileSearchTool.getName());
        assertEquals("edit_file", fileEditTool.getName());
        assertEquals("file_info", fileInfoTool.getName());
    }

    @Test
    public void testSecurityPathTraversal() {
        // Test that path traversal is blocked in all tools
        JsonObject maliciousArgs = new JsonObject();
        maliciousArgs.addProperty("path", "../../etc/passwd");
        maliciousArgs.addProperty("content", "malicious");

        ToolResult writeResult = fileWriteTool.execute(maliciousArgs);
        assertFalse(writeResult.isSuccess());
        assertTrue(writeResult.getError().contains("Security error") || 
                   writeResult.getError().contains("traversal"));

        ToolResult readResult = fileReadTool.execute(maliciousArgs);
        assertFalse(readResult.isSuccess());

        ToolResult deleteResult = fileDeleteTool.execute(maliciousArgs);
        assertFalse(deleteResult.isSuccess());
    }
}