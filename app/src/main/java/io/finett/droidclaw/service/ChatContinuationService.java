package io.finett.droidclaw.service;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;

/**
 * Service for handling chat continuation flows.
 * Manages creating new chats with context or adding context to existing chats.
 */
public class ChatContinuationService {
    private static final String TAG = "ChatContinuationService";

    private final Context context;
    private final ChatRepository chatRepository;

    public ChatContinuationService(Context context) {
        this.context = context;
        this.chatRepository = new ChatRepository(context);
    }

    /**
     * Create a new chat session with task result context.
     * The new session will have a context card message and an agent prompt.
     *
     * @param taskResult The task result to continue from
     * @return The newly created ChatSession
     */
    public ChatSession continueInNewChat(TaskResult taskResult) {
        Log.d(TAG, "Creating new chat session for task: " + taskResult.getId());

        // Create new session
        ChatSession session = new ChatSession(
                UUID.randomUUID().toString(),
                "Discussing " + getTaskTypeLabel(taskResult.getType()),
                System.currentTimeMillis()
        );

        // Link to task
        session.setParentTaskId(taskResult.getId());

        // Create messages for the new chat
        List<ChatMessage> messages = new ArrayList<>();

        // Add context card with task result
        ChatMessage contextCard = createContextMessage(taskResult);
        messages.add(contextCard);

        // Add agent prompt message
        ChatMessage agentPrompt = createAgentPrompt(taskResult);
        messages.add(agentPrompt);

        // Save messages
        chatRepository.saveMessages(session.getId(), messages);

        Log.d(TAG, "Created new chat session with " + messages.size() + " initial messages");
        return session;
    }

    /**
     * Add task result context to an existing chat session.
     *
     * @param taskResult The task result to add
     * @param sessionId  The existing session ID
     */
    public void continueInExistingChat(TaskResult taskResult, String sessionId) {
        Log.d(TAG, "Adding task result to existing chat: " + sessionId);

        // Load existing messages
        List<ChatMessage> messages = chatRepository.loadMessages(sessionId);

        // Add context card
        ChatMessage contextCard = createContextMessage(taskResult);
        messages.add(contextCard);

        // Add agent prompt
        ChatMessage agentPrompt = createAgentPrompt(taskResult);
        messages.add(agentPrompt);

        // Save updated messages
        chatRepository.saveMessages(sessionId, messages);

        Log.d(TAG, "Added " + 2 + " messages to existing chat");
    }

    /**
     * Create a context card message from a task result.
     *
     * @param taskResult The task result
     * @return A ChatMessage configured as a context card
     */
    public ChatMessage createContextMessage(TaskResult taskResult) {
        return ChatMessage.createContextCardMessage(taskResult);
    }

    /**
     * Create an agent prompt message for chat continuation.
     *
     * @param taskResult The task result
     * @return A ChatMessage with the agent prompt
     */
    private ChatMessage createAgentPrompt(TaskResult taskResult) {
        String promptText = String.format(
                "I've added the %s results above. What would you like to clarify or explore?",
                getTaskTypeLabel(taskResult.getType()).toLowerCase()
        );

        // This is a system message that primes the agent
        ChatMessage message = new ChatMessage(promptText, ChatMessage.TYPE_SYSTEM);
        return message;
    }

    /**
     * Get a human-readable label for a task type.
     */
    private String getTaskTypeLabel(int type) {
        switch (type) {
            case TaskResult.TYPE_HEARTBEAT:
                return "Heartbeat";
            case TaskResult.TYPE_CRON_JOB:
                return "Cron Job";
            case TaskResult.TYPE_MANUAL:
                return "Manual Task";
            default:
                return "Task";
        }
    }
}
