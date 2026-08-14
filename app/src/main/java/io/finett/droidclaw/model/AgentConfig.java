package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean screenControlEnabled;  // allow agent to read UI and control screen via accessibility
    private boolean screenControlTrustMode; // skip per-action approval for screen control tools
    private boolean calendarEnabled; // allow agent to read and manage device calendar events
    private int llmConnectTimeout;   // seconds
    private int llmReadTimeout;      // seconds
    private int llmWriteTimeout;     // seconds
    private Map<String, String> toolApprovalOverrides = new HashMap<>(); // toolName -> ToolApprovalMode.name()
    private String shellBackend;     // "local" or "ssh"
    private String sshHost;
    private int sshPort;
    private String sshUser;
    private String sshAuthType;      // "password" or "key"
    private String sshPassword;
    private String sshPrivateKeyPath;
    private boolean sshVerifyHostKey;

    public AgentConfig() {
        this.customAllowlist = new ArrayList<>();
        this.llmConnectTimeout = 30;
        this.llmReadTimeout = 120;
        this.llmWriteTimeout = 30;
        this.screenControlEnabled = false;
        this.screenControlTrustMode = false;
        this.calendarEnabled = false;
        this.toolApprovalOverrides = new HashMap<>();
        this.shellBackend = "local";
        this.sshPort = 22;
        this.sshVerifyHostKey = true;
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
        this.llmConnectTimeout = 30;
        this.llmReadTimeout = 120;
        this.llmWriteTimeout = 30;
        this.screenControlEnabled = false;
        this.screenControlTrustMode = false;
        this.calendarEnabled = false;
        this.sshPort = 22;
        this.sshVerifyHostKey = true;
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

    public int getLlmConnectTimeout() {
        return llmConnectTimeout;
    }

    public void setLlmConnectTimeout(int llmConnectTimeout) {
        this.llmConnectTimeout = llmConnectTimeout;
    }

    public int getLlmReadTimeout() {
        return llmReadTimeout;
    }

    public void setLlmReadTimeout(int llmReadTimeout) {
        this.llmReadTimeout = llmReadTimeout;
    }

    public int getLlmWriteTimeout() {
        return llmWriteTimeout;
    }

    public void setLlmWriteTimeout(int llmWriteTimeout) {
        this.llmWriteTimeout = llmWriteTimeout;
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

    public boolean isScreenControlEnabled() {
        return screenControlEnabled;
    }

    public void setScreenControlEnabled(boolean screenControlEnabled) {
        this.screenControlEnabled = screenControlEnabled;
    }

    public boolean isScreenControlTrustMode() {
        return screenControlTrustMode;
    }

    public void setScreenControlTrustMode(boolean screenControlTrustMode) {
        this.screenControlTrustMode = screenControlTrustMode;
    }

    public boolean isCalendarEnabled() {
        return calendarEnabled;
    }

    public void setCalendarEnabled(boolean calendarEnabled) {
        this.calendarEnabled = calendarEnabled;
    }

    public Map<String, String> getToolApprovalOverrides() {
        return toolApprovalOverrides;
    }

    public void setToolApprovalOverrides(Map<String, String> toolApprovalOverrides) {
        this.toolApprovalOverrides = toolApprovalOverrides != null
                ? new HashMap<>(toolApprovalOverrides)
                : new HashMap<>();
    }

    public String getShellBackend() {
        return shellBackend;
    }

    public void setShellBackend(String shellBackend) {
        this.shellBackend = shellBackend != null ? shellBackend : "local";
    }

    public String getSshHost() {
        return sshHost;
    }

    public void setSshHost(String sshHost) {
        this.sshHost = sshHost;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getSshUser() {
        return sshUser;
    }

    public void setSshUser(String sshUser) {
        this.sshUser = sshUser;
    }

    public String getSshAuthType() {
        return sshAuthType;
    }

    public void setSshAuthType(String sshAuthType) {
        this.sshAuthType = sshAuthType;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public String getSshPrivateKeyPath() {
        return sshPrivateKeyPath;
    }

    public void setSshPrivateKeyPath(String sshPrivateKeyPath) {
        this.sshPrivateKeyPath = sshPrivateKeyPath;
    }

    public boolean isSshVerifyHostKey() {
        return sshVerifyHostKey;
    }

    public void setSshVerifyHostKey(boolean sshVerifyHostKey) {
        this.sshVerifyHostKey = sshVerifyHostKey;
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