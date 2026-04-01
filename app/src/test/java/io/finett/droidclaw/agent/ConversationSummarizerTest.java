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

/**
 * Unit tests for ConversationSummarizer.
 */
@RunWith(MockitoJUnitRunner.class)
public class ConversationSummarizerTest {

    private static final int TOKEN_THRESHOLD = 3000;

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
        List<ChatMessage> messages = createMessagesWithTokens(100, 200, 300); // ~600 tokens

        boolean needs = summarizer.needsSummarization(messages);

        assertFalse("Should not need summarization below threshold", needs);
    }

    @Test
    public void testNeedsSummarization_atThreshold_returnsTrue() {
        // Approximate threshold: 3000 tokens
        List<ChatMessage> messages = createMessagesWithTokens(1000, 1000, 1000); // ~3000 tokens

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Should need summarization at threshold", needs);
    }

    @Test
    public void testNeedsSummarization_aboveThreshold_returnsTrue() {
        List<ChatMessage> messages = createMessagesWithTokens(1500, 1500, 1500); // ~4500 tokens

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
        // Too few messages to summarize (less than 3)
        List<ChatMessage> messages = createMessagesWithTokens(100, 200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        summarizer.summarizeAndSave(messages, callback);

        verify(callback).onResult(eq(messages));
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testSummarizeAndSave_llmSuccess_savesAndReturnsCompressed() throws IOException {
        // Create enough messages to trigger summarization
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);
        // Total ~6800 tokens, should summarize first 5, keep last 3

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        // Mock LLM response
        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onSuccess(new LlmApiService.LlmResponse("Summary of conversation", null));
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        // Verify callback received compressed list (last 3 messages)
        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertEquals("Should return compressed list with recent messages", 3, result.size());

        // Verify summary was saved to daily note
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

        // Mock LLM error
        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onError("API error");
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        // Verify callback received compressed list (fallback still compresses)
        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertEquals("Should return compressed list even on error", 3, result.size());

        // Verify fallback summary was saved
        ArgumentCaptor<String> entryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMemoryRepository).appendToDailyNote(entryCaptor.capture());

        String savedEntry = entryCaptor.getValue();
        assertTrue("Should contain fallback summary", savedEntry.contains("user messages and"));
        assertTrue("Should contain assistant messages", savedEntry.contains("assistant messages summarized"));
    }

    @Test
    public void testSummarizeAndSave_saveError_stillReturnsCompressed() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        // Mock LLM success
        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onSuccess(new LlmApiService.LlmResponse("Summary", null));
                return null;
            });

        // Mock save error
        doThrow(new IOException("Disk full")).when(mockMemoryRepository).appendToDailyNote(anyString());

        summarizer.summarizeAndSave(messages, callback);

        // Verify callback still receives compressed list (even though save failed)
        ArgumentCaptor<List<ChatMessage>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(callback).onResult(resultCaptor.capture());

        List<ChatMessage> result = resultCaptor.getValue();
        assertEquals("Should return compressed list even when save fails", 3, result.size());
    }

    @Test
    public void testGenerateSummary_buildsCorrectPrompt() throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT));

        // Use reflection to access the private generateSummary method via a test subclass
        // Or test through the public API
        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onSuccess(new LlmApiService.LlmResponse("Test summary", null));
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        // Verify LLM was called with a prompt
        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessageWithTools(requestCaptor.capture(), any(), any(), any());

        List<ChatMessage> request = requestCaptor.getValue();
        assertEquals("Should have one message (the prompt)", 1, request.size());
        assertTrue("Prompt should ask for summary", request.get(0).getContent().contains("Summarize"));
        assertTrue("Prompt should mention conversation", request.get(0).getContent().contains("Conversation"));
    }

    @Test
    public void testBuildSummaryPrompt_formatsMessagesCorrectly() throws IOException {
        // Test through the public API - the prompt building happens internally
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Hello world", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onSuccess(new LlmApiService.LlmResponse("Summary", null));
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessageWithTools(requestCaptor.capture(), any(), any(), any());

        String prompt = requestCaptor.getValue().get(0).getContent();
        assertTrue("Should mention user", prompt.contains("User"));
        assertTrue("Should mention assistant", prompt.contains("Assistant"));
        assertTrue("Should include content", prompt.contains("Hello world"));
        assertTrue("Should include content", prompt.contains("Hi there"));
    }

    @Test
    public void testBuildSummaryPrompt_truncatesLongMessages() throws IOException {
        // Create a very long message
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longContent.append("word ");
        }
        String longMessage = longContent.toString();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(longMessage, ChatMessage.TYPE_USER));

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onSuccess(new LlmApiService.LlmResponse("Summary", null));
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<List<ChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockApiService).sendMessageWithTools(requestCaptor.capture(), any(), any(), any());

        String prompt = requestCaptor.getValue().get(0).getContent();
        // Message should be truncated (500 chars + ...)
        assertTrue("Should truncate long message", prompt.length() < longMessage.length());
        assertTrue("Should show ellipsis", prompt.contains("..."));
    }

    @Test
    public void testCreateFallbackSummary_countsMessageTypes() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("User 1", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("User 2", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Assistant 1", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Assistant 2", ChatMessage.TYPE_ASSISTANT));
        messages.add(new ChatMessage("Assistant 3", ChatMessage.TYPE_ASSISTANT));

        // Use reflection or test through public API
        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onError("Error");
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        ArgumentCaptor<String> entryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMemoryRepository).appendToDailyNote(entryCaptor.capture());

        String savedEntry = entryCaptor.getValue();
        assertTrue("Should count user messages", savedEntry.contains("2 user messages"));
        assertTrue("Should count assistant messages", savedEntry.contains("3 assistant messages"));
    }

    @Test
    public void testGetTokenThreshold_returnsConstant() {
        assertEquals("Should return correct threshold", TOKEN_THRESHOLD, summarizer.getTokenThreshold());
    }

    @Test
    public void testSummarizeAndSave_callbackOnError_calledOnError() throws IOException {
        List<ChatMessage> messages = createMessagesWithTokens(500, 600, 700, 800, 900, 1000, 1100, 1200);

        ConversationSummarizer.SummarizeCallback callback = mock(ConversationSummarizer.SummarizeCallback.class);

        // Mock LLM error that calls onError
        when(mockApiService.sendMessageWithTools(anyList(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class)))
            .thenAnswer(invocation -> {
                LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(3);
                cb.onError("Network error");
                return null;
            });

        summarizer.summarizeAndSave(messages, callback);

        // On LLM error, the callback.onResult should still be called with full messages
        // (fallback behavior in onError handler)
        verify(callback).onResult(eq(messages));
    }

    @Test
    public void testNeedsSummarization_singleLargeMessage() {
        // One very large message
        List<ChatMessage> messages = createMessagesWithTokens(4000);

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Single large message should need summarization", needs);
    }

    @Test
    public void testNeedsSummarization_manySmallMessages() {
        // Many small messages that sum to over threshold
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            messages.add(new ChatMessage("small message", ChatMessage.TYPE_USER));
        }

        boolean needs = summarizer.needsSummarization(messages);

        assertTrue("Many small messages should need summarization", needs);
    }

    // Helper methods

    private List<ChatMessage> createMessagesWithTokens(int... tokenCounts) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int count : tokenCounts) {
            // Approximate: tokens / 1.3 ≈ words
            int words = Math.max(1, count / 13);
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
