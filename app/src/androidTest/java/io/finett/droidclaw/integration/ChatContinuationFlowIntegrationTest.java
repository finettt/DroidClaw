package io.finett.droidclaw.integration;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.service.ChatContinuationService;

/**
 * Integration tests for the complete chat continuation flow.
 * Tests the end-to-end workflow from task result creation to chat display.
 */
@RunWith(AndroidJUnit4.class)
public class ChatContinuationFlowIntegrationTest {

    private ChatRepository chatRepository;
    private ChatContinuationService continuationService;

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        chatRepository = new ChatRepository(getApplicationContext());
        continuationService = new ChatContinuationService(getApplicationContext());
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

    // ==================== END-TO-END FLOW TESTS ====================

    @Test
    public void heartbeatTaskResult_fullFlowFromCreationToChatDisplay() {
        // Step 1: Create task result
        TaskResult taskResult = new TaskResult(
                "hb-integration-1",
                TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(),
                "# Heartbeat Check Complete\n\nAll systems operational."
        );

        // Step 2: Create new chat with task result
        ChatSession newSession = continuationService.continueInNewChat(taskResult);
        assertNotNull("Session should be created", newSession);

        // Step 3: Verify session is linked to task
        assertEquals("Session should have parent task ID",
                taskResult.getId(), newSession.getParentTaskId());

        // Step 4: Verify messages are saved
        List<ChatMessage> messages = chatRepository.loadMessages(newSession.getId());
        assertTrue("Should have at least 2 messages", messages.size() >= 2);

        // Step 5: Verify first message is context card
        ChatMessage contextCard = messages.get(0);
        assertEquals("First message should be context card",
                ChatMessage.TYPE_CONTEXT_CARD, contextCard.getType());
        assertTrue("Should be marked as context card", contextCard.isContextCard());
        assertEquals("Context type should be heartbeat", "heartbeat", contextCard.getContextType());
        assertEquals("Should contain task content",
                "# Heartbeat Check Complete\n\nAll systems operational.",
                contextCard.getContent());

        // Step 6: Verify second message is agent prompt
        ChatMessage agentPrompt = messages.get(1);
        assertEquals("Second message should be system type",
                ChatMessage.TYPE_SYSTEM, agentPrompt.getType());
        assertTrue("Agent prompt should ask for exploration",
                agentPrompt.getContent().contains("What would you like to clarify or explore"));
    }

    @Test
    public void cronJobResult_fullFlowFromCreationToExistingChatUpdate() {
        // Step 1: Create existing chat session
        String existingSessionId = "existing-cron-chat";
        ChatSession existingSession = new ChatSession(existingSessionId, "Existing Chat", System.currentTimeMillis());
        chatRepository.saveMessages(existingSessionId, Arrays.asList(
                new ChatMessage("Initial user message", ChatMessage.TYPE_USER),
                new ChatMessage("Initial assistant response", ChatMessage.TYPE_ASSISTANT)
        ));

        // Step 2: Create cron job task result
        TaskResult taskResult = new TaskResult(
                "cron-integration-1",
                TaskResult.TYPE_CRON_JOB,
                System.currentTimeMillis(),
                "Daily report: 10 new items processed"
        );

        // Step 3: Continue in existing chat
        continuationService.continueInExistingChat(taskResult, existingSessionId);

        // Step 4: Verify messages were added
        List<ChatMessage> messages = chatRepository.loadMessages(existingSessionId);
        assertTrue("Should have at least 4 messages (2 original + 2 new)", messages.size() >= 4);

        // Step 5: Verify original messages are preserved
        assertEquals("First message should be original",
                "Initial user message", messages.get(0).getContent());
        assertEquals("Second message should be original",
                "Initial assistant response", messages.get(1).getContent());

        // Step 6: Verify new messages are appended
        ChatMessage contextCard = messages.get(messages.size() - 2);
        assertEquals("Context card should be second to last",
                ChatMessage.TYPE_CONTEXT_CARD, contextCard.getType());
        assertEquals("Context card should contain task result",
                "Daily report: 10 new items processed", contextCard.getContent());

        ChatMessage agentPrompt = messages.get(messages.size() - 1);
        assertEquals("Agent prompt should be last",
                ChatMessage.TYPE_SYSTEM, agentPrompt.getType());
        assertTrue("Agent prompt should mention cron job",
                agentPrompt.getContent().toLowerCase().contains("cron job"));
    }

