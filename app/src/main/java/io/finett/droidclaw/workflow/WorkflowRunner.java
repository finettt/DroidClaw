package io.finett.droidclaw.workflow;

import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Executes a validated workflow sequentially (spec §10.2, Phase 3: max_parallel = 1).
 *
 * <p>Each node is one {@link AgentLoop} run with a fresh conversation history.
 * Nodes communicate only through declared template outputs.
 *
 * <p>Thread safety: {@link #run} is blocking and must be called from a background
 * thread. {@link #cancel()} is safe to call from any thread.
 */
public final class WorkflowRunner {

    private static final String TAG = "WorkflowRunner";
    private static final Pattern GUARD_PATTERN = Pattern.compile(
            "^\\{\\{\\s*([\\w.]+)\\s*\\}\\}\\s*(==|!=|contains)\\s*'([^']*)'$");

    private final LlmApiService apiService;
    private final ToolRegistry toolRegistry;
    private final SettingsManager settingsManager;
    private final List<ChatMessage> identityMessages;
    private final String guidelinesContent;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile AgentLoop activeNodeLoop;

    /**
     * @param toolRegistry     the full tool registry (will be scoped per node)
     * @param identityMessages identity context messages (soul.md, user.md); may be null
     * @param guidelinesContent GUIDELINES.md content; may be null
     */
    public WorkflowRunner(LlmApiService apiService, ToolRegistry toolRegistry,
                          SettingsManager settingsManager,
                          List<ChatMessage> identityMessages, String guidelinesContent) {
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
        this.identityMessages = identityMessages;
        this.guidelinesContent = guidelinesContent;
    }

    /**
     * Run a workflow synchronously. Blocks until completion, failure, or cancellation.
     *
     * @param workflowJson the raw JSON workflow file content
     * @param input        runtime input for {{workflow.input}}; may be null
     * @param callback     main-looper progress/completion callbacks; may be null
     * @return the terminal result
     */
    public WorkflowRunResult run(String workflowJson, String input, WorkflowRunCallback callback) {
        cancelled.set(false);
        callback = MainThreadWorkflowRunCallback.wrap(callback);

        WorkflowLoader.Result loadResult = WorkflowLoader.load(workflowJson, buildEnvironment());
        if (!loadResult.isRunnable()) {
            String err = "Workflow validation failed:\n" + loadResult.describeProblems();
            if (callback != null) callback.onError(err);
            return WorkflowRunResult.failed(err, null);
        }

        Workflow wf = loadResult.getWorkflow();
        WorkflowGraph graph = loadResult.getGraph();

        List<String> order = graph.topologicalOrder();
        if (order == null) {
            String err = "Workflow contains a dependency cycle";
            if (callback != null) callback.onError(err);
            return WorkflowRunResult.failed(err, null);
        }

        if (!wf.getEntry().isEmpty()) {
            order = reorderWithEntry(order, wf.getEntry());
        }

        if (callback != null) {
            callback.onProgress("Workflow '" + (wf.getName() != null ? wf.getName() : "unnamed")
                    + "' loaded: " + wf.getAgentCount() + " agent(s), goal: " + wf.getGoal());
        }

        Map<String, TemplateResolver.NodeResult> results = new LinkedHashMap<>();
        Set<String> skipped = new HashSet<>();
        AtomicInteger totalTokens = new AtomicInteger(0);

        for (String key : order) {
            if (cancelled.get()) {
                if (callback != null) callback.onError("Workflow cancelled");
                return WorkflowRunResult.cancelled(results);
            }

            if (skipped.contains(key)) {
                results.put(key, TemplateResolver.NodeResult.skipped());
                if (callback != null) callback.onNodeComplete(key, WorkflowNodeStatus.SKIPPED);
                continue;
            }

            WorkflowAgent agent = wf.getAgent(key);
            WorkflowDefaults defaults = wf.getDefaults();

            if (agent.hasGuard()) {
                boolean guardResult = evaluateGuard(agent.getWhen(), wf, input, results);
                if (!guardResult) {
                    Log.d(TAG, "Node '" + key + "' guard is false, skipping");
                    results.put(key, TemplateResolver.NodeResult.skipped());
                    skipped.addAll(graph.transitiveDependents(key));
                    if (callback != null) callback.onNodeComplete(key, WorkflowNodeStatus.SKIPPED);
                    continue;
                }
            }

            if (callback != null) callback.onNodeStart(key);

            NodeExecutionResult nodeResult = executeNodeWithRetry(
                    key, agent, defaults, wf, input, results, totalTokens, callback);

            results.put(key, nodeResult.templateResult);

            if (nodeResult.templateResult.getStatus() == WorkflowNodeStatus.ERROR) {
                WorkflowErrorPolicy policy = defaults.resolveOnError(agent.getOnError());
                switch (policy) {
                    case FAIL:
                        String err = "Node '" + key + "' failed: " + nodeResult.errorMessage;
                        Log.e(TAG, err);
                        if (callback != null) {
                            callback.onNodeComplete(key, WorkflowNodeStatus.ERROR);
                            callback.onError(err);
                        }
                        return WorkflowRunResult.failed(err, results);

                    case SKIP:
                        Log.d(TAG, "Node '" + key + "' failed, skipping branch");
                        results.put(key, TemplateResolver.NodeResult.skipped());
                        skipped.addAll(graph.transitiveDependents(key));
                        break;

                    case CONTINUE:
                        Log.d(TAG, "Node '" + key + "' failed, continuing (on_error=continue)");
                        // Keep the error result; dependents see {{key.status}} == "error"
                        break;
                }
            }
            if (callback != null) {
                callback.onNodeComplete(key, results.get(key).getStatus());
            }
        }

        String finalOutput;
        try {
            finalOutput = resolveWorkflowOutput(wf, input, results);
        } catch (TemplateResolver.TemplateException e) {
            String err = "Failed to resolve workflow output: " + e.getMessage();
            if (callback != null) callback.onError(err);
            return WorkflowRunResult.failed(err, results);
        }

        if (callback != null) callback.onComplete(finalOutput, results);
        return WorkflowRunResult.success(finalOutput, results, totalTokens.get());
    }

    /** Cancel the running workflow. Safe to call from any thread. */
    public void cancel() {
        cancelled.set(true);
        AgentLoop loop = activeNodeLoop;
        if (loop != null) {
            loop.cancel();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    private NodeExecutionResult executeNodeWithRetry(
            String key, WorkflowAgent agent, WorkflowDefaults defaults,
            Workflow wf, String input,
            Map<String, TemplateResolver.NodeResult> results,
            AtomicInteger totalTokens, WorkflowRunCallback callback) {

        WorkflowRetry retry = defaults.resolveRetry(agent.getRetry());
        int maxAttempts = retry.getMaxAttempts();
        Integer nodeTimeoutMs = defaults.resolveTimeoutMs(agent.getTimeoutMs());
        long deadlineNanos = nodeTimeoutMs == null ? Long.MAX_VALUE
                : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(nodeTimeoutMs);
        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (cancelled.get()) {
                return new NodeExecutionResult(TemplateResolver.NodeResult.error(null), "cancelled");
            }

            if (attempt > 1) {
                long delay = retry.delayBeforeAttempt(attempt - 1);
                if (delay > 0) {
                    long remainingMs = remainingMillis(deadlineNanos);
                    if (remainingMs <= 0 || delay >= remainingMs) {
                        return timedOut(key, nodeTimeoutMs);
                    }
                    if (callback != null) callback.onProgress("Retrying '" + key + "' in " + delay + "ms (attempt " + attempt + "/" + maxAttempts + ")");
                    if (!sleepCancellable(delay)) {
                        return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                                cancelled.get() ? "cancelled" : "interrupted");
                    }
                }
            }

            Integer remainingTimeoutMs = nodeTimeoutMs == null ? null
                    : (int) Math.min(Integer.MAX_VALUE, remainingMillis(deadlineNanos));
            if (remainingTimeoutMs != null && remainingTimeoutMs <= 0) {
                return timedOut(key, nodeTimeoutMs);
            }
            NodeExecutionResult result = executeNode(key, agent, defaults, wf, input, results,
                    totalTokens, callback, remainingTimeoutMs);
            lastError = result.errorMessage;

            if (result.templateResult.getStatus() == WorkflowNodeStatus.OK) {
                return result;
            }

            // Only retry on transport/timeout errors, not content errors
            if (!isRetryableError(result.errorMessage)) {
                return result;
            }

            Log.d(TAG, "Node '" + key + "' attempt " + attempt + " failed: " + result.errorMessage);
        }

        return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                "Node '" + key + "' failed after " + maxAttempts + " attempt(s)"
                        + (lastError == null ? "" : ": " + lastError));
    }

    private NodeExecutionResult executeNode(
            String key, WorkflowAgent agent, WorkflowDefaults defaults,
            Workflow wf, String input,
            Map<String, TemplateResolver.NodeResult> results,
            AtomicInteger totalTokens, WorkflowRunCallback callback,
            Integer timeoutMs) {

        String resolvedPrompt;
        try {
            resolvedPrompt = resolveNodePrompt(key, agent, wf, input, results);
        } catch (TemplateResolver.TemplateException e) {
            return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                    "Template resolution failed: " + e.getMessage());
        }

        Map<String, String> resolvedInputs = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> in : agent.getInputs().entrySet()) {
                String value = TemplateResolver.resolve(in.getValue(), buildContext(wf, input, results, key));
                resolvedInputs.put(in.getKey(), value);
            }
        } catch (TemplateResolver.TemplateException e) {
            return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                    "Input resolution failed: " + e.getMessage());
        }

        List<String> allowedTools = defaults.resolveAllowedTools(agent.getAllowedTools());
        List<String> deniedTools = defaults.resolveDeniedTools(agent.getDeniedTools());
        ScopedToolRegistry scopedRegistry = new ScopedToolRegistry(toolRegistry, allowedTools, deniedTools);

        AgentLoop nodeLoop = new AgentLoop(apiService, new ScopedToolRegistryAdapter(scopedRegistry), settingsManager);

        Integer maxTurns = defaults.resolveMaxTurns(agent.getMaxTurns());
        if (maxTurns != null) {
            nodeLoop.setMaxIterations(capMaxTurns(maxTurns, settingsManager.getMaxAgentIterations()));
        }

        WorkflowApprovalPolicy approvalPolicy = defaults.resolveApproval(agent.getApproval());
        nodeLoop.setApprovalOverrides(buildApprovalOverrides(approvalPolicy, scopedRegistry));

        if (identityMessages != null) {
            nodeLoop.setIdentityContext(identityMessages);
        }
        if (guidelinesContent != null) {
            nodeLoop.setGuidelinesContext(guidelinesContent);
        }

        if (agent.hasOutputSchema()) {
            nodeLoop.setResponseSchema(agent.getOutputSchema());
        }

        activeNodeLoop = nodeLoop;

        // Timeout enforcement. timeoutMs is the remaining node-wide budget after
        // earlier attempts and retry backoff, not a fresh budget per attempt.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> finalResponse = new AtomicReference<>(null);
        final AtomicReference<String> nodeError = new AtomicReference<>(null);
        final AtomicReference<List<ChatMessage>> finalHistory = new AtomicReference<>(null);

        List<ChatMessage> freshHistory = new ArrayList<>();
        String goalPrefix = "Goal: " + wf.getGoal() + "\n\n";
        freshHistory.add(new ChatMessage(goalPrefix + resolvedPrompt, ChatMessage.TYPE_USER));

        nodeLoop.start(freshHistory, new AgentLoop.AgentCallback() {
            @Override public void onProgress(String status) {
                if (callback != null) callback.onProgress("[" + key + "] " + status);
            }
            @Override public void onToolCall(String toolName, String arguments) {
                if (callback != null) callback.onProgress("[" + key + "] Tool: " + toolName);
            }
            @Override public void onToolResult(String toolName, String result) {}
            @Override public void onComplete(String response, List<ChatMessage> history) {
                finalResponse.set(response);
                finalHistory.set(history);
                latch.countDown();
            }
            @Override public void onError(String error) {
                nodeError.set(error);
                latch.countDown();
            }
            @Override public void onCancelled(List<ChatMessage> history) {
                // Without this, a cancellation that wins the race against
                // onComplete/onError would leave the latch stuck forever.
                nodeError.set("cancelled");
                latch.countDown();
            }
            @Override public void onApprovalRequired(String toolName, String description,
                    JsonObject arguments, AgentLoop.ApprovalCallback approvalCallback) {
                // Workflow nodes should not reach here due to approval overrides,
                // but deny by default for safety
                approvalCallback.onDenied();
            }
        });

        try {
            if (timeoutMs != null) {
                boolean done = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
                if (!done) {
                    nodeLoop.cancel();
                    // Bounded wait so the node's cancellation finalizes (onCancelled
                    // counts the latch down) before we report the timeout. Cancellation
                    // finalization is posted to the main looper, so never wait for it
                    // while blocking that same looper.
                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    activeNodeLoop = null;
                    return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                            "Node '" + key + "' timed out after " + timeoutMs + "ms");
                }
            } else {
                latch.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            nodeLoop.cancel();
            activeNodeLoop = null;
            return new NodeExecutionResult(TemplateResolver.NodeResult.error(null), "interrupted");
        }

        activeNodeLoop = null;

        if (nodeError.get() != null) {
            return new NodeExecutionResult(TemplateResolver.NodeResult.error(null), nodeError.get());
        }

        String response = finalResponse.get();
        if (response == null || response.isEmpty()) {
            return new NodeExecutionResult(TemplateResolver.NodeResult.error(null), "Empty response from node");
        }

        totalTokens.addAndGet(nodeLoop.getTotalTokens());

        JsonObject structured = null;
        if (agent.hasOutputSchema()) {
            try {
                structured = JsonParser.parseString(response).getAsJsonObject();
            } catch (Exception e) {
                return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                        "Node output is not valid JSON: " + e.getMessage());
            }
        }

        return new NodeExecutionResult(TemplateResolver.NodeResult.ok(response, structured), null);
    }

    private String resolveNodePrompt(String key, WorkflowAgent agent, Workflow wf, String input,
                                     Map<String, TemplateResolver.NodeResult> results)
            throws TemplateResolver.TemplateException {

        WorkflowPrompt prompt = agent.getPrompt();
        String template = prompt.effectiveTemplate();
        TemplateResolver.Context ctx = buildContext(wf, input, results, key);

        for (Map.Entry<String, String> in : agent.getInputs().entrySet()) {
            String value = TemplateResolver.resolve(in.getValue(), ctx);
            ctx.input(in.getKey(), value);
        }

        String resolved = TemplateResolver.resolve(template, ctx, TemplateResolver.Mode.STRICT);

        if (prompt.getSystem() != null && !prompt.getSystem().isEmpty()) {
            String systemResolved = TemplateResolver.resolve(prompt.getSystem(), ctx, TemplateResolver.Mode.STRICT);
            return "[System: " + systemResolved + "]\n\n" + resolved;
        }
        return resolved;
    }

    private TemplateResolver.Context buildContext(Workflow wf, String input,
                                                   Map<String, TemplateResolver.NodeResult> results,
                                                   String currentNode) {
        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowName(wf.getName() != null ? wf.getName() : "")
                .workflowGoal(wf.getGoal())
                .workflowInput(input != null ? input : "");

        for (Map.Entry<String, TemplateResolver.NodeResult> e : results.entrySet()) {
            ctx.node(e.getKey(), e.getValue());
            WorkflowAgent a = wf.getAgent(e.getKey());
            if (a != null) {
                WorkflowErrorPolicy policy = wf.getDefaults().resolveOnError(a.getOnError());
                if (policy == WorkflowErrorPolicy.CONTINUE) {
                    ctx.tolerable(e.getKey());
                }
            }
        }
        return ctx;
    }

    private String resolveWorkflowOutput(Workflow wf, String input,
                                          Map<String, TemplateResolver.NodeResult> results)
            throws TemplateResolver.TemplateException {

        String outputTemplate = wf.getOutput();
        if (outputTemplate == null || outputTemplate.isEmpty()) {
            // Default: last completed node's output
            String lastKey = null;
            for (String k : results.keySet()) {
                if (results.get(k).getStatus() == WorkflowNodeStatus.OK) {
                    lastKey = k;
                }
            }
            if (lastKey == null) {
                throw new TemplateResolver.TemplateException("output",
                        "no node completed successfully and no explicit output template");
            }
            TemplateResolver.NodeResult last = results.get(lastKey);
            return last.getOutput() != null ? last.getOutput() : "";
        }

        TemplateResolver.Context ctx = new TemplateResolver.Context()
                .workflowName(wf.getName() != null ? wf.getName() : "")
                .workflowGoal(wf.getGoal())
                .workflowInput(input != null ? input : "");

        for (Map.Entry<String, TemplateResolver.NodeResult> e : results.entrySet()) {
            ctx.node(e.getKey(), e.getValue());
            WorkflowAgent a = wf.getAgent(e.getKey());
            if (a != null) {
                WorkflowErrorPolicy policy = wf.getDefaults().resolveOnError(a.getOnError());
                if (policy == WorkflowErrorPolicy.CONTINUE) {
                    ctx.tolerable(e.getKey());
                }
            }
        }

        return TemplateResolver.resolve(outputTemplate, ctx, TemplateResolver.Mode.FINAL);
    }

    private boolean evaluateGuard(String when, Workflow wf, String input,
                                   Map<String, TemplateResolver.NodeResult> results) {
        Matcher m = GUARD_PATTERN.matcher(when.trim());
        if (!m.matches()) {
            Log.w(TAG, "Invalid guard expression: " + when + " — treating as false");
            return false;
        }

        String expr = m.group(1);
        String op = m.group(2);
        String literal = m.group(3);

        String value;
        try {
            TemplateResolver.Context ctx = buildContext(wf, input, results, null);
            value = TemplateResolver.resolve("{{" + expr + "}}", ctx, TemplateResolver.Mode.STRICT);
        } catch (TemplateResolver.TemplateException e) {
            Log.w(TAG, "Guard expression failed: " + e.getMessage() + " — treating as false");
            return false;
        }

        switch (op) {
            case "==": return literal.equals(value);
            case "!=": return !literal.equals(value);
            case "contains": return value != null && value.contains(literal);
            default: return true;
        }
    }

    private Map<String, ToolApprovalMode> buildApprovalOverrides(
            WorkflowApprovalPolicy policy, ScopedToolRegistry scopedRegistry) {

        if (policy == WorkflowApprovalPolicy.INHERIT) {
            // Nodes have no interactive approval UI. Never inherit permission to write
            // from permissive globals or per-tool ALWAYS_APPROVE settings.
            policy = WorkflowApprovalPolicy.DENY_WRITES;
        }

        Map<String, ToolApprovalMode> overrides = new HashMap<>();
        for (String toolName : scopedRegistry.getEffectiveToolNames()) {
            switch (policy) {
                case AUTO_APPROVE:
                    overrides.put(toolName, ToolApprovalMode.ALWAYS_APPROVE);
                    break;
                case DENY_WRITES:
                    Tool tool = scopedRegistry.getTool(toolName);
                    if (tool != null && tool.requiresApproval()) {
                        overrides.put(toolName, ToolApprovalMode.ALWAYS_REJECT);
                    }
                    break;
                case STRICT:
                    overrides.put(toolName, ToolApprovalMode.ALWAYS_REJECT);
                    break;
                default:
                    break;
            }
        }
        return overrides;
    }

    private List<String> reorderWithEntry(List<String> topoOrder, List<String> entry) {
        List<String> reordered = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String e : entry) {
            if (topoOrder.contains(e) && added.add(e)) {
                reordered.add(e);
            }
        }
        for (String k : topoOrder) {
            if (added.add(k)) {
                reordered.add(k);
            }
        }
        return reordered;
    }

    static int capMaxTurns(int requested, int globalLimit) {
        return Math.max(1, Math.min(requested, globalLimit));
    }

    private long remainingMillis(long deadlineNanos) {
        if (deadlineNanos == Long.MAX_VALUE) return Long.MAX_VALUE;
        long nanos = deadlineNanos - System.nanoTime();
        if (nanos <= 0) return 0;
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos));
    }

    private boolean sleepCancellable(long delayMs) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        while (!cancelled.get()) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());
            if (remaining <= 0) return true;
            try {
                Thread.sleep(Math.min(remaining, 50L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private NodeExecutionResult timedOut(String key, Integer timeoutMs) {
        return new NodeExecutionResult(TemplateResolver.NodeResult.error(null),
                "Node '" + key + "' timed out after " + timeoutMs + "ms");
    }

    private boolean isRetryableError(String error) {
        if (error == null) return false;
        String lower = error.toLowerCase();
        return lower.contains("network") || lower.contains("timeout")
                || lower.contains("connection") || lower.contains("timed out")
                || lower.contains("503") || lower.contains("429");
    }

    private WorkflowEnvironment buildEnvironment() {
        return new WorkflowEnvironment() {
            @Override
            public boolean hasTool(String toolName) {
                return toolRegistry.hasToolWithName(toolName);
            }

            @Override
            public boolean hasModel(String providerId, String modelId) {
                // For now, accept all model references; full validation requires
                // SettingsManager model lookup which is done in B1
                return true;
            }

            @Override
            public int defaultMaxTurns() {
                return settingsManager.getMaxAgentIterations();
            }

            @Override
            public boolean globalRequireApproval() {
                return settingsManager.isRequireApproval();
            }
        };
    }

    private static final class NodeExecutionResult {
        final TemplateResolver.NodeResult templateResult;
        final String errorMessage;

        NodeExecutionResult(TemplateResolver.NodeResult templateResult, String errorMessage) {
            this.templateResult = templateResult;
            this.errorMessage = errorMessage;
        }
    }
}
