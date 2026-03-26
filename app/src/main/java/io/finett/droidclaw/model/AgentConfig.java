package io.finett.droidclaw.model;

public class AgentConfig {
    private String defaultModel; // Format: "provider-id/model-id"
    private boolean shellAccess;
    private String sandboxMode; // "strict" or "relaxed"
    private int maxIterations;
    private boolean requireApproval;
    private int shellTimeout;

    public AgentConfig() {
        // Default constructor for JSON deserialization
    }

    public AgentConfig(String defaultModel, boolean shellAccess, String sandboxMode,
                       int maxIterations, boolean requireApproval, int shellTimeout) {
        this.defaultModel = defaultModel;
        this.shellAccess = shellAccess;
        this.sandboxMode = sandboxMode;
        this.maxIterations = maxIterations;
        this.requireApproval = requireApproval;
        this.shellTimeout = shellTimeout;
    }

    public static AgentConfig getDefaults() {
        return new AgentConfig(
                "", // No default model initially
                false, // Shell access disabled
                "strict", // Strict sandbox mode
                20, // Max 20 iterations
                true, // Require approval
                30 // 30 seconds timeout
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