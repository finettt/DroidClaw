package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.Lesson;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Read-only view of the lesson store so the agent can recall what it has
 * learned (self-improvement layer ③).
 */
public class ListLessonsTool implements Tool {

    private static final String TAG = "ListLessonsTool";
    private static final String NAME = "list_lessons";
    private static final int MAX_RESULTS = 50;

    private final ToolDefinition definition;
    private final Context context;
    private LessonRepository lessonRepository;

    public ListLessonsTool(Context context) {
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
                .addString("days",
                        "Look-back window in days, as a number (default: '7')", false)
                .addString("category",
                        "Filter by category: 'user_preference', 'tool_pattern', "
                        + "'failure_cause', 'workflow' or 'fact'", false)
                .addBoolean("include_consumed",
                        "Also return lessons already absorbed by consolidation (default: false)",
                        false);

        return new ToolDefinition(
                NAME,
                "List lessons learned from past conversations. Fresh (unconsumed) lessons are "
                + "already injected into your context; use this tool to look further back or "
                + "filter by category.",
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
            int days = 7;
            String categoryFilter = null;
            boolean includeConsumed = false;

            if (arguments != null) {
                if (arguments.has("days")) {
                    try {
                        days = Integer.parseInt(
                                arguments.get("days").getAsString().trim());
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
                if (arguments.has("category")) {
                    categoryFilter = arguments.get("category").getAsString()
                            .trim().toLowerCase();
                }
                if (arguments.has("include_consumed")) {
                    includeConsumed = arguments.get("include_consumed").getAsBoolean();
                }
            }

            if (days < 1) days = 1;
            if (days > 90) days = 90;

            List<Lesson> lessons = getLessonRepository().readRecent(days, includeConsumed);

            JsonArray items = new JsonArray();
            int included = 0;
            for (Lesson lesson : lessons) {
                if (included >= MAX_RESULTS) {
                    break;
                }
                if (categoryFilter != null && !categoryFilter.isEmpty()
                        && !categoryFilter.equals(lesson.getCategory())) {
                    continue;
                }
                JsonObject item = new JsonObject();
                item.addProperty("id", lesson.getId());
                item.addProperty("timestamp", lesson.getTimestamp());
                item.addProperty("category", lesson.getCategory());
                item.addProperty("content", lesson.getContent());
                item.addProperty("scope", lesson.getScope());
                item.addProperty("source", lesson.getSource());
                item.addProperty("consumed", lesson.isConsumed());
                items.add(item);
                included++;
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", included);
            result.addProperty("window_days", days);
            result.add("lessons", items);
            return ToolResult.success(result.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to list lessons", e);
            return ToolResult.error("Failed to list lessons: " + e.getMessage());
        }
    }
}