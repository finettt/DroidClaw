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

/**
 * Tool for executing shell commands on the Android device.
 * Commands run in a sandboxed environment with security controls.
 * All working directories are validated against the virtual filesystem.
 */
public class ShellTool implements Tool {
    private static final String TOOL_NAME = "execute_shell";
    
    private final ShellExecutor executor;
    private final PathValidator pathValidator;

    /**
     * Creates a ShellTool with default configuration.
     *
     * @param workspaceRoot The workspace root directory for relative paths
     * @deprecated Use {@link #ShellTool(VirtualFileSystem)} instead for proper sandboxing
     */
    @Deprecated
    public ShellTool(File workspaceRoot) {
        this(workspaceRoot, ShellConfig.createDefault());
    }

    /**
     * Creates a ShellTool with custom configuration.
     *
     * @param workspaceRoot The workspace root directory for relative paths
     * @param config Shell execution configuration
     * @deprecated Use {@link #ShellTool(VirtualFileSystem, ShellConfig)} instead for proper sandboxing
     */
    @Deprecated
    public ShellTool(File workspaceRoot, ShellConfig config) {
        this.pathValidator = new PathValidator(workspaceRoot);
        this.executor = new ShellExecutor(config, pathValidator);
    }

    /**
     * Creates a ShellTool with a VirtualFileSystem for proper sandboxing.
     *
     * @param virtualFileSystem The virtual filesystem for path validation
     */
    public ShellTool(VirtualFileSystem virtualFileSystem) {
        this(virtualFileSystem, ShellConfig.createDefault());
    }

    /**
     * Creates a ShellTool with VirtualFileSystem and custom configuration.
     *
     * @param virtualFileSystem The virtual filesystem for path validation
     * @param config Shell execution configuration
     */
    public ShellTool(VirtualFileSystem virtualFileSystem, ShellConfig config) {
        // We need to get the PathValidator from VirtualFileSystem
        // Since VirtualFileSystem doesn't expose it directly, we need to use a workaround
        // by creating a tool-specific PathValidator from the workspace root
        // Note: VirtualFileSystem should expose its PathValidator for better integration
        this.pathValidator = extractPathValidator(virtualFileSystem);
        this.executor = new ShellExecutor(config, pathValidator);
    }

    /**
     * Creates a ShellTool with PathValidator for proper sandboxing.
     * This is the recommended constructor for maximum control.
     *
     * @param pathValidator The path validator for working directory validation
     * @param config Shell execution configuration
     */
    public ShellTool(PathValidator pathValidator, ShellConfig config) {
        this.pathValidator = pathValidator;
        this.executor = new ShellExecutor(config, pathValidator);
    }

    /**
     * Extracts PathValidator from VirtualFileSystem.
     * This is a temporary workaround until VirtualFileSystem exposes its PathValidator.
     */
    private static PathValidator extractPathValidator(VirtualFileSystem vfs) {
        // Use reflection or create from workspace root
        // For now, we'll get the workspace root by listing files
        try {
            // List the root to get workspace info - this validates vfs is working
            vfs.listFiles(".", false);
            // We need to create a PathValidator with the workspace root
            // Since we can't get it from VirtualFileSystem, we'll need to
            // use reflection or modify VirtualFileSystem
            // For now, create from a test file operation
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
     * Uses PathValidator to ensure the path is within the virtual filesystem sandbox.
     *
     * @param path Relative path from workspace root
     * @return Resolved File object
     * @throws SecurityException if path is outside workspace sandbox
     * @throws IOException if path is invalid or directory doesn't exist
     */
    private File resolveWorkingDirectory(String path) throws SecurityException, IOException {
        if (path == null || path.trim().isEmpty()) {
            return pathValidator.getWorkspaceRoot();
        }

        // Validate and resolve path using PathValidator
        File dir = pathValidator.validateAndResolve(path);
        
        // Verify the directory exists
        if (!dir.exists()) {
            throw new IOException("Directory does not exist: " + path);
        }
        
        if (!dir.isDirectory()) {
            throw new IOException("Path is not a directory: " + path);
        }
        
        return dir;
    }
}