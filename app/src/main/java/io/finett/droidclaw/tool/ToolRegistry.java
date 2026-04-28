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
import io.finett.droidclaw.tool.impl.SearxngSearchTool;
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
    private final ShellExecutor shellExecutor;
    private final PythonExecutor pythonExecutor;
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

        ShellConfig shellConfig = ShellConfig.createDefault();
        this.shellExecutor = new ShellExecutor(shellConfig, workspaceManager.getPathValidator());

        PythonConfig pythonConfig = PythonConfig.createDefault();
        this.pythonExecutor = new PythonExecutor(context, pythonConfig);

        registerTools();
    }

    private void registerTools() {
        registerTool(new FileReadTool(vfs));
        registerTool(new FileWriteTool(vfs));
        registerTool(new FileEditTool(vfs, workspaceManager.getPathValidator()));
        registerTool(new FileListTool(vfs));
        registerTool(new FileDeleteTool(vfs));
        registerTool(new FileInfoTool(vfs));
        registerTool(new FileSearchTool(vfs));

        registerTool(new ShellTool(workspaceManager.getPathValidator(), ShellConfig.createDefault()));
        registerTool(new PythonTool(context, workspaceManager.getWorkspaceRoot(), PythonConfig.createDefault()));

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

        if (settingsManager != null) {
            registerTool(new SearxngSearchTool(settingsManager));
        }
    }

    public boolean isShellAccessEnabled() {
        return settingsManager == null || settingsManager.isShellAccessEnabled();
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

        for (Tool tool : tools.values()) {
            if (requiresShellAccess(tool.getName()) && !shellEnabled) {
                continue;
            }
            definitions.add(tool.getDefinition().toJson());
        }
        return definitions;
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

    public ShellExecutor getShellExecutor() {
        return shellExecutor;
    }

    public PythonExecutor getPythonExecutor() {
        return pythonExecutor;
    }
}