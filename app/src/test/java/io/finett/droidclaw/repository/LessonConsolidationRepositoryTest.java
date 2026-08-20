package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Round-trip tests for the consolidation journal (append + readRecent).
 * Robolectric is required for a functional org.json implementation.
 */
@RunWith(RobolectricTestRunner.class)
public class LessonConsolidationRepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LessonConsolidationRepository repo;

    @Before
    public void setUp() throws Exception {
        repo = new LessonConsolidationRepository(tmp.newFolder("lessons"));
    }

    @Test
    public void appendAndRead_recentEntryReturned() throws Exception {
        long now = System.currentTimeMillis();
        repo.appendConsolidation(now, Arrays.asList("id1", "id2"),
                Collections.singletonList("id3"), "merged 2 lessons");

        List<JSONObject> entries = repo.readRecent(7);
        assertEquals(1, entries.size());
        JSONObject entry = entries.get(0);
        assertEquals(now, entry.getLong("timestamp"));
        assertEquals(2, entry.getJSONArray("consumed").length());
        assertEquals(1, entry.getJSONArray("rejected").length());
        assertEquals("merged 2 lessons", entry.getString("summary"));
    }

    @Test
    public void readRecent_filtersOldEntries() throws Exception {
        long now = System.currentTimeMillis();
        repo.appendConsolidation(now - 30L * 24 * 60 * 60 * 1000,
                Collections.singletonList("old"), Collections.emptyList(), "old run");
        repo.appendConsolidation(now, Collections.singletonList("new"),
                Collections.emptyList(), "new run");

        List<JSONObject> entries = repo.readRecent(7);
        assertEquals(1, entries.size());
        assertEquals("new run", entries.get(0).getString("summary"));
    }

    @Test
    public void readRecent_mostRecentFirst() throws Exception {
        long now = System.currentTimeMillis();
        repo.appendConsolidation(now - 60_000, Collections.singletonList("a"),
                Collections.emptyList(), "first");
        repo.appendConsolidation(now, Collections.singletonList("b"),
                Collections.emptyList(), "second");

        List<JSONObject> entries = repo.readRecent(7);
        assertEquals(2, entries.size());
        assertEquals("second", entries.get(0).getString("summary"));
        assertEquals("first", entries.get(1).getString("summary"));
    }

    @Test
    public void readRecent_skipsMalformedLines() throws Exception {
        repo.appendConsolidation(System.currentTimeMillis(),
                Collections.singletonList("ok"), Collections.emptyList(), "valid");

        // Corrupt the file by appending garbage
        java.io.FileWriter writer = new java.io.FileWriter(
                new java.io.File(tmp.getRoot(), "lessons/lesson-consolidations.jsonl"), true);
        writer.write("{not json\n");
        writer.close();

        List<JSONObject> entries = repo.readRecent(7);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).has("summary"));
    }

    @Test
    public void readRecent_emptyWhenNoJournal() {
        assertTrue(repo.readRecent(7).isEmpty());
    }
}