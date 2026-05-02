package io.finett.droidclaw.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.python.PythonConfig;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.tool.impl.FileDeleteTool;
import io.finett.droidclaw.tool.impl.FileEditTool;
import io.finett.droidclaw.tool.impl.FileInfoTool;
import io.finett.droidclaw.tool.impl.FileListTool;
import io.finett.droidclaw.tool.impl.FileReadTool;
import io.finett.droidclaw.tool.impl.FileSearchTool;
import io.finett.droidclaw.tool.impl.FileWriteTool;
import io.finett.droidclaw.tool.impl.HeartbeatOkTool;
import io.finett.droidclaw.tool.impl.PythonTool;
import io.finett.droidclaw.tool.impl.ShellTool;
import io.finett.droidclaw.tool.impl.CreateTaskTool;
import io.finett.droidclaw.tool.impl.ListTasksTool;
import io.finett.droidclaw.tool.impl.PauseTaskTool;
import io.finett.droidclaw.tool.impl.ResumeTaskTool;
import io.finett.droidclaw.tool.impl.DeleteTaskTool;
import io.finett.droidclaw.tool.impl.ViewTaskHistoryTool;
import io.finett.droidclaw.tool.impl.TaskStatsTool;
import io.finett.droidclaw.tool.impl.KillBackgroundProcessTool;
import io.finett.droidclaw.tool.impl.ListBackgroundProcessesTool;
import io.finett.droidclaw.tool.impl.SetupHeartbeatTool;
import io.finett.droidclaw.tool.impl.SubmitNotificationTool;
import io.finett.droidclaw.util.SettingsManager;

public class ToolRegistry {
    private static final String TOOL_EXECUTE_SHELL = "execute_shell";
    private static final String TOOL_EXECUTE_PYTHON = "execute_python";

    private static final Set<String> SHELL_ACCESS_TOOLS = new HashSet<>(Arrays.asList(
        TOOL_EXECUTE_SHELL,
        TOOL_EXECUTE_PYTHON
    ));

    private final Map<String, Tool> tools = new HashMap<>();
    private final Context context;
    private final WorkspaceManager workspaceManager;
    private final VirtualFileSystem vfs;
    private final SettingsManager settingsManager;

    public ToolRegistry(Context context) {
        this(context, null);
    }

    public ToolRegistry(Context context, SettingsManager settingsManager) {
        this.context = context;
        this.settingsManager = settingsManager;

        this.workspaceManager = new WorkspaceManager(context);
        try {
            workspaceManager.initializeWithSkills();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize workspace", e);
        }
        this.vfs = new VirtualFileSystem(workspaceManager);

        registerTools();
    }

    private void registerTools() {
        String sandboxMode = (settingsManager != null)
                ? settingsManager.getSandboxMode() : "strict";
        boolean shellEnabled = (settingsManager == null) || settingsManager.isShellAccessEnabled();

        // Read-only file tools — always available in all sandbox modes
        registerTool(new FileReadTool(vfs));
        registerTool(new FileListTool(vfs));
        registerTool(new FileInfoTool(vfs));
        registerTool(new FileSearchTool(vfs));

        // Destructive/mutating file tools — disabled in strict mode
        if (!"strict".equals(sandboxMode)) {
            registerTool(new FileWriteTool(vfs));
            registerTool(new FileEditTool(vfs, workspaceManager.getPathValidator()));
            registerTool(new FileDeleteTool(vfs));
        }

        // Shell and Python execution — only when shell access is explicitly enabled
        // AND sandbox mode is not strict.
        // In "relaxed" mode: allowlist policy (safe command set, user approval on miss).
        // In "full" mode: full policy (any command, approval always required).
        if (shellEnabled && !"strict".equals(sandboxMode)) {
            ShellConfig shellConfig = buildShellConfig(sandboxMode);
            registerTool(new ShellTool(workspaceManager.getPathValidator(), shellConfig));

            PythonConfig pythonConfig = PythonConfig.builder()
                    .safeMode(!"full".equals(sandboxMode))
                    .build();
            registerTool(new PythonTool(context, workspaceManager.getWorkspaceRoot(), pythonConfig));
        }

        registerTool(new HeartbeatOkTool());

        registerTool(new CreateTaskTool(context));
        registerTool(new ListTasksTool(context));
        registerTool(new PauseTaskTool(context));
        registerTool(new ResumeTaskTool(context));
        registerTool(new DeleteTaskTool(context));
        registerTool(new ViewTaskHistoryTool(context));
        registerTool(new TaskStatsTool(context));
        registerTool(new SetupHeartbeatTool(context));

        registerTool(new SubmitNotificationTool(context));

        registerTool(new KillBackgroundProcessTool(context));
        registerTool(new ListBackgroundProcessesTool(context));
    }

