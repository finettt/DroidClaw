package io.finett.droidclaw.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Node-level defaults applied when a node omits the field, plus two runner-level
 * settings that are not per-node.
 *
 * <p>Precedence, highest first: node field, defaults field, global
 * {@code AgentConfig}, built-in constant.
 *
 * <p>{@code allowedTools} here is <em>replaced</em>, never merged, by a node's own
 * list — merging lists is a frequent source of surprise.
 */
public final class WorkflowDefaults {

    public static final int DEFAULT_MAX_PARALLEL = 2;
    public static final int DEFAULT_MAX_NODES = 16;
    public static final int MIN_MAX_PARALLEL = 1;
    public static final int MAX_MAX_PARALLEL = 8;
    public static final int MIN_MAX_NODES = 1;
    public static final int MAX_MAX_NODES = 64;

    private String model;
    private Integer maxTurns;
    private Integer timeoutMs;
    private List<String> allowedTools;
    private List<String> deniedTools;
    private WorkflowApprovalPolicy approval;
    private WorkflowErrorPolicy onError;
    private WorkflowRetry retry;
    private int maxParallel = DEFAULT_MAX_PARALLEL;
    private int maxNodes = DEFAULT_MAX_NODES;

    public WorkflowDefaults() {}

    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public Integer getMaxTurns() { return maxTurns; }
    public void setMaxTurns(Integer v) { this.maxTurns = v; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer v) { this.timeoutMs = v; }
    public List<String> getAllowedTools() {
        return allowedTools == null ? null : Collections.unmodifiableList(allowedTools);
    }
    public void setAllowedTools(List<String> v) {
        this.allowedTools = v == null ? null : new ArrayList<>(v);
    }
    public List<String> getDeniedTools() {
        return deniedTools == null ? null : Collections.unmodifiableList(deniedTools);
    }
    public void setDeniedTools(List<String> v) {
        this.deniedTools = v == null ? null : new ArrayList<>(v);
    }
    public WorkflowApprovalPolicy getApproval() { return approval; }
    public void setApproval(WorkflowApprovalPolicy v) { this.approval = v; }
    public WorkflowErrorPolicy getOnError() { return onError; }
    public void setOnError(WorkflowErrorPolicy v) { this.onError = v; }
    public WorkflowRetry getRetry() { return retry; }
    public void setRetry(WorkflowRetry v) { this.retry = v; }
    public int getMaxParallel() { return maxParallel; }
    public void setMaxParallel(int v) { this.maxParallel = v; }
    public int getMaxNodes() { return maxNodes; }
    public void setMaxNodes(int v) { this.maxNodes = v; }

    public WorkflowApprovalPolicy resolveApproval(WorkflowApprovalPolicy node) {
        if (node != null) return node;
        if (approval != null) return approval;
        return WorkflowApprovalPolicy.defaultValue();
    }

    public WorkflowErrorPolicy resolveOnError(WorkflowErrorPolicy node) {
        if (node != null) return node;
        if (onError != null) return onError;
        return WorkflowErrorPolicy.defaultValue();
    }

    /** Effective model reference for a node; may still be {@code null} (use global default). */
    public String resolveModel(String node) {
        return node != null ? node : model;
    }

    public Integer resolveMaxTurns(Integer node) {
        return node != null ? node : maxTurns;
    }

    public Integer resolveTimeoutMs(Integer node) {
        return node != null ? node : timeoutMs;
    }

    public WorkflowRetry resolveRetry(WorkflowRetry node) {
        if (node != null) return node;
        return retry != null ? retry : WorkflowRetry.defaults();
    }

    public List<String> resolveAllowedTools(List<String> node) {
        if (node != null) return node;
        return allowedTools;
    }

    public List<String> resolveDeniedTools(List<String> node) {
        List<String> out = new ArrayList<>();
        if (deniedTools != null) out.addAll(deniedTools);
        if (node != null) {
            for (String t : node) if (!out.contains(t)) out.add(t);
        }
        return out;
    }
}
