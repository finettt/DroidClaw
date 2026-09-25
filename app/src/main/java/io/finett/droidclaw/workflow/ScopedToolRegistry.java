package io.finett.droidclaw.workflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

/**
 * A read-only decorator around {@link ToolRegistry} that exposes only the tools
 * a workflow node is allowed to use (spec §9).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code allowedTools == null} → every registered tool (subject to global gates)</li>
 *   <li>{@code allowedTools == []} → no tools at all; the node is a pure LLM call</li>
 *   <li>{@code deniedTools} is subtracted last</li>
 *   <li>{@code run_workflow} is always excluded (recursion guard, spec §13)</li>
 * </ul>
 */
public final class ScopedToolRegistry {

    /** Tool name that is always excluded from workflow node views. */
    private static final String RUN_WORKFLOW_TOOL = "run_workflow";

    private final ToolRegistry delegate;
    private final Set<String> effectiveTools;

    /**
     * @param delegate      the real registry
     * @param allowedTools  null = all tools; empty list = no tools
     * @param deniedTools   tools to subtract after the allowlist; may be null
     */
    public ScopedToolRegistry(ToolRegistry delegate, List<String> allowedTools, List<String> deniedTools) {
        this.delegate = delegate;
        this.effectiveTools = computeEffective(delegate, allowedTools, deniedTools);
    }

    private static Set<String> computeEffective(ToolRegistry registry, List<String> allowed, List<String> denied) {
        Set<String> effective = new java.util.LinkedHashSet<>();

        if (allowed == null) {
            for (Tool t : registry.getAllTools()) {
                effective.add(t.getName());
            }
        } else {
            for (String name : allowed) {
                if (registry.hasToolWithName(name)) {
                    effective.add(name);
                }
                // Unknown names are already caught by WorkflowValidator;
                // silently skip here for defense in depth.
            }
        }

        if (denied != null) {
            effective.removeAll(denied);
        }

        // Recursion guard: run_workflow is never available inside a workflow
        effective.remove(RUN_WORKFLOW_TOOL);

        return Collections.unmodifiableSet(effective);
    }

    public JsonArray getToolDefinitions() {
        JsonArray all = delegate.getToolDefinitions();
        if (effectiveTools.isEmpty()) {
            return new JsonArray();
        }
        JsonArray filtered = new JsonArray();
        for (int i = 0; i < all.size(); i++) {
            JsonObject toolJson = all.get(i).getAsJsonObject();
            String name = extractToolName(toolJson);
            if (name != null && effectiveTools.contains(name)) {
                filtered.add(toolJson);
            }
        }
        return filtered;
    }

    public ToolResult executeTool(String toolName, JsonObject arguments) {
        if (!effectiveTools.contains(toolName)) {
            return ToolResult.error("Tool '" + toolName + "' is not available in this workflow node's scope");
        }
        return delegate.executeTool(toolName, arguments);
    }

    public Tool getTool(String name) {
        if (!effectiveTools.contains(name)) return null;
        return delegate.getTool(name);
    }

    public boolean hasToolWithName(String name) {
        return effectiveTools.contains(name);
    }

    public Set<String> getEffectiveToolNames() {
        return effectiveTools;
    }

    public int getToolCount() {
        return effectiveTools.size();
    }

    public VirtualFileSystem getVirtualFileSystem() {
        return delegate.getVirtualFileSystem();
    }

    public File getWorkspaceRoot() {
        return delegate.getWorkspaceRoot();
    }

    public android.content.Context getContext() {
        return delegate.getContext();
    }

    private static String extractToolName(JsonObject toolJson) {
        try {
            if (toolJson.has("function")) {
                return toolJson.getAsJsonObject("function").get("name").getAsString();
            }
            if (toolJson.has("name")) {
                return toolJson.get("name").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
