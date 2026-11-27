package io.finett.droidclaw.agent;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;

import io.finett.droidclaw.approval.ApprovalAuditLog;
import io.finett.droidclaw.approval.CommandJudge;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Effort;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.service.BackgroundProcessManager;
import io.finett.droidclaw.shell.ExecPlan;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.impl.RunWorkflowTool;
import io.finett.droidclaw.tool.impl.ShellTool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.repository.MemoryRepository;

public class AgentLoop {
    private static final String TAG = "AgentLoop";

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(?:`([^`]+)`|(/data/data/[^\\s]+|uploads/[^\\s]+|home/[^\\s]+|tmp/[^\\s]+))"
    );

    /** Display bound for tool args in persisted history — same truncation the adapter applies. */
    private static final int TOOL_ARGS_HISTORY_LIMIT = 200;

    private final LlmApiService apiService;
    private final ToolRegistry toolRegistry;
    private final SettingsManager settingsManager;
    private final ConversationSummarizer summarizer;
    private final MemoryContextBuilder memoryContext;
    /**
     * Background dispatcher for blocking work (shell process execution, the auto-mode
     * judge, approval-plan building). Must run off the main looper. Process execution
     * (waitFor + stream pumping + output limiting + timeout) is only ever invoked
     * through this executor when the caller is on the main thread; the main thread
     * only renders results delivered via Handler/main executor.
     */
    private final Executor workflowExecutor;
    private int iterationCount;
    /** Explicit per-node turn cap (workflow max_turns); 0 means unset. */
    private int maxIterations;
    /** Effort override for this run (chat switcher / workflow node); null means global. */
    private Effort effortOverride;
    /** Effort resolved from the global agent config for this run. */
    private Effort globalEffort;
    private boolean requireApproval;
    private boolean streamResponses;
    private List<ChatMessage> identityMessages;
    private String guidelinesContent;
    private JsonObject responseSchema;
    /** Per-run approval overrides set by the workflow runner; null means use global config. */
    private java.util.Map<String, ToolApprovalMode> scopedApprovalOverrides;
    /** Per-run model override for workflow nodes; null means use global default. */
    private io.finett.droidclaw.model.Provider modelOverrideProvider;
    private io.finett.droidclaw.model.Model modelOverrideModel;

    // ==================== Cancellation state ====================

    /** Set when the user requests cancellation; stops the loop at the next checkpoint. */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Guarantees {@link #finalizeCancellation()} dispatches exactly once. */
    private final AtomicBoolean cancelFinalized = new AtomicBoolean(false);

    /** Text streamed so far in the current response segment (guarded by itself). */
    private final StringBuilder streamedText = new StringBuilder();

    /** Per-run request scope: cancelling it aborts only this run's HTTP calls (issue #144). */
    private volatile RequestScope runScope;

    /** Judge for shell commands when the shell-approval mode is {@code auto}. Null if unavailable. */
    private volatile CommandJudge judge;

    /** Append-only audit trail for shell commands across all approval modes. */
    private volatile ApprovalAuditLog auditLog;

    /**
     * Set while the judge + audit-log construction is dispatched off the main thread.
     * The dispatched task posts the re-entry back on the main Handler once construction
     * finishes; this flag stops a re-entrant {@link #ensureJudgeAndAudit()} (which the
     * post-back triggers) from dispatching a second time while the construction is still
     * pending on the background executor.
     */
    private volatile boolean auditBuiltOffMain;

    /**
     * Disposition recorded for the in-flight shell command, keyed by the tool call id and
     * consumed by the audit on completion. Keying by id (not a single field) guarantees the
     * line is never labelled with a disposition that belonged to a different command if
     * execution is interrupted between the set and the consume.
     */
    private final java.util.Map<String, String> pendingDisposition = new java.util.concurrent.ConcurrentHashMap<>();

    /** History of the in-flight run, used by cancellation to preserve partial output. */
    private volatile List<ChatMessage> activeHistory;

    /** Callback of the in-flight run; nulled once the run reaches a terminal state. */
    private volatile AgentCallback activeCallback;

    /**
     * Look up the per-tool approval override for a given tool name.
     * Returns {@link ToolApprovalMode#DEFAULT} if no override is set or if
     * reading the overrides fails (logged at warn level).
     */
    private ToolApprovalMode getPerToolApprovalMode(String toolName) {
        // Scoped overrides (workflow runner) take precedence over global config
        if (scopedApprovalOverrides != null) {
            ToolApprovalMode scoped = scopedApprovalOverrides.get(toolName);
            if (scoped != null) return scoped;
        }
        try {
            java.util.Map<String, String> overrides = settingsManager.getAgentConfig()
                    .getToolApprovalOverrides();
            if (overrides == null) return ToolApprovalMode.DEFAULT;
            String value = overrides.get(toolName);
            if (value == null) return ToolApprovalMode.DEFAULT;
            try {
                return ToolApprovalMode.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return ToolApprovalMode.DEFAULT;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading tool approval override for " + toolName, e);
            return ToolApprovalMode.DEFAULT;
        }
    }

    // ==================== Shell approval helpers ====================

    /**
     * Resolve the host Context via the tool registry. Returns null when unavailable so the
     * judge and audit log stay optional (legacy behaviour for context-less unit paths).
     */
    private android.content.Context resolveContext() {
        try {
            return toolRegistry != null ? toolRegistry.getContext() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lazily build the command judge and shell audit log, which need the host Context.
     * Called only on the first shell-command approval pass, never during construction,
     * so context-less unit paths keep the legacy behaviour and never call getContext().
     * Idempotent and safe under the concurrent tool-approval paths.
     *
     * <p>The audit-log constructor calls {@code context.getFilesDir()}, which performs a
     * disk read on the calling thread (a strict-mode {@code DiskReadViolation} on the main
     * thread). When the caller is on the main thread, construction is dispatched to the
     * background executor and re-entered on the main Handler once it completes, so the main
     * thread never reads disk here; off the main thread the build stays inline.
     */
    private synchronized void ensureJudgeAndAudit() {
        if (judge != null && auditLog != null) {
            return;
        }
        if (isOnMainThread() && !auditBuiltOffMain) {
            auditBuiltOffMain = true;
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                workflowExecutor.execute(() -> {
                    // Runs off the main thread: getFilesDir() disk read happens here, not on main.
                    buildJudgeAndAuditLocked();
                    final Handler postBack = mainHandler;
                    postBack.post(this::ensureJudgeAndAudit);
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Executor unavailable: build inline so the audit trail still exists.
                auditBuiltOffMain = false;
                buildJudgeAndAuditLocked();
            }
            return;
        }
        if (auditBuiltOffMain) {
            // Construction is already dispatched off main; the post-back re-enters and
            // finishes building here. Wait for it rather than building on this thread.
            return;
        }
        // Off the main thread (or a context-less unit path): build inline, no main I/O.
        buildJudgeAndAuditLocked();
    }

    /** Build the judge and audit log under the method's lock; safe to call off the main thread. */
    private synchronized void buildJudgeAndAuditLocked() {
        android.content.Context hostContext = resolveContext();
        if (hostContext == null) {
            return;
        }
        if (auditLog == null) {
            auditLog = new ApprovalAuditLog(hostContext);
        }
        if (judge == null && apiService != null && settingsManager != null) {
            judge = new CommandJudge(apiService, settingsManager);
        }
    }

    private String currentShellApprovalMode() {
        try {
            return settingsManager != null
                    ? settingsManager.getAgentConfig().getShellApprovalMode()
                    : "ask_all";
        } catch (Exception e) {
            return "ask_all";
        }
    }

    private String currentBackendLabel() {
        try {
            String backend = settingsManager != null
                    ? settingsManager.getAgentConfig().getShellBackend()
                    : null;
            return backend != null ? backend : "distro";
        } catch (Exception e) {
            return "distro";
        }
    }

    private String workspaceRootPath() {
        try {
            java.io.File root = toolRegistry.getWorkspaceRoot();
            return root != null ? root.getAbsolutePath() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String commandArgument(JsonObject arguments) {
        return arguments != null && arguments.has("command")
                ? arguments.get("command").getAsString()
                : "(unknown command)";
    }

    private String cwdArgument(JsonObject arguments) {
        if (arguments != null && arguments.has("working_directory")
                && !arguments.get("working_directory").isJsonNull()) {
            return arguments.get("working_directory").getAsString();
        }
        return "(workspace root)";
    }

    /**
     * Audit a single shell command. Writes one line per command with the disposition and
     * exit code; never includes tool output bodies. Guarded to shell tools only. The file
     * append runs on the background dispatcher when the caller is on the main thread, so
     * the UI thread never writes to disk; off the main thread the write stays inline.
     */
    private void auditShell(String toolName, String command, String disposition, Integer exitCode) {
        if (auditLog == null || !"execute_shell".equals(toolName)) {
            return;
        }
        String mode = currentShellApprovalMode();
        if (isOnMainThread()) {
            try {
                workflowExecutor.execute(() -> auditLog.log(mode, command, disposition, exitCode));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Executor unavailable: never lose the audit line, even for a rare
                // main-thread write in this degraded path.
                auditLog.log(mode, command, disposition, exitCode);
            }
            return;
        }
        auditLog.log(mode, command, disposition, exitCode);
    }

    /**
     * Wrap a background task so a shell command's completion is recorded in the audit log.
     * The disposition was resolved before dispatch and stored by tool call id; a failed or
     * missing disposition is recorded as "error" so it is never mislabelled with another
     * command's outcome. Non-shell background tools are returned unchanged.
     */
    private java.util.concurrent.Callable<ToolResult> auditOnCompletion(
            LlmApiService.ToolCall toolCall, JsonObject arguments,
            java.util.concurrent.Callable<ToolResult> delegate) {
        if (!(toolRegistry.getTool(toolCall.getName()) instanceof ShellTool)) {
            return delegate;
        }
        return () -> {
            ToolResult result = delegate.call();
            String disposition = pendingDisposition.remove(toolCall.getId());
            if (disposition == null) {
                disposition = "dialog-approved";
            }
            if (result == null || !result.isSuccess() || disposition == null) {
                disposition = "error";
            }
            auditShell(toolCall.getName(), commandArgument(arguments), disposition,
                    exitCodeFromResult(result == null ? null : result.getContent()));
            return result;
        };
    }

    /** Extract the numeric exit code from a shell tool result body, or null when absent. */
    private Integer exitCodeFromResult(String content) {
        if (content == null) return null;
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("exit_code") && !obj.get("exit_code").isJsonNull()) {
                return obj.get("exit_code").getAsInt();
            }
        } catch (Exception e) {
            // non-JSON body — no exit code to record
        }
        return null;
    }

    /**
     * Set per-run approval overrides. Used by the workflow runner to enforce
     * per-node approval policies without touching global settings.
     * Pass {@code null} to clear and fall back to global config.
     */
    public void setApprovalOverrides(java.util.Map<String, ToolApprovalMode> overrides) {
        this.scopedApprovalOverrides = overrides;
    }

    /**
     * Set a per-run model override. Used by the workflow runner to let each
     * node use a different model. Pass {@code null} for either
     * argument to fall back to the global default.
     */
    public void setModelOverride(io.finett.droidclaw.model.Provider provider,
                                  io.finett.droidclaw.model.Model model) {
        this.modelOverrideProvider = provider;
        this.modelOverrideModel = model;
        if (provider != null && model != null) {
            Log.d(TAG, "Model override set: " + provider.getId() + "/" + model.getId());
        }
    }

    public boolean hasModelOverride() {
        return modelOverrideProvider != null && modelOverrideModel != null;
    }

    // Token tracking - "Last Usage" algorithm
    // Current context tokens (from last API response - this is the ACTUAL context size)
    private int currentContextTokens = 0;
    private int currentPromptTokens = 0;
    private int currentCompletionTokens = 0;

    // Session cumulative tokens (total spent across all requests)
    private int totalTokens = 0;
    private int totalPromptTokens = 0;
    private int totalCompletionTokens = 0;
    private int totalToolCalls = 0;

    public interface AgentCallback {
        void onProgress(String status);
        void onToolCall(String toolName, String arguments);
        void onToolResult(String toolName, String result);
        void onComplete(String finalResponse, List<ChatMessage> conversationHistory);
        void onError(String error);

        /**
         * Called when a tool requires user approval before execution.
         *
         * @param toolName Name of the tool requesting approval
         * @param description Human-readable description of what the tool will do
         * @param arguments The tool arguments
         * @param approvalCallback Callback to indicate approval or denial
         */
        void onApprovalRequired(String toolName, String description, JsonObject arguments, ApprovalCallback approvalCallback);

        /**
         * Called with incremental text deltas while a streaming (SSE) response is
         * arriving, before {@link #onComplete}. Optional — the default is a no-op
         * for callbacks that only care about the final result.
         */
        default void onStreamDelta(String delta) {}

        /**
         * Called when the run was cancelled by the user. The history contains any
         * partially streamed assistant text preserved as a normal assistant message.
         * Optional — the default is a no-op.
         */
        default void onCancelled(List<ChatMessage> conversationHistory) {}
    }

    public interface ApprovalCallback {
        void onApproved();
        void onDenied();
    }

    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager) {
        this(apiService, toolRegistry, settingsManager, null, null);
    }

    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager,
                     ConversationSummarizer summarizer, MemoryContextBuilder memoryContext) {
        this(apiService, toolRegistry, settingsManager, summarizer, memoryContext,
                io.finett.droidclaw.workflow.WorkflowDispatch.executor());
    }

    /**
     * The supplied executor must run blocking work off the main looper. It dispatches
     * shell process execution, the auto-mode judge, and approval-plan building when
     * the caller is on the main thread.
     */
    public AgentLoop(LlmApiService apiService, ToolRegistry toolRegistry, SettingsManager settingsManager,
                     ConversationSummarizer summarizer, MemoryContextBuilder memoryContext,
                     Executor workflowExecutor) {
        this.workflowExecutor = java.util.Objects.requireNonNull(workflowExecutor);
        this.apiService = apiService;
        this.toolRegistry = toolRegistry;
        this.settingsManager = settingsManager;
        this.summarizer = summarizer;
        this.memoryContext = memoryContext;
        this.iterationCount = 0;

        // Judge + audit log are built lazily (ensureJudgeAndAudit) so context-less unit
        // paths keep the legacy (always-ask) shell behaviour and never touch getContext().
        this.maxIterations = 0; // unset; the effort budget caps the run unless a node sets max_turns

        if (settingsManager != null) {
            this.globalEffort = resolveEffort(settingsManager.getAgentConfig());
            this.requireApproval = settingsManager.isRequireApproval();
            io.finett.droidclaw.model.AgentConfig cfg = settingsManager.getAgentConfig();
            this.streamResponses = cfg != null && cfg.isStreamResponses();
        } else {
            this.globalEffort = Effort.getDefault();
            this.requireApproval = true;
            // No settings manager (unit tests / programmatic use): keep the
            // legacy non-streaming path.
            this.streamResponses = false;
        }
    }

    /** Whether streaming (SSE) responses are enabled for the standard chat path. */
    public boolean isStreamingEnabled() {
        return streamResponses;
    }

    /**
     * Override the maximum iteration cap for this run. Used by the workflow
     * runner to enforce per-node {@code max_turns}.
     *
     * @param max the new cap, clamped to [1, 200]
     */
    public void setMaxIterations(int max) {
        this.maxIterations = Math.max(1, Math.min(200, max));
    }

    /**
     * Override the reasoning effort / turn budget for this run. Takes priority
     * over the global setting. Pass {@code null} to clear and fall back to the
     * global effort. Used by the chat switcher and workflow nodes.
     */
    public void setEffortOverride(Effort effort) {
        this.effortOverride = effort;
    }

    /** Effort in effect for this run: the override if set, otherwise the global effort. */
    public Effort getEffort() {
        return effortOverride != null ? effortOverride : globalEffort;
    }

    /**
     * The turn cap for the current run: the tighter of the active effort's budget
     * and any explicit per-node cap set via {@link #setMaxIterations} (workflow
     * max_turns). An effort with no budget (Max) and no explicit cap leaves the
     * run uncapped, returned as 0 so the stop-check is skipped entirely.
     */
    private int effectiveTurnCap() {
        Effort effort = getEffort();
        int budget = effort.turnBudget();
        if (effort.isUnlimited()) {
            return maxIterations > 0 ? maxIterations : 0;
        }
        if (maxIterations <= 0) {
            return budget;
        }
        return Math.min(budget, maxIterations);
    }

    /** Resolve an effort string from the agent config, falling back to the default. */
    private static Effort resolveEffort(io.finett.droidclaw.model.AgentConfig cfg) {
        if (cfg == null) return Effort.getDefault();
        try {
            return Effort.fromString(cfg.getEffort());
        } catch (IllegalArgumentException e) {
            return Effort.getDefault();
        }
    }

    public void setIdentityContext(List<ChatMessage> identityMessages) {
        this.identityMessages = identityMessages;
        Log.d(TAG, "Identity context set: " + (identityMessages != null ? identityMessages.size() : 0) + " message(s)");
    }

    /**
     * Set the operational guidelines ({@code .agent/GUIDELINES.md}) to inject as
     * a system message into every iteration. Updated automatically after each
     * finished conversation by {@link GuidelinesReflector}.
     */
    public void setGuidelinesContext(String guidelinesContent) {
        this.guidelinesContent = guidelinesContent;
        Log.d(TAG, "Guidelines context set: "
                + (guidelinesContent != null ? guidelinesContent.length() : 0) + " chars");
    }

    public void setResponseSchema(JsonObject schema) {
        this.responseSchema = schema;
        Log.d(TAG, "Response schema set: " + (schema != null ? "enabled" : "disabled"));
    }

    public void start(List<ChatMessage> conversationHistory, AgentCallback callback) {
        iterationCount = 0;
        cancelled.set(false);
        cancelFinalized.set(false);
        runScope = new RequestScope();
        runScope.setEffort(getEffort());
        synchronized (streamedText) {
            streamedText.setLength(0);
        }

        // Make a mutable copy of the conversation history
        List<ChatMessage> workingHistory = new ArrayList<>(conversationHistory);
        this.activeHistory = workingHistory;
        this.activeCallback = callback;

        executeIteration(workingHistory, callback);
    }

    /** Whether cancellation has been requested for the current run. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * The request scope of the current run. Exposed for future mid-run steering,
     * which can cancel just the in-flight call and re-issue within the same scope.
     */
    RequestScope getRunScope() {
        return runScope;
    }

    /**
     * Cancel the running agent loop: aborts this run's in-flight HTTP requests
     * (only this run's — unrelated requests on the shared client are untouched,
     * see issue #144) and stops the
     * iteration cycle at the next checkpoint. Any text already streamed in the
     * current response segment is preserved as a partial assistant message, and the
     * callback receives {@link AgentCallback#onCancelled(List)}.
     *
     * <p>Safe to call from any thread. Finalization is posted to the main thread so
     * that stream deltas already queued there are accumulated first.</p>
     */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        Log.d(TAG, "Agent loop cancellation requested");
        RequestScope scope = runScope;
        if (scope != null) {
            scope.cancel();
        }
        new Handler(Looper.getMainLooper()).post(this::finalizeCancellation);
    }

    /**
     * Finalize a cancellation on the main thread: append any partially streamed
     * assistant text to the conversation history and notify the callback exactly once.
     */
    private void finalizeCancellation() {
        if (!cancelFinalized.compareAndSet(false, true)) {
            return;
        }
        AgentCallback callback = activeCallback;
        List<ChatMessage> history = activeHistory;
        detachActiveRun();
        if (callback == null || history == null) {
            Log.d(TAG, "Cancellation finalized after run already ended");
            return;
        }

        String partialText;
        synchronized (streamedText) {
            partialText = streamedText.toString().trim();
            streamedText.setLength(0);
        }
        if (!partialText.isEmpty()) {
            history.add(new ChatMessage(partialText, ChatMessage.TYPE_ASSISTANT));
            Log.d(TAG, "Cancellation preserved partial response (" + partialText.length() + " chars)");
        }
        callback.onCancelled(history);
    }

    /** Drop run-local policy before notifying callers, which may reuse this loop. */
    private void detachActiveRun() {
        activeCallback = null;
        activeHistory = null;
        pendingDisposition.clear();
        setApprovalOverrides(null);
        setModelOverride(null, null);
        this.effortOverride = null;
    }

    /**
     * Execute one iteration of the agent loop.
     */
    private void executeIteration(List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (cancelled.get()) {
            return;
        }
        iterationCount++;

        int cap = effectiveTurnCap();
        if (cap > 0 && iterationCount > cap) {
            detachActiveRun();
            callback.onError("Maximum iterations (" + cap + ") reached. The agent may be stuck in a loop.");
            return;
        }

        Log.d(TAG, "Iteration " + iterationCount + "/" + cap);

        // Check if summarization needed using "Last Usage" algorithm (if summarizer available)
        // Use actual current context tokens instead of estimated tokens
        if (summarizer != null && summarizer.needsSummarization(currentContextTokens)) {
            Log.d(TAG, "Context limit approaching (" + currentContextTokens + " tokens), summarizing conversation...");
            conversationHistory.add(ChatMessage.createSystemMessage(
                    "Context limit approaching — summarizing..."));

            final RequestScope summaryScope = runScope;
            summarizer.summarizeAndSave(conversationHistory, summaryScope, new ConversationSummarizer.SummarizeCallback() {
                @Override
                public void onResult(List<ChatMessage> compressedHistory) {
                    if (summaryScope.isCancelled()) return;
                    Log.d(TAG, "Summary saved, continuing conversation...");
                    conversationHistory.add(ChatMessage.createSystemMessage("Summary saved"));

                    // Replace conversation history with compressed version
                    conversationHistory.clear();
                    conversationHistory.addAll(compressedHistory);

                    // Reset current context tokens after compression (context is now clean)
                    // Session cumulative tokens are preserved
                    resetCurrentContext();

                    // Continue with compressed conversation
                    continueIteration(conversationHistory, callback);
                }

                @Override
                public void onError(Throwable error) {
                    if (summaryScope.isCancelled()) return;
                    Log.e(TAG, "Summarization failed, continuing with full history", error);
                    // Continue anyway with full history
                    continueIteration(conversationHistory, callback);
                }
            });
            return;
        }

        // Continue normal iteration
        continueIteration(conversationHistory, callback);
    }

    /**
     * Continue iteration with conversation (after optional summarization).
     */
    private void continueIteration(List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (cancelled.get()) {
            return;
        }
        List<ChatMessage> contextMessages = new ArrayList<>();

        if (identityMessages != null) {
            contextMessages.addAll(identityMessages);
        }

        if (memoryContext != null) {
            String memoryCtx = memoryContext.buildMemoryContext();
            if (!memoryCtx.isEmpty()) {
                contextMessages.add(new ChatMessage(memoryCtx, ChatMessage.TYPE_SYSTEM));
                Log.d(TAG, "Added memory context: " + memoryCtx.length() + " chars");
            }
        }

        if (guidelinesContent != null && !guidelinesContent.trim().isEmpty()) {
            String wrappedGuidelines = "--- OPERATIONAL GUIDELINES ---\n\n"
                    + guidelinesContent.trim()
                    + "\n\n--- END GUIDELINES ---\n";
            contextMessages.add(new ChatMessage(wrappedGuidelines, ChatMessage.TYPE_SYSTEM));
            Log.d(TAG, "Added guidelines context: " + wrappedGuidelines.length() + " chars");
        }

        JsonArray tools = toolRegistry.getToolDefinitions();

        if (responseSchema != null) {
            sendStructuredMessage(conversationHistory, tools, contextMessages, callback);
        } else {
            sendStandardMessage(conversationHistory, tools, contextMessages, callback);
        }
    }

    /**
     * Send message using structured outputs API.
     */
    private void sendStructuredMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                       List<ChatMessage> contextMessages, AgentCallback callback) {
        LlmApiService.StructuredResponseCallback cb = new LlmApiService.StructuredResponseCallback() {
            @Override
            public void onSuccess(LlmApiService.StructuredResponse response) {
                trackTokenUsage(response.getUsage());
                if (response.isRefusal()) {
                    Log.w(TAG, "Model refused to respond: " + response.getRefusal());
                    handleRefusal(response, conversationHistory, callback);
                    return;
                }
                handleStructuredLlmResponse(response, conversationHistory, callback);
            }

            @Override
            public void onError(String error) {
                detachActiveRun();
                callback.onError(error);
            }
        };

        if (hasModelOverride()) {
            apiService.sendMessageStructured(conversationHistory, tools, contextMessages,
                    responseSchema, modelOverrideProvider, modelOverrideModel, runScope, cb);
        } else {
            apiService.sendMessageStructured(conversationHistory, tools, contextMessages,
                    responseSchema, runScope, cb);
        }
    }

    /**
     * Send message using standard API.
     */
    private void sendStandardMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                     List<ChatMessage> contextMessages, AgentCallback callback) {
        if (streamResponses && !hasModelOverride()) {
            // Streaming path (global model only for now)
            apiService.sendMessageWithToolsStreaming(conversationHistory, tools, contextMessages,
                    runScope, buildStreamingCallback(conversationHistory, callback));
            return;
        }

        LlmApiService.ChatCallbackWithTools cb = new LlmApiService.ChatCallbackWithTools() {
            @Override
            public void onSuccess(LlmApiService.LlmResponse response) {
                trackTokenUsage(response.getUsage());
                handleLlmResponse(response, conversationHistory, callback);
            }

            @Override
            public void onError(String error) {
                detachActiveRun();
                callback.onError(error);
            }
        };

        if (hasModelOverride()) {
            apiService.sendMessageWithTools(conversationHistory, tools, contextMessages,
                    modelOverrideProvider, modelOverrideModel, runScope, cb);
        } else {
            apiService.sendMessageWithTools(conversationHistory, tools, contextMessages, runScope, cb);
        }
    }

    private LlmApiService.StreamingChatCallback buildStreamingCallback(
            List<ChatMessage> conversationHistory, AgentCallback callback) {
        return new LlmApiService.StreamingChatCallback() {
            @Override
            public void onDelta(String textChunk) {
                if (textChunk != null && !cancelled.get()) {
                    synchronized (streamedText) {
                        streamedText.append(textChunk);
                    }
                }
                callback.onStreamDelta(textChunk);
            }

            @Override
            public void onSuccess(LlmApiService.LlmResponse response) {
                trackTokenUsage(response.getUsage());
                handleLlmResponse(response, conversationHistory, callback);
            }

            @Override
            public void onError(String error) {
                detachActiveRun();
                callback.onError(error);
            }
        };
    }

    /**
     * Update the "Last Usage" token tracking fields from an API response usage
     * block (no-op when the provider did not report usage).
     */
    private void trackTokenUsage(io.finett.droidclaw.api.TokenUsage usage) {
        if (usage == null || !usage.isAvailable()) return;

        currentContextTokens = usage.getTotalTokens();
        currentPromptTokens = usage.getPromptTokens();
        currentCompletionTokens = usage.getCompletionTokens();

        totalTokens += usage.getTotalTokens();
        totalPromptTokens += usage.getPromptTokens();
        totalCompletionTokens += usage.getCompletionTokens();

        Log.d(TAG, "Token usage - Current context: " + currentContextTokens +
              ", Session total: " + totalTokens);
    }

    private void handleLlmResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (cancelled.get()) {
            return;
        }
        if (response.hasToolCalls()) {
            handleToolCalls(response, conversationHistory, callback);
        } else {
            handleFinalResponse(response, conversationHistory, callback);
        }
    }

    private void handleToolCalls(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        List<LlmApiService.ToolCall> toolCalls = response.getToolCalls();

        Log.d(TAG, "LLM requested " + toolCalls.size() + " tool call(s)");

        totalToolCalls += toolCalls.size();

        processToolCallsWithApproval(toolCalls, 0, conversationHistory, callback);
    }

    /**
     * Copy of a tool call whose argument JSON is truncated to the display bound, so
     * persisted history never stores more than the UI can show.
     */
    private static LlmApiService.ToolCall truncateArgsForHistory(LlmApiService.ToolCall toolCall) {
        String args = toolCall.getArguments().toString();
        if (args.length() <= TOOL_ARGS_HISTORY_LIMIT) {
            return toolCall;
        }
        try {
            JsonObject truncated = JsonParser.parseString(args.substring(0, TOOL_ARGS_HISTORY_LIMIT - 3) + "...")
                    .getAsJsonObject();
            return new LlmApiService.ToolCall(toolCall.getId(), toolCall.getName(), truncated);
        } catch (Exception e) {
            // Truncated text is not valid JSON; keep the original rather than lose the call.
            return toolCall;
        }
    }

    /** Whether the calling thread is the Android main thread. Returns false when no
     * main looper is available (plain JVM unit tests), so those paths keep the
     * legacy inline behaviour.
     */
    private static boolean isOnMainThread() {
        try {
            Looper main = Looper.getMainLooper();
            return main != null && Looper.myLooper() == main;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether the given tool call is process execution that blocks (waitFor + stream
     * pumping). Shell commands and workflow runs must never run on the main thread;
     * other tools keep the legacy inline behaviour.
     */
    private boolean isBlockingProcessTool(LlmApiService.ToolCall toolCall, Tool tool) {
        if ("run_workflow".equals(toolCall.getName())) {
            return true;
        }
        if (tool instanceof ShellTool) {
            return true;
        }
        return "execute_shell".equals(toolCall.getName());
    }

    /**
     * Process tool calls sequentially, requesting approval when needed.
     */
    private void processToolCallsWithApproval(List<LlmApiService.ToolCall> toolCalls, int index,
                                               List<ChatMessage> conversationHistory, AgentCallback callback) {
        if (cancelled.get()) {
            return;
        }
        if (index >= toolCalls.size()) {
            // All tool calls processed, continue the loop
            executeIteration(conversationHistory, callback);
            return;
        }

        LlmApiService.ToolCall toolCall = toolCalls.get(index);
        String toolName = toolCall.getName();
        JsonObject arguments = toolCall.getArguments();

        Log.d(TAG, "Processing tool: " + toolName + " with args: " + arguments.toString());

        // Canonical history entry for this executed call: one card per tool so the
        // transcript shows exactly what ran. Args are truncated before persistence,
        // same bound the adapter applies when rendering.
        conversationHistory.add(ChatMessage.createToolCallMessage(
                Collections.singletonList(truncateArgsForHistory(toolCall))));

        callback.onToolCall(toolName, arguments.toString());

        boolean wantsBackground = arguments.has("background")
                && !arguments.get("background").isJsonNull()
                && arguments.get("background").getAsBoolean();
        boolean bgExecEnabled = settingsManager != null
                && settingsManager.getAgentConfig().isBackgroundExecEnabled();

        // Resolve scoped approval before considering background execution. A workflow's
        // DENY_WRITES/STRICT policy must not be bypassable with {"background": true}.
        Tool tool = toolRegistry.getTool(toolName);

        // Only shell commands drive the judge + audit log. Gate here so workflow review
        // and other tools never call getContext() (keeps the context-less review path clean).
        if (tool instanceof ShellTool) {
            ensureJudgeAndAudit();
        }

        ToolApprovalMode mode = getPerToolApprovalMode(toolName);

        // ALWAYS_REJECT: block immediately, no prompt or background dispatch.
        if (mode == ToolApprovalMode.ALWAYS_REJECT) {
            String resultContent = "Tool execution blocked by per-tool setting";
            Log.d(TAG, "Tool " + toolName + " blocked (ALWAYS_REJECT)");
            callback.onToolResult(toolName, resultContent);

            ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                    toolCall.getId(),
                    toolName,
                    resultContent
            );
            conversationHistory.add(toolResultMessage);

            // Process next tool call
            processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
            return;
        }

        if (wantsBackground && bgExecEnabled && !"run_workflow".equals(toolName)) {
            JsonObject cleanedArgs = arguments.deepCopy();
            cleanedArgs.remove("background");
            dispatchBackgroundTool(toolCall, cleanedArgs, toolCalls, index, conversationHistory, callback);
            return;
        }

        // Determine whether we need user approval
        boolean needsApproval = tool != null && ("run_workflow".equals(toolName)
                || (mode != ToolApprovalMode.ALWAYS_APPROVE && requireApproval && tool.requiresApproval()));

        // Auto-mode judge: for shell commands, decide before any dialog so a benign command
        // can run silently, a dangerous one be refused with a reason, and the rest fall back
        // to the dialog. The judge sees only the command + context, never chat or web content.
        // The judge blocks on a network round-trip (up to 15s), so on the main thread it is
        // dispatched to the background executor and the verdict is delivered back on main.
        boolean needsJudge = judge != null && "auto".equals(currentShellApprovalMode())
                && tool instanceof ShellTool && !("run_workflow".equals(toolName));
        if (needsJudge && isOnMainThread()) {
            String judgeCommand = commandArgument(arguments);
            String judgeCwd = cwdArgument(arguments);
            String judgeBackend = currentBackendLabel();
            String judgeRoot = workspaceRootPath();
            Handler judgeMainHandler = new Handler(Looper.getMainLooper());
            try {
                workflowExecutor.execute(() -> {
                    CommandJudge.VerdictResult verdict;
                    try {
                        verdict = judge.evaluate(judgeCommand, judgeCwd, judgeBackend, judgeRoot);
                    } catch (RuntimeException e) {
                        verdict = new CommandJudge.VerdictResult(CommandJudge.Verdict.ASK,
                                "judge unavailable: " + e.getMessage());
                    }
                    final CommandJudge.VerdictResult delivered = verdict;
                    judgeMainHandler.post(() -> {
                        if (!cancelled.get()) {
                            continueAfterJudge(toolCall, toolCalls, index, conversationHistory, callback,
                                    tool, mode, needsApproval, delivered);
                        }
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                continueAfterJudge(toolCall, toolCalls, index, conversationHistory, callback,
                        tool, mode, needsApproval,
                        new CommandJudge.VerdictResult(CommandJudge.Verdict.ASK,
                                "judge unavailable: " + e.getMessage()));
            }
            return;
        }
        CommandJudge.VerdictResult judgeResult = null;
        if (needsJudge) {
            judgeResult = judge.evaluate(
                    commandArgument(arguments), cwdArgument(arguments),
                    currentBackendLabel(), workspaceRootPath());
        }
        continueAfterJudge(toolCall, toolCalls, index, conversationHistory, callback,
                tool, mode, needsApproval, judgeResult);
    }

    /**
     * Continue tool-call processing after the auto-mode judge has decided (or when no
     * judge was needed). Runs on the main thread when the judge was dispatched off it,
     * otherwise on the caller's thread. Approval-plan building and process execution
     * are dispatched off the main thread from here.
     */
    private void continueAfterJudge(LlmApiService.ToolCall toolCall,
                                    List<LlmApiService.ToolCall> toolCalls, int index,
                                    List<ChatMessage> conversationHistory, AgentCallback callback,
                                    Tool tool, ToolApprovalMode mode, boolean needsApproval,
                                    CommandJudge.VerdictResult judgeResult) {
        if (cancelled.get()) {
            return;
        }
        String toolName = toolCall.getName();
        JsonObject arguments = toolCall.getArguments();

        if (judgeResult != null && judgeResult.verdict == CommandJudge.Verdict.DENY) {
            String command = commandArgument(arguments);
            String refused = "Tool execution denied by command judge: " + judgeResult.reason;
            Log.d(TAG, "Shell command refused by judge: " + refused);
            auditShell(toolName, command, "deny", null);
            callback.onToolResult(toolName, refused);
            conversationHistory.add(ChatMessage.createToolResultMessage(
                    toolCall.getId(), toolName, refused));
            processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
            return;
        }

        // In auto mode, a silent "allow" verdict removes the need for a dialog; ask and the
        // legacy paths keep the prompt. full_access runs silently (requiresApproval() == false).
        if (judgeResult != null && judgeResult.verdict == CommandJudge.Verdict.ALLOW) {
            needsApproval = false;
        }

        // Record the disposition to audit if/when the command actually runs, keyed by the
        // tool call id so a later command can never pick up a stale or foreign disposition.
        if (tool instanceof ShellTool && !("run_workflow".equals(toolName))) {
            String disposition;
            if (judgeResult != null) {
                disposition = (judgeResult.verdict == CommandJudge.Verdict.ALLOW)
                        ? "allow" : "dialog-approved";
            } else if ("full_access".equals(currentShellApprovalMode())) {
                disposition = "silent";
            } else {
                disposition = "dialog-approved";
            }
            pendingDisposition.put(toolCall.getId(), disposition);
        }

        if (needsApproval) {
            // Build a normalised ExecPlan for exec-type tools (shell, etc.).
            // The approval dialog shows the plan's description — the raw command verbatim,
            // its resolved cwd, and plan hash — NOT some other rendering of the LLM input.
            // This prevents prompt-injection where approval description differs from execution.
            // Plan building canonicalises paths and reads the workflow file, so on the main
            // thread it is dispatched to the background executor first.
            if (isOnMainThread()) {
                Handler planMainHandler = new Handler(Looper.getMainLooper());
                try {
                    workflowExecutor.execute(() -> {
                        String planDescription;
                        java.util.function.Supplier<ToolResult> planExecution;
                        try {
                            if (tool instanceof RunWorkflowTool) {
                                RunWorkflowTool.ApprovalReview review =
                                        ((RunWorkflowTool) tool).prepareApproval(arguments);
                                planDescription = review.getDescription();
                                planExecution = review::execute;
                            } else {
                                ExecPlan execPlan = tool.buildExecPlan(arguments);
                                planDescription = execPlan != null ? execPlan.toApprovalDescription()
                                        : tool.getApprovalDescription(arguments);
                                if (judgeResult != null
                                        && judgeResult.verdict == CommandJudge.Verdict.ASK
                                        && !judgeResult.reason.isEmpty()) {
                                    planDescription += "\n\n[Command judge: "
                                            + judgeResult.reason + "]";
                                }
                                planExecution = () -> toolRegistry.executeTool(toolName, arguments);
                            }
                        } catch (Exception e) {
                            String errorMessage = "Cannot safely review workflow: " + e.getMessage();
                            planMainHandler.post(() -> {
                                if (!cancelled.get()) {
                                    executeToolAndContinue(toolCall, toolCalls, index,
                                            conversationHistory, callback,
                                            () -> ToolResult.error(errorMessage));
                                }
                            });
                            return;
                        }
                        final String deliveredDescription = planDescription;
                        final java.util.function.Supplier<ToolResult> deliveredExecution = planExecution;
                        planMainHandler.post(() -> {
                            if (!cancelled.get()) {
                                requestApproval(toolCall, toolCalls, index, conversationHistory, callback,
                                        deliveredDescription, deliveredExecution);
                            }
                        });
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // Executor unavailable: fall back to inline plan building. Execution
                    // itself still dispatches off the main thread below.
                    String fallbackDescription;
                    java.util.function.Supplier<ToolResult> fallbackExecution;
                    try {
                        if (tool instanceof RunWorkflowTool) {
                            RunWorkflowTool.ApprovalReview review =
                                    ((RunWorkflowTool) tool).prepareApproval(arguments);
                            fallbackDescription = review.getDescription();
                            fallbackExecution = review::execute;
                        } else {
                            ExecPlan execPlan = tool.buildExecPlan(arguments);
                            fallbackDescription = execPlan != null ? execPlan.toApprovalDescription()
                                    : tool.getApprovalDescription(arguments);
                            if (judgeResult != null
                                    && judgeResult.verdict == CommandJudge.Verdict.ASK
                                    && !judgeResult.reason.isEmpty()) {
                                fallbackDescription += "\n\n[Command judge: "
                                        + judgeResult.reason + "]";
                            }
                            fallbackExecution = () -> toolRegistry.executeTool(toolName, arguments);
                        }
                    } catch (Exception ex) {
                        executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback,
                                () -> ToolResult.error("Cannot safely review workflow: "
                                        + ex.getMessage()));
                        return;
                    }
                    requestApproval(toolCall, toolCalls, index, conversationHistory, callback,
                            fallbackDescription, fallbackExecution);
                }
                return;
            }
            final java.util.function.Supplier<ToolResult> approvedExecution;
            String description;
            if (tool instanceof RunWorkflowTool) {
                try {
                    RunWorkflowTool.ApprovalReview review = ((RunWorkflowTool) tool).prepareApproval(arguments);
                    description = review.getDescription();
                    approvedExecution = review::execute;
                } catch (Exception e) {
                    executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback,
                            () -> ToolResult.error("Cannot safely review workflow: " + e.getMessage()));
                    return;
                }
            } else {
                ExecPlan execPlan = tool.buildExecPlan(arguments);
                description = execPlan != null ? execPlan.toApprovalDescription()
                        : tool.getApprovalDescription(arguments);
                if (judgeResult != null && judgeResult.verdict == CommandJudge.Verdict.ASK
                        && !judgeResult.reason.isEmpty()) {
                    description += "\n\n[Command judge: " + judgeResult.reason + "]";
                }
                approvedExecution = () -> toolRegistry.executeTool(toolName, arguments);
            }
            requestApproval(toolCall, toolCalls, index, conversationHistory, callback,
                    description, approvedExecution);
        } else {
            // Execute without approval
            executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback);
        }
    }

    /**
     * Ask the user to approve a tool execution. The decision arrives later via the
     * {@link ApprovalCallback}; approved execution is dispatched off the main thread
     * by {@link #executeToolAndContinue}.
     */
    private void requestApproval(LlmApiService.ToolCall toolCall,
                                 List<LlmApiService.ToolCall> toolCalls, int index,
                                 List<ChatMessage> conversationHistory, AgentCallback callback,
                                 String description,
                                 java.util.function.Supplier<ToolResult> approvedExecution) {
        String toolName = toolCall.getName();
        JsonObject arguments = toolCall.getArguments();
        AtomicBoolean decided = new AtomicBoolean();
        callback.onApprovalRequired(toolName, description, arguments, new ApprovalCallback() {
            @Override
            public void onApproved() {
                if (cancelled.get() || !decided.compareAndSet(false, true)) {
                    return;
                }
                executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback,
                        approvedExecution);
            }

            @Override
            public void onDenied() {
                if (cancelled.get() || !decided.compareAndSet(false, true)) {
                    return;
                }
                // Add denial result to history and continue
                String resultContent = "Tool execution denied by user";
                Log.d(TAG, "Tool " + toolName + " denied by user");
                pendingDisposition.remove(toolCall.getId());
                auditShell(toolName, commandArgument(arguments), "dialog-denied", null);
                callback.onToolResult(toolName, resultContent);

                ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                    toolCall.getId(),
                    toolName,
                    resultContent
                );
                conversationHistory.add(toolResultMessage);

                // Process next tool call
                processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
            }
        });
    }

    /**
     * Dispatch a tool call to run asynchronously, bypassing the approval flow.
     * The user has already opted in via the background_exec agent setting, so re-prompting
     * for approval would defeat the purpose of fire-and-forget execution.
     */
    private void dispatchBackgroundTool(LlmApiService.ToolCall toolCall, JsonObject cleanedArgs,
                                        List<LlmApiService.ToolCall> toolCalls, int index,
                                        List<ChatMessage> conversationHistory, AgentCallback callback) {
        String toolName = toolCall.getName();

        android.content.Context context = null;
        try {
            context = toolRegistry.getContext();
        } catch (Exception e) {
            Log.w(TAG, "Could not get context for background dispatch, falling back to sync execution");
            executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback);
            return;
        }

        final android.content.Context finalContext = context;

        // Resolve the audit disposition the same way the sync path does (no judge runs for a
        // background dispatch). Stored by tool call id so the completion handler audits it.
        if (toolRegistry.getTool(toolCall.getName()) instanceof ShellTool) {
            String disposition;
            if ("full_access".equals(currentShellApprovalMode())) {
                disposition = "silent";
            } else {
                disposition = "dialog-approved";
            }
            pendingDisposition.put(toolCall.getId(), disposition);
        }

        final java.util.concurrent.Callable<ToolResult> backgroundTask =
                () -> toolRegistry.executeTool(toolName, cleanedArgs);

        String processId = BackgroundProcessManager.getInstance(finalContext).submit(
                toolName,
                cleanedArgs.toString(),
                auditOnCompletion(toolCall, cleanedArgs, backgroundTask)
        );

        Log.d(TAG, "Tool " + toolName + " dispatched to background: process_id=" + processId);
        callback.onToolCall(toolName, cleanedArgs.toString());

        JsonObject bgResult = new JsonObject();
        bgResult.addProperty("process_id", processId);
        bgResult.addProperty("status", "running");
        bgResult.addProperty("message",
                "Tool '" + toolName + "' is running in background. "
                + "Use list_background_processes to check status or kill_background_process to cancel.");

        String resultContent = bgResult.toString();
        callback.onToolResult(toolName, resultContent);

        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
                toolCall.getId(),
                toolName,
                resultContent
        );
        conversationHistory.add(toolResultMessage);

        processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
    }

    private void executeToolAndContinue(LlmApiService.ToolCall toolCall, List<LlmApiService.ToolCall> toolCalls,
                                        int index, List<ChatMessage> conversationHistory, AgentCallback callback) {
        executeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback,
                () -> toolRegistry.executeTool(toolCall.getName(), toolCall.getArguments()));
    }

    private void executeToolAndContinue(LlmApiService.ToolCall toolCall, List<LlmApiService.ToolCall> toolCalls,
                                        int index, List<ChatMessage> conversationHistory, AgentCallback callback,
                                        Supplier<ToolResult> execution) {
        if (cancelled.get()) {
            return;
        }
        // Process execution (shell waitFor + stream pumping + output limiting + timeout,
        // and workflow runs) blocks for the full command duration. It must never run on
        // the main thread: LLM callbacks and approval-dialog decisions arrive on main, so
        // dispatch to the background executor and deliver the result back on main. When
        // already off main (worker threads), run inline to avoid an extra hop.
        Tool tool = null;
        try {
            tool = toolRegistry.getTool(toolCall.getName());
        } catch (Exception ignored) {
            tool = null;
        }
        if (isBlockingProcessTool(toolCall, tool) && isOnMainThread()) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            java.util.function.Consumer<ToolResult> deliver = result -> mainHandler.post(() -> {
                if (!cancelled.get()) {
                    completeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback, result);
                }
            });
            try {
                workflowExecutor.execute(() -> {
                    if (cancelled.get()) {
                        return;
                    }
                    ToolResult result;
                    try {
                        result = execution.get();
                    } catch (RuntimeException e) {
                        result = ToolResult.error("Tool execution failed: " + e.getMessage());
                    }
                    deliver.accept(result);
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                String unavailable = "run_workflow".equals(toolCall.getName())
                        ? "Workflow execution unavailable: "
                        : "Tool execution unavailable: ";
                deliver.accept(ToolResult.error(unavailable + e.getMessage()));
            }
            return;
        }
        completeToolAndContinue(toolCall, toolCalls, index, conversationHistory, callback, execution.get());
    }

    private void completeToolAndContinue(LlmApiService.ToolCall toolCall, List<LlmApiService.ToolCall> toolCalls,
                                        int index, List<ChatMessage> conversationHistory, AgentCallback callback,
                                        ToolResult result) {
        String toolName = toolCall.getName();

        // Record the executed shell command (disposition resolved before execution) in the
        // audit log, in every mode and regardless of outcome, including commands that fail
        // to start or time out.
        if (result != null) {
            String disposition = pendingDisposition.remove(toolCall.getId());
            // Keep the resolved disposition only for a clean run; a failed command or a
            // missing disposition is recorded as "error" so it is never mislabelled with
            // a disposition that belongs to another command.
            if (!result.isSuccess() || disposition == null) {
                disposition = "error";
            }
            auditShell(toolName, commandArgument(toolCall.getArguments()),
                    disposition, exitCodeFromResult(result.getContent()));
        }

        String resultContent;
        if (result.isSuccess()) {
            resultContent = result.getContent();
            Log.d(TAG, "Tool " + toolName + " succeeded: " + resultContent);
        } else {
            resultContent = "Error: " + result.getError();
            Log.e(TAG, "Tool " + toolName + " failed: " + result.getError());
        }

        callback.onToolResult(toolName, resultContent);

        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
            toolCall.getId(),
            toolName,
            resultContent
        );
        conversationHistory.add(toolResultMessage);

        processToolCallsWithApproval(toolCalls, index + 1, conversationHistory, callback);
    }

    private void handleFinalResponse(LlmApiService.LlmResponse response, List<ChatMessage> conversationHistory, AgentCallback callback) {
        detachActiveRun();
        String content = response.getContent();

        if (content == null || content.isEmpty()) {
            content = "No response from assistant.";
        }

        Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s)");

        ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(assistantMessage);

        detectAndAddFileReferences(content, conversationHistory);

        callback.onComplete(content, conversationHistory);
    }

    private void detectAndAddFileReferences(String content, List<ChatMessage> conversationHistory) {
        if (content == null) return;

        Matcher matcher = FILE_PATH_PATTERN.matcher(content);
        while (matcher.find()) {
            String rawPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (rawPath == null || rawPath.isEmpty()) continue;

            // Skip if it looks like a code example (has common code markers)
            if (rawPath.contains("```") || rawPath.contains("\n")) continue;

            String displayName = rawPath;
            String filePath = rawPath;
            String mimeType = null;

            // Resolve relative paths to absolute
            if (!rawPath.startsWith("/")) {
                // Try to find the file in workspace
                File workspaceFile = findFileInWorkspace(rawPath);
                if (workspaceFile != null && workspaceFile.exists()) {
                    filePath = workspaceFile.getAbsolutePath();
                    displayName = workspaceFile.getName();
                    mimeType = io.finett.droidclaw.filesystem.FileUploadManager.resolveMimeType(displayName);
                }
            } else if (rawPath.startsWith("/data/data/")) {
                // Absolute workspace path
                File file = new File(rawPath);
                if (file.exists()) {
                    displayName = file.getName();
                    mimeType = io.finett.droidclaw.filesystem.FileUploadManager.resolveMimeType(displayName);
                }
            }

            // Only create attachment message if file exists
            File file = new File(filePath);
            if (file.exists()) {
                ChatMessage attachmentMsg = ChatMessage.createAttachmentMessage(
                    filePath, displayName, mimeType);
                conversationHistory.add(attachmentMsg);
                Log.d(TAG, "Detected file reference: " + displayName);
            }
        }
    }

    private File findFileInWorkspace(String relativePath) {
        String[] searchDirs = {
            "uploads",
            "home",
            "home/documents",
            "home/scripts",
            "home/notes",
            "tmp"
        };

        File workspaceRoot = getWorkspaceRoot();
        if (workspaceRoot != null) {
            File directFile = new File(workspaceRoot, relativePath);
            if (directFile.exists()) {
                return directFile;
            }

            // Search in standard directories
            for (String dir : searchDirs) {
                File file = new File(workspaceRoot, dir + "/" + relativePath);
                if (file.exists()) {
                    return file;
                }
            }
        }

        return null;
    }

    private File getWorkspaceRoot() {
        try {
            return toolRegistry.getWorkspaceRoot();
        } catch (Exception e) {
            return null;
        }
    }

    private void handleStructuredLlmResponse(LlmApiService.StructuredResponse response,
                                              List<ChatMessage> conversationHistory,
                                              AgentCallback callback) {
        if (cancelled.get()) {
            return;
        }
        if (response.hasToolCalls()) {
            LlmApiService.LlmResponse llmResponse = new LlmApiService.LlmResponse(
                    response.getContent(), response.getToolCalls(), response.getUsage());
            handleToolCalls(llmResponse, conversationHistory, callback);
        } else {
            detachActiveRun();
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                content = "No response from assistant.";
            }

            Log.d(TAG, "Agent loop completed in " + iterationCount + " iteration(s) (structured output)");

            ChatMessage assistantMessage = new ChatMessage(content, ChatMessage.TYPE_ASSISTANT);
            conversationHistory.add(assistantMessage);

            detectAndAddFileReferences(content, conversationHistory);

            callback.onComplete(content, conversationHistory);
        }
    }

    private void handleRefusal(LlmApiService.StructuredResponse response,
                               List<ChatMessage> conversationHistory,
                               AgentCallback callback) {
        detachActiveRun();
        String refusalMessage = response.getRefusal() != null ? response.getRefusal() : "Model refused to respond";

        Log.w(TAG, "Handling refusal: " + refusalMessage);

        ChatMessage refusalMsg = new ChatMessage("Refusal: " + refusalMessage, ChatMessage.TYPE_ASSISTANT);
        conversationHistory.add(refusalMsg);

        callback.onComplete("[REFUSAL] " + refusalMessage, conversationHistory);
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void reset() {
        iterationCount = 0;
        resetCurrentContext();
        totalTokens = 0;
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
        totalToolCalls = 0;
    }

    public int getCurrentContextTokens() {
        return currentContextTokens;
    }

    public int getCurrentPromptTokens() {
        return currentPromptTokens;
    }

    public int getCurrentCompletionTokens() {
        return currentCompletionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public int getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public int getTotalToolCalls() {
        return totalToolCalls;
    }

    public void resetCurrentContext() {
        currentContextTokens = 0;
        currentPromptTokens = 0;
        currentCompletionTokens = 0;
        Log.d(TAG, "Current context tokens reset (session totals preserved)");
    }

}
