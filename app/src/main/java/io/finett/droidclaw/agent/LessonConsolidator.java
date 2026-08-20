package io.finett.droidclaw.agent;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Lesson;
import io.finett.droidclaw.repository.LessonConsolidationRepository;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.repository.MemoryRepository;

/**
 * Self-improvement layer ② — consolidation.
 *
 * <p>Takes the oldest batch of fresh (unconsumed) lessons, asks the model
 * once to merge them into the existing MEMORY.md (deduping against current
 * content), rewrites the file, marks the lessons consumed, and appends a
 * journal entry. On any failure the lessons stay unconsumed and the next
 * run retries.
 *
 * <p>Synchronous — intended to run on a background worker thread.
 */
public class LessonConsolidator {

    private static final String TAG = "LessonConsolidator";

    private static final int BATCH_SIZE = 15;
    private static final int MEMORY_CAP_CHARS = 30 * 1024;
    private static final int LLM_TIMEOUT_SECONDS = 120;

    private final LlmApiService apiService;
    private final MemoryRepository memoryRepository;
    private final LessonRepository lessonRepository;
    private final LessonConsolidationRepository journalRepository;

    public LessonConsolidator(LlmApiService apiService,
                              MemoryRepository memoryRepository,
                              LessonRepository lessonRepository,
                              LessonConsolidationRepository journalRepository) {
        this.apiService = apiService;
        this.memoryRepository = memoryRepository;
        this.lessonRepository = lessonRepository;
        this.journalRepository = journalRepository;
    }

    /**
     * Runs one consolidation cycle.
     *
     * @return true when consolidation succeeded or nothing needed doing,
     *         false on any failure (safe to retry later).
     */
    public boolean runConsolidation() {
        try {
            List<Lesson> unconsumed = lessonRepository.readUnconsumed();
            if (unconsumed.isEmpty()) {
                Log.d(TAG, "No fresh lessons to consolidate");
                return true;
            }

            // readUnconsumed returns newest first — consolidate the oldest batch
            List<Lesson> batch = unconsumed.size() <= BATCH_SIZE
                    ? unconsumed
                    : unconsumed.subList(unconsumed.size() - BATCH_SIZE, unconsumed.size());

            String currentMemory = memoryRepository.readLongTermMemory();
            String planJson = requestConsolidationPlan(batch, currentMemory);
            if (planJson == null || planJson.isEmpty()) {
                Log.w(TAG, "Consolidation call produced no result; lessons stay unconsumed");
                return false;
            }

            JsonObject plan = JsonParser.parseString(planJson).getAsJsonObject();
            String updatedMemory = plan.has("updated_memory")
                    ? plan.get("updated_memory").getAsString().trim() : "";
            if (updatedMemory.isEmpty()) {
                Log.w(TAG, "Model returned empty updated_memory; skipping write");
                return false;
            }
            if (updatedMemory.length() > MEMORY_CAP_CHARS) {
                updatedMemory = updatedMemory.substring(0, MEMORY_CAP_CHARS);
            }

            Set<String> batchIds = new HashSet<>();
            for (Lesson lesson : batch) {
                batchIds.add(lesson.getId());
            }
            List<String> consumedIds = parseIds(plan, "consumed_ids", batchIds);
            List<String> rejectedIds = parseIds(plan, "rejected_ids", batchIds);

            memoryRepository.writeLongTermMemory(updatedMemory);
            lessonRepository.markConsumed(consumedIds);
            lessonRepository.markConsumed(rejectedIds);

            journalRepository.appendConsolidation(System.currentTimeMillis(),
                    consumedIds, rejectedIds,
                    plan.has("summary") ? plan.get("summary").getAsString().trim() : "");

            Log.i(TAG, "Consolidated " + consumedIds.size() + " lesson(s), rejected "
                    + rejectedIds.size() + ", memory " + updatedMemory.length() + " chars");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Consolidation failed; lessons stay unconsumed", e);
            return false;
        }
    }

    /** Synchronous structured call via latch; null on error/timeout/refusal. */
    private String requestConsolidationPlan(List<Lesson> batch, String currentMemory) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(buildSystemPrompt(), ChatMessage.TYPE_SYSTEM));
        messages.add(new ChatMessage(buildUserPrompt(batch, currentMemory),
                ChatMessage.TYPE_USER));

        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        apiService.sendMessageStructured(messages, null, null, getResponseSchema(),
                new LlmApiService.StructuredResponseCallback() {
                    @Override
                    public void onSuccess(LlmApiService.StructuredResponse response) {
                        try {
                            if (!response.isRefusal() && response.getContent() != null) {
                                result[0] = response.getContent();
                            }
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Consolidation LLM call failed: " + error);
                        latch.countDown();
                    }
                });

        try {
            boolean completed = latch.await(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                Log.w(TAG, "Consolidation LLM call timed out after "
                        + LLM_TIMEOUT_SECONDS + "s");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result[0];
    }

    /** Extracts an id array, dropping ids not present in the current batch. */
    List<String> parseIds(JsonObject plan, String field, Set<String> batchIds) {
        List<String> ids = new ArrayList<>();
        if (!plan.has(field)) {
            return ids;
        }
        for (JsonElement element : plan.getAsJsonArray(field)) {
            try {
                String id = element.getAsString();
                if (batchIds.contains(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    private String buildSystemPrompt() {
        return "You maintain a personal assistant's long-term memory file (MEMORY.md). "
                + "You receive the current file content and a batch of fresh lessons "
                + "extracted from recent conversations. Produce the updated file: merge "
                + "the valuable lessons in, dedupe against existing entries, keep the "
                + "structure compact (markdown bullet lists grouped by topic), and stay "
                + "under " + MEMORY_CAP_CHARS + " characters. Drop nothing valuable, add "
                + "nothing speculative.";
    }

    private String buildUserPrompt(List<Lesson> batch, String currentMemory) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Current MEMORY.md\n\n");
        builder.append(currentMemory.isEmpty() ? "(empty)" : currentMemory);
        builder.append("\n\n# Fresh lessons to consolidate\n\n");
        for (Lesson lesson : batch) {
            builder.append("- id: ").append(lesson.getId())
                    .append(" | category: ").append(lesson.getCategory())
                    .append(" | scope: ").append(lesson.getScope())
                    .append(" | confidence: ").append(lesson.getConfidence())
                    .append("\n  ").append(lesson.getContent()).append("\n");
        }
        builder.append("\nConsolidate these lessons into the memory file.");
        return builder.toString();
    }

    /** Structured output schema for the consolidation plan. */
    public static JsonObject getResponseSchema() {
        JsonObject contentProperty = new JsonObject();
        contentProperty.addProperty("type", "string");
        contentProperty.addProperty("description",
                "The complete updated MEMORY.md content (markdown)");

        JsonObject idArrayProperty = new JsonObject();
        idArrayProperty.addProperty("type", "array");
        JsonObject idItem = new JsonObject();
        idItem.addProperty("type", "string");
        idArrayProperty.add("items", idItem);

        JsonObject summaryProperty = new JsonObject();
        summaryProperty.addProperty("type", "string");
        summaryProperty.addProperty("description",
                "One sentence describing what changed in this consolidation");

        JsonObject properties = new JsonObject();
        properties.add("updated_memory", contentProperty);
        properties.add("consumed_ids", idArrayProperty);
        properties.add("rejected_ids", idArrayProperty);
        properties.add("summary", summaryProperty);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("updated_memory");
        required.add("consumed_ids");
        required.add("rejected_ids");
        required.add("summary");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }
}