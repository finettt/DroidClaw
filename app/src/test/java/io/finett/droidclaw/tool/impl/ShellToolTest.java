package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.shell.ExecPlan;
import io.finett.droidclaw.shell.ExecPlanner;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellTool} using the new ExecPlan-based security model.
 *
 * <p>Executable paths are resolved at runtime via the {@link ExecPlanner} so the tests
 * work on both Android ({@code /system/bin}) and Linux CI ({@code /usr/bin}, {@code /bin}).
 */
public class ShellToolTest {
    private ShellTool tool;
    private File workspaceRoot;
    private PathValidator pathValidator;

    /** Resolve an executable name through the trusted-dirs list at test time. */
    private static String findExe(String name, File workspace) throws Exception {
        ShellConfig cfg = ShellConfig.createFull();
        ExecPlanner planner = new ExecPlanner(cfg, workspace);
        return planner.plan(name, workspace).getCanonicalExePath();
    }

    private static String SH_PATH;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = new File(System.getProperty("java.io.tmpdir"),
                "shell_test_workspace_" + System.currentTimeMillis());
        workspaceRoot.mkdirs();

        pathValidator = new PathValidator(workspaceRoot);
        // Use FULL policy so most tests can execute commands successfully.
        // Tests that specifically verify DENY behaviour create their own tool instance.
        tool = new ShellTool(pathValidator, ShellConfig.createFull());

        try {
            SH_PATH = findExe("sh", workspaceRoot);
        } catch (Exception e) {
            SH_PATH = "/usr/bin/sh";
        }
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

        ToolResult result = tool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void testExecuteWithWorkingDirectory() throws IOException {
        File subdir = new File(workspaceRoot, "testdir_" + System.currentTimeMillis());
        subdir.mkdir();
        
        try {
            JsonObject args = new JsonObject();
            args.addProperty("command", "pwd");
            args.addProperty("working_directory", subdir.getName());
            
            ToolResult result = tool.execute(args);

            assertTrue("Tool execution should succeed", result.isSuccess());
            String content = result.getContent();
            assertTrue(content.contains("exit_code"));
        } finally {
            subdir.delete();
        }
    }

