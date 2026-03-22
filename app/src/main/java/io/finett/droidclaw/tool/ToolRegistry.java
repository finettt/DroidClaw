package io.finett.droidclaw.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import io.finett.droidclaw.tool.impl.PythonTool;
import io.finett.droidclaw.tool.impl.ShellTool;

/**
 * Registry for managing all available tools.
 * Provides tools to the LLM for function calling.
 */
public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();
    private final Context context;
    private final WorkspaceManager workspaceManager;
    private final VirtualFileSystem vfs;
    private final ShellExecutor shellExecutor;
    private final PythonExecutor pythonExecutor;

    public ToolRegistry(Context context) {
        this.context = context;
        
        // Initialize workspace and filesystem
        this.workspaceManager = new WorkspaceManager(context);
        try {
            workspaceManager.initialize();
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
        // File system tools
        registerTool(new FileReadTool(vfs));
        registerTool(new FileWriteTool(vfs));
        registerTool(new FileEditTool(vfs, workspaceManager.getPathValidator()));
        registerTool(new FileListTool(vfs));
        registerTool(new FileDeleteTool(vfs));
        registerTool(new FileInfoTool(vfs));
        registerTool(new FileSearchTool(vfs));
        
        // Execution tools
        registerTool(new ShellTool(workspaceManager.getPathValidator(), ShellConfig.createDefault()));
        registerTool(new PythonTool(context, workspaceManager.getWorkspaceRoot(), PythonConfig.createDefault()));
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
     * 
     * @return JsonArray of tool definitions
     */
    public JsonArray getToolDefinitions() {
        JsonArray definitions = new JsonArray();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition().toJson());
        }
        return definitions;
    }

    /**
     * Execute a tool by name with the given arguments.
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