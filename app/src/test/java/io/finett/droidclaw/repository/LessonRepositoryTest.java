package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.model.Lesson;

/**
 * Round-trip tests for the JSONL lesson store: append, read, markConsumed, prune.
 */
public class LessonRepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LessonRepository repo;

    @Before
    public void setUp() throws Exception {
        repo = new LessonRepository(new File(tmp.getRoot(), "lessons"));
    }

    private Lesson lesson(String content) {
        return new Lesson("session-1", Lesson.CATEGORY_USER_PREFERENCE, content,
                "evidence", Lesson.SCOPE_MEMORY, Lesson.SOURCE_REFLECTION, 2);
    }

    @Test
    public void appendAndReadUnconsumed_roundTrip() throws Exception {
        repo.appendLesson(lesson("always use UTC"));
        repo.appendLesson(lesson("reply in Russian"));

        List<Lesson> lessons = repo.readUnconsumed();
        assertEquals(2, lessons.size());
        assertEquals("always use UTC", lessons.get(0).getContent());
        assertFalse(lessons.get(0).isConsumed());
        assertTrue(lessons.get(0).getId() != null && !lessons.get(0).getId().isEmpty());
    }

    @Test
    public void readUnconsumed_skipsConsumedLessons() throws Exception {
        Lesson first = lesson("first");
        repo.appendLesson(first);
        repo.appendLesson(lesson("second"));

        repo.markConsumed(Collections.singletonList(first.getId()));

        List<Lesson> remaining = repo.readUnconsumed();
        assertEquals(1, remaining.size());
        assertEquals("second", remaining.get(0).getContent());
    }

    @Test
    public void markConsumed_emptyAndUnknownIds_areNoOps() throws Exception {
        repo.appendLesson(lesson("keep me"));

        repo.markConsumed(null);
        repo.markConsumed(Collections.emptyList());
        repo.markConsumed(Collections.singletonList("no-such-id"));

        assertEquals(1, repo.readUnconsumed().size());
    }

    @Test
    public void readRecent_filtersByDayWindow() throws Exception {
        repo.appendLesson(lesson("today lesson"));

        assertEquals(1, repo.readRecent(7, false).size());
        assertEquals(1, repo.readRecent(1, false).size());
    }

    @Test
    public void readRecent_includeConsumed_returnsAll() throws Exception {
        Lesson first = lesson("consumed one");
        repo.appendLesson(first);
        repo.appendLesson(lesson("fresh one"));
        repo.markConsumed(Collections.singletonList(first.getId()));

        assertEquals(1, repo.readRecent(7, false).size());
        assertEquals(2, repo.readRecent(7, true).size());
    }

    @Test
    public void pruneConsumed_keepsFreshConsumedLessons() throws Exception {
        Lesson fresh = lesson("fresh consumed");
        repo.appendLesson(fresh);
        repo.markConsumed(Collections.singletonList(fresh.getId()));

        repo.pruneConsumed();

        // Fresh consumed lessons are within the retention window — kept.
        assertEquals(1, repo.readRecent(7, true).size());
    }

    @Test
    public void readUnconsumed_emptyDir_returnsEmptyList() {
        assertTrue(repo.readUnconsumed().isEmpty());
    }

    @Test
    public void malformedLines_areSkipped() throws Exception {
        repo.appendLesson(lesson("valid"));
        File dir = repo.getLessonsDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jsonl"));
        assertEquals(1, files.length);

        // Append garbage directly, then verify reads survive it.
        java.io.FileWriter writer = new java.io.FileWriter(files[0], true);
        writer.write("{not json\n");
        writer.close();

        List<Lesson> lessons = repo.readUnconsumed();
        assertEquals(1, lessons.size());
        assertEquals("valid", lessons.get(0).getContent());
    }

    @Test
    public void markConsumed_acrossMultipleIds() throws Exception {
        Lesson a = lesson("a");
        Lesson b = lesson("b");
        Lesson c = lesson("c");
        repo.appendLesson(a);
        repo.appendLesson(b);
        repo.appendLesson(c);

        repo.markConsumed(Arrays.asList(a.getId(), c.getId()));

        List<Lesson> remaining = repo.readUnconsumed();
        assertEquals(1, remaining.size());
        assertEquals("b", remaining.get(0).getContent());
    }
}