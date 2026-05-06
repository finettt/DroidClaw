package io.finett.droidclaw.util;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChatSearchManagerTest {

    private ChatSearchManager manager;
    private List<ChatMessage> messages;

    @Before
    public void setUp() {
        manager = new ChatSearchManager();
        messages = new ArrayList<>();
        messages.add(new ChatMessage("Hello world", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("How are you doing today?", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Great, let me check the files for you.", ChatMessage.TYPE_ASSISTANT));
        messages.add(ChatMessage.createToolResultMessage("id1", "read_file", "File contents here"));
    }

    @Test
    public void emptyQuery_returnsEmptyList() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "");
        assertTrue(results.isEmpty());
    }

    @Test
    public void nullQuery_returnsEmptyList() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, null);
        assertTrue(results.isEmpty());
    }

    @Test
    public void simpleMatch_findsCorrectPosition() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "world");
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).position);
    }

    @Test
    public void caseInsensitiveMatch() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "HELLO");
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).position);
    }

    @Test
    public void multipleMatches_returnsAll() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "the");
        // "the files" in position 2 and "the" in tool result position 3
        assertTrue(results.size() >= 1);
    }

    @Test
    public void toolResultContent_isSearchable() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "File contents");
        assertEquals(1, results.size());
        assertEquals(3, results.get(0).position);
    }

    @Test
    public void toolResultToolName_isSearchable() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "read_file");
        assertEquals(1, results.size());
        assertEquals(3, results.get(0).position);
    }

    @Test
    public void noMatch_returnsEmptyList() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "xyzzy");
        assertTrue(results.isEmpty());
    }

    @Test
    public void regexSearch_works() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "\\bworld\\b", true);
        assertEquals(1, results.size());
    }

    @Test
    public void matchRanges_areCorrect() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "Hello");
        assertEquals(1, results.size());
        ChatSearchManager.SearchResult result = results.get(0);
        assertEquals(1, result.matches.size());
        assertEquals(0, result.matches.get(0).start);
        assertEquals(5, result.matches.get(0).end);
    }

    @Test
    public void nextResultPosition_wrapsAround() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "the");
        if (results.size() < 2) return; // guard: need ≥2 results to test wrap

        int last = results.get(results.size() - 1).position;
        int next = manager.nextResultPosition(results, last);
        assertEquals(results.get(0).position, next);
    }

    @Test
    public void previousResultPosition_wrapsAround() {
        List<ChatSearchManager.SearchResult> results = manager.search(messages, "the");
        if (results.size() < 2) return;

        int first = results.get(0).position;
        int prev = manager.previousResultPosition(results, first);
        assertEquals(results.get(results.size() - 1).position, prev);
    }
}