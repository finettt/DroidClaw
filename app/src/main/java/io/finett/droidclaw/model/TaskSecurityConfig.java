package io.finett.droidclaw.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Security configuration for background task execution (Cron Jobs & Heartbeat).
 *
 * Defines sandbox constraints and resource limits to ensure safe background execution.
 */
public class TaskSecurityConfig {
    // ========== SANDBOX SETTINGS ==========

    /**
     * Restrict file operations to workspace only.
     * Prevents cron jobs from accessing files outside the workspace.
     */
    private boolean restrictToWorkspace = true;

    /**
     * Block destructive file operations (delete, overwrite).
     */
    private boolean blockDestructiveOps = false;

    /**
     * Block shell execution entirely.
     */
    private boolean blockShellAccess = false;

    /**
     * Block Python execution entirely.
     */
    private boolean blockPythonAccess = false;

    /**
     * List of specific tools to block during background execution.
     */
    private Set<String> blockedTools = new HashSet<>();

    // ========== RESOURCE LIMITS ==========

    /**
     * Maximum execution time in seconds (default: 10 minutes = 600s).
     */
    private int maxExecutionTimeSeconds = 600;

    /**
     * Maximum number of agent iterations (default: 10).
     */
    private int maxIterations = 10;

    /**
     * Maximum number of tool calls per execution (default: 20).
     */
    private int maxToolCalls = 20;

    /**
     * Maximum token usage per execution (default: 50000).
     */
    private int maxTokenUsage = 50000;

    /**
     * Maximum memory context size in characters (default: 5000).
     */
    private int maxMemoryContextSize = 5000;

    // ========== EMERGENCY CONTROLS ==========

    /**
     * Master switch to disable ALL background execution.
     */
    private boolean emergencyDisable = false;

    /**
     * Reason for emergency disable (for audit trail).
     */
    private String emergencyDisableReason = "";

    /**
     * Timestamp when emergency disable was activated.
     */
    private long emergencyDisableTimestamp = 0;

    // ========== DEFAULT BLOCKED TOOLS ==========

    /**
     * Tools that are always blocked in background execution for safety.
     */
    private static final Set<String> DEFAULT_BLOCKED_TOOLS = new HashSet<>(Arrays.asList(
        // Add any tools that should never run in background
    ));

    public TaskSecurityConfig() {
        this.blockedTools = new HashSet<>(DEFAULT_BLOCKED_TOOLS);
    }

    // ========== GETTERS AND SETTERS ==========

    public boolean isRestrictToWorkspace() {
        return restrictToWorkspace;
    }

    public void setRestrictToWorkspace(boolean restrictToWorkspace) {
        this.restrictToWorkspace = restrictToWorkspace;
    }

    public boolean isBlockDestructiveOps() {
        return blockDestructiveOps;
    }

    public void setBlockDestructiveOps(boolean blockDestructiveOps) {
        this.blockDestructiveOps = blockDestructiveOps;
    }

    public boolean isBlockShellAccess() {
        return blockShellAccess;
    }

    public void setBlockShellAccess(boolean blockShellAccess) {
        this.blockShellAccess = blockShellAccess;
    }

    public boolean isBlockPythonAccess() {
        return blockPythonAccess;
    }

    public void setBlockPythonAccess(boolean blockPythonAccess) {
        this.blockPythonAccess = blockPythonAccess;
    }

    public Set<String> getBlockedTools() {
        return blockedTools;
    }

    public void setBlockedTools(Set<String> blockedTools) {
        this.blockedTools = blockedTools != null ? blockedTools : new HashSet<>();
    }

    public void addBlockedTool(String toolName) {
        if (toolName != null) {
            this.blockedTools.add(toolName);
        }
    }

    public void removeBlockedTool(String toolName) {
        if (toolName != null) {
            this.blockedTools.remove(toolName);
        }
    }

    public int getMaxExecutionTimeSeconds() {
        return maxExecutionTimeSeconds;
    }

    public void setMaxExecutionTimeSeconds(int maxExecutionTimeSeconds) {
        this.maxExecutionTimeSeconds = maxExecutionTimeSeconds;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxTokenUsage() {
        return maxTokenUsage;
    }

    public void setMaxTokenUsage(int maxTokenUsage) {
        this.maxTokenUsage = maxTokenUsage;
    }

    public int getMaxMemoryContextSize() {
        return maxMemoryContextSize;
    }

    public void setMaxMemoryContextSize(int maxMemoryContextSize) {
        this.maxMemoryContextSize = maxMemoryContextSize;
    }

    public boolean isEmergencyDisable() {
        return emergencyDisable;
    }

    public void setEmergencyDisable(boolean emergencyDisable) {
        this.emergencyDisable = emergencyDisable;
        if (emergencyDisable) {
            this.emergencyDisableTimestamp = System.currentTimeMillis();
        } else {
            this.emergencyDisableTimestamp = 0;
            this.emergencyDisableReason = "";
        }
    }

    public String getEmergencyDisableReason() {
        return emergencyDisableReason;
    }

    public void setEmergencyDisableReason(String emergencyDisableReason) {
        this.emergencyDisableReason = emergencyDisableReason;
    }

    public long getEmergencyDisableTimestamp() {
        return emergencyDisableTimestamp;
    }

    // ========== HELPER METHODS ==========

    /**
     * Check if a tool is blocked.
     */
    public boolean isToolBlocked(String toolName) {
        return blockedTools != null && blockedTools.contains(toolName);
    }

    /**
     * Check if shell access is allowed.
     */
    public boolean isShellAllowed() {
        return !blockShellAccess;
    }

    /**
     * Check if Python access is allowed.
     */
    public boolean isPythonAllowed() {
        return !blockPythonAccess;
    }

    /**
     * Get maximum execution time in milliseconds.
     */
    public long getMaxExecutionTimeMs() {
        return maxExecutionTimeSeconds * 1000L;
    }

    /**
     * Activate emergency disable with reason.
     */
    public void activateEmergencyDisable(String reason) {
        this.emergencyDisable = true;
        this.emergencyDisableReason = reason;
        this.emergencyDisableTimestamp = System.currentTimeMillis();
    }

    /**
     * Deactivate emergency disable.
     */
    public void deactivateEmergencyDisable() {
        this.emergencyDisable = false;
        this.emergencyDisableReason = "";
        this.emergencyDisableTimestamp = 0;
    }

    /**
     * Check if emergency disable is active.
     */
    public boolean isEmergencyActive() {
        return emergencyDisable;
    }
}
