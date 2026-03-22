package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.io.File;

import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.shell.ShellExecutor;
import io.finett.droidclaw.shell.ShellResult;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for executing shell commands on the Android device.
 * Commands run in a sandboxed environment with security controls.
 */
public class ShellTool implements Tool {
    private static final String TOOL_NAME = "execute_shell";
    
    private final ShellExecutor executor;
    private final File workspaceRoot;

    /**
     * Creates a ShellTool with default configuration.
     * 
     * @param workspaceRoot The workspace root directory for relative paths
     */
    public ShellTool(File workspaceRoot) {
        this(workspaceRoot, ShellConfig.createDefault());
    }

    /**
     * Creates a ShellTool with custom configuration.
     * 
     * @param workspaceRoot The workspace root directory for relative paths
     * @param config Shell execution configuration
     */
    public ShellTool(File workspaceRoot, ShellConfig config) {
        this.workspaceRoot = workspaceRoot;
        this.executor = new ShellExecutor(config);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
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
            // Extract command (required)
            if (!arguments.has("command")) {
                return ToolResult.error("Missing required parameter: command");
            }
            String command = arguments.get("command").getAsString();

            // Extract working directory (optional)
            File workingDir = workspaceRoot;
            if (arguments.has("working_directory")) {
                String workingDirPath = arguments.get("working_directory").getAsString();
                workingDir = resolveWorkingDirectory(workingDirPath);
                
                if (workingDir == null) {
                    return ToolResult.error("Invalid working directory: " + workingDirPath);
                }
            }

            // Extract timeout (optional)
            int timeout = 30;
            if (arguments.has("timeout_seconds")) {
                timeout = arguments.get("timeout_seconds").getAsInt();
                if (timeout <= 0) {
                    return ToolResult.error("Timeout must be positive");
                }
            }

            // Execute the command
            ShellResult result = executor.execute(command, workingDir, timeout);

            // Format the result as JSON
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

            // Return success if command completed (even with non-zero exit code)
            // The LLM can interpret the exit code
            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Execution error: " + e.getMessage());
        }
    }

    /**
     * Resolves a working directory path relative to the workspace root.
     * 
     * @param path Relative path from workspace root
     * @return Resolved File object, or null if invalid
     */
    private File resolveWorkingDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return workspaceRoot;
        }

        // Handle absolute paths by rejecting them (must be relative to workspace)
        if (path.startsWith("/")) {
            return null;
        }

        File dir = new File(workspaceRoot, path);
        
        // Verify the directory exists and is within workspace
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        // Security check: ensure the resolved path is within workspace root
        try {
            String canonicalWorkspace = workspaceRoot.getCanonicalPath();
            String canonicalDir = dir.getCanonicalPath();
            
            if (!canonicalDir.startsWith(canonicalWorkspace)) {
                return null; // Path traversal attempt
            }
            
            return dir;
        } catch (Exception e) {
            return null;
        }
    }
}