package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.gson.JsonObject;

import java.io.IOException;
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
import io.finett.droidclaw.filesystem.FileUploadManager;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.service.ChatContinuationService;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";
    public static final String ARG_SESSION_ID = "session_id";

    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton attachButton;
    private View statusContainer;
    private HorizontalScrollView attachmentBarContainer;
    private LinearLayout attachmentBar;
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
    private FileUploadManager fileUploadManager;
    private ChatContinuationService continuationService;
    private String currentSessionId;
    private TaskResult pendingTaskResult;

    // Pending file attachments (selected but not yet sent)
    private List<FileAttachment> pendingAttachments = new ArrayList<>();

    // File picker launcher
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsManager = new SettingsManager(requireContext());
        apiService = new LlmApiService(settingsManager);
        chatRepository = new ChatRepository(requireContext());
        continuationService = new ChatContinuationService(requireContext());

        // Check for task result in arguments
        Bundle taskArgs = getArguments();
        if (taskArgs != null) {
            pendingTaskResult = (TaskResult) taskArgs.getSerializable(ZenResultFragment.ARG_TASK_RESULT);
            if (pendingTaskResult != null) {
                Log.d(TAG, "Received task result for continuation: " + pendingTaskResult.getId());
            }
        }

        // Initialize workspace and identity
        workspaceManager = new WorkspaceManager(requireContext());
        try {
            workspaceManager.initializeWithSkills();
            Log.d(TAG, "Workspace initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize workspace", e);
        }

        // Initialize file upload manager
        fileUploadManager = new FileUploadManager(requireContext(), workspaceManager);

        // Register file picker launcher
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadSelectedFile(uri);
                }
            }
        );

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
        attachButton = view.findViewById(R.id.attachButton);
        statusContainer = view.findViewById(R.id.statusContainer);
        attachmentBarContainer = view.findViewById(R.id.attachmentBarContainer);
        attachmentBar = view.findViewById(R.id.attachmentBar);
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

            // Update toolbar with session title
            updateToolbarTitle();
        } else {
            Log.d(TAG, "loadChatHistory: No saved messages for session: " + currentSessionId);

            // If we have a pending task result, add context messages
            if (pendingTaskResult != null) {
                Log.d(TAG, "loadChatHistory: Adding task result context messages");
                addTaskResultContext(pendingTaskResult);
                pendingTaskResult = null; // Clear after use
            }

            // Update toolbar with session title (likely "New Chat")
            updateToolbarTitle();
        }
    }

    /**
     * Update the toolbar title with the current session's title.
     */
    private void updateToolbarTitle() {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }
        String title = ((MainActivity) requireActivity()).getCurrentSessionTitle();
        ((MainActivity) requireActivity()).setToolbarTitle(title);
    }

    /**
     * Add task result context messages to a new chat session.
     */
    private void addTaskResultContext(TaskResult taskResult) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // Add context card
        ChatMessage contextCard = continuationService.createContextMessage(taskResult);
        messages.add(contextCard);
        chatAdapter.addMessage(contextCard);
        
        // Add agent prompt
        ChatMessage agentPrompt = new ChatMessage(
            String.format("I've added the %s results above. What would you like to clarify or explore?", 
                         TaskResult.typeToString(taskResult.getType()).toLowerCase()),
            ChatMessage.TYPE_SYSTEM
        );
        messages.add(agentPrompt);
        chatAdapter.addMessage(agentPrompt);
        
        // Save messages
        saveMessages();
        scrollToBottom();
        
        Log.d(TAG, "Added task result context: " + messages.size() + " messages");
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

    /**
     * Generate an LLM-based title if the session still has a default title.
     * Called after the first assistant response.
     */
    private void generateTitleIfNeeded(List<ChatMessage> updatedHistory) {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }

        MainActivity activity = (MainActivity) requireActivity();
        String currentTitle = activity.getCurrentSessionTitle();

        // Only generate if still using a default title
        boolean isDefaultTitle = currentTitle == null
                || currentTitle.trim().isEmpty()
                || getString(R.string.new_chat).equals(currentTitle);

        if (!isDefaultTitle) {
            return;
        }

        String fallbackTitle = chatRepository.generateTitleFromMessage(
                getFirstUserMessageContent(updatedHistory)
        );

        chatRepository.generateTitleWithLLM(apiService, updatedHistory, fallbackTitle,
                new ChatRepository.TitleGenerationCallback() {
                    @Override
                    public void onTitleGenerated(String title) {
                        if (isAdded() && getContext() != null) {
                            // Update the session title directly in the activity
                            activity.updateSessionTitle(currentSessionId, title);
                            // Update the toolbar
                            updateToolbarTitle();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Fallback already applied in callback
                        Log.w(TAG, "Title generation error: " + error);
                    }
                });
    }

    /**
     * Get the content of the first user message from the history.
     */
    private String getFirstUserMessageContent(List<ChatMessage> messages) {
        if (messages == null) return null;
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_USER) {
                return msg.getContent();
            }
        }
        return null;
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        attachButton.setOnClickListener(v -> launchFilePicker());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    /**
     * Launch the system file picker to select a file for attachment.
     */
    private void launchFilePicker() {
        filePickerLauncher.launch("*/*");
    }

    /**
     * Handle the selected file from the picker. Uploads it to the workspace uploads directory.
     */
    private void uploadSelectedFile(Uri uri) {
        if (fileUploadManager == null) {
            Toast.makeText(requireContext(), "File upload not available", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                FileUploadManager.UploadResult result = fileUploadManager.uploadFile(uri, null);
                FileAttachment attachment = new FileAttachment(
                    result.getFilename(),
                    result.getOriginalName(),
                    result.getAbsolutePath(),
                    result.getMimeType()
                );

                requireActivity().runOnUiThread(() -> {
                    pendingAttachments.add(attachment);
                    renderPendingAttachments();
                    Toast.makeText(requireContext(),
                        "Attached: " + result.getOriginalName(),
                        Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "File attached: " + result.getOriginalName() + " -> " + result.getFilename());
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to upload file", e);
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(),
                        "Failed to attach file: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    /**
     * Clear pending attachments and return a copy (consumed when sending message).
     */
    private List<FileAttachment> consumePendingAttachments() {
        List<FileAttachment> copy = new ArrayList<>(pendingAttachments);
        pendingAttachments.clear();
        renderPendingAttachments();
        return copy;
    }

    /**
     * Renders pending attachment chips in the bar above the input.
     */
    private void renderPendingAttachments() {
        if (attachmentBar == null || attachmentBarContainer == null) return;

        attachmentBar.removeAllViews();

        if (pendingAttachments.isEmpty()) {
            attachmentBarContainer.setVisibility(View.GONE);
            return;
        }

        attachmentBarContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < pendingAttachments.size(); i++) {
            final int index = i;
            FileAttachment attachment = pendingAttachments.get(i);

            Chip chip = (Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_pending_attachment_chip, attachmentBar, false);

            int iconRes = attachment.getDisplayIconResId();
            String displayName = truncateName(attachment.getOriginalName());
            chip.setText(displayName);
            chip.setChipIconResource(iconRes);

            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> removePendingAttachment(index));

            attachmentBar.addView(chip);
        }
    }

    /**
     * Removes a pending attachment at the given index.
     */
    private void removePendingAttachment(int index) {
        if (index >= 0 && index < pendingAttachments.size()) {
            String name = pendingAttachments.get(index).getOriginalName();
            pendingAttachments.remove(index);
            renderPendingAttachments();
            Toast.makeText(requireContext(), "Removed: " + name, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Truncates a filename to 15 characters with "..." if longer.
     */
    private String truncateName(String name) {
        if (name == null) return "";
        if (name.length() > 15) {
            return name.substring(0, 15) + "...";
        }
        return name;
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

        // Consume pending attachments
        List<FileAttachment> attachments = consumePendingAttachments();

        ChatMessage userMessage;
        if (!attachments.isEmpty()) {
            userMessage = ChatMessage.createUserMessageWithAttachments(messageText, attachments);
        } else {
            userMessage = new ChatMessage(messageText, ChatMessage.TYPE_USER);
        }
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

                // Generate LLM-based title if this is the first assistant response
                generateTitleIfNeeded(updatedHistory);

                // Update toolbar title in case the session was renamed
                updateToolbarTitle();
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
            return "Discovering available skills...";
        } else if (toolName.equals("read_file") && arguments.contains(".agent/skills")) {
            return "Loading skill definition...";
        } else if (toolName.equals("read_file") && arguments.contains("SKILL.md")) {
            return "Reading skill instructions...";
        }

        // Tool-specific messages
        switch (toolName) {
            case "execute_shell":
                return "Running shell command...";
            case "execute_python":
                return "Executing Python script...";
            case "pip_install":
                return "Installing Python package...";
            case "read_file":
                return "Reading file...";
            case "write_file":
                return "Writing file...";
            case "edit_file":
                return "Editing file...";
            case "list_files":
                return "Listing directory...";
            case "search_files":
                return "Searching files...";
            case "delete_file":
                return "Deleting file...";
            case "file_info":
                return "Getting file info...";
            default:
                return "Executing: " + formatToolName(toolName);
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
    public void onResume() {
        super.onResume();
        // Reload settings in case the user changed provider/model in the Settings screen
        settingsManager = new SettingsManager(requireContext());
        apiService = new LlmApiService(settingsManager);
        // Propagate the refreshed service to the agent loop components
        // (ConversationSummarizer and AgentLoop hold their own apiService reference)
        int contextWindow = getModelContextWindow();
        io.finett.droidclaw.agent.ConversationSummarizer summarizer =
                new io.finett.droidclaw.agent.ConversationSummarizer(apiService, memoryRepository, contextWindow);
        io.finett.droidclaw.agent.MemoryContextBuilder memoryContext =
                new io.finett.droidclaw.agent.MemoryContextBuilder(memoryRepository);
        agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager, summarizer, memoryContext);
        loadIdentityContext();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        apiService.cancelAllRequests();
    }
}