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

        io.finett.droidclaw.filesystem.WorkspaceManager mockWorkspaceManager = mock(io.finett.droidclaw.filesystem.WorkspaceManager.class);
        when(mockWorkspaceManager.getMemoryDirectory()).thenReturn(memoryDir);
        memoryRepository = new MemoryRepository(mockWorkspaceManager);
    }

    @Test
    public void testFullConversationWithMemory_workflow() throws IOException {
        createMemoryFile("MEMORY.md", "# Long-term Memory\n\nUser prefers dark mode");
        createTodayNote("Working on feature X");

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        MemoryContextBuilder contextBuilder = new MemoryContextBuilder(memoryRepository);
        String memoryContext = contextBuilder.buildMemoryContext();

        assertTrue("Memory context should be built", memoryContext.contains("Long-term Memory"));
        assertTrue("Memory context should contain user preference", memoryContext.contains("User prefers dark mode"));
        assertTrue("Memory context should contain today's note", memoryContext.contains("Working on feature X"));
    }

    @Test
    public void testMemoryPersistence_acrossSimulatedSessions() throws IOException {
        memoryRepository.appendToLongTermMemory("User is a developer");
        memoryRepository.appendToDailyNote("Started working on project A");

        String longTerm = memoryRepository.readLongTermMemory();
        assertTrue("Long-term memory should persist", longTerm.contains("User is a developer"));

        String today = memoryRepository.readTodayNote();
        assertTrue("Today's note should persist", today.contains("Started working on project A"));

        String loadedLongTerm = memoryRepository.readLongTermMemory();
        String loadedToday = memoryRepository.readTodayNote();

        assertEquals("Long-term memory should be same", longTerm, loadedLongTerm);
        assertEquals("Today's note should be same", today, loadedToday);
    }

    @Test
    public void testSummarizationFlow_withRealisticConversation() throws IOException {
        List<ChatMessage> conversation = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            StringBuilder userMsg = new StringBuilder("User message " + i + " ");
            StringBuilder assistantMsg = new StringBuilder("Assistant response " + i + " ");
            for (int j = 0; j < 60; j++) {
                userMsg.append("word ");
                assistantMsg.append("word ");
            }
            conversation.add(new ChatMessage(userMsg.toString(), ChatMessage.TYPE_USER));
            conversation.add(new ChatMessage(assistantMsg.toString(), ChatMessage.TYPE_ASSISTANT));
        }

        ConversationSummarizer summarizer = new ConversationSummarizer(mockApiService, memoryRepository);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback callback = invocation.getArgument(2);
            callback.onSuccess("Summary: User asked about various topics");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        assertTrue("Should need summarization for large conversation", summarizer.needsSummarization(conversation));

        final boolean[] callbackCalled = {false};
        final List<ChatMessage>[] resultHolder = new List[1];
        summarizer.summarizeAndSave(conversation, new ConversationSummarizer.SummarizeCallback() {
            @Override
            public void onResult(List<ChatMessage> compressedHistory) {
                resultHolder[0] = compressedHistory;
                callbackCalled[0] = true;
            }

            @Override
            public void onError(Throwable error) {
                fail("Summarization should succeed: " + error.getMessage());
            }
        });

        try { Thread.sleep(100); } catch (InterruptedException e) { }
        
        assertTrue("Callback should have been called", callbackCalled[0]);
        assertNotNull("Result should not be null", resultHolder[0]);
        assertTrue("Compressed history should be smaller", resultHolder[0].size() < conversation.size());

        String todayNote = memoryRepository.readTodayNote();
        assertTrue("Daily note should contain summary", todayNote.contains("Conversation Summary"));
        assertTrue("Daily note should contain LLM summary", todayNote.contains("Summary:"));
    }

    @Test
    public void testMemoryContextInjectedIntoLLMRequest() throws IOException {
        memoryRepository.appendToLongTermMemory("User likes Java");
        createTodayNote("Debugging issue #123");

        MemoryContextBuilder memoryContext = new MemoryContextBuilder(memoryRepository);
        AgentLoop agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, memoryContext);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new com.google.gson.JsonArray());

        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools callback = invocation.getArgument(3);

            List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);

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
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Yesterday's Context\n\nCompleted task Y");
        writer.close();

        String content = memoryRepository.readYesterdayNote();
        assertTrue("Yesterday note should have header", content.contains("# Yesterday's Context"));
        assertTrue("Yesterday note should have content", content.contains("Completed task Y"));
    }

    @Test
    public void testMultipleDailyNotes_sortedCorrectly() throws IOException {
        createDailyNoteFile(LocalDate.of(2025, 12, 1), "Note 1");
        createDailyNoteFile(LocalDate.of(2025, 12, 5), "Note 5");
        createDailyNoteFile(LocalDate.of(2025, 12, 10), "Note 10");

        List<File> notes = memoryRepository.getAllDailyNotes();

        assertEquals("Should have 3 notes", 3, notes.size());

        String firstDate = notes.get(0).getName().replace(".md", "");
        assertEquals("First note should be newest", "2025-12-10", firstDate);

        String secondDate = notes.get(1).getName().replace(".md", "");
        assertEquals("Second note should be middle", "2025-12-05", secondDate);

        String thirdDate = notes.get(2).getName().replace(".md", "");
        assertEquals("Third note should be oldest", "2025-12-01", thirdDate);
    }

    @Test
    public void testMemoryContextBuilder_emptyContext_forNewUser() {
        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);

        String context = builder.buildMemoryContext();

        assertEquals("New user should have empty context", "", context);
        assertFalse("New user should not have memory", builder.hasMemory());
    }

    @Test
    public void testMemoryContextBuilder_allMemory_types_included() throws IOException {
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

        assertEquals("Token threshold should be 3072 (75% of 4096)", 3072, summarizer.getTokenThreshold());
    }

    @Test
    public void testSummarization_withVeryLargeConversation() throws IOException {
        List<ChatMessage> conversation = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            StringBuilder userMsg = new StringBuilder("User message " + i + " ");
            StringBuilder assistantMsg = new StringBuilder("Assistant response " + i + " ");
            for (int j = 0; j < 25; j++) {
                userMsg.append("extra ");
                assistantMsg.append("extra ");
            }
            conversation.add(new ChatMessage(userMsg.toString(), ChatMessage.TYPE_USER));
            conversation.add(new ChatMessage(assistantMsg.toString(), ChatMessage.TYPE_ASSISTANT));
        }

        ConversationSummarizer summarizer = new ConversationSummarizer(mockApiService, memoryRepository);

        assertTrue("Should need summarization", summarizer.needsSummarization(conversation));

        doAnswer(invocation -> {
            LlmApiService.ChatCallback callback = invocation.getArgument(2);
            callback.onSuccess("Comprehensive summary");
            return null;
        }).when(mockApiService).sendMessage(any(), any(), any(LlmApiService.ChatCallback.class));

        final boolean[] callbackCalled = {false};
        summarizer.summarizeAndSave(conversation, new ConversationSummarizer.SummarizeCallback() {
            @Override
            public void onResult(List<ChatMessage> compressedHistory) {
                assertTrue("Should compress to few messages", compressedHistory.size() <= 10);
                callbackCalled[0] = true;
            }

            @Override
            public void onError(Throwable error) {
                fail("Should not error: " + error.getMessage());
            }
        });

        try { Thread.sleep(100); } catch (InterruptedException e) { }
        
        assertTrue("Callback should have been called", callbackCalled[0]);
    }

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
