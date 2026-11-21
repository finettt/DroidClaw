package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.shell.ExecPolicy;
import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.shell.ExecPlan;
import io.finett.droidclaw.shell.ExecPlanner;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.shell.ShellBackend;
import io.finett.droidclaw.shell.ShellResult;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellTool} on the single shell execution path: the raw command is
 * passed verbatim to {@code sh -c} by the backend, so full shell syntax runs and the
 * approval modes (not argv curation) are the gate.
 *
 * <p>Executable paths are resolved at runtime via the trusted-dirs list so the tests work
 * on both Android ({@code /system/bin}) and Linux CI ({@code /usr/bin}, {@code /bin}).
 */
public class ShellToolTest {
    private ShellTool tool;
    private File workspaceRoot;
    private PathValidator pathValidator;

    /** Resolve an executable name through the trusted-dirs list at test time. */
    private static String findExe(String name) throws Exception {
        for (String dir : buildNixosTrustedDirs()) {
            File candidate = new File(dir, name);
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        throw new IllegalStateException("Executable not found in trusted dirs: " + name);
    }

    /** Build trusted dirs that include NixOS paths for test environments. */
    private static List<String> buildNixosTrustedDirs() {
        List<String> dirs = new ArrayList<>(Arrays.asList(
                "/system/bin", "/system/xbin", "/usr/bin", "/bin"));

        File nixStore = new File("/nix/store");
        if (nixStore.isDirectory()) {
            File[] entries = nixStore.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory() && new File(entry, "bin").exists()) {
                        dirs.add(new File(entry, "bin").getAbsolutePath());
                    }
                }
            }
        }

        return dirs;
    }

    /** Minimal host-execution backend for tests: re-verifies the plan hash, runs
     *  {@link ShellConfig#validatePlan}, then runs the raw command through {@code sh -c}. */
    private static final class LocalTestBackend implements ShellBackend {
        private final ShellConfig config;

        LocalTestBackend(ShellConfig config) {
            this.config = config;
        }

        @Override
        public ShellResult execute(ExecPlan plan, int timeoutSeconds) throws SecurityException {
            String denial = config.validatePlan(plan);
            if (denial != null) {
                throw new SecurityException(denial);
            }
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("sh");
            command.add("-c");
            command.add(plan.getRawCommand());
            long start = System.currentTimeMillis();
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(plan.getCwd());
                Process process = builder.start();
                java.io.InputStream in = process.getInputStream();
                java.io.InputStream err = process.getErrorStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                java.io.ByteArrayOutputStream errOut = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                while ((n = err.read(buf)) != -1) {
                    errOut.write(buf, 0, n);
                }
                int exit = process.waitFor();
                return new ShellResult(out.toString("UTF-8"), errOut.toString("UTF-8"), exit,
                        false, System.currentTimeMillis() - start);
            } catch (java.io.IOException e) {
                return new ShellResult("", "Failed to start: " + e.getMessage(), -1, false,
                        System.currentTimeMillis() - start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ShellResult("", "Interrupted", -1, true,
                        System.currentTimeMillis() - start);
            }
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private static ShellConfig fullConfig() {
        return new ShellConfig.Builder().policy(ExecPolicy.full()).build();
    }

    private ShellTool makeTool(ShellConfig config) {
        return new ShellTool(pathValidator, config, new LocalTestBackend(config),
                new ExecPlanner(workspaceRoot));
    }

    private static String SH_PATH;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = new File(System.getProperty("java.io.tmpdir"),
                "shell_test_workspace_" + System.currentTimeMillis());
        workspaceRoot.mkdirs();

        pathValidator = new PathValidator(workspaceRoot);
        tool = makeTool(fullConfig());

        try {
            SH_PATH = findExe("sh");
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
            assertTrue(result.getContent().contains("exit_code"));
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

        assertTrue(tool.execute(args).isSuccess());
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
        ShellTool disabledTool = makeTool(ShellConfig.createDefault()); // DENY

        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");

        ToolResult result = disabledTool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Security"));
    }

    @Test
    public void testExecuteCommandWithNonZeroExitCode() {
        JsonObject args = new JsonObject();
        // sh -c "exit 42" — sh is resolved via the trusted dirs so the planner accepts it.
        args.addProperty("command", SH_PATH + " -c 'exit 42'");

        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"exit_code\":42"));
    }

    @Test
    public void testExecuteCommandWithStderr() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "ls /nonexistent_dir_98765");

        ToolResult result = tool.execute(args);

        // Tool execution succeeds (exit code 2), stderr is captured.
        assertTrue(result.isSuccess());
        assertTrue("Result should contain stderr field", result.getContent().contains("stderr"));
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

            assertFalse("Should reject path outside workspace", tool.execute(args).isSuccess());
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
        ShellTool fullTool = makeTool(fullConfig());

        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("working_directory", "");

        assertTrue("Empty working directory should use workspace root",
                   fullTool.execute(args).isSuccess());
    }

    @Test
    public void testPathValidatorIntegration() throws IOException {
        ShellTool fullTool = makeTool(fullConfig());

        File nestedDir = new File(workspaceRoot, "level1/level2");
        nestedDir.mkdirs();

        try {
            JsonObject args = new JsonObject();
            args.addProperty("command", "pwd");
            args.addProperty("working_directory", "level1/level2");

            assertTrue("Should succeed with nested directory", fullTool.execute(args).isSuccess());
        } finally {
            new File(nestedDir, "level2").delete();
            nestedDir.delete();
            new File(workspaceRoot, "level1").delete();
        }
    }

    @Test
    public void testToolResultJsonFormat() {
        ShellTool fullTool = makeTool(fullConfig());

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
        ShellTool pathValidatorTool = makeTool(ShellConfig.createDefault());
        assertNotNull(pathValidatorTool);
        assertEquals("execute_shell", pathValidatorTool.getName());
    }

    @Test
    public void testBuildExecPlan_withValidCommand_returnsPlan() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNotNull("Plan should be built for valid command", plan);
        assertTrue("Plan should contain the raw command",
                plan.getRawCommand().contains("echo hello"));
        assertNotNull("Plan hash should not be null", plan.getPlanHash());
        assertTrue("Plan hash should be non-empty", plan.getPlanHash().length() > 0);
    }

    @Test
    public void testBuildExecPlan_withEmptyCommand_returnsNull() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "   ");

        assertNull("Plan should be null for empty command", tool.buildExecPlan(args));
    }

    @Test
    public void testBuildExecPlan_withMetachar_returnsPlan() {
        // Full shell syntax runs inside the Linux environment, so metacharacters are
        // passed through to sh -c rather than rejected at plan-build time.
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello; rm -rf /");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNotNull("Metacharacters are allowed (governed by the approval modes)", plan);
        assertEquals("echo hello; rm -rf /", plan.getRawCommand());
    }

    @Test
    public void testBuildExecPlan_withMissingCommand_returnsNull() {
        assertNull("Plan should be null when command missing", tool.buildExecPlan(new JsonObject()));
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
        assertNotEquals("Different commands should produce different hashes",
                plan1.getPlanHash(), plan2.getPlanHash());
    }

    @Test
    public void testBuildExecPlan_hashStableForSameArgs() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan1 = tool.buildExecPlan(args);
        ExecPlan plan2 = tool.buildExecPlan(args);

        assertEquals("Same command should produce same hash",
                plan1.getPlanHash(), plan2.getPlanHash());
    }

    @Test
    public void testBuildExecPlan_approvalDescriptionContainsCommandAndHash() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello");

        ExecPlan plan = tool.buildExecPlan(args);

        assertNotNull(plan);
        String desc = plan.toApprovalDescription();
        assertTrue("Description should contain the raw command", desc.contains("echo hello"));
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
        String desc = tool.getApprovalDescription(new JsonObject());

        assertTrue("Should show unknown command", desc.contains("unknown command"));
    }

    // ==================== Full shell syntax now runs through sh -c ====================

    @Test
    public void testExecuteWithCommandSubstitution_dollarParen() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo $(whoami)");

        ToolResult result = tool.execute(args);

        assertTrue("Command substitution runs inside sh -c", result.isSuccess());
        assertTrue("stdout should carry the expanded value",
                result.getContent().contains("stdout") &&
                !result.getContent().contains("\"stdout\":\"\""));
    }

    @Test
    public void testExecuteWithCommandSubstitution_backtick() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo `whoami`");

        assertTrue("Backtick substitution runs inside sh -c", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithRedirection_output() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test > /tmp/droidclaw_redir.txt");

        ToolResult result = tool.execute(args);

        assertTrue("Output redirection runs inside sh -c", result.isSuccess());
    }

    @Test
    public void testExecuteWithRedirection_input() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "cat < /etc/hostname");

        assertTrue("Input redirection runs inside sh -c", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithPipe() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "printf 'a\\nb\\nc\\n' | grep b");

        ToolResult result = tool.execute(args);

        assertTrue("Pipe runs inside sh -c", result.isSuccess());
        assertTrue("grep should have selected the middle line", result.getContent().contains("b"));
    }

    @Test
    public void testExecuteWithAmpersand() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo one && echo two");

        ToolResult result = tool.execute(args);

        assertTrue("&& runs inside sh -c", result.isSuccess());
        assertTrue(result.getContent().contains("one"));
        assertTrue(result.getContent().contains("two"));
    }

    @Test
    public void testExecuteWithSemicolon() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo one; echo two");

        ToolResult result = tool.execute(args);

        assertTrue("; runs inside sh -c", result.isSuccess());
        assertTrue(result.getContent().contains("one"));
        assertTrue(result.getContent().contains("two"));
    }

    @Test
    public void testExecuteWithQuestionMarkGlob() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo ?");

        // '?' may expand to single-char filenames or stay literal; just don't crash.
        assertNotNull("Result should not be null", tool.execute(args));
    }

    @Test
    public void testExecuteWithTildeExpansion() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo ~");

        assertTrue("Tilde expansion runs inside sh -c", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithExclamationMark() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo hello!");

        assertTrue("Exclamation mark runs inside sh -c", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithBraceExpansion() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo {a,b}");

        // Brace expansion is shell-dependent; the command must simply run without rejection.
        assertTrue(tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithDoubleDotTraversal() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "foo/../..");

        assertFalse("Should reject double-dot traversal", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithNullWorkingDirectory_usesRoot() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        assertTrue("Null working directory should use workspace root",
                   makeTool(fullConfig()).execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithWhitespaceWorkingDirectory_usesRoot() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "pwd");
        args.addProperty("working_directory", "   ");

        assertTrue("Whitespace working directory should use workspace root",
                   makeTool(fullConfig()).execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAtBoundary_300() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 300);

        assertTrue("Timeout at upper boundary should be valid", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAtBoundary_1() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 1);

        assertTrue("Timeout at lower boundary should be valid", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutAboveBoundary_301() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 301);

        assertFalse("Timeout above upper boundary should fail", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutZero() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", 0);

        assertFalse("Timeout of zero should fail", tool.execute(args).isSuccess());
    }

    @Test
    public void testExecuteWithTimeoutNegative() {
        JsonObject args = new JsonObject();
        args.addProperty("command", "echo test");
        args.addProperty("timeout_seconds", -1);

        assertFalse("Negative timeout should fail", tool.execute(args).isSuccess());
    }

    @Test
    public void testRequiresApproval_returnsTrue() {
        assertTrue("Shell tool should require approval", tool.requiresApproval());
    }
}
