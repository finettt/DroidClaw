package io.finett.droidclaw.model;

public class AgentConfig {
    private String defaultModel; // Format: "provider-id/model-id"
    private boolean shellAccess;
    private String sandboxMode; // "strict" or "relaxed"
    private int maxIterations;
    private boolean requireApproval;
    private int shellTimeout;

    // Bluetooth/Agent connectivity settings
    private boolean agentAccessibilityEnabled; // Can other agents connect to this agent?
    private boolean agentAutoConnect; // Can this agent initiate connections to nearby agents?
    private boolean agentDiscoverable; // Is this agent visible during Bluetooth discovery?

    public AgentConfig() {
        // Default constructor for JSON deserialization
    }

    public AgentConfig(String defaultModel, boolean shellAccess, String sandboxMode,
                       int maxIterations, boolean requireApproval, int shellTimeout) {
        this(defaultModel, shellAccess, sandboxMode, maxIterations, requireApproval, shellTimeout,
                true, // agentAccessibilityEnabled by default
                true, // agentAutoConnect by default
                false // agentDiscoverable by default (user must enable)
        );
    }

    public AgentConfig(String defaultModel, boolean shellAccess, String sandboxMode,
                       int maxIterations, boolean requireApproval, int shellTimeout,
                       boolean agentAccessibilityEnabled, boolean agentAutoConnect, boolean agentDiscoverable) {
        this.defaultModel = defaultModel;
        this.shellAccess = shellAccess;
        this.sandboxMode = sandboxMode;
        this.maxIterations = maxIterations;
        this.requireApproval = requireApproval;
        this.shellTimeout = shellTimeout;
        this.agentAccessibilityEnabled = agentAccessibilityEnabled;
        this.agentAutoConnect = agentAutoConnect;
        this.agentDiscoverable = agentDiscoverable;
    }

    public static AgentConfig getDefaults() {
        return new AgentConfig(
                "", // No default model initially
                false, // Shell access disabled
                "strict", // Strict sandbox mode
                20, // Max 20 iterations
                true, // Require approval
                30, // 30 seconds timeout
                true, // agentAccessibilityEnabled by default
                true, // agentAutoConnect by default
                false // agentDiscoverable by default (user must enable)
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

    // ==================== Agent Accessibility Settings ====================

    public boolean isAgentAccessibilityEnabled() {
        return agentAccessibilityEnabled;
    }

    public void setAgentAccessibilityEnabled(boolean agentAccessibilityEnabled) {
        this.agentAccessibilityEnabled = agentAccessibilityEnabled;
    }

    public boolean isAgentAutoConnect() {
        return agentAutoConnect;
    }

    public void setAgentAutoConnect(boolean agentAutoConnect) {
        this.agentAutoConnect = agentAutoConnect;
    }

    public boolean isAgentDiscoverable() {
        return agentDiscoverable;
    }

    public void setAgentDiscoverable(boolean agentDiscoverable) {
        this.agentDiscoverable = agentDiscoverable;
    }

    // Helper methods to parse provider and model from defaultModel string
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

    public void setDefaultModelFromIds(String providerId, String modelId) {
        if (providerId != null && modelId != null) {
            this.defaultModel = providerId + "/" + modelId;
        } else {
            this.defaultModel = "";
        }
    }
}