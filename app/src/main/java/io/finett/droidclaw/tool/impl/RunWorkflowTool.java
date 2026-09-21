package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.agent.GuidelinesManager;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.workflow.WorkflowRunCallback;
import io.finett.droidclaw.workflow.WorkflowNodeStatus;
import io.finett.droidclaw.workflow.WorkflowRunResult;
import io.finett.droidclaw.workflow.WorkflowRunner;
import io.finett.droidclaw.workflow.TemplateResolver;

import java.util.Map;

/**
 * Agent-launched workflow execution tool (spec §11B item B9, Q11).
 *
 * <p>Workflows are discovered by listing {@code .agent/workflows/} and reading
 * a JSON file, the same way skills are discovered. This tool executes a workflow
 * synchronously and returns the final output.
 *
 * <p>Security:
 * <ul>
 *   <li>{@code requiresApproval() == true} — workflows fan out into many LLM calls</li>
 *   <li>Recursion guard: a workflow cannot launch another workflow (depth counter)</li>
 *   <li>{@code run_workflow} is excluded from workflow node tool views by ScopedToolRegistry</li>
 * </ul>
 */
public class RunWorkflowTool implements Tool {

    private static final String TAG = "RunWorkflowTool";
    private static final String NAME = "run_workflow";
    private static final String WORKFLOWS_DIR = ".agent/workflows";

    /** Recursion guard scoped to the synchronous execution context. Independent
     * top-level runs on other threads must not block each other. */
    private static final ThreadLocal<Integer> runDepth =
            ThreadLocal.withInitial(() -> 0);

    private final ToolDefinition definition;
    private final Context context;
    private final LlmApiService apiService;
    private final ToolRegistry toolRegistry;
    private final SettingsManager settingsManager;

    public RunWorkflowTool(Context context, LlmApiService apiService,
                           ToolRegistry toolRegistry, SettingsManager settingsManager) {
        this.context = context.getApplicationContext();
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        ToolDefinition.ParametersBuilder builder = new ToolDefinition.ParametersBuilder()
                .addString("workflow",
                        "Workflow file name (without .json) from .agent/workflows/, "
                        + "e.g. 'bug-triage' for .agent/workflows/bug-triage.json", true)
                .addString("input",
                        "Runtime input text available to the workflow as {{workflow.input}}. "
                        + "Optional — omit when the workflow does not need external input.", false);

        return new ToolDefinition(
                NAME,
                "Execute a workflow: a multi-agent pipeline defined in a JSON file under "
                + ".agent/workflows/. Workflows run several subagents in sequence or parallel, "
                + "each with its own prompt and tools, and produce one final output. "
                + "Use list_files on '.agent/workflows/' to discover available workflows.",
                builder.build()
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        if (arguments != null && arguments.has("workflow")) {
            return "Run workflow '" + arguments.get("workflow").getAsString()
                    + "' (may execute multiple LLM calls and tools)";
        }
        return "Run a workflow";
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (arguments == null || !arguments.has("workflow")) {
            return ToolResult.error("Missing required parameter: workflow");
        }

        String workflowName = arguments.get("workflow").getAsString().trim();
        if (workflowName.isEmpty()) {
            return ToolResult.error("Parameter 'workflow' cannot be empty");
        }

        // Strip .json if provided
        if (workflowName.endsWith(".json")) {
            workflowName = workflowName.substring(0, workflowName.length() - 5);
        }

        // Reject path traversal
        if (workflowName.contains("/") || workflowName.contains("..")) {
            return ToolResult.error("Invalid workflow name: use a plain filename without path separators");
        }

        // Recursion guard
        if (runDepth.get() > 0) {
            return ToolResult.error("Cannot launch a workflow from inside a workflow (recursion guard)");
        }

        String input = null;
        if (arguments.has("input") && !arguments.get("input").isJsonNull()) {
            input = arguments.get("input").getAsString();
        }

        // Locate the workflow file
        File workspaceRoot = toolRegistry.getWorkspaceRoot();
        if (workspaceRoot == null) {
            return ToolResult.error("Workspace not available");
        }

        File workflowFile = new File(workspaceRoot, WORKFLOWS_DIR + "/" + workflowName + ".json");
        if (!workflowFile.exists()) {
            return ToolResult.error("Workflow file not found: " + WORKFLOWS_DIR + "/" + workflowName + ".json"
                    + ". Use list_files on '" + WORKFLOWS_DIR + "/' to see available workflows.");
        }

        // Read the file
        String workflowJson;
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(workflowFile);
            byte[] bytes = new byte[(int) workflowFile.length()];
            fis.read(bytes);
            fis.close();
            workflowJson = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read workflow file", e);
            return ToolResult.error("Failed to read workflow file: " + e.getMessage());
        }

        // Build identity context
        List<ChatMessage> identityMessages = null;
        String guidelines = null;
        try {
            io.finett.droidclaw.filesystem.WorkspaceManager wm =
                    new io.finett.droidclaw.filesystem.WorkspaceManager(context);
            IdentityManager identityManager = new IdentityManager(context, wm);
            identityMessages = identityManager.getIdentityMessages();

            GuidelinesManager gm = new GuidelinesManager(wm);
            guidelines = gm.loadGuidelines();
        } catch (Exception e) {
            Log.w(TAG, "Could not build identity/guidelines context for workflow", e);
        }

        // Run the workflow
        runDepth.set(runDepth.get() + 1);
        try {
            WorkflowRunner runner = new WorkflowRunner(
                    apiService, toolRegistry, settingsManager, identityMessages, guidelines);

            final StringBuilder progressLog = new StringBuilder();
            WorkflowRunResult result = runner.run(workflowJson, input, new WorkflowRunCallback() {
                @Override
                public void onNodeStart(String key) {
                    Log.d(TAG, "Workflow node started: " + key);
                    progressLog.append("→ ").append(key).append("\n");
                }

                @Override
                public void onNodeComplete(String key, WorkflowNodeStatus status) {
                    Log.d(TAG, "Workflow node complete: " + key + " -> " + status);
                }

                @Override
                public void onProgress(String message) {
                    Log.d(TAG, "Workflow progress: " + message);
                }

                @Override
                public void onComplete(String finalOutput, Map<String, TemplateResolver.NodeResult> results) {
                    Log.d(TAG, "Workflow completed successfully");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Workflow error: " + error);
                }
            });

            if (result.isSuccess()) {
                JsonObject output = new JsonObject();
                output.addProperty("status", "success");
                output.addProperty("workflow", workflowName);
                output.addProperty("output", result.getOutput());
                output.addProperty("total_tokens", result.getTotalTokens());
                output.addProperty("nodes_completed", result.getNodeResults().size());
                return ToolResult.success(output);
            } else {
                JsonObject output = new JsonObject();
                output.addProperty("status", "failed");
                output.addProperty("workflow", workflowName);
                output.addProperty("error", result.getError());
                return ToolResult.error(result.getError());
            }
        } finally {
            int depth = runDepth.get() - 1;
            if (depth == 0) runDepth.remove();
            else runDepth.set(depth);
        }
    }
}
