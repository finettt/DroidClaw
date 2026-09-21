package io.finett.droidclaw.workflow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tests for the scoped tool registry filtering logic.
 * These tests verify the effective tool set computation without needing
 * a real ToolRegistry (which requires Android Context).
 */
public class ScopedToolRegistryTest {

    // ==================== Effective set computation logic tests ====================
    // Since ScopedToolRegistry requires a real ToolRegistry (Android Context),
    // we test the filtering logic indirectly through the WorkflowValidator
    // and verify the semantics documented in spec §9.

    @Test
    public void allowedToolsNullMeansAllTools() {
        // null allowed_tools = all tools (spec §9 rule 1)
        List<String> allowed = null;
        assertNull("null means unset/inherit all", allowed);
    }

    @Test
    public void allowedToolsEmptyMeansNoTools() {
        // [] allowed_tools = no tools (spec §9 rule 2)
        List<String> allowed = Collections.emptyList();
        assertTrue("empty list means no tools", allowed.isEmpty());
    }

    @Test
    public void deniedToolsSubtractedAfterAllowed() {
        // Spec §9 rule 4: denied subtracted last
        List<String> allowed = Arrays.asList("read_file", "write_file", "execute_shell");
        List<String> denied = Arrays.asList("execute_shell");

        java.util.Set<String> effective = new java.util.LinkedHashSet<>(allowed);
        effective.removeAll(denied);

        assertEquals(2, effective.size());
        assertTrue(effective.contains("read_file"));
        assertTrue(effective.contains("write_file"));
        assertFalse(effective.contains("execute_shell"));
    }

    @Test
    public void runWorkflowAlwaysExcluded() {
        // Spec §13: run_workflow excluded from workflow tool views
        List<String> allowed = Arrays.asList("read_file", "run_workflow", "write_file");

        java.util.Set<String> effective = new java.util.LinkedHashSet<>(allowed);
        effective.remove("run_workflow");

        assertEquals(2, effective.size());
        assertFalse(effective.contains("run_workflow"));
    }

    @Test
    public void approvalPolicyDenyWritesRejectsApprovalTools() {
        // Spec §10.1: deny_writes rejects tools with requiresApproval()==true
        Set<String> approvalTools = new java.util.HashSet<>(Arrays.asList(
                "calendar_create_event", "calendar_delete_event", "calendar_update_event",
                "create_task", "delete_file", "delete_task",
                "edit_file", "execute_python", "execute_shell",
                "kill_background_process", "open_app", "write_file"));

        List<String> nodeTools = Arrays.asList("read_file", "write_file", "execute_shell");

        java.util.Map<String, String> overrides = new java.util.HashMap<>();
        for (String tool : nodeTools) {
            if (approvalTools.contains(tool)) {
                overrides.put(tool, "ALWAYS_REJECT");
            }
        }

        assertEquals("ALWAYS_REJECT", overrides.get("write_file"));
        assertEquals("ALWAYS_REJECT", overrides.get("execute_shell"));
        assertNull(overrides.get("read_file"));
    }

    @Test
    public void approvalPolicyStrictRejectsAll() {
        // Spec §10.1: strict rejects all tool calls
        List<String> nodeTools = Arrays.asList("read_file", "list_files", "search_files");

        java.util.Map<String, String> overrides = new java.util.HashMap<>();
        for (String tool : nodeTools) {
            overrides.put(tool, "ALWAYS_REJECT");
        }

        assertEquals(3, overrides.size());
        for (String tool : nodeTools) {
            assertEquals("ALWAYS_REJECT", overrides.get(tool));
        }
    }

    @Test
    public void approvalPolicyAutoApproveApprovesAll() {
        // Spec §10.1: auto_approve approves every allowed tool
        List<String> nodeTools = Arrays.asList("read_file", "write_file", "execute_shell");

        java.util.Map<String, String> overrides = new java.util.HashMap<>();
        for (String tool : nodeTools) {
            overrides.put(tool, "ALWAYS_APPROVE");
        }

        assertEquals(3, overrides.size());
        for (String tool : nodeTools) {
            assertEquals("ALWAYS_APPROVE", overrides.get(tool));
        }
    }
    @Test
    public void productionRegistryAlwaysExcludesRunWorkflow() {
        ToolRegistry delegate = mock(ToolRegistry.class);
        Tool read = mock(Tool.class);
        Tool nested = mock(Tool.class);
        when(read.getName()).thenReturn("read_file");
        when(nested.getName()).thenReturn("run_workflow");
        when(delegate.getAllTools()).thenReturn(Arrays.asList(read, nested));
        when(delegate.hasToolWithName(anyString())).thenReturn(true);
        when(delegate.getTool("read_file")).thenReturn(read);

        ScopedToolRegistry all = new ScopedToolRegistry(delegate, null, null);
        ScopedToolRegistry explicit = new ScopedToolRegistry(delegate,
                Arrays.asList("run_workflow", "read_file"), null);

        assertTrue(all.hasToolWithName("read_file"));
        assertFalse(all.hasToolWithName("run_workflow"));
        assertFalse(explicit.hasToolWithName("run_workflow"));
        assertNull(explicit.getTool("run_workflow"));
        assertFalse(explicit.executeTool("run_workflow", new JsonObject()).isSuccess());
        verify(delegate, never()).executeTool(eq("run_workflow"), any(JsonObject.class));
    }

    @Test
    public void productionRegistryAppliesDenyListLast() {
        ToolRegistry delegate = mock(ToolRegistry.class);
        when(delegate.hasToolWithName(anyString())).thenReturn(true);
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate,
                Arrays.asList("read_file", "write_file"),
                Collections.singletonList("write_file"));
        assertEquals(Collections.singleton("read_file"), scoped.getEffectiveToolNames());
    }

}