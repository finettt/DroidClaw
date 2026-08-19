package io.finett.droidclaw.agent;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import io.finett.droidclaw.model.Lesson;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.repository.MemoryRepository;

public class MemoryContextBuilder {
    private static final String TAG = "MemoryContextBuilder";

    /** Max fresh lessons injected into a conversation. */
    private static final int MAX_LESSONS = 10;
    /** Approximate size cap of the lessons section. */
    private static final int MAX_LESSONS_CHARS = 1024;

    private final MemoryRepository memoryRepository;
    private final LessonRepository lessonRepository; // nullable — injection is skipped when absent

    public MemoryContextBuilder(MemoryRepository memoryRepository) {
        this(memoryRepository, null);
    }

    public MemoryContextBuilder(MemoryRepository memoryRepository,
                                LessonRepository lessonRepository) {
        this.memoryRepository = memoryRepository;
        this.lessonRepository = lessonRepository;
    }

    public String buildMemoryContext() {
        StringBuilder context = new StringBuilder();

        try {
            String longTerm = memoryRepository.readLongTermMemory();
            if (!longTerm.isEmpty()) {
                context.append("# Long-term Memory\n\n");
                context.append(longTerm.trim()).append("\n\n");
                Log.d(TAG, "Loaded long-term memory: " + longTerm.length() + " chars");
            }

            String lessons = buildLessonsSection();
            if (!lessons.isEmpty()) {
                context.append(lessons);
            }

            String today = memoryRepository.readTodayNote();
            if (!today.isEmpty()) {
                context.append("# Today's Context\n\n");
                context.append(today.trim()).append("\n\n");
                Log.d(TAG, "Loaded today's note: " + today.length() + " chars");
            }

            String yesterday = memoryRepository.readYesterdayNote();
            if (!yesterday.isEmpty()) {
                context.append("# Yesterday's Context\n\n");
                context.append(yesterday.trim()).append("\n\n");
                Log.d(TAG, "Loaded yesterday's note: " + yesterday.length() + " chars");
            }

            if (context.length() == 0) {
                Log.d(TAG, "No memory context available");
                return "";
            }

            String wrapped = "--- MEMORY CONTEXT ---\n\n" + context.toString() + "--- END MEMORY CONTEXT ---\n";
            Log.d(TAG, "Built memory context: " + wrapped.length() + " total chars");
            return wrapped;

        } catch (IOException e) {
            Log.e(TAG, "Failed to build memory context", e);
            return "";
        }
    }

    /**
     * Fresh (unconsumed) lessons, most recent first. They stay visible to the
     * agent immediately after capture, until consolidation absorbs them into
     * long-term memory. Never throws — capture failures must not break context.
     */
    private String buildLessonsSection() {
        if (lessonRepository == null) {
            return "";
        }
        try {
            List<Lesson> lessons = lessonRepository.readUnconsumed();
            if (lessons.isEmpty()) {
                return "";
            }

            StringBuilder section = new StringBuilder("# Lessons\n\n");
            int included = 0;
            for (Lesson lesson : lessons) {
                if (included >= MAX_LESSONS) {
                    break;
                }
                String line = "- [" + lesson.getCategory() + "] "
                        + lesson.getContent() + "\n";
                if (section.length() + line.length() > MAX_LESSONS_CHARS) {
                    break;
                }
                section.append(line);
                included++;
            }

            if (included == 0) {
                return "";
            }
            section.append("\n");
            Log.d(TAG, "Injected " + included + " fresh lesson(s)");
            return section.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to load lessons for context", e);
            return "";
        }
    }

    public boolean hasMemory() {
        try {
            boolean hasLongTerm = memoryRepository.longTermMemoryExists();
            boolean hasToday = !memoryRepository.readTodayNote().isEmpty();
            boolean hasYesterday = !memoryRepository.readYesterdayNote().isEmpty();

            return hasLongTerm || hasToday || hasYesterday;
        } catch (IOException e) {
            Log.e(TAG, "Failed to check memory existence", e);
            return false;
        }
    }
}