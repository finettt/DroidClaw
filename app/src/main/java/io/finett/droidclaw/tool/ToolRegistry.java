package io.finett.droidclaw.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
import io.finett.droidclaw.python.PythonExecutor;
import io.finett.droidclaw.shell.ShellConfig;
import io.finett.droidclaw.shell.ShellExecutor;
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
import io.finett.droidclaw.tool.impl.SetupHeartbeatTool;
import io.finett.droidclaw.tool.impl.SubmitNotificationTool;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Registry for managing all available tools.
 * Provides tools to the LLM for function calling.
 * Respects SettingsManager configuration for tool availability.
 */
public class ToolRegistry {
    private static final String TOOL_EXECUTE_SHELL = "execute_shell";
    private static final String TOOL_EXECUTE_PYTHON = "execute_python";
    
    // Tools that require shell access to be enabled
    private static final Set<String> SHELL_ACCESS_TOOLS = new HashSet<>(Arrays.asList(
        TOOL_EXECUTE_SHELL,
        TOOL_EXECUTE_PYTHON
    ));
    
    private final Map<String, Tool> tools = new HashMap<>();
    private final Context context;
    private final WorkspaceManager workspaceManager;
    private final VirtualFileSystem vfs;
    private final ShellExecutor shellExecutor;
    private final PythonExecutor pythonExecutor;
    private final SettingsManager settingsManager;

    /**
     * Creates a ToolRegistry without settings (for backwards compatibility).
     */
    public ToolRegistry(Context context) {
        this(context, null);
    }
    
    /**
     * Creates a ToolRegistry with settings for configuration.
     */
    public ToolRegistry(Context context, SettingsManager settingsManager) {
        this.context = context;
        this.settingsManager = settingsManager;

        // Initialize workspace and filesystem
        this.workspaceManager = new WorkspaceManager(context);
        try {
            workspaceManager.initializeWithSkills();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize workspace", e);
        }
        this.vfs = new VirtualFileSystem(workspaceManager);

        // Initialize executors
        ShellConfig shellConfig = ShellConfig.createDefault();
        this.shellExecutor = new ShellExecutor(shellConfig, workspaceManager.getPathValidator());

        PythonConfig pythonConfig = PythonConfig.createDefault();
        this.pythonExecutor = new PythonExecutor(context, pythonConfig);

        // Register all tools
        registerTools();
    }

    /**
     * Register all available tools.
     */
    private void registerTools() {
        // File system tools (always available)
        registerTool(new FileReadTool(vfs));
        registerTool(new FileWriteTool(vfs));
        registerTool(new FileEditTool(vfs, workspaceManager.getPathValidator()));
        registerTool(new FileListTool(vfs));
        registerTool(new FileDeleteTool(vfs));
        registerTool(new FileInfoTool(vfs));
        registerTool(new FileSearchTool(vfs));
        
        // Execution tools (always registered, but execution may be blocked by settings)
        registerTool(new ShellTool(workspaceManager.getPathValidator(), ShellConfig.createDefault()));
        registerTool(new PythonTool(context, workspaceManager.getWorkspaceRoot(), PythonConfig.createDefault()));

        // Heartbeat tool
        registerTool(new HeartbeatOkTool());

        // Automation management tools
        registerTool(new CreateTaskTool(context));
        registerTool(new ListTasksTool(context));
        registerTool(new PauseTaskTool(context));
        registerTool(new ResumeTaskTool(context));
        registerTool(new DeleteTaskTool(context));
        registerTool(new ViewTaskHistoryTool(context));
        registerTool(new TaskStatsTool(context));
        registerTool(new SetupHeartbeatTool(context));

        // Notification tool for background tasks
        registerTool(new SubmitNotificationTool(context));
    }
    
    /**
     * Check if shell access is enabled in settings.
     */
    public boolean isShellAccessEnabled() {
        return settingsManager == null || settingsManager.isShellAccessEnabled();
    }
    
    /**
     * Check if a tool requires shell access to be enabled.
     */
    public boolean requiresShellAccess(String toolName) {
        return SHELL_ACCESS_TOOLS.contains(toolName);
    }

    /**
     * Register a single tool.
     * 
     * @param tool Tool to register
     */
    private void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * Get a tool by name.
     * 
     * @param name Tool name
     * @return Tool instance or null if not found
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Check if a tool with the given name exists.
     * 
     * @param name Tool name
     * @return true if tool exists
     */
    public boolean hasToolWithName(String name) {
        return tools.containsKey(name);
    }

    /**
     * Get all registered tools.
     * 
     * @return List of all tools
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Get tool definitions for the OpenAI API.
     * Returns a JSON array of tool definitions.
     * Only includes tools that are currently enabled in settings.
     *
     * @return JsonArray of tool definitions
     */
    public JsonArray getToolDefinitions() {
        JsonArray definitions = new JsonArray();
        boolean shellEnabled = isShellAccessEnabled();
        
        for (Tool tool : tools.values()) {
            // Skip shell/python tools if shell access is disabled
            if (requiresShellAccess(tool.getName()) && !shellEnabled) {
                continue;
            }
            definitions.add(tool.getDefinition().toJson());
        }
        return definitions;
    }

    /**
     * Execute a tool by name with the given arguments.
     * Checks if the tool is allowed to execute based on settings.
     *
     * @param toolName Name of the tool to execute
     * @param arguments Arguments for the tool
     * @return ToolResult containing the execution result
     */
    public ToolResult executeTool(String toolName, JsonObject arguments) {
        Tool tool = getTool(toolName);
        if (tool == null) {
            return ToolResult.error("Tool not found: " + toolName);
        }
        
        // Check if shell access is required but not enabled
        if (requiresShellAccess(toolName) && !isShellAccessEnabled()) {
            return ToolResult.error("Shell access is disabled. Enable it in Settings to use " + toolName);
        }
        
        try {
            return tool.execute(arguments);
        } catch (Exception e) {
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * Get the count of registered tools.
     * 
     * @return Number of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Get the virtual file system instance.
     * 
     * @return VirtualFileSystem instance
     */
    public VirtualFileSystem getVirtualFileSystem() {
        return vfs;
    }

    /**
     * Get the shell executor instance.
     * 
     * @return ShellExecutor instance
     */
    public ShellExecutor getShellExecutor() {
        return shellExecutor;
    }

    /**
     * Get the Python executor instance.
     * 
     * @return PythonExecutor instance
     */
    public PythonExecutor getPythonExecutor() {
        return pythonExecutor;
    }
}