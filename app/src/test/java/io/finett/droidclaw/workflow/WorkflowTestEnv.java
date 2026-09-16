package io.finett.droidclaw.workflow;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Stub host environment for JVM tests. Mirrors the 36 tool names registered by
 * {@code ToolRegistry} and a small set of model references.
 */
final class WorkflowTestEnv implements WorkflowEnvironment {

    static final Set<String> TOOLS = new HashSet<>(Arrays.asList(
            "calendar_create_event", "calendar_delete_event", "calendar_list_calendars",
            "calendar_list_events", "calendar_update_event", "create_task",
            "delete_file", "delete_task", "edit_file",
            "execute_python", "execute_shell", "file_info",
            "heartbeat_ok", "kill_background_process", "list_apps",
            "list_background_processes", "list_files", "list_lessons",
            "list_tasks", "open_app", "pause_task",
            "read_file", "resume_task", "save_lesson",
            "screen_get_ui_tree", "screen_perform_action", "screen_swipe",
            "screen_tap", "screen_type_text", "search_files",
            "searxng_web_search", "setup_heartbeat", "submit_notification",
            "task_stats", "view_task_history", "write_file"));

    private final Set<String> models;
    private final int maxTurns;

    WorkflowTestEnv(int maxTurns, String... modelRefs) {
        this.maxTurns = maxTurns;
        this.models = new HashSet<>(Arrays.asList(modelRefs));
    }

    static WorkflowTestEnv withExampleModels() {
        return new WorkflowTestEnv(20,
                "openrouter/anthropic/claude-sonnet-4",
                "openrouter/openai/gpt-4o-mini");
    }

    @Override public boolean hasTool(String toolName) { return TOOLS.contains(toolName); }

    @Override public boolean hasModel(String providerId, String modelId) {
        return models.contains(providerId + "/" + modelId);
    }

    @Override public int defaultMaxTurns() { return maxTurns; }

    @Override public boolean globalRequireApproval() { return true; }
}
