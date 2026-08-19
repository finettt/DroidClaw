package io.finett.droidclaw.repository;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.finett.droidclaw.model.Lesson;

/**
 * Append-only JSONL store for extracted lessons, one file per UTC day:
 * {@code .agent/memory/lessons/YYYY-MM-DD.jsonl}.
 *
 * Lessons stay unconsumed until the consolidation pass absorbs them into
 * long-term memory; they are injected into conversations while unconsumed.
 */
public class LessonRepository {

    private static final String TAG = "LessonRepository";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Hard cap of lessons recorded per single run (enforced by callers). */
    public static final int MAX_LESSONS_PER_RUN = 10;

    /** Consumed lessons older than this are pruned during consolidation. */
    public static final long RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000;

    private final File lessonsDir;
    private final Gson gson = new Gson();

    public LessonRepository(File lessonsDir) {
        this.lessonsDir = lessonsDir;
        if (!lessonsDir.exists()) {
            lessonsDir.mkdirs();
        }
    }

    public synchronized void appendLesson(Lesson lesson) throws IOException {
        File dayFile = fileForDay(LocalDate.now(ZoneOffset.UTC));
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(dayFile, true), StandardCharsets.UTF_8))) {
            writer.write(gson.toJson(lesson));
            writer.newLine();
        }
    }

    /** All unconsumed lessons, most recent day first, newest within day first. */
    public synchronized List<Lesson> readUnconsumed() {
        List<Lesson> result = new ArrayList<>();
        for (File file : listDayFilesDescending()) {
            for (Lesson lesson : readLessons(file)) {
                if (!lesson.isConsumed()) {
                    result.add(lesson);
                }
            }
        }
        return result;
    }

    /**
     * Lessons from the last {@code days} UTC days.
     *
     * @param includeConsumed whether to also return already-consumed lessons
     */
    public synchronized List<Lesson> readRecent(int days, boolean includeConsumed) {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        List<Lesson> result = new ArrayList<>();
        for (File file : listDayFilesDescending()) {
            LocalDate day = parseDay(file.getName());
            if (day == null || day.isBefore(cutoff)) {
                continue;
            }
            for (Lesson lesson : readLessons(file)) {
                if (includeConsumed || !lesson.isConsumed()) {
                    result.add(lesson);
                }
            }
        }
        return result;
    }

    /** Marks the given lesson ids as consumed by rewriting their day files. */
    public synchronized void markConsumed(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<String> idSet = new HashSet<>(ids);
        for (File file : listDayFilesDescending()) {
            List<Lesson> lessons = readLessons(file);
            boolean changed = false;
            for (Lesson lesson : lessons) {
                if (idSet.contains(lesson.getId()) && !lesson.isConsumed()) {
                    lesson.setConsumed(true);
                    changed = true;
                }
            }
            if (changed) {
                writeLessons(file, lessons);
            }
        }
    }

    /**
     * Deletes consumed lessons older than the retention window.
     * Day files left empty are removed.
     */
    public synchronized void pruneConsumed() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        for (File file : listDayFilesDescending()) {
            List<Lesson> lessons = readLessons(file);
            List<Lesson> kept = new ArrayList<>();
            for (Lesson lesson : lessons) {
                if (!(lesson.isConsumed() && lesson.getTimestamp() < cutoff)) {
                    kept.add(lesson);
                }
            }
            if (kept.size() != lessons.size()) {
                if (kept.isEmpty()) {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete empty lesson file: " + file.getName());
                    }
                } else {
                    writeLessons(file, kept);
                }
            }
        }
    }

    public File getLessonsDir() {
        return lessonsDir;
    }

    private List<File> listDayFilesDescending() {
        File[] files = lessonsDir.listFiles((dir, name) -> name.endsWith(".jsonl"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        Collections.sort(sorted, (a, b) -> b.getName().compareTo(a.getName()));
        return sorted;
    }

    private List<Lesson> readLessons(File file) {
        List<Lesson> lessons = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Lesson lesson = gson.fromJson(line, Lesson.class);
                    if (lesson != null && lesson.getId() != null && lesson.getContent() != null) {
                        lessons.add(lesson);
                    }
                } catch (JsonSyntaxException e) {
                    Log.w(TAG, "Skipping malformed lesson line in " + file.getName());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read lessons file: " + file.getName(), e);
        }
        return lessons;
    }

    private void writeLessons(File file, List<Lesson> lessons) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (Lesson lesson : lessons) {
                writer.write(gson.toJson(lesson));
                writer.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write lessons file: " + file.getName(), e);
        }
    }

    private File fileForDay(LocalDate day) {
        return new File(lessonsDir, day.format(DAY_FORMAT) + ".jsonl");
    }

    private LocalDate parseDay(String filename) {
        try {
            return LocalDate.parse(filename.replace(".jsonl", ""), DAY_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}