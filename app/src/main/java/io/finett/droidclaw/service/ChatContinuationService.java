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

public class ChatContinuationService {
    private static final String TAG = "ChatContinuationService";

    private final Context context;
    private final ChatRepository chatRepository;

    public ChatContinuationService(Context context) {
        this.context = context;
        this.chatRepository = new ChatRepository(context);
    }

    public ChatSession continueInNewChat(TaskResult taskResult) {
        Log.d(TAG, "Creating new chat session for task: " + taskResult.getId());

        ChatSession session = new ChatSession(
                UUID.randomUUID().toString(),
                "Discussing " + getTaskTypeLabel(taskResult.getType()),
                System.currentTimeMillis()
        );

        session.setParentTaskId(taskResult.getId());

        List<ChatMessage> messages = new ArrayList<>();

        ChatMessage contextCard = createContextMessage(taskResult);
        messages.add(contextCard);

        ChatMessage agentPrompt = createAgentPrompt(taskResult);
        messages.add(agentPrompt);

        chatRepository.saveMessages(session.getId(), messages);

        Log.d(TAG, "Created new chat session with " + messages.size() + " initial messages");
        return session;
    }

    public void continueInExistingChat(TaskResult taskResult, String sessionId) {
        Log.d(TAG, "Adding task result to existing chat: " + sessionId);

        List<ChatMessage> messages = chatRepository.loadMessages(sessionId);

        ChatMessage contextCard = createContextMessage(taskResult);
        messages.add(contextCard);

        ChatMessage agentPrompt = createAgentPrompt(taskResult);
        messages.add(agentPrompt);

        chatRepository.saveMessages(sessionId, messages);

        Log.d(TAG, "Added " + 2 + " messages to existing chat");
    }

    public ChatMessage createContextMessage(TaskResult taskResult) {
        return ChatMessage.createContextCardMessage(taskResult);
    }

    private ChatMessage createAgentPrompt(TaskResult taskResult) {
        String promptText = String.format(
                "I've added the %s results above. What would you like to clarify or explore?",
                getTaskTypeLabel(taskResult.getType()).toLowerCase()
        );
        return new ChatMessage(promptText, ChatMessage.TYPE_SYSTEM);
    }

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
