package io.finett.droidclaw.agent;

import android.content.Context;
import android.content.res.AssetManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the memory system components working together.
 * Tests the full flow of memory loading, summarization, and persistence.
 */
@RunWith(MockitoJUnitRunner.class)
public class MemorySystemIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private Context mockContext;

    @Mock
    private AssetManager mockAssets;

    @Mock
    private LlmApiService mockApiService;

    @Mock
    private ToolRegistry mockToolRegistry;

    @Mock
    private AgentLoop.AgentCallback mockCallback;

    private File workspaceRoot;
    private File memoryDir;
    private MemoryRepository memoryRepository;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = tempFolder.newFolder("workspace");
        memoryDir = new File(workspaceRoot, ".agent/memory");
        memoryDir.mkdirs();

        // Mock WorkspaceManager
        io.finett.droidclaw.filesystem.WorkspaceManager mockWorkspaceManager = mock(io.finett.droidclaw.filesystem.WorkspaceManager.class);
        when(mockWorkspaceManager.getMemoryDirectory()).thenReturn(memoryDir);
        memoryRepository = new MemoryRepository(mockWorkspaceManager);
    }

    @Test
    public void testFullConversationWithMemory_workflow() throws IOException {
        // Setup: Create initial memory files
        createMemoryFile("MEMORY.md", "# Long-term Memory\n\nUser prefers dark mode");
        createTodayNote("Working on feature X");

        // Create conversation with initial message
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        // Build memory context
        MemoryContextBuilder contextBuilder = new MemoryContextBuilder(memoryRepository);
        String memoryContext = contextBuilder.buildMemoryContext();

        assertTrue("Memory context should be built", memoryContext.contains("Long-term Memory"));
        assertTrue("Memory context should contain user preference", memoryContext.contains("User prefers dark mode"));
        assertTrue("Memory context should contain today's note", memoryContext.contains("Working on feature X"));
    }

    @Test
    public void testMemoryPersistence_acrossSimulatedSessions() throws IOException {
        // Session 1: Create initial memory
        memoryRepository.appendToLongTermMemory("User is a developer");
        memoryRepository.appendToDailyNote("Started working on project A");

        // Verify memory was saved
        String longTerm = memoryRepository.readLongTermMemory();
        assertTrue("Long-term memory should persist", longTerm.contains("User is a developer"));

        String today = memoryRepository.readTodayNote();
        assertTrue("Today's note should persist", today.contains("Started working on project A"));

        // Session 2: Load memory (simulated by reading from files again)
        String loadedLongTerm = memoryRepository.readLongTermMemory();
        String loadedToday = memoryRepository.readTodayNote();

        assertEquals("Long-term memory should be same", longTerm, loadedLongTerm);
        assertEquals("Today's note should be same", today, loadedToday);
    }

    @Test
    public void testSummarizationFlow_withRealisticConversation() throws IOException {
        // Setup: Create a realistic conversation with many messages
        List<ChatMessage> conversation = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            conversation.add(new ChatMessage("User message " + i, ChatMessage.TYPE_USER));
            conversation.add(new ChatMessage("Assistant response " + i, ChatMessage.TYPE_ASSISTANT));
        }

        // Create summarizer
        ConversationSummarizer summarizer = new ConversationSummarizer(mockApiService, memoryRepository);

        // Mock LLM to return a summary
        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools callback = invocation.getArgument(3);
            callback.onSuccess(new LlmApiService.LlmResponse("Summary: User asked about various topics", null));
            return null;
        }).when(mockApiService).sendMessageWithTools(any(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        // Check if summarization needed
        assertTrue("Should need summarization for large conversation", summarizer.needsSummarization(conversation));

        // Perform summarization
        summarizer.summarizeAndSave(conversation, new ConversationSummarizer.SummarizeCallback() {
            @Override
            public void onResult(List<ChatMessage> compressedHistory) {
                // Verify compressed history has fewer messages
                assertTrue("Compressed history should be smaller", compressedHistory.size() < conversation.size());
            }

            @Override
            public void onError(Throwable error) {
                fail("Summarization should succeed: " + error.getMessage());
            }
        });

        // Verify summary was saved to daily note
        String todayNote = memoryRepository.readTodayNote();
        assertTrue("Daily note should contain summary", todayNote.contains("Conversation Summary"));
        assertTrue("Daily note should contain LLM summary", todayNote.contains("Summary:"));
    }

    @Test
    public void testMemoryContextInjectedIntoLLMRequest() throws IOException {
        // Setup: Create memory files
        memoryRepository.appendToLongTermMemory("User likes Java");
        createTodayNote("Debugging issue #123");

        // Setup AgentLoop with memory
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(memoryRepository);
        AgentLoop agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, memoryContext);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new com.google.gson.JsonArray());

        // Capture the context messages passed to LLM
        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools callback = invocation.getArgument(3);

            // Get the context messages argument
            List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);

            // Verify memory context is included
            boolean foundMemory = false;
            for (ChatMessage msg : contextMessages) {
                if (msg.isSystem() && msg.getContent().contains("MEMORY CONTEXT")) {
                    foundMemory = true;
                    break;
                }
            }
            assertTrue("Memory context should be in context messages", foundMemory);

            callback.onSuccess(new LlmApiService.LlmResponse("Response", null));
            return null;
        }).when(mockApiService).sendMessageWithTools(any(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(anyString(), anyList());
    }

    @Test
    public void testYesterdayNote_creationAndLoading() throws IOException {
        // Create yesterday's note
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Yesterday's Context\n\nCompleted task Y");
        writer.close();

        // Verify it was created with correct header
        String content = memoryRepository.readYesterdayNote();
        assertTrue("Yesterday note should have header", content.contains("# Yesterday's Context"));
        assertTrue("Yesterday note should have content", content.contains("Completed task Y"));
    }

    @Test
    public void testMultipleDailyNotes_sortedCorrectly() throws IOException {
        // Create multiple daily notes
        createDailyNoteFile(LocalDate.of(2025, 12, 1), "Note 1");
        createDailyNoteFile(LocalDate.of(2025, 12, 5), "Note 5");
        createDailyNoteFile(LocalDate.of(2025, 12, 10), "Note 10");

        // Get all daily notes
        List<File> notes = memoryRepository.getAllDailyNotes();

        assertEquals("Should have 3 notes", 3, notes.size());

        // Verify sorted by date (newest first)
        String firstDate = notes.get(0).getName().replace(".md", "");
        assertEquals("First note should be newest", "2025-12-10", firstDate);

        String secondDate = notes.get(1).getName().replace(".md", "");
        assertEquals("Second note should be middle", "2025-12-05", secondDate);

        String thirdDate = notes.get(2).getName().replace(".md", "");
        assertEquals("Third note should be oldest", "2025-12-01", thirdDate);
    }

    @Test
    public void testMemoryContextBuilder_emptyContext_forNewUser() {
        // For a new user with no memory files
        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);

        String context = builder.buildMemoryContext();

        assertEquals("New user should have empty context", "", context);
        assertFalse("New user should not have memory", builder.hasMemory());
    }

    @Test
    public void testMemoryContextBuilder_allMemory_types_included() throws IOException {
        // Create all types of memory
        memoryRepository.appendToLongTermMemory("Fact 1");
        memoryRepository.appendToLongTermMemory("Fact 2");
        createTodayNote("Today's note");
        createYesterdayNote("Yesterday's note");

        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);

        String context = builder.buildMemoryContext();

        assertTrue("Should contain long-term memory", context.contains("# Long-term Memory"));
        assertTrue("Should contain today's context", context.contains("# Today's Context"));
        assertTrue("Should contain yesterday's context", context.contains("# Yesterday's Context"));
        assertTrue("Should contain all facts", context.contains("Fact 1"));
        assertTrue("Should contain all facts", context.contains("Fact 2"));
        assertTrue("Should contain today note", context.contains("Today's note"));
        assertTrue("Should contain yesterday note", context.contains("Yesterday's note"));
    }

    @Test
    public void testTokenThreshold_constant() {
        ConversationSummarizer summarizer = new ConversationSummarizer(mockApiService, memoryRepository);

        assertEquals("Token threshold should be 3000", 3000, summarizer.getTokenThreshold());
    }

    @Test
    public void testSummarization_withVeryLargeConversation() throws IOException {
        // Create a very large conversation (50 messages, ~10000 tokens)
        List<ChatMessage> conversation = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            conversation.add(new ChatMessage("User message " + i + " with more content", ChatMessage.TYPE_USER));
            conversation.add(new ChatMessage("Assistant response " + i + " with more content", ChatMessage.TYPE_ASSISTANT));
        }

        ConversationSummarizer summarizer = new ConversationSummarizer(mockApiService, memoryRepository);

        // Should need summarization
        assertTrue("Should need summarization", summarizer.needsSummarization(conversation));

        // Mock LLM response
        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools callback = invocation.getArgument(3);
            callback.onSuccess(new LlmApiService.LlmResponse("Comprehensive summary", null));
            return null;
        }).when(mockApiService).sendMessageWithTools(any(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        summarizer.summarizeAndSave(conversation, new ConversationSummarizer.SummarizeCallback() {
            @Override
            public void onResult(List<ChatMessage> compressedHistory) {
                // Should keep only a few recent messages
                assertTrue("Should compress to few messages", compressedHistory.size() <= 10);
            }

            @Override
            public void onError(Throwable error) {
                fail("Should not error: " + error.getMessage());
            }
        });
    }

    // Helper methods

    private void createMemoryFile(String filename, String content) throws IOException {
        File file = new File(memoryDir, filename);
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(content);
        writer.close();
    }

    private void createTodayNote(String content) throws IOException {
        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File todayFile = new File(memoryDir, todayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(todayFile);
        writer.write("# Daily Notes - " + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + "\n\n");
        writer.write(content);
        writer.close();
    }

    private void createYesterdayNote(String content) throws IOException {
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Daily Notes - " + LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + "\n\n");
        writer.write(content);
        writer.close();
    }

    private void createDailyNoteFile(LocalDate date, String content) throws IOException {
        String filename = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File file = new File(memoryDir, filename);
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write("# Daily Notes - " + date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + "\n\n");
        writer.write(content);
        writer.close();
    }
}
