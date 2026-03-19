package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.util.SettingsManager;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";
    public static final String ARG_SESSION_ID = "session_id";
    
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ProgressBar progressBar;
    private ChatAdapter chatAdapter;
    private LlmApiService apiService;
    private SettingsManager settingsManager;
    private ChatRepository chatRepository;
    private String currentSessionId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        settingsManager = new SettingsManager(requireContext());
        apiService = new LlmApiService(settingsManager);
        chatRepository = new ChatRepository(requireContext());
        
        // Get session ID from arguments
        Bundle args = getArguments();
        if (args != null) {
            currentSessionId = args.getString(ARG_SESSION_ID);
            Log.d(TAG, "onCreate: Received session_id: " + currentSessionId);
        } else {
            Log.w(TAG, "onCreate: No session_id received");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupClickListeners();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);
        progressBar = view.findViewById(R.id.progressBar);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);
        
        // Load messages from repository if session ID exists
        loadChatHistory();
    }
    
    private void loadChatHistory() {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Log.w(TAG, "loadChatHistory: No session ID, cannot load messages");
            return;
        }
        
        List<ChatMessage> savedMessages = chatRepository.loadMessages(currentSessionId);
        if (!savedMessages.isEmpty()) {
            chatAdapter.setMessages(savedMessages);
            Log.d(TAG, "loadChatHistory: Loaded " + savedMessages.size() + " messages for session: " + currentSessionId);
            scrollToBottom();
        } else {
            Log.d(TAG, "loadChatHistory: No saved messages for session: " + currentSessionId);
        }
    }
    
    private void saveMessages() {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Log.w(TAG, "saveMessages: No session ID, cannot save messages");
            return;
        }
        
        chatRepository.saveMessages(currentSessionId, chatAdapter.getMessages());
    }
    
    private void updateSessionMetadata(String firstUserMessage) {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }
        
        ((MainActivity) requireActivity()).updateSessionMetadata(
                currentSessionId,
                firstUserMessage,
                System.currentTimeMillis()
        );
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }

        if (!settingsManager.isConfigured()) {
            Toast.makeText(requireContext(), "Please configure API settings first", Toast.LENGTH_LONG).show();
            Navigation.findNavController(requireView())
                    .navigate(R.id.settingsFragment);
            return;
        }

        ChatMessage userMessage = new ChatMessage(messageText, ChatMessage.TYPE_USER);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();
        
        // Save after adding user message
        saveMessages();
        updateSessionMetadata(messageText);
        Log.d(TAG, "sendMessage: Added and saved user message. Total: " + chatAdapter.getItemCount());

        messageInput.setText("");

        setLoading(true);

        apiService.sendMessage(chatAdapter.getMessages(), new LlmApiService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                setLoading(false);
                ChatMessage assistantMessage = new ChatMessage(response, ChatMessage.TYPE_ASSISTANT);
                chatAdapter.addMessage(assistantMessage);
                scrollToBottom();
                
                // Save after adding assistant message
                saveMessages();
                updateSessionMetadata(null);
                Log.d(TAG, "onSuccess: Added and saved assistant message. Total: " + chatAdapter.getItemCount());
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        messageInput.setEnabled(!loading);
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        apiService.cancelAllRequests();
    }
}