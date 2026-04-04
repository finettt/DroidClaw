package io.finett.droidclaw.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.finett.droidclaw.model.TaskSecurityConfig;
import io.finett.droidclaw.util.AuditLogger;

/**
 * Sandboxed version of ToolRegistry for background task execution.
 *
 * Enforces security constraints on tool access during cron job and heartbeat execution.
 * Blocks dangerous tools and respects resource limits.
 */
public class SandboxedToolRegistry {
    private static final String TAG = "SandboxedToolRegistry";

    private final ToolRegistry wrappedRegistry;
    private final TaskSecurityConfig securityConfig;
    private final AuditLogger auditLogger;
    private final String taskId;
    private final String taskType;

    // Execution counters
    private int toolCallCount = 0;

    /**
     * Create a sandboxed tool registry.
     *
     * @param context Android context
     * @param securityConfig Security configuration to enforce
     * @param auditLogger Audit logger for tracking
     * @param taskId ID of the task being executed
     * @param taskType Type of task ("cronjob" or "heartbeat")
     */
    public SandboxedToolRegistry(
            Context context,
            TaskSecurityConfig securityConfig,
            AuditLogger auditLogger,
            String taskId,
            String taskType
    ) {
        this.securityConfig = securityConfig;
        this.auditLogger = auditLogger;
        this.taskId = taskId;
        this.taskType = taskType;

        // Create the underlying tool registry
        this.wrappedRegistry = new ToolRegistry(context);
    }

    /**
     * Get tool definitions for the API.
     * Only returns tools that are allowed by the security config.
     */
    public JsonArray getToolDefinitions() {
        JsonArray allDefinitions = wrappedRegistry.getToolDefinitions();
        JsonArray filteredDefinitions = new JsonArray();

        for (int i = 0; i < allDefinitions.size(); i++) {
            JsonObject toolDef = allDefinitions.get(i).getAsJsonObject();
            String toolName = toolDef.getAsJsonObject("function").get("name").getAsString();

            if (isToolAllowed(toolName)) {
                filteredDefinitions.add(toolDef);
            } else {
                // Log blocked tool
                auditLogger.logSecurityEvent(taskId, taskType, "tool_blocked", "Tool blocked: " + toolName);
            }
        }

        return filteredDefinitions;
    }

    /**
     * Execute a tool with security checks.
     */
    public ToolResult executeTool(String toolName, JsonObject arguments) {
        // Check emergency disable
        if (securityConfig.isEmergencyActive()) {
            String msg = "Execution blocked: Emergency disable active (" + securityConfig.getEmergencyDisableReason() + ")";
            auditLogger.logSecurityEvent(taskId, taskType, "emergency_block", msg);
            return ToolResult.error(msg);
        }

        // Check if tool is allowed
        if (!isToolAllowed(toolName)) {
            String msg = "Tool execution blocked: " + toolName + " is not allowed in background execution";
            auditLogger.logToolBlocked(taskId, taskType, toolName);
            return ToolResult.error(msg);
        }

        // Check resource limits
        if (toolCallCount >= securityConfig.getMaxToolCalls()) {
            String msg = "Tool execution blocked: Maximum tool calls exceeded (" + securityConfig.getMaxToolCalls() + ")";
            auditLogger.logSecurityEvent(taskId, taskType, "resource_limit", msg);
            return ToolResult.error(msg);
        }

        // Increment counter
        toolCallCount++;

        // Execute the tool
        return wrappedRegistry.executeTool(toolName, arguments);
    }

    /**
     * Check if a tool is allowed based on security config.
     */
    private boolean isToolAllowed(String toolName) {
        // Check explicitly blocked tools
        if (securityConfig.isToolBlocked(toolName)) {
            return false;
        }

        // Check shell access
        if (securityConfig.isBlockShellAccess()) {
            if (toolName.equals("execute_shell") || toolName.equals("execute_python")) {
                return false;
            }
        }

        // Check Python access
        if (securityConfig.isBlockPythonAccess()) {
            if (toolName.equals("execute_python")) {
                return false;
            }
        }

        // Check destructive operations
        if (securityConfig.isBlockDestructiveOps()) {
            Set<String> destructiveTools = securityConfig.getBlockedTools();
            if (destructiveTools.contains(toolName)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the current tool call count.
     */
    public int getToolCallCount() {
        return toolCallCount;
    }

    /**
     * Get the underlying tool registry (for getting tool count, etc).
     * Use with caution - bypasses security checks.
     */
    public ToolRegistry getWrappedRegistry() {
        return wrappedRegistry;
    }
}
