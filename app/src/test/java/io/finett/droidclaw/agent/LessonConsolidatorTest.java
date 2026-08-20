package io.finett.droidclaw.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.finett.droidclaw.repository.LessonRepository;

/**
 * Pure-logic tests for LessonConsolidator: id filtering and the no-op path.
 * LLM interaction itself is exercised on-device (integration).
 */
public class LessonConsolidatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final LessonConsolidator consolidator = new LessonConsolidator(null, null, null, null);

    private JsonObject plan(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    public void parseIds_keepsOnlyBatchIds() {
        Set<String> batch = new HashSet<>(Arrays.asList("a", "b"));
        List<String> ids = consolidator.parseIds(
                plan("{\"consumed_ids\":[\"a\",\"z\",\"b\"]}"), "consumed_ids", batch);
        assertEquals(Arrays.asList("a", "b"), ids);
    }

    @Test
    public void parseIds_deduplicates() {
        Set<String> batch = new HashSet<>(Arrays.asList("a"));
        List<String> ids = consolidator.parseIds(
                plan("{\"consumed_ids\":[\"a\",\"a\"]}"), "consumed_ids", batch);
        assertEquals(1, ids.size());
    }

    @Test
    public void parseIds_missingFieldReturnsEmpty() {
        Set<String> batch = new HashSet<>(Arrays.asList("a"));
        assertTrue(consolidator.parseIds(plan("{}"), "consumed_ids", batch).isEmpty());
    }

    @Test
    public void parseIds_ignoresNonStringEntries() {
        Set<String> batch = new HashSet<>(Arrays.asList("a"));
        List<String> ids = consolidator.parseIds(
                plan("{\"consumed_ids\":[\"a\",42,null]}"), "consumed_ids", batch);
        assertEquals(Arrays.asList("a"), ids);
    }

    @Test
    public void runConsolidation_noFreshLessons_returnsTrueWithoutSideEffects() throws Exception {
        LessonRepository repo = new LessonRepository(tmp.newFolder("lessons"));
        LessonConsolidator withEmptyStore = new LessonConsolidator(null, null, repo, null);
        assertTrue(withEmptyStore.runConsolidation());
    }

    @Test
    public void runConsolidation_apiFailure_leavesLessonsUnconsumed() throws Exception {
        LessonRepository repo = new LessonRepository(tmp.newFolder("lessons"));
        repo.appendLesson(new io.finett.droidclaw.model.Lesson(
                "s1", io.finett.droidclaw.model.Lesson.CATEGORY_USER_PREFERENCE,
                "content", "evidence", io.finett.droidclaw.model.Lesson.SCOPE_MEMORY,
                "auto", 4));
        // null apiService -> extraction returns no result -> failure, nothing consumed
        LessonConsolidator failing = new LessonConsolidator(null, null, repo, null);
        assertFalse(failing.runConsolidation());
        assertEquals(1, repo.readUnconsumed().size());
    }

    @Test
    public void responseSchema_requiresAllFields() {
        JsonObject schema = LessonConsolidator.getResponseSchema();
        assertEquals("object", schema.get("type").getAsString());
        assertTrue(schema.getAsJsonObject("properties").has("updated_memory"));
        JsonArray required = schema.getAsJsonArray("required");
        assertEquals(4, required.size());
    }
}