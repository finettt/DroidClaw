package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.shell.ExecPlan;
import io.finett.droidclaw.shell.ExecPlanner;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.shell.ShellExecutor;
import io.finett.droidclaw.shell.ShellResult;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool that executes a shell command in the workspace sandbox.
 *
 * <p>Security model:
 * <ul>
 *   <li>Commands are normalised into an {@link ExecPlan} by {@link ExecPlanner} before
 *       any execution happens — shell metacharacters are rejected at plan-build time
 *       in {@link ExecPlan.ExecMode#DIRECT} mode.</li>
 *   <li>The plan is shown verbatim to the user in the approval dialog (not the raw
 *       LLM-provided string) via {@link #buildExecPlan(JsonObject)}.</li>
 *   <li>The plan's SHA-256 hash is verified by {@link ShellExecutor} immediately before
 *       the process starts — preventing substitution between approval and execution.</li>
 *   <li>The working directory is validated against the workspace root by both
 *       {@link ExecPlanner} and {@link PathValidator}.</li>
 * </ul>
 */
public class ShellTool implements Tool {

    private static final String TOOL_NAME = "execute_shell";

    private final PathValidator pathValidator;
    private final ShellExecutor executor;
    private final ExecPlanner planner;

    public ShellTool(PathValidator pathValidator, ShellConfig config) {
        this.pathValidator = pathValidator;
        this.executor = new ShellExecutor(config);
        this.planner = new ExecPlanner(config, pathValidator.getWorkspaceRoot());
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    /**
     * Build a normalised {@link ExecPlan} from the LLM-provided arguments.
     *
     * <p>This is called by {@link io.finett.droidclaw.agent.AgentLoop} <em>before</em>
     * showing the approval dialog. The dialog displays
     * {@link ExecPlan#toApprovalDescription()} — the normalised plan, not the raw string.
     * The plan's hash is stored at approval time and re-verified when
     * {@link #execute(JsonObject)} runs.
     *
     * @return a normalised ExecPlan, or {@code null} if planning fails (the caller
     *         will fall back to {@link #getApprovalDescription(JsonObject)})
     */
    @Override
    public ExecPlan buildExecPlan(JsonObject arguments) {
        try {
            String command = arguments.has("command")
                    ? arguments.get("command").getAsString() : "";
            if (command.trim().isEmpty()) return null;

            File cwd = resolveWorkingDirectory(arguments);
            return planner.plan(command, cwd);
        } catch (Exception e) {
            // Planning failed (e.g. metachar detected, exe not in trusted dirs).
            // Return null so the agent loop falls back to the string description;
            // execute() will throw SecurityException and return an error result.
            return null;
        }
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        String command = arguments.has("command")
                ? arguments.get("command").getAsString() : "unknown command";
        String workingDir = arguments.has("working_directory")
                ? arguments.get("working_directory").getAsString() : "(workspace root)";
        return "Execute shell command:\n" + command + "\n\nWorking directory: " + workingDir;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("command", "The shell command to execute", true)
                .addString("working_directory",
                        "Working directory relative to workspace root. Defaults to workspace root.",
                        false)
                .addInteger("timeout_seconds",
                        "Maximum execution time in seconds (1–300). Default: 30.",
                        false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Execute a shell command on the Android device. Commands run in a sandboxed "
                + "workspace environment with an allowlist-based security policy. "
                + "Shell metacharacters are rejected in direct execution mode. "
                + "Requires user approval before execution.",
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
            if (command.trim().isEmpty()) {
                return ToolResult.error("Command must not be empty");
            }

            // Resolve working directory
            File cwd;
            try {
                cwd = resolveWorkingDirectory(arguments);
            } catch (SecurityException e) {
                return ToolResult.error("Security error: " + e.getMessage());
            } catch (IOException e) {
                return ToolResult.error("Invalid working directory: " + e.getMessage());
            }

            // Parse timeout
            int timeout = 30;
            if (arguments.has("timeout_seconds") && !arguments.get("timeout_seconds").isJsonNull()) {
                timeout = arguments.get("timeout_seconds").getAsInt();
                if (timeout <= 0 || timeout > 300) {
                    return ToolResult.error("Timeout must be between 1 and 300 seconds");
                }
            }

            // Build ExecPlan (tokenise, reject metachar, resolve exe, validate cwd)
            ExecPlan plan;
            try {
                plan = planner.plan(command, cwd);
            } catch (SecurityException e) {
                return ToolResult.error("Security policy violation: " + e.getMessage());
            } catch (IOException e) {
                return ToolResult.error("Command planning error: " + e.getMessage());
            }

            // Execute (plan hash verified inside; policy re-validated inside)
            ShellResult result = executor.execute(plan, timeout);

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

    // ==================== Helpers ====================

    private File resolveWorkingDirectory(JsonObject arguments) throws SecurityException, IOException {
        if (!arguments.has("working_directory")) {
            return pathValidator.getWorkspaceRoot();
        }

        String dirPath = arguments.get("working_directory").getAsString();
        if (dirPath == null || dirPath.trim().isEmpty()) {
            return pathValidator.getWorkspaceRoot();
        }

        File dir = pathValidator.validateAndResolve(dirPath);

        if (!dir.exists()) {
            throw new IOException("Directory does not exist: " + dirPath);
        }
        if (!dir.isDirectory()) {
            throw new IOException("Path is not a directory: " + dirPath);
        }

        return dir;
    }
}