    @Test
    public void multipleTaskResults_createDistinctChats() {
        // Create multiple task results
        TaskResult hbResult = new TaskResult("hb-multi", TaskResult.TYPE_HEARTBEAT, 1000L, "Heartbeat 1");
        TaskResult cronResult = new TaskResult("cron-multi", TaskResult.TYPE_CRON_JOB, 2000L, "Cron 1");
        TaskResult manualResult = new TaskResult("manual-multi", TaskResult.TYPE_MANUAL, 3000L, "Manual 1");

        // Create separate chats for each
        ChatSession hbSession = continuationService.continueInNewChat(hbResult);
        ChatSession cronSession = continuationService.continueInNewChat(cronResult);
        ChatSession manualSession = continuationService.continueInNewChat(manualResult);

        // Verify all sessions are distinct
        assertNotNull("Heartbeat session should exist", hbSession);
        assertNotNull("Cron session should exist", cronSession);
        assertNotNull("Manual session should exist", manualSession);

        assertTrue("Sessions should have different IDs",
                hbSession.getId().equals(cronSession.getId()) == false &&
                hbSession.getId().equals(manualSession.getId()) == false &&
                cronSession.getId().equals(manualSession.getId()) == false);

        // Verify each chat has correct content
        List<ChatMessage> hbMessages = chatRepository.loadMessages(hbSession.getId());
        assertEquals("Heartbeat content", "Heartbeat 1", hbMessages.get(0).getContent());

        List<ChatMessage> cronMessages = chatRepository.loadMessages(cronSession.getId());
        assertEquals("Cron content", "Cron 1", cronMessages.get(0).getContent());

        List<ChatMessage> manualMessages = chatRepository.loadMessages(manualSession.getId());
        assertEquals("Manual content", "Manual 1", manualMessages.get(0).getContent());
    }

    // ==================== CHAT ADAPTER INTEGRATION TESTS ====================

    @Test
    public void chatAdapter_loadsContextCardMessages_fromContinuedChat() {
        // Create task result and chat
        TaskResult taskResult = new TaskResult("hb-adapter-1", TaskResult.TYPE_HEARTBEAT, 1000L,
                "Adapter test content");
        ChatSession session = continuationService.continueInNewChat(taskResult);

        // Load messages into adapter
        ChatAdapter adapter = new ChatAdapter();
        List<ChatMessage> messages = chatRepository.loadMessages(session.getId());
        adapter.setMessages(messages);

        // Verify adapter has correct messages
        assertEquals("Should have at least 2 messages", 2, adapter.getItemCount());

        // Verify context card is first
        ChatMessage firstMessage = adapter.getMessages().get(0);
        assertEquals("First should be context card",
                ChatMessage.TYPE_CONTEXT_CARD, firstMessage.getType());
        assertTrue("Should be marked as context card", firstMessage.isContextCard());

        // Verify agent prompt is second
        ChatMessage secondMessage = adapter.getMessages().get(1);
        assertEquals("Second should be system prompt",
                ChatMessage.TYPE_SYSTEM, secondMessage.getType());
    }

    @Test
    public void chatAdapter_displaysMixedMessages_correctlyWithContinuation() {
        // Create existing chat with various message types
        String sessionId = "mixed-chat";
        chatRepository.saveMessages(sessionId, Arrays.asList(
                new ChatMessage("User message 1", ChatMessage.TYPE_USER),
                new ChatMessage("Assistant response 1", ChatMessage.TYPE_ASSISTANT),
                new ChatMessage("User message 2", ChatMessage.TYPE_USER)
        ));

        // Add task result via continuation
        TaskResult taskResult = new TaskResult("hb-mixed-1", TaskResult.TYPE_HEARTBEAT, 1000L,
                "Heartbeat during conversation");
        continuationService.continueInExistingChat(taskResult, sessionId);

        // Load into adapter
        ChatAdapter adapter = new ChatAdapter();
        List<ChatMessage> messages = chatRepository.loadMessages(sessionId);
        adapter.setMessages(messages);

        // Verify message count
        assertTrue("Should have at least 5 messages (3 original + 2 new)",
                adapter.getItemCount() >= 5);

        // Verify message order and types
        List<ChatMessage> adapterMessages = adapter.getMessages();
        assertEquals("Message 1 should be user", ChatMessage.TYPE_USER, adapterMessages.get(0).getType());
        assertEquals("Message 2 should be assistant", ChatMessage.TYPE_ASSISTANT, adapterMessages.get(1).getType());
        assertEquals("Message 3 should be user", ChatMessage.TYPE_USER, adapterMessages.get(2).getType());
        assertEquals("Message 4 should be context card", ChatMessage.TYPE_CONTEXT_CARD, adapterMessages.get(3).getType());
        assertEquals("Message 5 should be system", ChatMessage.TYPE_SYSTEM, adapterMessages.get(4).getType());
    }

