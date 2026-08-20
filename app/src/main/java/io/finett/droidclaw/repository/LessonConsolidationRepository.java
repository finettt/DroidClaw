package io.finett.droidclaw.repository;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only journal of lesson consolidation runs
 * (self-improvement layer ②). One JSON object per line:
 * {"timestamp": ms, "consumed": [ids], "rejected": [ids], "summary": "..."}
 */
public class LessonConsolidationRepository {

    private static final String TAG = "LessonConsolidationRepo";

    private final File journalFile;

    public LessonConsolidationRepository(File lessonsDir) {
        this.journalFile = new File(lessonsDir, "lesson-consolidations.jsonl");
        if (!lessonsDir.exists()) {
            boolean created = lessonsDir.mkdirs();
            if (created) {
                Log.d(TAG, "Created lessons directory: " + lessonsDir.getAbsolutePath());
            }
        }
    }

    public synchronized void appendConsolidation(long timestampMillis,
                                                 List<String> consumedIds,
                                                 List<String> rejectedIds,
                                                 String summary) throws IOException {
        JSONObject entry = new JSONObject();
        try {
            entry.put("timestamp", timestampMillis);
            entry.put("consumed", new JSONArray(consumedIds));
            entry.put("rejected", new JSONArray(rejectedIds));
            entry.put("summary", summary);
        } catch (Exception e) {
            throw new IOException("Failed to build journal entry", e);
        }

        FileWriter writer = new FileWriter(journalFile, true);
        try {
            writer.write(entry.toString());
            writer.write('\n');
        } finally {
            writer.close();
        }
        Log.d(TAG, "Appended consolidation journal entry");
    }

    /**
     * Journal entries from the last {@code days} days, most recent first.
     * Malformed lines are skipped.
     */
    public synchronized List<JSONObject> readRecent(int days) {
        List<JSONObject> result = new ArrayList<>();
        if (!journalFile.exists()) {
            return result;
        }

        long cutoffMillis = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(journalFile), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject entry = new JSONObject(line);
                    if (entry.optLong("timestamp", 0L) >= cutoffMillis) {
                        result.add(entry);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Skipping malformed journal line");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read consolidation journal", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

        result.sort((a, b) -> Long.compare(b.optLong("timestamp"), a.optLong("timestamp")));
        return result;
    }
}