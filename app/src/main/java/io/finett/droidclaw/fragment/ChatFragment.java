package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
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

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.ConversationSummarizer;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.agent.MemoryContextBuilder;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
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
    private TextView statusSubText;
    private ChatAdapter chatAdapter;
    private LlmApiService apiService;
    private SettingsManager settingsManager;
    private ChatRepository chatRepository;
    private MemoryRepository memoryRepository;
    private ToolRegistry toolRegistry;
    private AgentLoop agentLoop;
    private IdentityManager identityManager;
    private WorkspaceManager workspaceManager;
    private String currentSessionId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        settingsManager = new SettingsManager(requireContext());
        apiService = new LlmApiService(settingsManager);
        chatRepository = new ChatRepository(requireContext());
        
        // Initialize workspace and identity
        workspaceManager = new WorkspaceManager(requireContext());
        try {
            workspaceManager.initializeWithSkills();
            Log.d(TAG, "Workspace initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize workspace", e);
        }
        
        identityManager = new IdentityManager(requireContext(), workspaceManager);
        
        // Initialize memory system
        memoryRepository = new MemoryRepository(workspaceManager);
        
        // Get context window from selected model configuration
        int contextWindow = getModelContextWindow();
        ConversationSummarizer summarizer = new ConversationSummarizer(apiService, memoryRepository, contextWindow);
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(memoryRepository);
        
        // Create ToolRegistry with SettingsManager for shell access settings
        toolRegistry = new ToolRegistry(requireContext(), settingsManager);
        
        // Create AgentLoop with full memory support
        agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager, summarizer, memoryContext);
        
        // Load and set identity context
        loadIdentityContext();
        
        // Get session ID from arguments
        Bundle args = getArguments();
        if (args != null) {
            currentSessionId = args.getString(ARG_SESSION_ID);
            Log.d(TAG, "onCreate: Received session_id: " + currentSessionId);
        } else {
            Log.w(TAG, "onCreate: No session_id received");
        }
    }
    
    /**
     * Loads identity context (soul.md and user.md) and sets it in the agent loop.
     */
    private void loadIdentityContext() {
        try {
            List<ChatMessage> identityMessages = identityManager.getIdentityMessages();
            agentLoop.setIdentityContext(identityMessages);
            Log.d(TAG, "Loaded identity context: " + identityMessages.size() + " message(s)");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load identity context, continuing without it", e);
            // Continue without identity context - not critical
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
        statusSubText = view.findViewById(R.id.statusSubText);
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
                updateStatus(status, null);
            }

            @Override
            public void onToolCall(String toolName, String arguments) {
                Log.d(TAG, "Tool call: " + toolName + " with args: " + arguments);
                
                // Enhanced status messages for different tool types
                String statusMessage = getToolStatusMessage(toolName, arguments);
                String iterationInfo = "Step " + agentLoop.getIterationCount() + "/20";
                updateStatus(statusMessage, iterationInfo);
            }

            @Override
            public void onToolResult(String toolName, String result) {
                Log.d(TAG, "Tool result: " + toolName + " -> " + result.substring(0, Math.min(100, result.length())));
                
                // Show brief result feedback
                String iterationInfo = "Step " + agentLoop.getIterationCount() + "/20";
                updateStatus("✓ " + formatToolName(toolName) + " completed", iterationInfo);
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
                if (!isAdded() || getContext() == null) {
                    Log.w(TAG, "onError: Fragment not attached, ignoring error: " + error);
                    return;
                }
                setLoading(false);
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onApprovalRequired(String toolName, String description, JsonObject arguments,
                                           AgentLoop.ApprovalCallback approvalCallback) {
                // Show approval dialog on UI thread
                requireActivity().runOnUiThread(() -> {
                    showApprovalDialog(toolName, description, approvalCallback);
                });
            }
        });
    }
    
    /**
     * Show a dialog asking user to approve or deny a tool execution.
     */
    private void showApprovalDialog(String toolName, String description, AgentLoop.ApprovalCallback approvalCallback) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Approve Tool Execution?")
            .setMessage("Tool: " + formatToolName(toolName) + "\n\n" + description)
            .setPositiveButton("Approve", (dialog, which) -> {
                Log.d(TAG, "User approved tool: " + toolName);
                approvalCallback.onApproved();
            })
            .setNegativeButton("Deny", (dialog, which) -> {
                Log.d(TAG, "User denied tool: " + toolName);
                approvalCallback.onDenied();
            })
            .setCancelable(false)
            .show();
    }

    private void setLoading(boolean loading) {
        if (statusContainer != null) {
            statusContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        sendButton.setEnabled(!loading);
        messageInput.setEnabled(!loading);
    }

    private void updateStatus(String status, String subStatus) {
        if (statusText != null) {
            statusText.setText(status);
        }
        if (statusSubText != null) {
            if (subStatus != null) {
                statusSubText.setText(subStatus);
                statusSubText.setVisibility(View.VISIBLE);
            } else {
                statusSubText.setVisibility(View.GONE);
            }
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
    
    /**
     * Get a user-friendly status message for tool execution.
     */
    private String getToolStatusMessage(String toolName, String arguments) {
        // Check for skill-related tools
        if (toolName.equals("list_files") && arguments.contains(".agent/skills")) {
            return "🔍 Discovering available skills...";
        } else if (toolName.equals("read_file") && arguments.contains(".agent/skills")) {
            return "📖 Loading skill definition...";
        } else if (toolName.equals("read_file") && arguments.contains("SKILL.md")) {
            return "📖 Reading skill instructions...";
        }
        
        // Tool-specific messages
        switch (toolName) {
            case "execute_shell":
                return "💻 Running shell command...";
            case "execute_python":
                return "🐍 Executing Python script...";
            case "pip_install":
                return "📦 Installing Python package...";
            case "read_file":
                return "📄 Reading file...";
            case "write_file":
                return "✏️ Writing file...";
            case "edit_file":
                return "✏️ Editing file...";
            case "list_files":
                return "📋 Listing directory...";
            case "search_files":
                return "🔍 Searching files...";
            case "delete_file":
                return "🗑️ Deleting file...";
            case "file_info":
                return "ℹ️ Getting file info...";
            default:
                return "🔧 Executing: " + formatToolName(toolName);
        }
    }
    
    /**
     * Format tool name from snake_case to Title Case.
     */
    private String formatToolName(String toolName) {
        String[] parts = toolName.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(part.substring(0, 1).toUpperCase())
                    .append(part.substring(1).toLowerCase());
        }
        return formatted.toString();
    }
    
    /**
     * Get the context window size from the currently selected model.
     * Falls back to default if model is not configured.
     */
    private int getModelContextWindow() {
        Object[] selected = settingsManager.getSelectedProviderAndModel();
        if (selected != null && selected[1] instanceof io.finett.droidclaw.model.Model) {
            io.finett.droidclaw.model.Model model = (io.finett.droidclaw.model.Model) selected[1];
            int contextWindow = model.getContextWindow();
            Log.d(TAG, "Using model context window: " + contextWindow);
            return contextWindow;
        }
        
        // Fallback to default
        Log.w(TAG, "Model not configured, using default context window: 4096");
        return 4096;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        apiService.cancelAllRequests();
    }
}