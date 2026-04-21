package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.MemoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class ConversationSummarizerTest {

    private static final int DEFAULT_CONTEXT_WINDOW = 4096;
    private static final int TOKEN_THRESHOLD = (int) (DEFAULT_CONTEXT_WINDOW * 0.75); // 3072

    @Mock
    private LlmApiService mockApiService;

    @Mock
    private MemoryRepository mockMemoryRepository;

    private ConversationSummarizer summarizer;

    @Before
    public void setUp() {
        summarizer = new ConversationSummarizer(mockApiService, mockMemoryRepository);
    }

    @Test
    public void testNeedsSummarization_belowThreshold_returnsFalse() {
        List<ChatMessage> messages = createSmallMessages(5);

        boolean needs = summarizer.needsSummarization(messages);

        assertFalse("Should not need summarization below threshold", needs);
    }

    @Test
    public void testNeedsSummarization_atThreshold_returnsTrue() {
        List<ChatMessage> messages = createLargeMessages(3, 800);

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Should need summarization at threshold", needs);
    }

    @Test
    public void testNeedsSummarization_aboveThreshold_returnsTrue() {
        List<ChatMessage> messages = createLargeMessages(5, 800);

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Should need summarization above threshold", needs);
    }

    @Test
    public void testNeedsSummarization_emptyList_returnsFalse() {
        List<ChatMessage> messages = new ArrayList<>();

        boolean needs = summarizer.needsSummarization(messages);

        assertFalse("Empty list should not need summarization", needs);
    }

    @Test
    public void testNeedsSummarization_nullList_returnsFalse() {
        boolean needs = summarizer.needsSummarization(null);

        assertFalse("Null list should not need summarization", needs);
    }

    @Test
    public void testSummarizeAndSave_emptyMessages_callsOnResult() {
        List<ChatMessage> messages = new ArrayList<>();

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        summarizer.summarizeAndSave(messages, callback);

        verify(callback).onResult(eq(messages));
        verifyNoMoreInteractions(callback);
        verifyNoInteractions(mockMemoryRepository);
    }

    @Test
    public void testSummarizeAndSave_fewMessages_callsOnResult() {
        List<ChatMessage> messages = createSmallMessages(2);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Summary");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        verify(callback).onResult(any());
    }

    @Test
    public void testSummarizeAndSave_llmSuccess_savesAndReturnsCompressed() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Summary of conversation");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertTrue("Should return compressed list with recent messages", result.size() <= 3);

        ArgumentCaptor<String> entryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMemoryRepository).appendToDailyNote(entryCaptor.capture());

        String savedEntry = entryCaptor.getValue();
        assertTrue("Should contain timestamp", savedEntry.contains("Conversation Summary"));
        assertTrue("Should contain summary", savedEntry.contains("Summary of conversation"));
    }

    @Test
    public void testSummarizeAndSave_llmError_fallbackSummary() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onError("API error");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertTrue("Should return compressed list even on error", result.size() <= 3);

        ArgumentCaptor<String> entryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMemoryRepository).appendToDailyNote(entryCaptor.capture());

        String savedEntry = entryCaptor.getValue();
        assertTrue("Should contain fallback summary", savedEntry.contains("user messages and"));
        assertTrue("Should contain assistant messages", savedEntry.contains("assistant messages"));
    }

    @Test
    public void testSummarizeAndSave_saveError_stillReturnsCompressed() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Summary");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        doThrow(new IOException("Disk full")).when(mockMemoryRepository).appendToDailyNote(anyString());

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertTrue("Should return compressed list even when save fails", result.size() <= 3);
    }

    @Test
    public void testGenerateSummary_buildsCorrectPrompt() throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("More messages", ChatMessage.TYPE_USER));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Test summary");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessage(requestCaptor.capture(), any(), any());

        List<ChatMessage> request = requestCaptor.getValue();
        assertEquals("Should have one message (the prompt)", 1, request.size());
        assertTrue("Prompt should ask for summary", request.get(0).getContent().contains("Summarize"));
        assertTrue("Prompt should mention conversation", request.get(0).getContent().contains("Conversation"));
    }

    @Test
    public void testBuildSummaryPrompt_formatsMessagesCorrectly() throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Hello world", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("More content", ChatMessage.TYPE_USER));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Summary");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessage(requestCaptor.capture(), any(), any());

        String prompt = requestCaptor.getValue().get(0).getContent();
        assertTrue("Should mention user", prompt.contains("User"));
        assertTrue("Should mention assistant", prompt.contains("Assistant"));
        assertTrue("Should include content", prompt.contains("Hello world"));
        assertTrue("Should include content", prompt.contains("Hi there"));
    }

    @Test
    public void testBuildSummaryPrompt_truncatesLongMessages() throws IOException {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longContent.append("word ");
        }
        String longMessage = longContent.toString();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(longMessage, ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Second message", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Third message", ChatMessage.TYPE_USER));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onSuccess("Summary");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessage(requestCaptor.capture(), any(), any());

        String prompt = requestCaptor.getValue().get(0).getContent();
        assertTrue("Should truncate long message", prompt.length() < longMessage.length());
        assertTrue("Should show ellipsis", prompt.contains("..."));
    }

    @Test
    public void testCreateFallbackSummary_countsMessageTypes() throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("User 1", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("User 2", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Assistant 1", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Assistant 2", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Assistant 3", ChatMessage.TYPE_ASSISTANT));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onError("Error");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<String> entryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMemoryRepository).appendToDailyNote(entryCaptor.capture());

        String savedEntry = entryCaptor.getValue();
        assertTrue("Should count messages", savedEntry.contains("user messages and"));
        assertTrue("Should count assistant messages", savedEntry.contains("assistant messages"));
    }

    @Test
    public void testGetTokenThreshold_returnsConstant() {
        assertEquals("Should return correct threshold", 3072, summarizer.getTokenThreshold());
    }

    @Test
    public void testSummarizeAndSave_callbackOnError_calledOnError() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        doAnswer(invocation -> {
            LlmApiService.ChatCallback cb = invocation.getArgument(2);
            cb.onError("Network error");
            return null;
        }).when(mockApiService).sendMessage(anyList(), any(), any(LlmApiService.ChatCallback.class));

        summarizer.summarizeAndSave(messages, callback);

        // On LLM error, the callback.onResult should still be called with compressed messages
        // (fallback behavior uses createFallbackSummary)
        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());
        
        List<ChatMessage> result = resultCaptor.getValue();
        assertTrue("Should return compressed list on error", result.size() <= 3);
    }

    @Test
    public void testNeedsSummarization_singleLargeMessage() {
        List<ChatMessage> messages = createLargeMessages(1, 3100);

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Single large message should need summarization", needs);
    }

    @Test
    public void testNeedsSummarization_manySmallMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            messages.add(new ChatMessage("small message", ChatMessage.TYPE_USER));
        }

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Many small messages should need summarization", needs);
    }

    private List<ChatMessage> createSmallMessages(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(new ChatMessage("Small message " + i, ChatMessage.TYPE_USER));
        }
        return messages;
    }

    private List<ChatMessage> createLargeMessages(int count, int wordsPerMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder content = new StringBuilder();
            for (int j = 0; j < wordsPerMessage; j++) {
                content.append("word ");
            }
            messages.add(new ChatMessage(content.toString().trim(), ChatMessage.TYPE_USER));
        }
        return messages;
    }

    private List<ChatMessage> createMessagesWithTokens(int... tokenCounts) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int count : tokenCounts) {
            int words = (int) Math.ceil(count / 1.3);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < words; i++) {
                content.append("word");
                if (i < words - 1) content.append(" ");
            }
            messages.add(new ChatMessage(content.toString(), ChatMessage.TYPE_USER));
        }
        return messages;
    }
}
