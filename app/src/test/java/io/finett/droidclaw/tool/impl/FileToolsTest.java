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
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "read_test.txt");
        writeArgs.addProperty("content", "Test content");
        fileWriteTool.execute(writeArgs);

        JsonObject readArgs = new JsonObject();
        readArgs.addProperty("path", "read_test.txt");

        ToolResult result = fileReadTool.execute(readArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Test content"));
    }

    @Test
    public void testFileReadToolWithOffset() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "multiline.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2\nLine 3");
        fileWriteTool.execute(writeArgs);

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
        JsonObject writeArgs1 = new JsonObject();
        writeArgs1.addProperty("path", "file1.txt");
        writeArgs1.addProperty("content", "content1");
        fileWriteTool.execute(writeArgs1);

        JsonObject writeArgs2 = new JsonObject();
        writeArgs2.addProperty("path", "file2.txt");
        writeArgs2.addProperty("content", "content2");
        fileWriteTool.execute(writeArgs2);

        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("path", ".");

        ToolResult result = fileListTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("file1.txt") || result.getContent().contains("file2.txt"));
    }

    @Test
    public void testFileListToolRecursive() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "dir/subdir/file.txt");
        writeArgs.addProperty("content", "nested content");
        fileWriteTool.execute(writeArgs);

        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("path", ".");
        listArgs.addProperty("recursive", true);

        ToolResult result = fileListTool.execute(listArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("file.txt"));
    }

    @Test
    public void testFileDeleteToolSuccess() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "delete_me.txt");
        writeArgs.addProperty("content", "content");
        fileWriteTool.execute(writeArgs);

        JsonObject deleteArgs = new JsonObject();
        deleteArgs.addProperty("path", "delete_me.txt");

        ToolResult result = fileDeleteTool.execute(deleteArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("deleted"));
    }

    @Test
    public void testFileSearchToolSuccess() {
        JsonObject writeArgs1 = new JsonObject();
        writeArgs1.addProperty("path", "search1.txt");
        writeArgs1.addProperty("content", "Find this keyword");
        fileWriteTool.execute(writeArgs1);

        JsonObject writeArgs2 = new JsonObject();
        writeArgs2.addProperty("path", "search2.txt");
        writeArgs2.addProperty("content", "Another keyword here");
        fileWriteTool.execute(writeArgs2);

        JsonObject searchArgs = new JsonObject();
        searchArgs.addProperty("pattern", "keyword");
        searchArgs.addProperty("file_pattern", "*.txt");

        ToolResult result = fileSearchTool.execute(searchArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("keyword"));
    }

    @Test
    public void testFileEditToolReplace() throws Exception {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "edit.txt");
        writeArgs.addProperty("content", "Hello World\nHello Everyone");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "edit.txt");
        editArgs.addProperty("operation", "replace");
        editArgs.addProperty("search", "Hello");
        editArgs.addProperty("replacement", "Hi");

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());

        JsonObject readArgs = new JsonObject();
        readArgs.addProperty("path", "edit.txt");
        ToolResult readResult = fileReadTool.execute(readArgs);
        assertTrue(readResult.getContent().contains("Hi World"));
    }

    @Test
    public void testFileEditToolInsert() throws Exception {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "insert.txt");
        writeArgs.addProperty("content", "Line 1\nLine 3");
        fileWriteTool.execute(writeArgs);

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
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "delete_lines.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2\nLine 3\nLine 4");
        fileWriteTool.execute(writeArgs);

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
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "info.txt");
        writeArgs.addProperty("content", "Test content");
        fileWriteTool.execute(writeArgs);

        JsonObject infoArgs = new JsonObject();
        infoArgs.addProperty("path", "info.txt");

        ToolResult result = fileInfoTool.execute(infoArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("info.txt"));
        assertTrue(result.getContent().contains("\"type\":\"file\""));
    }

    @Test
    public void testToolDefinitions() {
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

    @Test
    public void testFileWriteToolMissingContent() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");

        ToolResult result = fileWriteTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: content"));
    }

    @Test
    public void testFileWriteToolWithAppend() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "append.txt");
        writeArgs.addProperty("content", "First line");
        fileWriteTool.execute(writeArgs);

        JsonObject appendArgs = new JsonObject();
        appendArgs.addProperty("path", "append.txt");
        appendArgs.addProperty("content", "\nSecond line");
        appendArgs.addProperty("append", true);

        ToolResult result = fileWriteTool.execute(appendArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"appended\":true"));
    }

    @Test
    public void testFileReadToolMissingPath() {
        JsonObject args = new JsonObject();

        ToolResult result = fileReadTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: path"));
    }

    @Test
    public void testFileReadToolNonexistentFile() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "nonexistent.txt");

        ToolResult result = fileReadTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found") || result.getError().contains("Failed"));
    }

    @Test
    public void testFileDeleteToolMissingPath() {
        JsonObject args = new JsonObject();

        ToolResult result = fileDeleteTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: path"));
    }

    @Test
    public void testFileDeleteToolNonexistentFile() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "nonexistent.txt");

        ToolResult result = fileDeleteTool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void testFileSearchToolMissingPattern() {
        JsonObject args = new JsonObject();
        args.addProperty("path", ".");

        ToolResult result = fileSearchTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: pattern"));
    }

    @Test
    public void testFileSearchToolWithoutFilePattern() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "searchable content");
        fileWriteTool.execute(writeArgs);

        JsonObject searchArgs = new JsonObject();
        searchArgs.addProperty("pattern", "searchable");

        ToolResult result = fileSearchTool.execute(searchArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("searchable"));
    }

    @Test
    public void testFileEditToolMissingPath() {
        JsonObject args = new JsonObject();
        args.addProperty("operation", "replace");

        ToolResult result = fileEditTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: path"));
    }

    @Test
    public void testFileEditToolMissingOperation() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");

        ToolResult result = fileEditTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: operation"));
    }

    @Test
    public void testFileEditToolInvalidOperation() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Test");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "invalid_op");

        ToolResult result = fileEditTool.execute(editArgs);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid operation"));
    }

    @Test
    public void testFileEditToolReplaceMissingSearch() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Test");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "replace");
        editArgs.addProperty("replacement", "New");

        ToolResult result = fileEditTool.execute(editArgs);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument") || result.getError().contains("search"));
    }

    @Test
    public void testFileEditToolInsertMissingLineNumber() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Line 1");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "insert");
        editArgs.addProperty("content", "New line");

        ToolResult result = fileEditTool.execute(editArgs);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument") || result.getError().contains("line_number"));
    }

    @Test
    public void testFileEditToolDeleteLinesMissingLineNumber() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "delete_lines");

        ToolResult result = fileEditTool.execute(editArgs);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument") || result.getError().contains("line_number"));
    }

    @Test
    public void testFileEditToolNonexistentFile() {
        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "nonexistent.txt");
        editArgs.addProperty("operation", "replace");
        editArgs.addProperty("search", "old");
        editArgs.addProperty("replacement", "new");

        ToolResult result = fileEditTool.execute(editArgs);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found") || result.getError().contains("Failed"));
    }

    @Test
    public void testFileInfoToolMissingPath() {
        JsonObject args = new JsonObject();

        ToolResult result = fileInfoTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required argument: path"));
    }

    @Test
    public void testFileInfoToolNonexistentFile() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "nonexistent.txt");

        ToolResult result = fileInfoTool.execute(args);

        assertFalse(result.isSuccess());
    }

    @Test
    public void testFileInfoToolForDirectory() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "testdir/file.txt");
        writeArgs.addProperty("content", "content");
        fileWriteTool.execute(writeArgs);

        JsonObject infoArgs = new JsonObject();
        infoArgs.addProperty("path", "testdir");

        ToolResult result = fileInfoTool.execute(infoArgs);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"type\":\"directory\""));
    }

    @Test
    public void testFileEditToolInsertAtEnd() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "insert");
        editArgs.addProperty("line_number", 3);
        editArgs.addProperty("content", "Line 3");

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());
    }

    @Test
    public void testFileEditToolDeleteMultipleLines() {
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("path", "test.txt");
        writeArgs.addProperty("content", "Line 1\nLine 2\nLine 3\nLine 4\nLine 5");
        fileWriteTool.execute(writeArgs);

        JsonObject editArgs = new JsonObject();
        editArgs.addProperty("path", "test.txt");
        editArgs.addProperty("operation", "delete_lines");
        editArgs.addProperty("line_number", 2);
        editArgs.addProperty("count", 3);

        ToolResult result = fileEditTool.execute(editArgs);

        assertTrue(result.isSuccess());
        JsonObject resultJson = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals(3, resultJson.get("changes_made").getAsInt());
    }
}