    // ==================== CHAT REPOSITORY INTEGRATION TESTS ====================

    @Test
    public void chatRepository_persistsContextCardFields() {
        // Create session with context card message
        String sessionId = "context-persist-session";
        ChatMessage contextCard = new ChatMessage("Persisted content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setOriginalTaskId("hb-persist-1");
        contextCard.setTimestamp(12345L);

        chatRepository.saveMessages(sessionId, Arrays.asList(
                new ChatMessage("User message", ChatMessage.TYPE_USER),
                contextCard
        ));

        // Reload messages
        List<ChatMessage> loadedMessages = chatRepository.loadMessages(sessionId);

        // Verify context card fields persisted
        assertEquals("Should have 2 messages", 2, loadedMessages.size());
        ChatMessage loadedCard = loadedMessages.get(1);

        assertEquals("Type should be CONTEXT_CARD",
                ChatMessage.TYPE_CONTEXT_CARD, loadedCard.getType());
        assertTrue("Should be marked as context card", loadedCard.isContextCard());
        assertEquals("Context type should be heartbeat", "heartbeat", loadedCard.getContextType());
        assertEquals("Should link to original task", "hb-persist-1", loadedCard.getOriginalTaskId());
        assertEquals("Timestamp should persist", 12345L, loadedCard.getTimestamp());
        assertEquals("Content should persist", "Persisted content", loadedCard.getContent());
    }

    // ==================== TASK RESULT SERIALIZATION TESTS ====================

    @Test
    public void taskResult_serializable_forFragmentNavigation() {
        // Create task result with all fields
        TaskResult taskResult = new TaskResult(
                "serializable-1",
                TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(),
                "# Serializable Content\n\nThis should be serializable"
        );
        taskResult.putMetadata("key1", "value1");
        taskResult.putMetadata("key2", "value2");

        // Verify it can be put in a bundle (simulating fragment navigation)
        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable("task_result", taskResult);

        // Verify it can be retrieved
        TaskResult retrieved = (TaskResult) args.getSerializable("task_result");
        assertNotNull("Task result should be retrievable from bundle", retrieved);
        assertEquals("ID should match", "serializable-1", retrieved.getId());
        assertEquals("Type should match", TaskResult.TYPE_HEARTBEAT, retrieved.getType());
        assertEquals("Content should match",
                "# Serializable Content\n\nThis should be serializable",
                retrieved.getContent());
        assertEquals("Metadata should persist", "value1", retrieved.getMetadataValue("key1"));
    }

    // ==================== COMPLEX SCENARIO TESTS ====================

    @Test
    public void complexScenario_multipleContinuationsInSameChat() {
        // Create initial chat
        String sessionId = "complex-chat";
        chatRepository.saveMessages(sessionId, Arrays.asList(
                new ChatMessage("Initial message", ChatMessage.TYPE_USER)
        ));

        // Add first task result
        TaskResult hbResult = new TaskResult("hb-complex-1", TaskResult.TYPE_HEARTBEAT, 1000L,
                "First heartbeat");
        continuationService.continueInExistingChat(hbResult, sessionId);

        // Add user response
        List<ChatMessage> messages = chatRepository.loadMessages(sessionId);
        messages.add(new ChatMessage("User response to heartbeat", ChatMessage.TYPE_USER));
        chatRepository.saveMessages(sessionId, messages);

        // Add second task result
        TaskResult cronResult = new TaskResult("cron-complex-1", TaskResult.TYPE_CRON_JOB, 2000L,
                "First cron job");
        continuationService.continueInExistingChat(cronResult, sessionId);

        // Verify final state
        List<ChatMessage> finalMessages = chatRepository.loadMessages(sessionId);

        // Should have: initial + hb context + hb prompt + user response + cron context + cron prompt
        assertTrue("Should have at least 6 messages", finalMessages.size() >= 6);

        // Verify the sequence
        assertEquals("1st: Initial user message", ChatMessage.TYPE_USER, finalMessages.get(0).getType());
        assertEquals("2nd: HB context card", ChatMessage.TYPE_CONTEXT_CARD, finalMessages.get(1).getType());
        assertEquals("3rd: HB agent prompt", ChatMessage.TYPE_SYSTEM, finalMessages.get(2).getType());
        assertEquals("4th: User response", ChatMessage.TYPE_USER, finalMessages.get(3).getType());
        assertEquals("5th: Cron context card", ChatMessage.TYPE_CONTEXT_CARD, finalMessages.get(4).getType());
        assertEquals("6th: Cron agent prompt", ChatMessage.TYPE_SYSTEM, finalMessages.get(5).getType());
    }

    @Test
    public void complexScenario_chatContinuationAfterMultipleTasks() {
        // Create multiple task results
        TaskResult[] taskResults = new TaskResult[5];
        ChatSession[] sessions = new ChatSession[5];

        // Create 5 different task results and chats
        for (int i = 0; i < 5; i++) {
            taskResults[i] = new TaskResult(
                    "task-" + i,
                    TaskResult.TYPE_HEARTBEAT,
                    i * 1000L,
                    "Heartbeat result " + i
            );
            sessions[i] = continuationService.continueInNewChat(taskResults[i]);
        }

        // Verify all sessions created successfully
        for (int i = 0; i < 5; i++) {
            assertNotNull("Session " + i + " should exist", sessions[i]);
            List<ChatMessage> messages = chatRepository.loadMessages(sessions[i].getId());
            assertTrue("Session " + i + " should have messages", messages.size() >= 2);
            assertEquals("Session " + i + " context card should have correct content",
                    "Heartbeat result " + i, messages.get(0).getContent());
        }
    }

    @Test
    public void complexScenario_taskResultWithRichContent_preservesFormatting() {
        String richContent = "# Complex Report\n\n" +
                "## Summary\n" +
                "- Item 1: Value A\n" +
                "- Item 2: Value B\n" +
                "- Item 3: Value C\n\n" +
                "## Details\n" +
                "This is a **detailed** report with _various_ formatting.\n\n" +
                "### Metrics\n" +
                "- Response time: 150ms\n" +
                "- Memory usage: 45%\n" +
                "- CPU: 23%";

        TaskResult taskResult = new TaskResult(
                "rich-content-1",
                TaskResult.TYPE_HEARTBEAT,
                1000L,
                richContent
        );

        ChatSession session = continuationService.continueInNewChat(taskResult);
        List<ChatMessage> messages = chatRepository.loadMessages(session.getId());

        // Verify content is preserved exactly
        ChatMessage contextCard = messages.get(0);
        assertEquals("Content should be preserved exactly", richContent, contextCard.getContent());
    }

    @Test
    public void complexScenario_longConversationWithPeriodicHeartbeats() {
        // Simulate a long conversation with periodic heartbeat checks
        String sessionId = "long-conversation";

        // Initial messages
        chatRepository.saveMessages(sessionId, Arrays.asList(
                new ChatMessage("Hello", ChatMessage.TYPE_USER),
                new ChatMessage("Hi there!", ChatMessage.TYPE_ASSISTANT)
        ));

        // Simulate 3 heartbeat checks during conversation
        for (int i = 1; i <= 3; i++) {
            // Add heartbeat
            TaskResult hbResult = new TaskResult("hb-long-" + i, TaskResult.TYPE_HEARTBEAT, i * 10000L,
                    "Heartbeat check " + i);
            continuationService.continueInExistingChat(hbResult, sessionId);

            // Add user interaction
            List<ChatMessage> messages = chatRepository.loadMessages(sessionId);
            messages.add(new ChatMessage("User message after heartbeat " + i, ChatMessage.TYPE_USER));
            messages.add(new ChatMessage("Assistant response " + i, ChatMessage.TYPE_ASSISTANT));
            chatRepository.saveMessages(sessionId, messages);
        }

        // Verify final conversation structure
        List<ChatMessage> finalMessages = chatRepository.loadMessages(sessionId);

        // Should have: 2 initial + 3 * (2 heartbeat + 2 user interactions) = 14 messages
        assertEquals("Should have 14 messages total", 14, finalMessages.size());

        // Verify pattern: user, assistant, [context, system, user, assistant] * 3
        assertEquals("1: Initial user", ChatMessage.TYPE_USER, finalMessages.get(0).getType());
        assertEquals("2: Initial assistant", ChatMessage.TYPE_ASSISTANT, finalMessages.get(1).getType());

        for (int i = 0; i < 3; i++) {
            int base = 2 + i * 4;
            assertEquals("Heartbeat " + (i + 1) + " context", ChatMessage.TYPE_CONTEXT_CARD,
                    finalMessages.get(base).getType());
            assertEquals("Heartbeat " + (i + 1) + " prompt", ChatMessage.TYPE_SYSTEM,
                    finalMessages.get(base + 1).getType());
            assertEquals("User after HB " + (i + 1), ChatMessage.TYPE_USER,
                    finalMessages.get(base + 2).getType());
            assertEquals("Assistant " + (i + 1), ChatMessage.TYPE_ASSISTANT,
                    finalMessages.get(base + 3).getType());
        }
    }
}
