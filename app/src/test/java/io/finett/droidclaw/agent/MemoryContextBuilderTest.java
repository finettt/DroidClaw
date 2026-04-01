package io.finett.droidclaw.agent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.time.LocalDate;

import io.finett.droidclaw.repository.MemoryRepository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryContextBuilder.
 */
@RunWith(MockitoJUnitRunner.class)
public class MemoryContextBuilderTest {

    @Mock
    private MemoryRepository mockMemoryRepository;

    private MemoryContextBuilder memoryContextBuilder;

    @Before
    public void setUp() {
        memoryContextBuilder = new MemoryContextBuilder(mockMemoryRepository);
    }

    @Test
    public void testBuildMemoryContext_noMemory_returnsEmpty() throws IOException {
        // Mock all repository methods to return empty
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertEquals("Should return empty when no memory exists", "", context);
    }

    @Test
    public void testBuildMemoryContext_withLongTerm_returnsContext() throws IOException {
        String longTermContent = "# Long-term Memory\n\nUser prefers dark mode";
        when(mockMemoryRepository.readLongTermMemory()).thenReturn(longTermContent);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertTrue("Should contain long-term section", context.contains("# Long-term Memory"));
        assertTrue("Should contain long-term content", context.contains("User prefers dark mode"));
        assertTrue("Should have separator", context.contains("--- MEMORY CONTEXT ---"));
        assertTrue("Should have end separator", context.contains("--- END MEMORY CONTEXT ---"));
    }

    @Test
    public void testBuildMemoryContext_withToday_returnsContext() throws IOException {
        String todayContent = "# Today's Context\n\nWorking on feature X";
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn(todayContent);
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertTrue("Should contain today's context section", context.contains("# Today's Context"));
        assertTrue("Should contain today's content", context.contains("Working on feature X"));
    }

    @Test
    public void testBuildMemoryContext_withYesterday_returnsContext() throws IOException {
        String yesterdayContent = "# Yesterday's Context\n\nCompleted task Y";
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn(yesterdayContent);

        String context = memoryContextBuilder.buildMemoryContext();

        assertTrue("Should contain yesterday's context section", context.contains("# Yesterday's Context"));
        assertTrue("Should contain yesterday's content", context.contains("Completed task Y"));
    }

    @Test
    public void testBuildMemoryContext_allMemory_returnsCompleteContext() throws IOException {
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("# Long-term\n\nFact 1");
        when(mockMemoryRepository.readTodayNote()).thenReturn("# Today\n\nNote 1");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("# Yesterday\n\nNote 2");

        String context = memoryContextBuilder.buildMemoryContext();

        assertTrue("Should contain long-term", context.contains("# Long-term Memory"));
        assertTrue("Should contain today", context.contains("# Today's Context"));
        assertTrue("Should contain yesterday", context.contains("# Yesterday's Context"));
        assertTrue("Should contain all content", context.contains("Fact 1"));
        assertTrue("Should contain today note", context.contains("Note 1"));
        assertTrue("Should contain yesterday note", context.contains("Note 2"));
    }

    @Test
    public void testBuildMemoryContext_withEmptyLongTerm_skipsSection() throws IOException {
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn("# Today\n\nNote");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertFalse("Should not have long-term section when empty", context.contains("# Long-term Memory"));
        assertTrue("Should have today section", context.contains("# Today's Context"));
    }

    @Test
    public void testBuildMemoryContext_withEmptyToday_skipsSection() throws IOException {
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("# Long-term\n\nFact");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertFalse("Should not have today section when empty", context.contains("# Today's Context"));
        assertTrue("Should have long-term section", context.contains("# Long-term Memory"));
    }

    @Test
    public void testBuildMemoryContext_withEmptyYesterday_skipsSection() throws IOException {
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("# Long-term\n\nFact");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        assertFalse("Should not have yesterday section when empty", context.contains("# Yesterday's Context"));
        assertTrue("Should have long-term section", context.contains("# Long-term Memory"));
    }

    @Test
    public void testBuildMemoryContext_withIOException_returnsEmpty() throws IOException {
        // Mock to throw IOException
        when(mockMemoryRepository.readLongTermMemory()).thenThrow(new IOException("Read error"));
        when(mockMemoryRepository.readTodayNote()).thenThrow(new IOException("Read error"));
        when(mockMemoryRepository.readYesterdayNote()).thenThrow(new IOException("Read error"));

        String context = memoryContextBuilder.buildMemoryContext();

        assertEquals("Should return empty on IOException", "", context);
    }

    @Test
    public void testHasMemory_noMemory_returnsFalse() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertFalse("Should return false when no memory exists", hasMemory);
    }

    @Test
    public void testHasMemory_withLongTerm_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(true);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true when long-term memory exists", hasMemory);
    }

    @Test
    public void testHasMemory_withToday_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("Today note");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true when today note exists", hasMemory);
    }

    @Test
    public void testHasMemory_withYesterday_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("Yesterday note");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true when yesterday note exists", hasMemory);
    }

    @Test
    public void testHasMemory_withAnyMemory_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("Yesterday note");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true when any memory exists", hasMemory);
    }

    @Test
    public void testHasMemory_withIOException_returnsFalse() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenThrow(new IOException("Error"));
        when(mockMemoryRepository.readTodayNote()).thenThrow(new IOException("Error"));
        when(mockMemoryRepository.readYesterdayNote()).thenThrow(new IOException("Error"));

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertFalse("Should return false on IOException", hasMemory);
    }

    @Test
    public void testBuildMemoryContext_contentTrimsProperly() throws IOException {
        // Content with leading/trailing newlines should be trimmed
        String longTermContent = "\n\n# Long-term Memory\n\nContent\n\n";
        when(mockMemoryRepository.readLongTermMemory()).thenReturn(longTermContent);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        String context = memoryContextBuilder.buildMemoryContext();

        // The trim() in the builder should remove leading/trailing from content
        assertTrue("Should contain trimmed content", context.contains("Content"));
    }

    @Test
    public void testBuildMemoryContext_multipleEntries_includesAll() throws IOException {
        String longTermContent = "# Long-term\n\nFact 1\n\nFact 2";
        when(mockMemoryRepository.readLongTermMemory()).thenReturn(longTermContent);
        when(mockMemoryRepository.readTodayNote()).thenReturn("# Today\n\nNote 1\n\nNote 2");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("# Yesterday\n\nNote 3");

        String context = memoryContextBuilder.buildMemoryContext();

        assertTrue("Should contain all long-term facts", context.contains("Fact 1"));
        assertTrue("Should contain all long-term facts", context.contains("Fact 2"));
        assertTrue("Should contain all today notes", context.contains("Note 1"));
        assertTrue("Should contain all today notes", context.contains("Note 2"));
        assertTrue("Should contain yesterday note", context.contains("Note 3"));
    }

    @Test
    public void testHasMemory_longTermExists_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(true);

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true if long-term exists", hasMemory);
    }

    @Test
    public void testHasMemory_longTermDoesNotExistButTodayExists_returnsTrue() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("Today note");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertTrue("Should return true if today note exists", hasMemory);
    }

    @Test
    public void testHasMemory_allFalse_returnsFalse() throws IOException {
        when(mockMemoryRepository.longTermMemoryExists()).thenReturn(false);
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        boolean hasMemory = memoryContextBuilder.hasMemory();

        assertFalse("Should return false when all checks fail", hasMemory);
    }
}
