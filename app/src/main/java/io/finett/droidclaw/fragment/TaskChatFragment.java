package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.TextView;

import com.google.android.material.chip.Chip;

import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Fragment for viewing the full conversation of a task execution.
 * Shows the complete hidden session messages in read-only mode.
 */
public class TaskChatFragment extends Fragment {
    private static final String TAG = "TaskChatFragment";
    public static final String ARG_RECORD_ID = "recordId";

    private String recordId;

    private Chip statusChip;
    private TextView durationText;
    private TextView tokensText;
    private TextView toolCallsText;
    private RecyclerView messagesList;

    private ChatAdapter chatAdapter;
    private TaskRepository taskRepository;
    private ChatRepository chatRepository;
    private TaskRecord currentRecord;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get record ID from arguments
        if (getArguments() != null) {
            recordId = getArguments().getString(ARG_RECORD_ID);
        }

        taskRepository = new TaskRepository(requireContext());
        chatRepository = new ChatRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusChip = view.findViewById(R.id.statusChip);
        durationText = view.findViewById(R.id.durationText);
        tokensText = view.findViewById(R.id.tokensText);
        toolCallsText = view.findViewById(R.id.toolCallsText);
        messagesList = view.findViewById(R.id.messagesList);

        // Load task record
        if (recordId != null) {
            currentRecord = taskRepository.getTaskRecord(recordId);
        }

        // Set up toolbar title in MainActivity
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                String title = currentRecord != null ? currentRecord.getCronJobName() : "Task Chat";
                ((MainActivity) getActivity()).getSupportActionBar().setTitle(title);
                ((MainActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // Set up RecyclerView with ChatAdapter
        chatAdapter = new ChatAdapter();
        messagesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesList.setAdapter(chatAdapter);

        // Load data
        if (currentRecord != null) {
            renderTaskDetails();
            loadConversation();
        }
    }

    /**
     * Render task execution metadata.
     */
    private void renderTaskDetails() {
        // Status chip
        statusChip.setText(currentRecord.getStatusDisplayText());

        // Duration
        durationText.setText(currentRecord.getDurationDisplayString());

        // Tokens
        tokensText.setText(String.valueOf(currentRecord.getTokensUsed()));

        // Tool calls
        toolCallsText.setText(String.valueOf(currentRecord.getToolCallsCount()));
    }

    /**
     * Load full conversation from hidden session.
     */
    private void loadConversation() {
        if (currentRecord.getSessionId() == null) {
            return;
        }

        List<ChatMessage> messages = chatRepository.loadMessages(currentRecord.getSessionId());
        chatAdapter.setMessages(messages);
    }
}
