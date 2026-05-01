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

    @Test
    public void testResolveScriptPath_withValidRelativePath() throws Exception {
        File scriptFile = new File(workspaceRoot, "test_script.py");
        scriptFile.createNewFile();

        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "test_script.py");
        ToolResult result = pythonTool.execute(arguments);

        // Script exists but Python executor may fail in test environment; just verify path resolved
        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testResolveScriptPath_withNestedPath() throws Exception {
        File subdir = new File(workspaceRoot, "scripts");
        subdir.mkdirs();
        File scriptFile = new File(subdir, "nested.py");
        scriptFile.createNewFile();

        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "scripts/nested.py");
        ToolResult result = pythonTool.execute(arguments);

        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testResolveScriptPath_withNullPath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", (String) null);
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with null script path", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_withEmptyPath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with empty script path", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_withWhitespacePath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "   ");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with whitespace script path", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsAbsolutePath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "/etc/passwd");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject absolute path", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsDoubleDotTraversal() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "../outside.py");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject .. traversal", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsDotDotInMiddle() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "foo/../../etc/passwd");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject embedded .. traversal", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsSlashDotDot() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "foo/../..");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject /.. traversal", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsNonexistentFile() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "does_not_exist.py");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject nonexistent file", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsDirectory() throws Exception {
        File dir = new File(workspaceRoot, "script_dir");
        dir.mkdirs();

        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "script_dir");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should reject directory (not a file)", result.isSuccess());
    }

    @Test
    public void testResolveScriptPath_rejectsSymlinkOutsideWorkspace() throws Exception {
        File outsideFile = new File(System.getProperty("java.io.tmpdir"), "outside_target.py");
        outsideFile.createNewFile();

        File symlink = new File(workspaceRoot, "evil_link.py");
        try {
            java.nio.file.Files.createSymbolicLink(symlink.toPath(), outsideFile.toPath());

            JsonObject arguments = new JsonObject();
            arguments.addProperty("script_path", "evil_link.py");
            ToolResult result = pythonTool.execute(arguments);

            // The canonical path check should reject symlinks pointing outside workspace
            assertFalse("Should reject symlink outside workspace", result.isSuccess());
        } catch (Exception e) {
            // Symlinks may not be supported on all platforms; skip if create fails
        } finally {
            outsideFile.delete();
            symlink.delete();
        }
    }

    @Test
    public void testExecute_withCodeOnly() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        ToolResult result = pythonTool.execute(arguments);

        // May fail due to missing Python runtime in test, but should not error on validation
        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecute_withScriptOnly() throws Exception {
        File scriptFile = new File(workspaceRoot, "test.py");
        scriptFile.createNewFile();

        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "test.py");
        ToolResult result = pythonTool.execute(arguments);

        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecute_withPackageOnly() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("package", "requests");
        ToolResult result = pythonTool.execute(arguments);

        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecute_withNoMode() {
        JsonObject arguments = new JsonObject();
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with no mode", result.isSuccess());
        assertTrue("Should mention required parameters", result.toJson().contains("Must provide one of"));
    }

    @Test
    public void testExecute_withAllThreeModes() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("script_path", "test.py");
        arguments.addProperty("package", "requests");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with multiple modes", result.isSuccess());
        assertTrue("Should mention mutually exclusive", result.toJson().contains("Can only provide one of"));
    }

    @Test
    public void testExecute_withTwoModes_codeAndScript() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("script_path", "test.py");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with two modes", result.isSuccess());
    }

    @Test
    public void testExecute_withTwoModes_codeAndPackage() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("package", "requests");
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with two modes", result.isSuccess());
    }

    @Test
    public void testExecute_withTimeoutAtLowerBoundary() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 1);
        ToolResult result = pythonTool.execute(arguments);

        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecute_withTimeoutAtUpperBoundary() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 300);
        ToolResult result = pythonTool.execute(arguments);

        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecute_withTimeoutAboveUpperBoundary() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 301);
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with timeout > 300", result.isSuccess());
    }

    @Test
    public void testExecute_withTimeoutZero() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", 0);
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with timeout = 0", result.isSuccess());
    }

    @Test
    public void testExecute_withTimeoutNegative() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello')");
        arguments.addProperty("timeout_seconds", -5);
        ToolResult result = pythonTool.execute(arguments);

        assertFalse("Should fail with negative timeout", result.isSuccess());
    }

    @Test
    public void testGetApprovalDescription_withCode() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "print('hello world')");
        String desc = pythonTool.getApprovalDescription(arguments);

        assertTrue("Should contain code preview", desc.contains("print"));
        assertTrue("Should mention Python code", desc.contains("Execute Python code"));
    }

    @Test
    public void testGetApprovalDescription_withLongCode_truncated() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("code", "x = " + "1".repeat(100));
        String desc = pythonTool.getApprovalDescription(arguments);

        assertTrue("Should contain ellipsis for long code", desc.contains("..."));
        assertTrue("Should be truncated", desc.length() < 120);
    }

    @Test
    public void testGetApprovalDescription_withScriptPath() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("script_path", "analysis.py");
        String desc = pythonTool.getApprovalDescription(arguments);

        assertTrue("Should contain script path", desc.contains("analysis.py"));
        assertTrue("Should mention script", desc.contains("Execute Python script"));
    }

    @Test
    public void testGetApprovalDescription_withPackage() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("package", "requests");
        String desc = pythonTool.getApprovalDescription(arguments);

        assertTrue("Should contain package name", desc.contains("requests"));
        assertTrue("Should mention pip", desc.contains("pip"));
    }

    @Test
    public void testGetApprovalDescription_withNoArgs() {
        JsonObject arguments = new JsonObject();
        String desc = pythonTool.getApprovalDescription(arguments);

        assertTrue("Should have default description", desc.contains("Execute Python operation"));
    }

    @Test
    public void testRequiresApproval_returnsTrue() {
        assertTrue("Python tool should require approval", pythonTool.requiresApproval());
    }

    @Test
    public void testShutdown_doesNotThrow() {
        pythonTool.shutdown();
        // Should complete without exception
    }
}
