package io.finett.droidclaw.service;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;

/**
 * Instrumented tests for ChatContinuationService.
 * Tests the chat continuation flow for task results.
 */
@RunWith(AndroidJUnit4.class)
public class ChatContinuationServiceInstrumentedTest {

    private ChatContinuationService service;
    private ChatRepository chatRepository;

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        service = new ChatContinuationService(getApplicationContext());
        chatRepository = new ChatRepository(getApplicationContext());
    }

    @After
    public void tearDown() {
        // Clean up after tests
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== CONTINUE IN NEW CHAT TESTS ====================

    @Test
    public void continueInNewChat_createsSessionWithTaskResult() {
        TaskResult taskResult = new TaskResult("task-1", TaskResult.TYPE_HEARTBEAT, 1000L,
                "Heartbeat check completed successfully");

        ChatSession newSession = service.continueInNewChat(taskResult);

        assertNotNull("New session should be created", newSession);
        assertNotNull("Session ID should not be null", newSession.getId());
        assertTrue("Session title should contain task type",
                newSession.getTitle().contains("Heartbeat"));
        assertEquals("Session should be linked to task",
                taskResult.getId(), newSession.getParentTaskId());
    }

    @Test
    public void continueInNewChat_savesMessages() {
        TaskResult taskResult = new TaskResult("task-2", TaskResult.TYPE_CRON_JOB, 2000L,
                "Cron job completed: Generated report");

        ChatSession newSession = service.continueInNewChat(taskResult);

        List<ChatMessage> messages = chatRepository.loadMessages(newSession.getId());

        assertNotNull("Messages should be saved", messages);
        assertTrue("Should have at least 2 messages (context card + agent prompt)",
                messages.size() >= 2);
    }

    @Test
    public void continueInNewChat_firstMessageIsContextCard() {
        TaskResult taskResult = new TaskResult("task-3", TaskResult.TYPE_HEARTBEAT, 3000L,
                "Heartbeat result content");

        ChatSession newSession = service.continueInNewChat(taskResult);
        List<ChatMessage> messages = chatRepository.loadMessages(newSession.getId());

        ChatMessage firstMessage = messages.get(0);

        assertEquals("First message should be context card type",
                ChatMessage.TYPE_CONTEXT_CARD, firstMessage.getType());
        assertTrue("First message should be marked as context card",
                firstMessage.isContextCard());
        assertEquals("Context type should match task type",
                "heartbeat", firstMessage.getContextType());
        assertEquals("Should link to original task",
                taskResult.getId(), firstMessage.getOriginalTaskId());
    }

    @Test
    public void continueInNewChat_secondMessageIsAgentPrompt() {
        TaskResult taskResult = new TaskResult("task-4", TaskResult.TYPE_MANUAL, 4000L,
                "Manual task completed");

        ChatSession newSession = service.continueInNewChat(taskResult);
        List<ChatMessage> messages = chatRepository.loadMessages(newSession.getId());

        ChatMessage secondMessage = messages.get(1);

        assertEquals("Second message should be system type",
                ChatMessage.TYPE_SYSTEM, secondMessage.getType());
        assertTrue("Agent prompt should ask for clarification",
                secondMessage.getContent().contains("What would you like to clarify or explore"));
    }

    @Test
    public void continueInNewChat_withDifferentTaskTypes_generatesCorrectTitles() {
        // Heartbeat task
        TaskResult heartbeatResult = new TaskResult("hb-1", TaskResult.TYPE_HEARTBEAT, 1000L, "HB");
        ChatSession heartbeatSession = service.continueInNewChat(heartbeatResult);
        assertTrue("Heartbeat session title should contain 'Heartbeat'",
                heartbeatSession.getTitle().contains("Heartbeat"));

        // Cron job task
        TaskResult cronResult = new TaskResult("cron-1", TaskResult.TYPE_CRON_JOB, 2000L, "CJ");
        ChatSession cronSession = service.continueInNewChat(cronResult);
        assertTrue("Cron session title should contain 'Cron'",
                cronSession.getTitle().contains("Cron"));

        // Manual task
        TaskResult manualResult = new TaskResult("manual-1", TaskResult.TYPE_MANUAL, 3000L, "MT");
        ChatSession manualSession = service.continueInNewChat(manualResult);
        assertTrue("Manual session title should contain 'Manual'",
                manualSession.getTitle().contains("Manual"));
    }

    @Test
    public void continueInNewChat_contextCardHasCorrectContent() {
        String taskContent = "# Detailed Report\n\n- Item 1: Value\n- Item 2: Value";
        TaskResult taskResult = new TaskResult("task-content", TaskResult.TYPE_HEARTBEAT, 1000L, taskContent);

        ChatSession newSession = service.continueInNewChat(taskResult);
        List<ChatMessage> messages = chatRepository.loadMessages(newSession.getId());

        ChatMessage contextCard = messages.get(0);

        assertEquals("Context card content should match task result content",
                taskContent, contextCard.getContent());
        assertEquals("Context card timestamp should match task result timestamp",
                taskResult.getTimestamp(), contextCard.getTimestamp());
    }

    @Test
    public void continueInNewChat_multipleCalls_createsDistinctSessions() {
        TaskResult taskResult1 = new TaskResult("task-multi-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Result 1");
        TaskResult taskResult2 = new TaskResult("task-multi-2", TaskResult.TYPE_HEARTBEAT, 2000L, "Result 2");

        ChatSession session1 = service.continueInNewChat(taskResult1);
        ChatSession session2 = service.continueInNewChat(taskResult2);

        assertNotNull("Session 1 should be created", session1);
        assertNotNull("Session 2 should be created", session2);
        assertEquals("Sessions should have different IDs",
                false, session1.getId().equals(session2.getId()));

        // Verify both sessions have their own messages
        List<ChatMessage> messages1 = chatRepository.loadMessages(session1.getId());
        List<ChatMessage> messages2 = chatRepository.loadMessages(session2.getId());

        assertEquals("First message content should differ",
                messages1.get(0).getContent(), "Result 1");
        assertEquals("Second message content should differ",
                messages2.get(0).getContent(), "Result 2");
    }

    // ==================== CONTINUE IN EXISTING CHAT TESTS ====================

    @Test
    public void continueInExistingChat_addsMessagesToSession() {
        // Create existing session with some messages
        ChatSession existingSession = new ChatSession("existing-1", "Existing Chat", 1000L);
        chatRepository.saveMessages("existing-1", java.util.Arrays.asList(
                new ChatMessage("User message", ChatMessage.TYPE_USER),
                new ChatMessage("Assistant response", ChatMessage.TYPE_ASSISTANT)
        ));

        TaskResult taskResult = new TaskResult("task-existing", TaskResult.TYPE_HEARTBEAT, 2000L,
                "New heartbeat result");

        service.continueInExistingChat(taskResult, "existing-1");

        List<ChatMessage> messages = chatRepository.loadMessages("existing-1");

        assertNotNull("Messages should exist", messages);
        assertTrue("Should have 4 messages (2 original + 2 new)",
                messages.size() >= 4);
    }

    @Test
    public void continueInExistingChat_newMessagesAreAppended() {
        // Create existing session
        chatRepository.saveMessages("existing-2", java.util.Arrays.asList(
                new ChatMessage("Old message 1", ChatMessage.TYPE_USER),
                new ChatMessage("Old message 2", ChatMessage.TYPE_ASSISTANT)
        ));

        TaskResult taskResult = new TaskResult("task-append", TaskResult.TYPE_CRON_JOB, 3000L,
                "Cron job result");

        service.continueInExistingChat(taskResult, "existing-2");

        List<ChatMessage> messages = chatRepository.loadMessages("existing-2");

        // First two messages should be original
        assertEquals("First message should be original",
                "Old message 1", messages.get(0).getContent());
        assertEquals("Second message should be original",
                "Old message 2", messages.get(1).getContent());

        // New messages should be appended
        ChatMessage contextCard = messages.get(2);
        assertEquals("Third message should be context card",
                ChatMessage.TYPE_CONTEXT_CARD, contextCard.getType());
        assertEquals("Context card content should match task result",
                "Cron job result", contextCard.getContent());
    }

    @Test
    public void continueInExistingChat_withEmptySession_addsMessages() {
        // Create empty session
        ChatSession emptySession = new ChatSession("empty-session", "Empty Chat", 1000L);
        chatRepository.saveMessages("empty-session", java.util.Collections.emptyList());

        TaskResult taskResult = new TaskResult("task-empty", TaskResult.TYPE_MANUAL, 2000L,
                "Manual task result");

        service.continueInExistingChat(taskResult, "empty-session");

        List<ChatMessage> messages = chatRepository.loadMessages("empty-session");

        assertTrue("Should have messages added", messages.size() >= 2);
        assertEquals("First message should be context card",
                ChatMessage.TYPE_CONTEXT_CARD, messages.get(0).getType());
    }

    @Test
    public void continueInExistingChat_contextCardIsConfiguredCorrectly() {
        chatRepository.saveMessages("existing-3", java.util.Arrays.asList(
                new ChatMessage("Existing message", ChatMessage.TYPE_USER)
        ));

        TaskResult taskResult = new TaskResult("task-card", TaskResult.TYPE_HEARTBEAT, 5000L,
                "Heartbeat check OK");

        service.continueInExistingChat(taskResult, "existing-3");

        List<ChatMessage> messages = chatRepository.loadMessages("existing-3");

        // Find context card (should be second to last)
        ChatMessage contextCard = messages.get(messages.size() - 2);

        assertTrue("Should be context card", contextCard.isContextCard());
        assertEquals("Context type should be heartbeat", "heartbeat", contextCard.getContextType());
        assertEquals("Should link to original task", "task-card", contextCard.getOriginalTaskId());
        assertEquals("Content should match task result", "Heartbeat check OK", contextCard.getContent());
    }

    // ==================== CREATE CONTEXT MESSAGE TESTS ====================

    @Test
    public void createContextMessage_fromTaskResult_configuresCorrectly() {
        TaskResult taskResult = new TaskResult("task-create", TaskResult.TYPE_CRON_JOB, 7000L,
                "Cron task content");

        ChatMessage contextMessage = service.createContextMessage(taskResult);

        assertNotNull("Context message should be created", contextMessage);
        assertEquals("Type should be CONTEXT_CARD",
                ChatMessage.TYPE_CONTEXT_CARD, contextMessage.getType());
        assertTrue("Should be marked as context card", contextMessage.isContextCard());
        assertEquals("Context type should be cron_job", "cron_job", contextMessage.getContextType());
        assertEquals("Should link to original task", "task-create", contextMessage.getOriginalTaskId());
        assertEquals("Content should match task result", "Cron task content", contextMessage.getContent());
        assertEquals("Timestamp should match task result", 7000L, contextMessage.getTimestamp());
    }

    // ==================== AGENT PROMPT TESTS ====================

    @Test
    public void continueInNewChat_agentPromptMentionsTaskType() {
        TaskResult heartbeatResult = new TaskResult("hb-prompt", TaskResult.TYPE_HEARTBEAT, 1000L, "HB");
        ChatSession heartbeatSession = service.continueInNewChat(heartbeatResult);
        List<ChatMessage> heartbeatMessages = chatRepository.loadMessages(heartbeatSession.getId());

        String agentPrompt = heartbeatMessages.get(1).getContent();
        assertTrue("Agent prompt should mention 'heartbeat'",
                agentPrompt.toLowerCase().contains("heartbeat"));

        TaskResult cronResult = new TaskResult("cron-prompt", TaskResult.TYPE_CRON_JOB, 2000L, "CJ");
        ChatSession cronSession = service.continueInNewChat(cronResult);
        List<ChatMessage> cronMessages = chatRepository.loadMessages(cronSession.getId());

        String cronAgentPrompt = cronMessages.get(1).getContent();
        assertTrue("Agent prompt should mention 'cron job'",
                cronAgentPrompt.toLowerCase().contains("cron job"));
    }

    @Test
    public void continueInExistingChat_agentPromptIsAppropriate() {
        chatRepository.saveMessages("existing-prompt", java.util.Arrays.asList(
                new ChatMessage("Existing message", ChatMessage.TYPE_USER)
        ));

        TaskResult taskResult = new TaskResult("task-prompt", TaskResult.TYPE_MANUAL, 3000L, "MT");

        service.continueInExistingChat(taskResult, "existing-prompt");

        List<ChatMessage> messages = chatRepository.loadMessages("existing-prompt");
        String agentPrompt = messages.get(messages.size() - 1).getContent();

        assertTrue("Agent prompt should ask for clarification or exploration",
                agentPrompt.contains("What would you like to clarify or explore"));
        assertTrue("Agent prompt should mention task type",
                agentPrompt.toLowerCase().contains("manual task"));
    }

    // ==================== PERSISTENCE TESTS ====================

    @Test
    public void continueInNewChat_messagesPersistAcrossServiceInstances() {
        TaskResult taskResult = new TaskResult("task-persist", TaskResult.TYPE_HEARTBEAT, 1000L,
                "Persistent content");

        ChatSession session = service.continueInNewChat(taskResult);

        // Create new service instance
        ChatContinuationService newService = new ChatContinuationService(getApplicationContext());
        ChatRepository newRepo = new ChatRepository(getApplicationContext());

        // Verify messages persisted
        List<ChatMessage> messages = newRepo.loadMessages(session.getId());
        assertTrue("Messages should persist across service instances", messages.size() >= 2);
        assertEquals("Context card content should match", "Persistent content", messages.get(0).getContent());
    }

    @Test
    public void continueInExistingChat_messagesPersistCorrectly() {
        chatRepository.saveMessages("existing-persist", java.util.Arrays.asList(
                new ChatMessage("Original", ChatMessage.TYPE_USER)
        ));

        TaskResult taskResult = new TaskResult("task-persist-2", TaskResult.TYPE_CRON_JOB, 2000L,
                "New content");

        service.continueInExistingChat(taskResult, "existing-persist");

        // Create new repository instance
        ChatRepository newRepo = new ChatRepository(getApplicationContext());
        List<ChatMessage> messages = newRepo.loadMessages("existing-persist");

        assertTrue("Should have all messages", messages.size() >= 3);
        assertEquals("Original message should persist", "Original", messages.get(0).getContent());
        assertEquals("Context card should persist", "New content", messages.get(messages.size() - 2).getContent());
    }
}
