package io.finett.droidclaw.tool;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.List;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.python.PythonExecutor;
import io.finett.droidclaw.shell.ShellExecutor;

/**
 * Unit tests for ToolRegistry.
 */
@RunWith(RobolectricTestRunner.class)
public class ToolRegistryTest {

    private ToolRegistry toolRegistry;
    private Context context;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        toolRegistry = new ToolRegistry(context);
    }

    @Test
    public void testToolRegistryInitialization() {
        assertNotNull("ToolRegistry should be initialized", toolRegistry);
        assertTrue("Should have registered tools", toolRegistry.getToolCount() > 0);
    }

    @Test
    public void testGetToolCount() {
        int toolCount = toolRegistry.getToolCount();
        // Should have 10 tools: 7 file tools + shell + python + heartbeat_ok
        assertEquals("Should have exactly 10 tools", 10, toolCount);
    }

    @Test
    public void testGetAllTools() {
        List<Tool> tools = toolRegistry.getAllTools();
        assertNotNull("Tools list should not be null", tools);
        assertEquals("Should return all registered tools", 10, tools.size());
    }

    @Test
    public void testGetToolByName_FileRead() {
        Tool tool = toolRegistry.getTool("read_file");
        assertNotNull("read_file tool should exist", tool);
        assertEquals("Tool name should match", "read_file", tool.getName());
    }

    @Test
    public void testGetToolByName_FileWrite() {
        Tool tool = toolRegistry.getTool("write_file");
        assertNotNull("write_file tool should exist", tool);
        assertEquals("Tool name should match", "write_file", tool.getName());
    }

    @Test
    public void testGetToolByName_FileEdit() {
        Tool tool = toolRegistry.getTool("edit_file");
        assertNotNull("edit_file tool should exist", tool);
        assertEquals("Tool name should match", "edit_file", tool.getName());
    }

    @Test
    public void testGetToolByName_FileList() {
        Tool tool = toolRegistry.getTool("list_files");
        assertNotNull("list_files tool should exist", tool);
        assertEquals("Tool name should match", "list_files", tool.getName());
    }

    @Test
    public void testGetToolByName_FileDelete() {
        Tool tool = toolRegistry.getTool("delete_file");
        assertNotNull("delete_file tool should exist", tool);
        assertEquals("Tool name should match", "delete_file", tool.getName());
    }

    @Test
    public void testGetToolByName_FileInfo() {
        Tool tool = toolRegistry.getTool("file_info");
        assertNotNull("file_info tool should exist", tool);
        assertEquals("Tool name should match", "file_info", tool.getName());
    }

    @Test
    public void testGetToolByName_FileSearch() {
        Tool tool = toolRegistry.getTool("search_files");
        assertNotNull("search_files tool should exist", tool);
        assertEquals("Tool name should match", "search_files", tool.getName());
    }

    @Test
    public void testGetToolByName_Shell() {
        Tool tool = toolRegistry.getTool("execute_shell");
        assertNotNull("execute_shell tool should exist", tool);
        assertEquals("Tool name should match", "execute_shell", tool.getName());
    }

    @Test
    public void testGetToolByName_Python() {
        Tool tool = toolRegistry.getTool("execute_python");
        assertNotNull("execute_python tool should exist", tool);
        assertEquals("Tool name should match", "execute_python", tool.getName());
    }

    @Test
    public void testGetToolByName_NonExistent() {
        Tool tool = toolRegistry.getTool("non_existent_tool");
        assertNull("Non-existent tool should return null", tool);
    }

    @Test
    public void testHasToolWithName_Existing() {
        assertTrue("Should find read_file tool", toolRegistry.hasToolWithName("read_file"));
        assertTrue("Should find execute_shell tool", toolRegistry.hasToolWithName("execute_shell"));
        assertTrue("Should find execute_python tool", toolRegistry.hasToolWithName("execute_python"));
    }

    @Test
    public void testHasToolWithName_NonExisting() {
        assertFalse("Should not find non-existent tool", toolRegistry.hasToolWithName("non_existent"));
    }

    @Test
    public void testGetToolDefinitions() {
        JsonArray definitions = toolRegistry.getToolDefinitions();
        assertNotNull("Tool definitions should not be null", definitions);
        assertEquals("Should have definitions for all tools", 10, definitions.size());
        
        // Verify structure of first definition
        JsonObject firstDef = definitions.get(0).getAsJsonObject();
        assertTrue("Should have 'type' field", firstDef.has("type"));
        assertTrue("Should have 'function' field", firstDef.has("function"));
        assertEquals("Type should be 'function'", "function", firstDef.get("type").getAsString());
        
        JsonObject function = firstDef.getAsJsonObject("function");
        assertTrue("Function should have 'name'", function.has("name"));
        assertTrue("Function should have 'description'", function.has("description"));
        assertTrue("Function should have 'parameters'", function.has("parameters"));
    }

    @Test
    public void testExecuteTool_NonExistent() {
        JsonObject args = new JsonObject();
        ToolResult result = toolRegistry.executeTool("non_existent_tool", args);
        
        assertNotNull("Result should not be null", result);
        assertFalse("Result should indicate failure", result.isSuccess());
        assertTrue("Error should mention tool not found", 
            result.getError().contains("Tool not found"));
    }

    @Test
    public void testExecuteTool_FileList() {
        JsonObject args = new JsonObject();
        args.addProperty("path", ".");
        
        ToolResult result = toolRegistry.executeTool("list_files", args);
        assertNotNull("Result should not be null", result);
        // Result may succeed or fail depending on workspace setup
        // Just verify we get a result
    }

    @Test
    public void testExecuteTool_WithInvalidArguments() {
        // Try to execute read_file without required 'path' argument
        JsonObject args = new JsonObject();
        
        ToolResult result = toolRegistry.executeTool("read_file", args);
        assertNotNull("Result should not be null", result);
        // Should fail due to missing required parameter
    }

    @Test
    public void testGetVirtualFileSystem() {
        VirtualFileSystem vfs = toolRegistry.getVirtualFileSystem();
        assertNotNull("VirtualFileSystem should not be null", vfs);
    }

    @Test
    public void testGetShellExecutor() {
        ShellExecutor shellExecutor = toolRegistry.getShellExecutor();
        assertNotNull("ShellExecutor should not be null", shellExecutor);
    }

    @Test
    public void testGetPythonExecutor() {
        PythonExecutor pythonExecutor = toolRegistry.getPythonExecutor();
        assertNotNull("PythonExecutor should not be null", pythonExecutor);
    }

    @Test
    public void testToolDefinitions_AllHaveValidStructure() {
        JsonArray definitions = toolRegistry.getToolDefinitions();

        for (int i = 0; i < definitions.size(); i++) {
            JsonObject def = definitions.get(i).getAsJsonObject();

            assertTrue("Definition " + i + " should have 'type'", def.has("type"));
            assertEquals("Definition " + i + " type should be 'function'",
                "function", def.get("type").getAsString());

            assertTrue("Definition " + i + " should have 'function'", def.has("function"));
            JsonObject function = def.getAsJsonObject("function");

            assertTrue("Function should have 'name'", function.has("name"));
            assertTrue("Function should have 'description'", function.has("description"));
            assertTrue("Function should have 'parameters'", function.has("parameters"));

            String name = function.get("name").getAsString();
            assertNotNull("Function name should not be null", name);
            assertFalse("Function name should not be empty", name.isEmpty());

            // Parameters can be null for tools with no parameters (e.g., heartbeat_ok)
            if (function.has("parameters") && !function.get("parameters").isJsonNull()) {
                JsonObject parameters = function.getAsJsonObject("parameters");
                assertTrue("Parameters should have 'type'", parameters.has("type"));
                assertEquals("Parameters type should be 'object'",
                    "object", parameters.get("type").getAsString());
            }
        }
    }

    @Test
    public void testMultipleToolExecutions() {
        // Test that we can execute multiple tools in sequence
        JsonObject listArgs = new JsonObject();
        listArgs.addProperty("path", ".");
        
        ToolResult result1 = toolRegistry.executeTool("list_files", listArgs);
        assertNotNull("First result should not be null", result1);
        
        ToolResult result2 = toolRegistry.executeTool("list_files", listArgs);
        assertNotNull("Second result should not be null", result2);
        
        // Registry should handle multiple executions without issues
    }

    @Test
    public void testToolRegistry_ThreadSafety() throws InterruptedException {
        // Test concurrent access to tool registry
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                toolRegistry.getTool("read_file");
                toolRegistry.getToolDefinitions();
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                toolRegistry.getAllTools();
                toolRegistry.hasToolWithName("execute_shell");
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Should complete without errors
        assertEquals("Tool count should remain consistent", 10, toolRegistry.getToolCount());
    }
}