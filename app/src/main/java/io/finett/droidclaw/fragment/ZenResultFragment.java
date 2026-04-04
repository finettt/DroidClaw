package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.noties.markwon.Markwon;

/**
 * Zen-focused result viewer for background task results (Heartbeat & Cron Jobs).
 *
 * Provides a clean, minimal UI showing just the response with a "Chat about this" action.
 */
public class ZenResultFragment extends Fragment {
    private static final String TAG = "ZenResultFragment";
    public static final String ARG_RESULT_ID = "resultId";

    private String resultId;
    private TaskResult taskResult;

    private MaterialToolbar toolbar;
    private TextView responseText;
    private LinearLayout metadataContainer;
    private TextView metadataText;
    private Button chatAboutButton;

    private TaskRepository taskRepository;
    private ChatRepository chatRepository;
    private Markwon markwon;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get result ID from arguments
        if (getArguments() != null) {
            resultId = getArguments().getString(ARG_RESULT_ID);
        }

        // Initialize repositories
        taskRepository = new TaskRepository(requireContext());
        chatRepository = new ChatRepository(requireContext());

        // Initialize Markwon
        markwon = Markwon.create(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zen_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        responseText = view.findViewById(R.id.responseText);
        metadataContainer = view.findViewById(R.id.metadataContainer);
        metadataText = view.findViewById(R.id.metadataText);
        chatAboutButton = view.findViewById(R.id.chatAboutButton);

        // Load result
        if (resultId != null) {
            taskResult = taskRepository.getTaskResult(resultId);
            if (taskResult != null) {
                renderResult();
            } else {
                Log.e(TAG, "TaskResult not found: " + resultId);
                showError("Result not found");
            }
        } else {
            Log.e(TAG, "No result ID provided");
            showError("Invalid result");
        }

        // Set up toolbar
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSupportActionBar(toolbar);
            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                ((MainActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        // Chat about this button
        chatAboutButton.setOnClickListener(v -> showChatOptions());
    }

    /**
     * Render the task result in the zen UI.
     */
    private void renderResult() {
        // Set toolbar title
        String title = taskResult.getTaskName() != null ? taskResult.getTaskName() : "Task Result";
        toolbar.setTitle(title);

        // Render markdown response
        String response = taskResult.getResponse() != null ? taskResult.getResponse() : "No response available";
        markwon.setMarkdown(responseText, response);

        // Show execution metadata
        if (taskResult.getExecutedAt() > 0) {
            StringBuilder metadata = new StringBuilder();

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            metadata.append("Executed: ").append(sdf.format(new Date(taskResult.getExecutedAt())));

            if (taskResult.getDurationMs() > 0) {
                long seconds = taskResult.getDurationMs() / 1000;
                metadata.append("\nDuration: ").append(seconds).append("s");
            }

            if (taskResult.getToolCallsCount() > 0) {
                metadata.append("\nTool calls: ").append(taskResult.getToolCallsCount());
            }

            if (taskResult.getTokensUsed() > 0) {
                metadata.append("\nTokens used: ").append(taskResult.getTokensUsed());
            }

            metadataText.setText(metadata.toString());
            metadataContainer.setVisibility(View.VISIBLE);
        }

        // Mark as viewed
        taskResult.setUserViewed(true);
        taskRepository.saveTaskResult(taskResult);
    }

    /**
     * Show error state.
     */
    private void showError(String message) {
        toolbar.setTitle("Error");
        responseText.setText("Unable to load result: " + message);
        chatAboutButton.setVisibility(View.GONE);
    }

    /**
     * Show dialog to choose where to continue conversation.
     */
    private void showChatOptions() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Continue conversation")
                .setItems(new String[]{
                        "Create new chat",
                        "Add to existing chat..."
                }, (dialog, which) -> {
                    if (which == 0) {
                        createNewChatWithContext();
                    } else {
                        showChatPicker();
                    }
                })
                .show();
    }

    /**
     * Create a new chat session with the result as context.
     */
    private void createNewChatWithContext() {
        // Create new chat session
        ChatSession newSession = new ChatSession(
                UUID.randomUUID().toString(),
                "Chat: " + (taskResult.getTaskName() != null ? taskResult.getTaskName() : "Task Result"),
                System.currentTimeMillis()
        );

        // Add context message (the result)
        ChatMessage contextMessage = new ChatMessage(
                "[Background task result]\n\n" + taskResult.getResponse() +
                        "\n\n---\n\nWhat would you like to clarify or explore further?",
                ChatMessage.TYPE_ASSISTANT
        );
        contextMessage.setIsContext(true);
        contextMessage.setContextSourceId(taskResult.getId());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(contextMessage);

        // Save session and messages
        chatRepository.saveSessions(List.of(newSession));
        chatRepository.saveMessages(newSession.getId(), messages);

        // Update task result
        taskResult.setUserChatted(true);
        taskResult.setContinuedInSessionId(newSession.getId());
        taskRepository.saveTaskResult(taskResult);

        // Navigate to chat
        navigateToChat(newSession.getId());
    }

    /**
     * Show dialog to pick existing chat session.
     */
    private void showChatPicker() {
        List<ChatSession> sessions = chatRepository.loadSessions();
        if (sessions.isEmpty()) {
            showSimpleMessage("No chat sessions available");
            return;
        }

        String[] sessionNames = sessions.stream()
                .map(s -> s.getTitle() != null ? s.getTitle() : "Untitled")
                .toArray(String[]::new);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select chat")
                .setItems(sessionNames, (dialog, which) -> {
                    ChatSession selectedSession = sessions.get(which);
                    addToExistingChat(selectedSession.getId());
                })
                .show();
    }

    /**
     * Add result to existing chat session.
     */
    private void addToExistingChat(String sessionId) {
        // Load existing messages
        List<ChatMessage> messages = chatRepository.loadMessages(sessionId);

        // Add context separator and result
        ChatMessage contextMessage = new ChatMessage(
                "[Continuing from background task: " + taskResult.getTaskName() + "]\n\n" +
                        taskResult.getResponse() +
                        "\n\n---\n\nWhat would you like to clarify or explore further?",
                ChatMessage.TYPE_ASSISTANT
        );
        contextMessage.setIsContext(true);
        contextMessage.setContextSourceId(taskResult.getId());
        messages.add(contextMessage);

        // Save messages
        chatRepository.saveMessages(sessionId, messages);

        // Update task result
        taskResult.setUserChatted(true);
        taskResult.setContinuedInSessionId(sessionId);
        taskRepository.saveTaskResult(taskResult);

        // Navigate to chat
        navigateToChat(sessionId);
    }

    /**
     * Navigate to a chat session.
     */
    private void navigateToChat(String sessionId) {
        if (getActivity() instanceof MainActivity) {
            Bundle args = new Bundle();
            args.putString(ChatFragment.ARG_SESSION_ID, sessionId);
            ((MainActivity) getActivity()).getNavController().navigate(R.id.chatFragment, args);
            Log.d(TAG, "Navigated to chat: " + sessionId);
        }
    }

    /**
     * Show a simple message dialog.
     */
    private void showSimpleMessage(String message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Info")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
