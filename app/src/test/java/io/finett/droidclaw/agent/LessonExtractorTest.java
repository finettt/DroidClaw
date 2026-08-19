package io.finett.droidclaw.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

import io.finett.droidclaw.model.Lesson;

/**
 * Validation/normalization tests for LessonExtractor.parseLessons and the
 * structured output schema. No Android or network dependencies.
 */
public class LessonExtractorTest {

    private final LessonExtractor extractor = new LessonExtractor(null, null);

    private JsonArray lessons(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    public void parseLessons_validEntry_isNormalized() {
        List<Lesson> result = extractor.parseLessons(lessons(
                "[{\"category\":\"user_preference\",\"content\":\" Always use UTC \","
                + "\"evidence\":\"user said so\",\"scope\":\"memory\",\"confidence\":3}]"),
                "session-1");

        assertEquals(1, result.size());
        Lesson lesson = result.get(0);
        assertEquals("Always use UTC", lesson.getContent()); // trimmed
        assertEquals(Lesson.CATEGORY_USER_PREFERENCE, lesson.getCategory());
        assertEquals(Lesson.SCOPE_MEMORY, lesson.getScope());
        assertEquals(3, lesson.getConfidence());
        assertEquals(Lesson.SOURCE_REFLECTION, lesson.getSource());
        assertEquals("session-1", lesson.getSessionId());
        assertNotNull(lesson.getId());
    }

    @Test
    public void parseLessons_unknownCategoryAndScope_fallBack() {
        List<Lesson> result = extractor.parseLessons(lessons(
                "[{\"category\":\"weird\",\"content\":\"x\",\"scope\":\"galaxy\","
                + "\"confidence\":9}]"),
                "s");

        assertEquals(1, result.size());
        assertEquals(Lesson.CATEGORY_FACT, result.get(0).getCategory());
        assertEquals(Lesson.SCOPE_MEMORY, result.get(0).getScope());
        assertEquals(3, result.get(0).getConfidence()); // clamped to 1..3
    }

    @Test
    public void parseLessons_missingContent_isDropped() {
        List<Lesson> result = extractor.parseLessons(lessons(
                "[{\"category\":\"fact\"},{\"content\":\"   \"},{\"content\":\"kept\"}]"),
                "s");

        assertEquals(1, result.size());
        assertEquals("kept", result.get(0).getContent());
    }

    @Test
    public void parseLessons_duplicateContent_isDeduped() {
        List<Lesson> result = extractor.parseLessons(lessons(
                "[{\"content\":\"same lesson\"},{\"content\":\"same lesson\"},"
                + "{\"content\":\"different\"}]"),
                "s");

        assertEquals(2, result.size());
    }

    @Test
    public void parseLessons_capAtTenPerRun() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 15; i++) {
            if (i > 0) json.append(",");
            json.append("{\"content\":\"lesson ").append(i).append("\"}");
        }
        json.append("]");

        List<Lesson> result = extractor.parseLessons(lessons(json.toString()), "s");

        assertEquals(10, result.size());
    }

    @Test
    public void parseLessons_nonObjectEntries_areSkipped() {
        List<Lesson> result = extractor.parseLessons(lessons(
                "[\"garbage\", 42, {\"content\":\"valid\"}]"),
                "s");

        assertEquals(1, result.size());
        assertEquals("valid", result.get(0).getContent());
    }

    @Test
    public void responseSchema_hasRequiredStructure() {
        JsonObject schema = LessonExtractor.getResponseSchema();

        assertEquals("object", schema.get("type").getAsString());
        assertTrue(schema.getAsJsonObject("properties").has("lessons"));
        assertTrue(schema.getAsJsonArray("required").contains(
                JsonParser.parseString("\"lessons\"")));

        JsonObject item = schema.getAsJsonObject("properties")
                .getAsJsonObject("lessons").getAsJsonObject("items");
        assertTrue(item.getAsJsonObject("properties").has("content"));
        assertTrue(item.getAsJsonObject("properties")
                .getAsJsonObject("category").has("enum"));
    }
}