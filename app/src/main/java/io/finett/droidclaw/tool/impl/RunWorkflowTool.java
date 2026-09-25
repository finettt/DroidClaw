package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.finett.droidclaw.R;
import io.finett.droidclaw.workflow.Workflow;
import io.finett.droidclaw.workflow.WorkflowAgent;
import io.finett.droidclaw.workflow.WorkflowParser;
import io.finett.droidclaw.workflow.WorkflowApprovalPolicy;
import io.finett.droidclaw.workflow.ScopedToolRegistry;
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
        try {
            return prepareApproval(arguments).getDescription();
        } catch (Exception e) {
            return context.getString(R.string.workflow_review_failed, e.getMessage());
        }
    }

    /** Only trusted approval UI code may retain and execute this capability.
     * It is never stored in JSON arguments, history, or a shared approval cache. */
    public final class ApprovalReview {
        private final String name;
        private final String input;
        private final File file;
        private final String hash;
        private final String description;
        private final com.google.gson.JsonElement agentConfig;
        private final List<String> apiDestination;
        private final AtomicBoolean consumed = new AtomicBoolean();

        private ApprovalReview(String name, String input, File file, byte[] bytes) throws IOException {
            this.name = name;
            this.input = input;
            this.file = file;
            this.hash = sha256(bytes);
            this.description = buildApprovalSummary(name, new String(bytes, StandardCharsets.UTF_8), hash);
            this.agentConfig = new com.google.gson.Gson().toJsonTree(settingsManager.getAgentConfig());
            this.apiDestination = currentApiDestination();
        }

        public String getDescription() { return description; }

        public ToolResult execute() {
            if (!consumed.compareAndSet(false, true)) {
                return ToolResult.error("Workflow approval has already been used");
            }
            try {
                File current = resolveWorkflowFile(toolRegistry.getWorkspaceRoot(), name);
                byte[] bytes = readWorkflowFile(current);
                if (!file.equals(current) || !hash.equals(sha256(bytes))) {
                    return ToolResult.error("Workflow changed after approval; review and approve it again");
                }
                String json = new String(bytes, StandardCharsets.UTF_8);
                if (!agentConfig.equals(new com.google.gson.Gson().toJsonTree(settingsManager.getAgentConfig()))
                        || !apiDestination.equals(currentApiDestination())
                        || !description.equals(buildApprovalSummary(name, json, hash))) {
                    return ToolResult.error("Workflow execution policy changed after approval; review and approve it again");
                }
                // Execute these verified bytes, never reopen the path after the hash check.
                return executeReviewed(name, input, json);
            } catch (Exception e) {
                return ToolResult.error("Cannot execute approved workflow: " + e.getMessage());
            }
        }
    }

    public ApprovalReview prepareApproval(JsonObject arguments) throws IOException {
        if (arguments == null || !arguments.has("workflow")) {
            throw new IOException("Missing required parameter: workflow");
        }
        String name = requireValidWorkflowName(arguments.get("workflow").getAsString());
        String input = arguments.has("input") && !arguments.get("input").isJsonNull()
                ? arguments.get("input").getAsString() : null;
        File file = resolveWorkflowFile(toolRegistry.getWorkspaceRoot(), name);
        return new ApprovalReview(name, input, file, readWorkflowFile(file));
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        // Even an exact SHA-256 supplied by the model is not evidence of user approval.
        return ToolResult.error("Workflow requires a fresh content review and explicit approval");
    }

    private ToolResult executeReviewed(String workflowName, String input, String workflowJson) {
        if (runDepth.get() > 0) {
            return ToolResult.error("Cannot launch a workflow from inside a workflow (recursion guard)");
        }

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

    private List<String> currentApiDestination() {
        // Provider settings live outside AgentConfig. Pin the destination without
        // copying API credentials into the review, dialog, or conversation history.
        return java.util.Arrays.asList(settingsManager.getApiUrl(), settingsManager.getApiType(),
                settingsManager.getModelName());
    }

    private String buildApprovalSummary(String name, String json, String hash) throws IOException {
        WorkflowParser.Result parsed = WorkflowParser.parse(json);
        if (!parsed.isSuccess()) {
            throw new IOException(parsed.getIssues().describe());
        }
        Workflow workflow = parsed.getWorkflow();
        StringBuilder summary = new StringBuilder(context.getString(R.string.workflow_review_header,
                name, workflow.getGoal(), workflow.getAgentCount()));
        String globalModel = settingsManager.getDefaultModel();
        if (globalModel == null) globalModel = context.getString(R.string.workflow_review_global_model);
        summary.append(context.getString(R.string.workflow_review_execution_model, globalModel));
        summary.append(context.getString(R.string.workflow_review_live_settings));
        boolean autoApprove = false;
        for (WorkflowAgent agent : workflow.getAgents().values()) {
            List<String> allowed = workflow.getDefaults().resolveAllowedTools(agent.getAllowedTools());
            List<String> denied = workflow.getDefaults().resolveDeniedTools(agent.getDeniedTools());
            ScopedToolRegistry scoped = new ScopedToolRegistry(toolRegistry, allowed, denied);
            java.util.Set<String> effectiveDenied = new java.util.TreeSet<>(denied);
            effectiveDenied.add("run_workflow");
            WorkflowApprovalPolicy policy = workflow.getDefaults().resolveApproval(agent.getApproval());
            WorkflowApprovalPolicy effectivePolicy = policy == WorkflowApprovalPolicy.INHERIT
                    ? WorkflowApprovalPolicy.DENY_WRITES : policy;
            java.util.Set<String> effectiveAllowed = new java.util.TreeSet<>(scoped.getEffectiveToolNames());
            for (Tool tool : toolRegistry.getAllTools()) {
                boolean policyDenies = effectivePolicy == WorkflowApprovalPolicy.STRICT
                        || (effectivePolicy == WorkflowApprovalPolicy.DENY_WRITES && tool.requiresApproval());
                // Non-write tools under deny_writes/inherit still inherit global rejects.
                if (effectivePolicy != WorkflowApprovalPolicy.AUTO_APPROVE
                        && settingsManager.getAgentConfig() != null
                        && "ALWAYS_REJECT".equals(settingsManager.getAgentConfig()
                                .getToolApprovalOverrides().get(tool.getName()))) {
                    policyDenies = true;
                }
                boolean shellDenied = toolRegistry.requiresShellAccess(tool.getName())
                        && !toolRegistry.isShellAccessEnabled();
                if (!scoped.hasToolWithName(tool.getName()) || policyDenies || shellDenied) {
                    effectiveDenied.add(tool.getName());
                    effectiveAllowed.remove(tool.getName());
                }
            }
            String model = workflow.getDefaults().resolveModel(agent.getModel());
            if (model == null) model = settingsManager.getDefaultModel();
            if (model == null) model = context.getString(R.string.workflow_review_global_model);
            summary.append(context.getString(R.string.workflow_review_agent, agent.getKey(), model,
                    effectiveAllowed.toString(), effectiveDenied.toString(),
                    policy.jsonValue(), effectivePolicy.jsonValue()));
            autoApprove |= effectivePolicy == WorkflowApprovalPolicy.AUTO_APPROVE;
        }
        if (autoApprove) summary.append(context.getString(R.string.workflow_review_auto_approve));
        return summary.append("\nSHA-256: ").append(hash).toString();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static String requireValidWorkflowName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid workflow name: use a plain filename without path separators");
        }
        return name;
    }

    static File resolveWorkflowFile(File workspaceRoot, String name) throws IOException {
        if (workspaceRoot == null) throw new IOException("Workspace not available");
        // Reject links in either directory component too, not only the final filename.
        File lexical = new File(workspaceRoot.getCanonicalFile(), WORKFLOWS_DIR + "/" + name + ".json");
        File canonical = lexical.getCanonicalFile();
        if (!canonical.equals(lexical)) throw new IOException("Workflow path must not contain symbolic links");
        if (!canonical.isFile()) throw new IOException("Workflow file not found: " + name);
        return canonical;
    }

    static byte[] readWorkflowFile(File file) throws IOException {
        long length = file.length();
        if (length < 0 || length > Workflow.MAX_FILE_BYTES) {
            throw new IOException("Workflow file exceeds " + Workflow.MAX_FILE_BYTES + " byte cap");
        }
        byte[] bytes = new byte[(int) length];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new IOException("Workflow file changed while being read");
                offset += read;
            }
            if (input.read() != -1) throw new IOException("Workflow file changed while being read");
        }
        return bytes;
    }
}
