package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

import io.finett.droidclaw.python.PythonConfig;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PythonToolTest {

    private PythonTool pythonTool;
    private File workspaceRoot;

    @Before
    public void setUp() {
        workspaceRoot = new File("/tmp/workspace");
        workspaceRoot.mkdirs();
        
        PythonConfig config = PythonConfig.builder()
                .timeout(30)
                .enablePip(true)
                .build();
        
        pythonTool = new PythonTool(RuntimeEnvironment.getApplication(), workspaceRoot, config);
    }

    @Test
    public void testToolName() {
        assertEquals("Tool name should be 'execute_python'", "execute_python", pythonTool.getName());
    }

    @Test
    public void testToolDefinition() {
        ToolDefinition definition = pythonTool.getDefinition();
        
        assertNotNull("Tool definition should not be null", definition);
        assertEquals("Function name should match tool name", "execute_python", 
                definition.getFunction().getName());
        assertNotNull("Parameters should not be null", definition.getFunction().getParameters());
    }

    @Test
    public void testExecuteWithNoArguments() {
        JsonObject arguments = new JsonObject();
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention required parameters", 
                result.toJson().contains("Must provide one of"));
    }

    @Test
    public void testExecuteWithEmptyCode() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "");
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention required parameters",
                result.toJson().contains("Must provide one of"));
    }

    @Test
    public void testExecuteWithMultipleModes() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("package", "requests");
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention mutually exclusive",
                result.toJson().contains("Can only provide one of"));
    }

    @Test
    public void testExecuteWithInvalidTimeout() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 0);
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention timeout range",
                result.toJson().contains("Timeout must be between"));
    }

    @Test
    public void testExecuteWithExcessiveTimeout() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 400);
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention timeout range",
                result.toJson().contains("Timeout must be between"));
    }

    @Test
    public void testExecuteWithInvalidScriptPath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "/absolute/path/script.py");
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention invalid path",
                result.toJson().contains("Invalid script path"));
    }

    @Test
    public void testExecuteWithDirectoryTraversalPath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "../../../etc/passwd");
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
    }

    @Test
    public void testExecuteWithNonexistentScript() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "nonexistent.py");
        ToolResult result = pythonTool.execute(arguments);
        
        assertFalse("Result should be an error", result.isSuccess());
        assertTrue("Error message should mention invalid path",
                result.toJson().contains("Invalid script path"));
    }

    @Test
    public void testToolDefinitionParameters() {
        ToolDefinition definition = pythonTool.getDefinition();
        JsonObject params = definition.getFunction().getParameters();
        
        assertTrue("Should have code parameter", 
                params.getAsJsonObject("properties").has("code"));
        assertTrue("Should have script_path parameter", 
                params.getAsJsonObject("properties").has("script_path"));
        assertTrue("Should have package parameter", 
                params.getAsJsonObject("properties").has("package"));
        assertTrue("Should have timeout_seconds parameter", 
                params.getAsJsonObject("properties").has("timeout_seconds"));
    }
}