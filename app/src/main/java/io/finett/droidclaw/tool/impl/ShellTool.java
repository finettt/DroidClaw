package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.shell.ShellExecutor;
import io.finett.droidclaw.shell.ShellResult;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class ShellTool implements Tool {
    private static final String TOOL_NAME = "execute_shell";

    private final ShellExecutor executor;
    private final PathValidator pathValidator;

    /** @deprecated Use {@link #ShellTool(VirtualFileSystem)} instead for proper sandboxing. */
    @Deprecated
    public ShellTool(File workspaceRoot) {
        this(workspaceRoot, ShellConfig.createDefault());
    }

    /** @deprecated Use {@link #ShellTool(VirtualFileSystem, ShellConfig)} instead for proper sandboxing. */
    @Deprecated
    public ShellTool(File workspaceRoot, ShellConfig config) {
        this.pathValidator = new PathValidator(workspaceRoot);
        this.executor = new ShellExecutor(config, pathValidator);
    }

    public ShellTool(VirtualFileSystem virtualFileSystem) {
        this(virtualFileSystem, ShellConfig.createDefault());
    }

    public ShellTool(VirtualFileSystem virtualFileSystem, ShellConfig config) {
        // VirtualFileSystem does not expose PathValidator directly; use extractPathValidator as workaround
        this.pathValidator = extractPathValidator(virtualFileSystem);
        this.executor = new ShellExecutor(config, pathValidator);
    }

    public ShellTool(PathValidator pathValidator, ShellConfig config) {
        this.pathValidator = pathValidator;
        this.executor = new ShellExecutor(config, pathValidator);
    }

    private static PathValidator extractPathValidator(VirtualFileSystem vfs) {
        // VirtualFileSystem does not expose PathValidator; callers should use ShellTool(PathValidator, ShellConfig)
        try {
            vfs.listFiles(".", false);
            throw new UnsupportedOperationException(
                "VirtualFileSystem does not expose PathValidator. " +
                "Use ShellTool(PathValidator, ShellConfig) constructor instead."
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Cannot extract PathValidator from VirtualFileSystem: " + e.getMessage()
            );
        }
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String getApprovalDescription(com.google.gson.JsonObject arguments) {
        String command = arguments.has("command") ?
            arguments.get("command").getAsString() : "unknown command";
        String workingDir = arguments.has("working_directory") ?
            arguments.get("working_directory").getAsString() : ".";
        return "Execute shell command:\n" + command + "\n\nWorking directory: " + workingDir;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("command", "The shell command to execute", true)
                .addString("working_directory", 
                    "Working directory (relative to workspace root). Defaults to workspace root.", 
                    false)
                .addInteger("timeout_seconds", 
                    "Maximum execution time in seconds. Default: 30", 
                    false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Execute a shell command on the Android device. Commands run in a sandboxed environment. " +
                "Use for running scripts, checking system info, or performing operations not covered by other tools.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("command")) {
                return ToolResult.error("Missing required parameter: command");
            }
            String command = arguments.get("command").getAsString();

            File workingDir = pathValidator.getWorkspaceRoot();
            if (arguments.has("working_directory")) {
                String workingDirPath = arguments.get("working_directory").getAsString();

                try {
                    workingDir = resolveWorkingDirectory(workingDirPath);
                } catch (SecurityException e) {
                    return ToolResult.error("Security error: " + e.getMessage());
                } catch (IOException e) {
                    return ToolResult.error("Invalid working directory: " + workingDirPath +
                        " - " + e.getMessage());
                }
            }

            int timeout = 30;
            if (arguments.has("timeout_seconds")) {
                timeout = arguments.get("timeout_seconds").getAsInt();
                if (timeout <= 0) {
                    return ToolResult.error("Timeout must be positive");
                }
            }

            ShellResult result = executor.execute(command, workingDir, timeout);

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("exit_code", result.getExitCode());
            resultJson.addProperty("timed_out", result.isTimedOut());
            resultJson.addProperty("execution_time_ms", result.getExecutionTimeMs());

            if (!result.getStdout().isEmpty()) {
                resultJson.addProperty("stdout", result.getStdout());
            }

            if (!result.getStderr().isEmpty()) {
                resultJson.addProperty("stderr", result.getStderr());
            }

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Execution error: " + e.getMessage());
        }
    }

    private File resolveWorkingDirectory(String path) throws SecurityException, IOException {
        if (path == null || path.trim().isEmpty()) {
            return pathValidator.getWorkspaceRoot();
        }

        File dir = pathValidator.validateAndResolve(path);

        if (!dir.exists()) {
            throw new IOException("Directory does not exist: " + path);
        }

        if (!dir.isDirectory()) {
            throw new IOException("Path is not a directory: " + path);
        }

        return dir;
    }
}