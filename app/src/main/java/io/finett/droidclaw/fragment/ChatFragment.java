package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";
    public static final String ARG_SESSION_ID = "session_id";
    
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private View statusContainer;
    private ProgressBar progressBar;
    private TextView statusText;
    private ChatAdapter chatAdapter;
    private LlmApiService apiService;
    private SettingsManager settingsManager;
    private ChatRepository chatRepository;
    private ToolRegistry toolRegistry;
    private AgentLoop agentLoop;
    private String currentSessionId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        settingsManager = new SettingsManager(requireContext());
        apiService = new LlmApiService(settingsManager);
        chatRepository = new ChatRepository(requireContext());
        toolRegistry = new ToolRegistry(requireContext());
        agentLoop = new AgentLoop(apiService, toolRegistry);
        
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
        statusContainer = view.findViewById(R.id.statusContainer);
        progressBar = view.findViewById(R.id.progressBar);
        statusText = view.findViewById(R.id.statusText);
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

        // Use agent loop for tool-enabled conversation
        List<ChatMessage> conversationHistory = new ArrayList<>(chatAdapter.getMessages());
        
        agentLoop.start(conversationHistory, new AgentLoop.AgentCallback() {
            @Override
            public void onProgress(String status) {
                updateStatus(status);
            }

            @Override
            public void onToolCall(String toolName, String arguments) {
                Log.d(TAG, "Tool call: " + toolName + " with args: " + arguments);
                updateStatus("Executing: " + toolName);
            }

            @Override
            public void onToolResult(String toolName, String result) {
                Log.d(TAG, "Tool result: " + toolName + " -> " + result.substring(0, Math.min(100, result.length())));
            }

            @Override
            public void onComplete(String finalResponse, List<ChatMessage> updatedHistory) {
                setLoading(false);
                
                // Update adapter with the full conversation history from the agent
                chatAdapter.setMessages(updatedHistory);
                scrollToBottom();
                
                // Save after adding assistant message
                saveMessages();
                updateSessionMetadata(null);
                Log.d(TAG, "onComplete: Agent completed. Total messages: " + chatAdapter.getItemCount());
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (statusContainer != null) {
            statusContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        sendButton.setEnabled(!loading);
        messageInput.setEnabled(!loading);
    }

    private void updateStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
        if (statusContainer != null) {
            statusContainer.setVisibility(View.VISIBLE);
        }
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        apiService.cancelAllRequests();
    }
}