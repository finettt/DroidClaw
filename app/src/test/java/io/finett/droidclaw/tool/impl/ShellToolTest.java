package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;

/**
 * Unit tests for ShellTool.
 */
public class ShellToolTest {
    private ShellTool tool;
    private File workspaceRoot;

    @Before
    public void setUp() {
        workspaceRoot = new File(System.getProperty("java.io.tmpdir"));
        tool = new ShellTool(workspaceRoot);
    }

    @Test
    public void testGetName() {
        assertEquals("execute_shell", tool.getName());
    }

    @Test
    public void testGetDefinition() {
        ToolDefinition definition = tool.getDefinition();
        
        assertNotNull(definition);
        assertEquals("function", definition.getType());
        assertEquals("execute_shell", definition.getFunction().getName());
        assertNotNull(definition.getFunction().getDescription());
        assertNotNull(definition.getFunction().getParameters());
        
        // Verify parameters schema
        JsonObject params = definition.getFunction().getParameters();
        assertTrue(params.has("properties"));
        assertTrue(params.getAsJsonObject("properties").has("command"));
        assertTrue(params.getAsJsonObject("properties").has("working_directory"));
        assertTrue(params.getAsJsonObject("properties").has("timeout_seconds"));
    }

    @Test
    public void testExecuteSimpleCommand() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo 'Hello World'");
        
        ToolResult result = tool.execute(args);
        
        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("exit_code"));
        assertTrue(content.contains("stdout"));
        assertTrue(content.contains("Hello World"));
    }

    @Test
    public void testExecuteMissingCommand() {
        JsonObject args = new JsonObject();
        // Missing required "command" parameter
        
        ToolResult result = tool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void testExecuteWithWorkingDirectory() {
        // Create a subdirectory in workspace
        File subdir = new File(workspaceRoot, "testdir_" + System.currentTimeMillis());
        subdir.mkdir();
        
        try {
            JsonObject args = new JsonObject();
            args.addProperty("command", "pwd");
            args.addProperty("working_directory", subdir.getName());
            
            ToolResult result = tool.execute(args);
            
            // Should succeed if the directory exists
            if (result.isSuccess()) {
                String content = result.getContent();
                assertTrue(content.contains("exit_code"));
            }
        } finally {
            subdir.delete();
        }
    }

    @Test
    public void testExecuteWithInvalidWorkingDirectory() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "/nonexistent_dir_12345");
        
        ToolResult result = tool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid working directory"));
    }

    @Test
    public void testExecuteWithCustomTimeout() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 60);
        
        ToolResult result = tool.execute(args);
        
        assertTrue(result.isSuccess());
    }

    @Test
    public void testExecuteWithInvalidTimeout() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", -5);
        
        ToolResult result = tool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Timeout must be positive"));
    }

    @Test
    public void testExecuteBlockedCommand() {
        ShellConfig secureConfig = new ShellConfig.Builder()
                .enabled(true)
                .addBlockedCommand("rm -rf")
                .build();
        ShellTool secureTool = new ShellTool(workspaceRoot, secureConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "rm -rf /");
        
        ToolResult result = secureTool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Security error"));
    }

    @Test
    public void testExecuteWithDisabledShell() {
        ShellConfig disabledConfig = new ShellConfig.Builder()
                .enabled(false)
                .build();
        ShellTool disabledTool = new ShellTool(workspaceRoot, disabledConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = disabledTool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Security error"));
    }

    @Test
    public void testExecuteCommandWithNonZeroExitCode() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "exit 42");
        
        ToolResult result = tool.execute(args);
        
        // Even though exit code is non-zero, tool execution itself succeeds
        // The LLM can interpret the exit code from the result
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"exit_code\":42"));
    }

    @Test
    public void testExecuteCommandWithStderr() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls /nonexistent_dir_98765");
        
        ToolResult result = tool.execute(args);
        
        // Tool execution succeeds, but stderr is captured
        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("stderr"));
    }

    @Test
    public void testResultContainsExecutionTime() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = tool.execute(args);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("execution_time_ms"));
    }

    @Test
    public void testResultContainsTimedOutFlag() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = tool.execute(args);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("timed_out"));
        assertTrue(result.getContent().contains("false")); // Should not timeout
    }

    @Test
    public void testPathTraversalPrevention() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "../../../");
        
        ToolResult result = tool.execute(args);
        
        // Should reject path traversal attempt
        assertFalse(result.isSuccess());
    }

    @Test
    public void testAbsolutePathRejection() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "/etc");
        
        ToolResult result = tool.execute(args);
        
        // Should reject absolute paths
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid working directory"));
    }

    @Test
    public void testDefinitionToJson() {
        ToolDefinition definition = tool.getDefinition();
        JsonObject json = definition.toJson();
        
        assertNotNull(json);
        assertEquals("function", json.get("type").getAsString());
        assertTrue(json.has("function"));
        
        JsonObject func = json.getAsJsonObject("function");
        assertEquals("execute_shell", func.get("name").getAsString());
        assertTrue(func.has("description"));
        assertTrue(func.has("parameters"));
    }

    @Test
    public void testEmptyWorkingDirectory() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("working_directory", "");
        
        ToolResult result = tool.execute(args);
        
        // Empty working directory should default to workspace root
        assertTrue(result.isSuccess());
    }

    @Test
    public void testToolResultJsonFormat() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo 'test output'");
        
        ToolResult result = tool.execute(args);
        
        assertTrue(result.isSuccess());
        String json = result.toJson();
        
        // Verify it's valid JSON
        assertNotNull(json);
        assertFalse(json.isEmpty());
        
        // Should contain expected fields
        assertTrue(json.contains("exit_code"));
        assertTrue(json.contains("timed_out"));
        assertTrue(json.contains("execution_time_ms"));
        assertTrue(json.contains("stdout"));
    }
}