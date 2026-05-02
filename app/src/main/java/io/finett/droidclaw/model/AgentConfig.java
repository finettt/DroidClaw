package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.List;

public class AgentConfig {
    private String defaultModel; // Format: "provider-id/model-id"
    private boolean shellAccess;
    private String sandboxMode; // "strict", "relaxed", or "full"
    private int maxIterations;
    private boolean requireApproval;
    private int shellTimeout;
    private boolean backgroundShellEnabled; // allow shell/python in background workers
    private boolean backgroundExecEnabled; // allow agent to dispatch any tool with background=true
    private List<String> customAllowlist; // extra executable paths for relaxed mode

    public AgentConfig() {
        this.customAllowlist = new ArrayList<>();
    }

    public AgentConfig(String defaultModel, boolean shellAccess, String sandboxMode,
                       int maxIterations, boolean requireApproval, int shellTimeout,
                       boolean backgroundShellEnabled, List<String> customAllowlist) {
        this.defaultModel = defaultModel;
        this.shellAccess = shellAccess;
        this.sandboxMode = sandboxMode;
        this.maxIterations = maxIterations;
        this.requireApproval = requireApproval;
        this.shellTimeout = shellTimeout;
        this.backgroundShellEnabled = backgroundShellEnabled;
        this.backgroundExecEnabled = false;
        this.customAllowlist = customAllowlist != null ? new ArrayList<>(customAllowlist) : new ArrayList<>();
    }

    public static AgentConfig getDefaults() {
        return new AgentConfig(
                "",
                false,
                "strict",
                20,
                true,
                30,
                false,
                new ArrayList<>()
        );
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public boolean isShellAccess() {
        return shellAccess;
    }

    public void setShellAccess(boolean shellAccess) {
        this.shellAccess = shellAccess;
    }

    public String getSandboxMode() {
        return sandboxMode;
    }

    public void setSandboxMode(String sandboxMode) {
        this.sandboxMode = sandboxMode;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    public int getShellTimeout() {
        return shellTimeout;
    }

    public void setShellTimeout(int shellTimeout) {
        this.shellTimeout = shellTimeout;
    }

    public boolean isBackgroundShellEnabled() {
        return backgroundShellEnabled;
    }

    public void setBackgroundShellEnabled(boolean backgroundShellEnabled) {
        this.backgroundShellEnabled = backgroundShellEnabled;
    }

    public boolean isBackgroundExecEnabled() {
        return backgroundExecEnabled;
    }

    public void setBackgroundExecEnabled(boolean backgroundExecEnabled) {
        this.backgroundExecEnabled = backgroundExecEnabled;
    }

    public List<String> getCustomAllowlist() {
        return customAllowlist;
    }

    public void setCustomAllowlist(List<String> customAllowlist) {
        this.customAllowlist = customAllowlist != null ? new ArrayList<>(customAllowlist) : new ArrayList<>();
    }

    public String getDefaultProviderId() {
        if (defaultModel == null || !defaultModel.contains("/")) {
            return null;
        }
        return defaultModel.split("/")[0];
    }

    public String getDefaultModelId() {
        if (defaultModel == null || !defaultModel.contains("/")) {
            return null;
        }
        String[] parts = defaultModel.split("/", 2);
        return parts.length > 1 ? parts[1] : null;
    }

}