package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.File;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.Lesson;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Lets the agent deliberately record a durable insight mid-conversation
 * (self-improvement layer ③, active capture). Workspace-only write —
 * no approval required.
 */
public class SaveLessonTool implements Tool {

    private static final String TAG = "SaveLessonTool";
    private static final String NAME = "save_lesson";

    private final ToolDefinition definition;
    private final Context context;
    private LessonRepository lessonRepository;

    public SaveLessonTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private LessonRepository getLessonRepository() {
        if (lessonRepository == null) {
            WorkspaceManager workspaceManager = new WorkspaceManager(context);
            lessonRepository = new LessonRepository(
                    new File(workspaceManager.getMemoryDirectory(), "lessons"));
        }
        return lessonRepository;
    }

    private ToolDefinition createDefinition() {
        ParametersBuilder builder = new ParametersBuilder()
                .addString("content",
                        "One self-contained sentence with the durable lesson to remember", true)
                .addString("category",
                        "Lesson category: 'user_preference', 'tool_pattern', 'failure_cause', "
                        + "'workflow' or 'fact' (default: 'fact')", false)
                .addString("scope",
                        "Where the lesson belongs: 'memory' (default), 'user' or 'skill'", false);

        return new ToolDefinition(
                NAME,
                "Save a durable lesson learned in this conversation so it is remembered in "
                + "future conversations. Use it deliberately for user corrections, preferences, "
                + "tool-usage patterns, failure causes and effective workflows. Do NOT save "
                + "one-off task details.",
                builder.build()
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (arguments == null || !arguments.has("content")
                    || arguments.get("content").getAsString().trim().isEmpty()) {
                return ToolResult.error("Missing required argument: content");
            }

            String content = arguments.get("content").getAsString().trim();
            String category = normalizeCategory(arguments);
            String scope = normalizeScope(arguments);

            Lesson lesson = new Lesson(null, category, content, null, scope,
                    Lesson.SOURCE_AGENT, 3);
            getLessonRepository().appendLesson(lesson);

            Log.i(TAG, "Agent saved a lesson (" + category + "/" + scope + "): " + content);

            JsonObject result = new JsonObject();
            result.addProperty("status", "saved");
            result.addProperty("id", lesson.getId());
            return ToolResult.success(result.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save lesson", e);
            return ToolResult.error("Failed to save lesson: " + e.getMessage());
        }
    }

    private String normalizeCategory(JsonObject arguments) {
        if (arguments.has("category")) {
            String raw = arguments.get("category").getAsString().trim().toLowerCase();
            switch (raw) {
                case Lesson.CATEGORY_USER_PREFERENCE:
                case Lesson.CATEGORY_TOOL_PATTERN:
                case Lesson.CATEGORY_FAILURE_CAUSE:
                case Lesson.CATEGORY_WORKFLOW:
                    return raw;
                default:
                    return Lesson.CATEGORY_FACT;
            }
        }
        return Lesson.CATEGORY_FACT;
    }

    private String normalizeScope(JsonObject arguments) {
        if (arguments.has("scope")) {
            String raw = arguments.get("scope").getAsString().trim().toLowerCase();
            switch (raw) {
                case Lesson.SCOPE_USER:
                case Lesson.SCOPE_SKILL:
                    return raw;
                default:
                    return Lesson.SCOPE_MEMORY;
            }
        }
        return Lesson.SCOPE_MEMORY;
    }
}