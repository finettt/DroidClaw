package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentConfig {
    private String defaultModel; // Format: "provider-id/model-id"
    private boolean shellAccess;
    private String sandboxMode; // "strict", "relaxed", or "full"
    private String effort; // reasoning effort / turn budget level (see Effort)
    private boolean requireApproval;
    private int shellTimeout;
    private boolean backgroundShellEnabled; // allow shell/python in background workers
    private boolean backgroundExecEnabled; // allow agent to dispatch any tool with background=true
    private boolean screenControlEnabled;  // allow agent to read UI and control screen via accessibility
    private boolean screenControlTrustMode; // skip per-action approval for screen control tools
    private boolean streamResponses;        // stream LLM text via SSE as it is generated
    private boolean calendarEnabled; // allow agent to read and manage device calendar events
    private boolean webToolsEnabled; // allow agent to fetch and read web pages via the fetch_page tool
    private String searxngUrl;   // base URL of the user's own SearXNG instance (empty = web_search unavailable)
    private boolean guidelinesLearningEnabled; // analyze each finished chat and update .agent/GUIDELINES.md
    private boolean lessonExtractionEnabled; // extract durable lessons from finished chats into the lesson store
    private boolean lessonConsolidationEnabled; // daily consolidation of lessons into MEMORY.md
    private int llmConnectTimeout;   // seconds
    private int llmReadTimeout;      // seconds
    private int llmWriteTimeout;     // seconds
    private Map<String, String> toolApprovalOverrides = new HashMap<>(); // toolName -> ToolApprovalMode.name()
    private String shellBackend;     // null / "distro" = distro backend (default); "ssh" = remote
    private String shellApprovalMode; // "ask_all", "auto", or "full_access"
    private String sshHost;
    private int sshPort;
    private String sshUser;
    private String sshAuthType;      // "password" or "key"
    private String sshPassword;
    private String sshPrivateKeyPath;
    private boolean sshVerifyHostKey;
    private int distroDiskLimitMb; // advisory host-side quota (MB) for the Linux image
    private boolean distroNoSeccomp; // set PROOT_NO_SECCOMP=1 inside the guest (default on)

    public AgentConfig() {
        this.streamResponses = true;
        this.effort = Effort.getDefault().persistedName();
        this.llmConnectTimeout = 30;
        this.llmReadTimeout = 120;
        this.llmWriteTimeout = 30;
        this.screenControlEnabled = false;
        this.screenControlTrustMode = false;
        this.calendarEnabled = false;
        this.webToolsEnabled = true;
        this.searxngUrl = "";
        this.guidelinesLearningEnabled = true;
        this.lessonExtractionEnabled = true;
        this.lessonConsolidationEnabled = true;
        this.toolApprovalOverrides = new HashMap<>();
        this.shellBackend = null; // distro backend is the default
        this.shellApprovalMode = "auto";
        this.sshPort = 22;
        this.sshVerifyHostKey = true;
        this.distroDiskLimitMb = 2048;
        this.distroNoSeccomp = true;
    }

    public AgentConfig(String defaultModel, boolean shellAccess, String sandboxMode,
                       boolean requireApproval, int shellTimeout,
                       boolean backgroundShellEnabled) {
        this.defaultModel = defaultModel;
        this.shellAccess = shellAccess;
        this.sandboxMode = sandboxMode;
        this.requireApproval = requireApproval;
        this.effort = Effort.getDefault().persistedName();
        this.shellTimeout = shellTimeout;
        this.backgroundShellEnabled = backgroundShellEnabled;
        this.backgroundExecEnabled = false;
        this.streamResponses = true;
        this.shellApprovalMode = "auto";
        this.llmConnectTimeout = 30;
        this.llmReadTimeout = 120;
        this.llmWriteTimeout = 30;
        this.screenControlEnabled = false;
        this.screenControlTrustMode = false;
        this.calendarEnabled = false;
        this.webToolsEnabled = true;
        this.searxngUrl = "";
        this.guidelinesLearningEnabled = true;
        this.lessonExtractionEnabled = true;
        this.lessonConsolidationEnabled = true;
        this.sshPort = 22;
        this.sshVerifyHostKey = true;
        this.distroDiskLimitMb = 2048;
        this.distroNoSeccomp = true;
    }

    public static AgentConfig getDefaults() {
        return new AgentConfig(
                "",
                false,
                "strict",
                true,
                30,
                false
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

    /** Current reasoning effort / turn-budget level. See {@link Effort}. */
    public String getEffort() {
        return effort;
    }

    public void setEffort(String effort) {
        this.effort = effort;
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

    public boolean isStreamResponses() {
        return streamResponses;
    }

    public void setStreamResponses(boolean streamResponses) {
        this.streamResponses = streamResponses;
    }

    public boolean isCalendarEnabled() {
        return calendarEnabled;
    }

    public void setCalendarEnabled(boolean calendarEnabled) {
        this.calendarEnabled = calendarEnabled;
    }

    public boolean isWebToolsEnabled() {
        return webToolsEnabled;
    }

    public void setWebToolsEnabled(boolean webToolsEnabled) {
        this.webToolsEnabled = webToolsEnabled;
    }

    public String getSearxngUrl() {
        return (searxngUrl == null) ? "" : searxngUrl.trim();
    }

    public void setSearxngUrl(String searxngUrl) {
        this.searxngUrl = (searxngUrl == null) ? "" : searxngUrl.trim();
    }

    public boolean isGuidelinesLearningEnabled() {
        return guidelinesLearningEnabled;
    }

    public void setGuidelinesLearningEnabled(boolean guidelinesLearningEnabled) {
        this.guidelinesLearningEnabled = guidelinesLearningEnabled;
    }

    public boolean isLessonExtractionEnabled() {
        return lessonExtractionEnabled;
    }

    public void setLessonExtractionEnabled(boolean lessonExtractionEnabled) {
        this.lessonExtractionEnabled = lessonExtractionEnabled;
    }

    public boolean isLessonConsolidationEnabled() {
        return lessonConsolidationEnabled;
    }

    public void setLessonConsolidationEnabled(boolean lessonConsolidationEnabled) {
        this.lessonConsolidationEnabled = lessonConsolidationEnabled;
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
        // null (or "distro") selects the local distro backend; only "ssh" is remote.
        this.shellBackend = shellBackend;
    }

    public String getShellApprovalMode() {
        if (shellApprovalMode == null || shellApprovalMode.isEmpty()) {
            return "auto";
        }
        return shellApprovalMode;
    }

    public void setShellApprovalMode(String shellApprovalMode) {
        this.shellApprovalMode = shellApprovalMode;
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

    /** Advisory host-side quota, in megabytes, for the installed Linux image. */
    public int getDistroDiskLimitMb() {
        return distroDiskLimitMb;
    }

    public void setDistroDiskLimitMb(int distroDiskLimitMb) {
        this.distroDiskLimitMb = distroDiskLimitMb <= 0 ? 2048 : distroDiskLimitMb;
    }

    /** Whether {@code PROOT_NO_SECCOMP=1} is set inside the guest (default on). */
    public boolean isDistroNoSeccomp() {
        return distroNoSeccomp;
    }

    public void setDistroNoSeccomp(boolean distroNoSeccomp) {
        this.distroNoSeccomp = distroNoSeccomp;
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