    /**
     * Build a ShellConfig appropriate for the given sandbox mode.
     * <ul>
     *   <li>"relaxed" → ALLOWLIST policy with the safe minimal allowlist + ON_MISS ask,
     *       plus any user-defined custom allowlist entries.</li>
     *   <li>"full"    → FULL policy (any command) + ALWAYS ask.</li>
     *   <li>anything else → DENY (belt-and-suspenders, should not be reached).</li>
     * </ul>
     */
    private ShellConfig buildShellConfig(String sandboxMode) {
        int timeout = (settingsManager != null)
                ? settingsManager.getShellTimeoutSeconds() : 30;

        if ("full".equals(sandboxMode)) {
            return new ShellConfig.Builder()
                    .policy(io.finett.droidclaw.shell.ExecPolicy.full())
                    .defaultMode(io.finett.droidclaw.shell.ExecPlan.ExecMode.DIRECT)
                    .timeoutSeconds(timeout)
                    .build();
        }

        // "relaxed" — allowlist with safe defaults + user custom entries
        List<io.finett.droidclaw.shell.AllowlistEntry> extra = new ArrayList<>();
        if (settingsManager != null) {
            for (String path : settingsManager.getAgentConfig().getCustomAllowlist()) {
                if (path != null && !path.trim().isEmpty()) {
                    extra.add(new io.finett.droidclaw.shell.AllowlistEntry.Builder(path.trim()).build());
                }
            }
        }
        return ShellConfig.createAllowlistDefault(extra);
    }

    public boolean isShellAccessEnabled() {
        return settingsManager != null && settingsManager.isShellAccessEnabled()
                && !"strict".equals(settingsManager.getSandboxMode());
    }

    public boolean requiresShellAccess(String toolName) {
        return SHELL_ACCESS_TOOLS.contains(toolName);
    }

    private void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public boolean hasToolWithName(String name) {
        return tools.containsKey(name);
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public JsonArray getToolDefinitions() {
        JsonArray definitions = new JsonArray();
        boolean shellEnabled = isShellAccessEnabled();
        boolean bgExecEnabled = settingsManager != null
                && settingsManager.getAgentConfig().isBackgroundExecEnabled();

        for (Tool tool : tools.values()) {
            if (requiresShellAccess(tool.getName()) && !shellEnabled) {
                continue;
            }
            JsonObject toolJson = tool.getDefinition().toJson();

            // Inject "background" boolean property into every tool's schema
            // so the LLM can request async execution when backgroundExecEnabled is on
            if (bgExecEnabled) {
                injectBackgroundParam(toolJson);
            }

            definitions.add(toolJson);
        }
        return definitions;
    }

    /**
     * Inject an optional "background" boolean parameter into a tool definition's JSON schema.
     * This allows the LLM to pass {@code "background": true} in strict/structured output mode
     * where additionalProperties is false.
     */
    private void injectBackgroundParam(JsonObject toolJson) {
        try {
            JsonObject function = toolJson.getAsJsonObject("function");
            if (function == null) return;

            JsonObject parameters = function.getAsJsonObject("parameters");
            if (parameters == null) return;

            JsonObject properties = parameters.getAsJsonObject("properties");
            if (properties == null) return;

            // Skip tools that already manage background processes
            String name = function.has("name") ? function.get("name").getAsString() : "";
            if ("kill_background_process".equals(name)
                    || "list_background_processes".equals(name)) {
                return;
            }

            JsonObject bgProp = new JsonObject();
            bgProp.addProperty("type", "boolean");
            bgProp.addProperty("description",
                    "Set to true to run this tool asynchronously in the background. "
                    + "Returns a process_id immediately. Default: false.");
            properties.add("background", bgProp);
        } catch (Exception e) {
            // Schema injection is best-effort — do not break tool registration
        }
    }

    public ToolResult executeTool(String toolName, JsonObject arguments) {
        Tool tool = getTool(toolName);
        if (tool == null) {
            return ToolResult.error("Tool not found: " + toolName);
        }

        if (requiresShellAccess(toolName) && !isShellAccessEnabled()) {
            return ToolResult.error("Shell access is disabled. Enable it in Settings to use " + toolName);
        }

        try {
            return tool.execute(arguments);
        } catch (Exception e) {
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    public int getToolCount() {
        return tools.size();
    }

    public VirtualFileSystem getVirtualFileSystem() {
        return vfs;
    }

    public File getWorkspaceRoot() {
        return workspaceManager.getWorkspaceRoot();
    }

    public Context getContext() {
        return context;
    }

    /**
     * Release resources held by registered tools that own lifecycle-managed executors.
     * Must be called when the registry is discarded (e.g. fragment onDestroy, worker completion).
     */
    public void shutdown() {
        for (Tool tool : tools.values()) {
            if (tool instanceof PythonTool) {
                ((PythonTool) tool).shutdown();
            }
        }
    }
}