    @Test
    public void testExecuteWithInvalidWorkingDirectory() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "nonexistent_dir_12345");
        
        ToolResult result = tool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not exist") ||
                   result.getError().contains("Invalid working directory"));
    }

    @Test
    public void testExecuteWithNonexistentDirectory() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "nonexistent_subdir");
        
        ToolResult result = tool.execute(args);
        
        assertFalse("Should fail for nonexistent directory", result.isSuccess());
        assertTrue("Error should mention directory",
                   result.getError().contains("does not exist") ||
                   result.getError().contains("Invalid"));
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
        assertTrue(result.getError().contains("Timeout must be between 1 and 300"));
    }

    @Test
    public void testExecuteWithDisabledShell() {
        ShellConfig disabledConfig = ShellConfig.createDefault(); // DENY
        ShellTool disabledTool = new ShellTool(pathValidator, disabledConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = disabledTool.execute(args);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Security"));
    }

    @Test
    public void testExecuteCommandWithNonZeroExitCode() {
        // Run sh -c "exit 42" via SHELL mode — the planner default is DIRECT, so
        // we supply the sh binary directly with -c to get a non-zero exit via shell.
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);

        JsonObject args = new JsonObject();
        // sh -c "exit 42" — sh is in the trusted dirs so ExecPlanner resolves it.
        args.addProperty("command", SH_PATH + " -c 'exit 42'");

        ToolResult result = fullTool.execute(args);

        // Even though exit code is non-zero, tool execution itself succeeds
        // The LLM can interpret the exit code from the result
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"exit_code\":42"));
    }

    @Test
    public void testExecuteCommandWithStderr() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls /nonexistent_dir_98765");
        
        ToolResult result = fullTool.execute(args);
        
        // Tool execution succeeds, but stderr is captured
        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("stderr"));
    }

    @Test
    public void testResultContainsExecutionTime() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = fullTool.execute(args);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("execution_time_ms"));
    }

    @Test
    public void testResultContainsTimedOutFlag() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        
        ToolResult result = fullTool.execute(args);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("timed_out"));
        assertTrue(result.getContent().contains("false"));
    }

    @Test
    public void testPathTraversalPrevention() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "../../../");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject path traversal", result.isSuccess());
        assertTrue("Error should mention security or path traversal",
                   result.getError().contains("Security") ||
                   result.getError().contains("traversal") ||
                   result.getError().contains("outside"));
    }

    @Test
    public void testAbsolutePathRejection() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "/etc");
        
        ToolResult result = tool.execute(args);
        
        // Absolute paths like "/etc" get the leading slash stripped by PathValidator,
        // so "/etc" becomes "etc" relative to workspace. Since this directory doesn't
        // exist in the workspace, the operation fails.
        // This is still secure because the path is sandboxed to the workspace.
        assertFalse("Should reject path that doesn't exist in workspace", result.isSuccess());
        assertTrue("Error should mention directory does not exist or invalid path",
                   result.getError().contains("does not exist") ||
                   result.getError().contains("not a directory") ||
                   result.getError().contains("Security") ||
                   result.getError().contains("Invalid"));
    }
    
    @Test
    public void testWorkingDirectoryOutsideWorkspace() throws IOException {
        File outsideDir = new File(System.getProperty("java.io.tmpdir"),
                "outside_workspace_" + System.currentTimeMillis());
        outsideDir.mkdir();

        try {
            JsonObject args = new JsonObject();
            args.addProperty("command", "pwd");
            args.addProperty("working_directory", "../../" + outsideDir.getName());
            
            ToolResult result = tool.execute(args);

            assertFalse("Should reject path outside workspace", result.isSuccess());
        } finally {
            outsideDir.delete();
        }
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
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("working_directory", "");
        
        ToolResult result = fullTool.execute(args);
        
        // Empty working directory should default to workspace root
        assertTrue("Empty working directory should use workspace root", result.isSuccess());
    }
    
    @Test
    public void testPathValidatorIntegration() throws IOException {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        File nestedDir = new File(workspaceRoot, "level1/level2");
        nestedDir.mkdirs();
        
        try {
            JsonObject args = new JsonObject();
            args.addProperty("command", "pwd");
            args.addProperty("working_directory", "level1/level2");
            
            ToolResult result = fullTool.execute(args);
            
            assertTrue("Should succeed with nested directory", result.isSuccess());
        } finally {
            new File(nestedDir, "level2").delete();
            nestedDir.delete();
            new File(workspaceRoot, "level1").delete();
        }
    }

    @Test
    public void testToolResultJsonFormat() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);
        
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo 'test output'");
        
        ToolResult result = fullTool.execute(args);
        
        assertTrue(result.isSuccess());
        String json = result.toJson();

        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains("exit_code"));
        assertTrue(json.contains("timed_out"));
        assertTrue(json.contains("execution_time_ms"));
        assertTrue(json.contains("stdout"));
    }
    
    @Test
    public void testConstructorWithPathValidator() {
        ShellTool pathValidatorTool = new ShellTool(pathValidator, ShellConfig.createDefault());
        assertNotNull(pathValidatorTool);
        assertEquals("execute_shell", pathValidatorTool.getName());
    }

    @Test
    public void testBuildExecPlan_withValidCommand_returnsPlan() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNotNull("Plan should be built for valid command", plan);
        assertTrue("Plan should contain echo", plan.getCanonicalExePath().endsWith("echo"));
        assertEquals("Plan should have 1 arg", 1, plan.getArgv().size());
        assertEquals("hello", plan.getArgv().get(0));
        assertNotNull("Plan hash should not be null", plan.getPlanHash());
        assertTrue("Plan hash should be non-empty", plan.getPlanHash().length() > 0);
    }

    @Test
    public void testBuildExecPlan_withEmptyCommand_returnsNull() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "   ");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNull("Plan should be null for empty command", plan);
    }

    @Test
    public void testBuildExecPlan_withMetachar_returnsNull() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello; rm -rf /");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNull("Plan should be null when metachar detected", plan);
    }

    @Test
    public void testBuildExecPlan_withMissingCommand_returnsNull() {
        JsonObject args = new JsonObject();

        ExecPlan plan = tool.buildExecPlan(args);

        assertNull("Plan should be null when command missing", plan);
    }

    @Test
    public void testBuildExecPlan_hashChangesWithDifferentArgs() {
        JsonObject args1 = new JsonObject();
        args1.addProperty("command", "echo hello");

        JsonObject args2 = new JsonObject();
        args2.addProperty("command", "echo world");

        ExecPlan plan1 = tool.buildExecPlan(args1);
        ExecPlan plan2 = tool.buildExecPlan(args2);

        assertNotNull(plan1);
        assertNotNull(plan2);
        assertNotEquals("Different args should produce different hashes",
                plan1.getPlanHash(), plan2.getPlanHash());
    }

    @Test
    public void testBuildExecPlan_hashStableForSameArgs() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan1 = tool.buildExecPlan(args);
        ExecPlan plan2 = tool.buildExecPlan(args);

        assertNotNull(plan1);
        assertNotNull(plan2);
        assertEquals("Same args should produce same hash",
                plan1.getPlanHash(), plan2.getPlanHash());
    }

    @Test
    public void testBuildExecPlan_approvalDescriptionContainsHash() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNotNull(plan);
        String desc = plan.toApprovalDescription();
        assertTrue("Description should contain mode", desc.contains("DIRECT"));
        assertTrue("Description should contain executable", desc.contains("echo"));
        assertTrue("Description should contain working dir", desc.contains("Working dir"));
        assertTrue("Description should contain plan ID", desc.contains("Plan ID"));
    }

    @Test
    public void testGetApprovalDescription_withCommand() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls -la");

        String desc = tool.getApprovalDescription(args);

        assertTrue("Should contain command", desc.contains("ls -la"));
        assertTrue("Should mention shell command", desc.contains("Execute shell command"));
    }

    @Test
    public void testGetApprovalDescription_withWorkingDir() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "subdir");

        String desc = tool.getApprovalDescription(args);

        assertTrue("Should contain working directory", desc.contains("subdir"));
    }

    @Test
    public void testGetApprovalDescription_missingCommand() {
        JsonObject args = new JsonObject();

        String desc = tool.getApprovalDescription(args);

        assertTrue("Should show unknown command", desc.contains("unknown command"));
    }

    @Test
    public void testExecuteWithCommandSubstitution_dollarParen() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo $(whoami)");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject command substitution", result.isSuccess());
        assertTrue("Error should mention metacharacter or security",
                result.getError().contains("metacharacter") || result.getError().contains("Security"));
    }

    @Test
    public void testExecuteWithCommandSubstitution_backtick() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo `whoami`");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject backtick substitution", result.isSuccess());
    }

    @Test
    public void testExecuteWithRedirection_output() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test > /tmp/file.txt");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject redirection", result.isSuccess());
    }

    @Test
    public void testExecuteWithRedirection_input() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "cat < /etc/passwd");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject input redirection", result.isSuccess());
    }

    @Test
    public void testExecuteWithPipe() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls | wc -l");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject pipe", result.isSuccess());
    }

    @Test
    public void testExecuteWithAmpersand() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls && echo done");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject ampersand", result.isSuccess());
    }

    @Test
    public void testExecuteWithSemicolon() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls; echo done");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject semicolon", result.isSuccess());
    }

    @Test
    public void testExecuteWithQuestionMarkGlob() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo ?");

        ToolResult result = tool.execute(args);

        // '?' is not in metachars, so this may succeed or fail depending on shell
        // Just verify it doesn't crash
        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testExecuteWithTildeExpansion() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls ~");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject tilde expansion", result.isSuccess());
    }

    @Test
    public void testExecuteWithExclamationMark() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello!");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject exclamation mark", result.isSuccess());
    }

    @Test
    public void testExecuteWithBraceExpansion() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo {a,b,c}");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject brace expansion", result.isSuccess());
    }

    @Test
    public void testExecuteWithDoubleDotTraversal() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "foo/../..");

        ToolResult result = tool.execute(args);

        assertFalse("Should reject double-dot traversal", result.isSuccess());
    }

    @Test
    public void testExecuteWithNullWorkingDirectory_usesRoot() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);

        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");

        ToolResult result = fullTool.execute(args);

        assertTrue("Null working directory should use workspace root", result.isSuccess());
    }

    @Test
    public void testExecuteWithWhitespaceWorkingDirectory_usesRoot() {
        ShellConfig fullConfig = ShellConfig.createFull();
        ShellTool fullTool = new ShellTool(pathValidator, fullConfig);

        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "   ");

        ToolResult result = fullTool.execute(args);

        assertTrue("Whitespace working directory should use workspace root", result.isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAtBoundary_300() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 300);

        ToolResult result = tool.execute(args);

        assertTrue("Timeout at upper boundary should be valid", result.isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAtBoundary_1() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 1);

        ToolResult result = tool.execute(args);

        assertTrue("Timeout at lower boundary should be valid", result.isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAboveBoundary_301() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 301);

        ToolResult result = tool.execute(args);

        assertFalse("Timeout above upper boundary should fail", result.isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutZero() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 0);

        ToolResult result = tool.execute(args);

        assertFalse("Timeout of zero should fail", result.isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutNegative() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", -1);

        ToolResult result = tool.execute(args);

        assertFalse("Negative timeout should fail", result.isSuccess());
    }

    @Test
    public void testRequiresApproval_returnsTrue() {
        assertTrue("Shell tool should require approval", tool.requiresApproval());
    }
}
