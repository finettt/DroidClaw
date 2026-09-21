package io.finett.droidclaw.workflow;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One node in a workflow.
 *
 * <p>Nullable fields mean "not set on this node; fall back to {@code defaults},
 * then to the global {@code AgentConfig}, then to a built-in constant". This
 * three-level precedence is why boxed types are used instead of primitives.
 *
 * <p>{@link #getAllowedTools()} is the one field where {@code null} and an empty
 * list differ sharply: {@code null} means "every registered tool", while an
 * empty list means "no tools at all" — a pure LLM call. See spec §9.
 */
public final class WorkflowAgent {

    private final String key;
    private final String description;
    private final String model;
    private final WorkflowPrompt prompt;
    private final List<String> allowedTools;   // null = unset
    private final List<String> deniedTools;    // null = unset
    private final Integer maxTurns;            // null = unset
    private final Integer timeoutMs;           // null = unset
    private final WorkflowRetry retry;         // null = unset
    private final WorkflowErrorPolicy onError; // null = unset
    private final WorkflowApprovalPolicy approval; // null = unset
    private final JsonObject outputSchema;     // null = none
    private final List<String> dependsOn;
    private final Map<String, String> inputs;
    private final String when;                 // null = always run

    private WorkflowAgent(Builder b) {
        this.key = b.key;
        this.description = b.description;
        this.model = b.model;
        this.prompt = b.prompt;
        this.allowedTools = b.allowedTools == null ? null : Collections.unmodifiableList(new ArrayList<>(b.allowedTools));
        this.deniedTools = b.deniedTools == null ? null : Collections.unmodifiableList(new ArrayList<>(b.deniedTools));
        this.maxTurns = b.maxTurns;
        this.timeoutMs = b.timeoutMs;
        this.retry = b.retry;
        this.onError = b.onError;
        this.approval = b.approval;
        this.outputSchema = b.outputSchema;
        this.dependsOn = Collections.unmodifiableList(new ArrayList<>(b.dependsOn));
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(b.inputs));
        this.when = b.when;
    }

    public String getKey() { return key; }
    public String getDescription() { return description; }
    public String getModel() { return model; }
    public WorkflowPrompt getPrompt() { return prompt; }

    /** {@code null} when unset (inherit all tools); empty means "no tools". */
    public List<String> getAllowedTools() { return allowedTools; }
    public List<String> getDeniedTools() { return deniedTools; }
    public Integer getMaxTurns() { return maxTurns; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public WorkflowRetry getRetry() { return retry; }
    public WorkflowErrorPolicy getOnError() { return onError; }
    public WorkflowApprovalPolicy getApproval() { return approval; }
    public JsonObject getOutputSchema() { return outputSchema; }
    public List<String> getDependsOn() { return dependsOn; }

    /** Named data edges: local name -> template expression. Also imply dependencies. */
    public Map<String, String> getInputs() { return inputs; }

    public String getWhen() { return when; }
    public boolean hasGuard() { return when != null && !when.trim().isEmpty(); }
    public boolean hasOutputSchema() { return outputSchema != null; }

    /** The provider part of a {@code providerId/modelId} reference, or {@code null}. */
    public String getProviderId() {
        if (model == null) return null;
        int i = model.indexOf('/');
        return i < 0 ? null : model.substring(0, i);
    }

    /** The model part of a {@code providerId/modelId} reference, or {@code null}. */
    public String getModelId() {
        if (model == null) return null;
        int i = model.indexOf('/');
        return i < 0 ? null : model.substring(i + 1);
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final String key;
        private String description;
        private String model;
        private WorkflowPrompt prompt;
        private List<String> allowedTools;
        private List<String> deniedTools;
        private Integer maxTurns;
        private Integer timeoutMs;
        private WorkflowRetry retry;
        private WorkflowErrorPolicy onError;
        private WorkflowApprovalPolicy approval;
        private JsonObject outputSchema;
        private final List<String> dependsOn = new ArrayList<>();
        private final Map<String, String> inputs = new LinkedHashMap<>();
        private String when;

        private Builder(String key) { this.key = key; }

        public Builder description(String v) { this.description = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder prompt(WorkflowPrompt v) { this.prompt = v; return this; }
        public Builder allowedTools(List<String> v) { this.allowedTools = v; return this; }
        public Builder deniedTools(List<String> v) { this.deniedTools = v; return this; }
        public Builder maxTurns(Integer v) { this.maxTurns = v; return this; }
        public Builder timeoutMs(Integer v) { this.timeoutMs = v; return this; }
        public Builder retry(WorkflowRetry v) { this.retry = v; return this; }
        public Builder onError(WorkflowErrorPolicy v) { this.onError = v; return this; }
        public Builder approval(WorkflowApprovalPolicy v) { this.approval = v; return this; }
        public Builder outputSchema(JsonObject v) { this.outputSchema = v; return this; }
        public Builder dependsOn(List<String> v) { this.dependsOn.clear(); if (v != null) this.dependsOn.addAll(v); return this; }
        public Builder inputs(Map<String, String> v) { this.inputs.clear(); if (v != null) this.inputs.putAll(v); return this; }
        public Builder when(String v) { this.when = v; return this; }

        public WorkflowAgent build() { return new WorkflowAgent(this); }
    }
}
