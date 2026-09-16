package io.finett.droidclaw.workflow;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Adapts a {@link ScopedToolRegistry} to the {@link ToolRegistry} type so it
 * can be passed to {@link io.finett.droidclaw.agent.AgentLoop}.
 *
 * <p>This is a thin delegation wrapper. All filtering logic lives in
 * {@link ScopedToolRegistry}; this class only satisfies the type system.
 */
public final class ScopedToolRegistryAdapter extends ToolRegistry {

    private final ScopedToolRegistry scoped;

    public ScopedToolRegistryAdapter(ScopedToolRegistry scoped) {
        // Use the protected no-op constructor — all methods are overridden
        super();
        this.scoped = scoped;
    }

    @Override
    public JsonArray getToolDefinitions() {
        return scoped.getToolDefinitions();
    }

    @Override
    public ToolResult executeTool(String toolName, JsonObject arguments) {
        return scoped.executeTool(toolName, arguments);
    }

    @Override
    public Tool getTool(String name) {
        return scoped.getTool(name);
    }

    @Override
    public boolean hasToolWithName(String name) {
        return scoped.hasToolWithName(name);
    }

    @Override
    public java.util.List<Tool> getAllTools() {
        java.util.List<Tool> out = new java.util.ArrayList<>();
        for (String name : scoped.getEffectiveToolNames()) {
            Tool t = scoped.getTool(name);
            if (t != null) out.add(t);
        }
        return out;
    }

    @Override
    public int getToolCount() {
        return scoped.getToolCount();
    }

    @Override
    public VirtualFileSystem getVirtualFileSystem() {
        return scoped.getVirtualFileSystem();
    }

    @Override
    public File getWorkspaceRoot() {
        return scoped.getWorkspaceRoot();
    }

    @Override
    public Context getContext() {
        return scoped.getContext();
    }

    @Override
    public boolean isShellAccessEnabled() {
        // Delegate to scoped: if shell tools are in scope, they are enabled
        return scoped.hasToolWithName("execute_shell");
    }

    @Override
    public boolean requiresShellAccess(String toolName) {
        return "execute_shell".equals(toolName) || "execute_python".equals(toolName);
    }

    @Override
    public void shutdown() {
        // No-op: the delegate registry owns the lifecycle
    }
}