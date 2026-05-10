package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
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
import java.io.InputStream;
import java.io.OutputStream;
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
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.service.ChatContinuationService;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.ChatExportManager;
import io.finett.droidclaw.util.ChatImportManager;
import io.finett.droidclaw.util.ChatSearchManager;
import io.finett.droidclaw.util.SettingsManager;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";
    public static final String ARG_SESSION_ID = "session_id";

    // Chat UI
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

    // Search bar views (from included layout)
    private View searchBar;
    private EditText searchInput;
    private TextView searchResultCount;
    private ImageButton searchPrevButton;
    private ImageButton searchNextButton;
    private ImageButton searchCloseButton;

    // Core components
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

    // Search state
    private ChatSearchManager chatSearchManager;
    private List<ChatSearchManager.SearchResult> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;

    // Export / import managers
    private ChatExportManager chatExportManager;
    private ChatImportManager chatImportManager;

    // Pending attachments
    private List<FileAttachment> pendingAttachments = new ArrayList<>();

    // Activity result launchers
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> importPickerLauncher;

    // Export launchers (SAF)
    private ActivityResultLauncher<String> exportMarkdownLauncher;
    private ActivityResultLauncher<String> exportJsonLauncher;
    private ActivityResultLauncher<String> exportPdfLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        settingsManager = new SettingsManager(requireContext());
        chatRepository = new ChatRepository(requireContext());
        continuationService = new ChatContinuationService(requireContext());

        // Use Activity-scoped API service so requests survive chat switches
        if (requireActivity() instanceof MainActivity) {
            apiService = ((MainActivity) requireActivity()).getApiService();
        } else {
            apiService = new LlmApiService(settingsManager);
        }

        Bundle taskArgs = getArguments();
        if (taskArgs != null) {
            pendingTaskResult = (TaskResult) taskArgs.getSerializable(ZenResultFragment.ARG_TASK_RESULT);
            if (pendingTaskResult != null) {
                Log.d(TAG, "Received task result for continuation: " + pendingTaskResult.getId());
            }
        }

        workspaceManager = new WorkspaceManager(requireContext());
        try {
            workspaceManager.initializeWithSkills();
            Log.d(TAG, "Workspace initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize workspace", e);
        }

        fileUploadManager = new FileUploadManager(requireContext(), workspaceManager);

        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadSelectedFile(uri);
                }
            }
        );

        // Import file picker – accepts text/* files
        importPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handleImportFile(uri);
                }
            }
        );

        // Export launchers using SAF CREATE_DOCUMENT
        exportMarkdownLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/markdown"),
            uri -> {
                if (uri != null) runExport(uri, "md");
            }
        );
        exportJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) runExport(uri, "json");
            }
        );
        exportPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null) runExport(uri, "pdf");
            }
        );

        identityManager = new IdentityManager(requireContext(), workspaceManager);
        memoryRepository = new MemoryRepository(workspaceManager);

        int contextWindow = getModelContextWindow();
        ConversationSummarizer summarizer = new ConversationSummarizer(apiService, memoryRepository, contextWindow);
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(memoryRepository);

        toolRegistry = new ToolRegistry(requireContext(), settingsManager);
        agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager, summarizer, memoryContext);

        loadIdentityContext();

        chatSearchManager = new ChatSearchManager();
        chatExportManager = new ChatExportManager(requireContext());
        chatImportManager = new ChatImportManager();

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
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupClickListeners();
        setupSearchBar();
    }

    private void initViews(View view) {
        recyclerView           = view.findViewById(R.id.recyclerView);
        messageInput           = view.findViewById(R.id.messageInput);
        sendButton             = view.findViewById(R.id.sendButton);
        attachButton           = view.findViewById(R.id.attachButton);
        statusContainer        = view.findViewById(R.id.statusContainer);
        attachmentBarContainer = view.findViewById(R.id.attachmentBarContainer);
        attachmentBar          = view.findViewById(R.id.attachmentBar);
        progressBar            = view.findViewById(R.id.progressBar);
        statusText             = view.findViewById(R.id.statusText);
        statusSubText          = view.findViewById(R.id.statusSubText);

        // Search bar (included layout)
        searchBar         = view.findViewById(R.id.searchBar);
        searchInput       = searchBar.findViewById(R.id.searchInput);
        searchResultCount = searchBar.findViewById(R.id.searchResultCount);
        searchPrevButton  = searchBar.findViewById(R.id.searchPrevButton);
        searchNextButton  = searchBar.findViewById(R.id.searchNextButton);
        searchCloseButton = searchBar.findViewById(R.id.searchCloseButton);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        loadChatHistory();
    }

    private void setupSearchBar() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchPrevButton.setOnClickListener(v -> navigateSearch(false));
        searchNextButton.setOnClickListener(v -> navigateSearch(true));
        searchCloseButton.setOnClickListener(v -> hideSearchBar());
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_chat, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            toggleSearchBar();
            return true;
        } else if (id == R.id.action_export) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_import) {
            showImportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void toggleSearchBar() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            hideSearchBar();
        } else {
            showSearchBar();
        }
    }

    private void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        searchBar.startAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.slide_in_left));
        searchInput.requestFocus();
        searchInput.setText("");
    }

    private void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        searchInput.setText("");
        searchResults.clear();
        currentSearchIndex = -1;
        chatAdapter.setSearchQuery(null);
        updateSearchResultCount();
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear();
            currentSearchIndex = -1;
            chatAdapter.setSearchQuery(null);
            updateSearchResultCount();
            return;
        }

        chatAdapter.setSearchQuery(query);
        searchResults = chatSearchManager.search(chatAdapter.getMessages(), query);

        if (!searchResults.isEmpty()) {
            currentSearchIndex = 0;
            scrollToSearchResult(searchResults.get(0).position);
        } else {
            currentSearchIndex = -1;
        }
        updateSearchResultCount();
    }

    private void navigateSearch(boolean forward) {
        if (searchResults.isEmpty()) return;

        if (forward) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        } else {
            currentSearchIndex = (currentSearchIndex - 1 + searchResults.size()) % searchResults.size();
        }
        scrollToSearchResult(searchResults.get(currentSearchIndex).position);
        updateSearchResultCount();
    }

    private void scrollToSearchResult(int position) {
        recyclerView.scrollToPosition(position);
    }

    private void updateSearchResultCount() {
        if (searchResults.isEmpty()) {
            if (searchInput.getText().length() > 0) {
                searchResultCount.setText(getString(R.string.no_search_results));
                searchResultCount.setVisibility(View.VISIBLE);
            } else {
                searchResultCount.setVisibility(View.GONE);
            }
            searchPrevButton.setVisibility(View.GONE);
            searchNextButton.setVisibility(View.GONE);
        } else {
            searchResultCount.setText(getString(R.string.search_result_count,
                    currentSearchIndex + 1, searchResults.size()));
            searchResultCount.setVisibility(View.VISIBLE);
            searchPrevButton.setVisibility(View.VISIBLE);
            searchNextButton.setVisibility(View.VISIBLE);
        }
    }


    private void showExportDialog() {
        String[] options = {
            getString(R.string.export_as_markdown),
            getString(R.string.export_as_json),
            getString(R.string.export_as_pdf)
        };

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.export_choose_format))
            .setItems(options, (dialog, which) -> {
                ChatSession session = getCurrentSession();
                switch (which) {
                    case 0:
                        exportMarkdownLauncher.launch(
                                chatExportManager.buildExportFileName(session, "md"));
                        break;
                    case 1:
                        exportJsonLauncher.launch(
                                chatExportManager.buildExportFileName(session, "json"));
                        break;
                    case 2:
                        exportPdfLauncher.launch(
                                chatExportManager.buildExportFileName(session, "pdf"));
                        break;
                }
            })
            .show();
    }

    private void runExport(Uri uri, String format) {
        List<ChatMessage> messages = chatAdapter.getMessages();
        ChatSession session = getCurrentSession();

        new Thread(() -> {
            try {
                try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Cannot open output stream");
                    switch (format) {
                        case "md":
                            chatExportManager.exportToMarkdown(session, messages, out);
                            break;
                        case "json":
                            chatExportManager.exportToJson(session, messages, out);
                            break;
                        case "pdf":
                            chatExportManager.exportToPdf(session, messages, out);
                            break;
                    }
                }
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(),
                            getString(R.string.export_success), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(),
                            getString(R.string.export_error, e.getMessage()),
                            Toast.LENGTH_LONG).show());
            }
        }).start();
    }


    private void showImportDialog() {
        String[] options = {
            getString(R.string.import_from_json),
            getString(R.string.import_from_markdown)
        };

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.import_chat))
            .setItems(options, (dialog, which) -> {
                // Store the chosen format for later use in the callback
                // We use a tag on the view as a simple storage mechanism
                searchBar.setTag(which == 0 ? "json" : "md");
                importPickerLauncher.launch("*/*");
            })
            .show();
    }

    private void handleImportFile(Uri uri) {
        String format = searchBar.getTag() instanceof String ? (String) searchBar.getTag() : "json";

        new Thread(() -> {
            try {
                ChatImportManager.ImportResult result;
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Cannot open input stream");
                    if ("md".equals(format)) {
                        result = chatImportManager.importFromMarkdown(in);
                    } else {
                        result = chatImportManager.importFromJson(in);
                    }
                }

                final ChatImportManager.ImportResult finalResult = result;
                requireActivity().runOnUiThread(() -> showImportActionDialog(finalResult));

            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(),
                            getString(R.string.import_error, e.getMessage()),
                            Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showImportActionDialog(ChatImportManager.ImportResult result) {
        if (result.messages.isEmpty()) {
            Toast.makeText(requireContext(),
                    getString(R.string.import_error, "No messages found in file"),
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] options = {
            getString(R.string.import_append),
            getString(R.string.import_replace)
        };

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.import_choose_action))
            .setMessage(getString(R.string.import_success, result.messages.size())
                    + (result.warnings.isEmpty() ? ""
                    : "\n" + getString(R.string.import_warnings, result.warnings.size())))
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Append to current chat
                    for (ChatMessage message : result.messages) {
                        chatAdapter.addMessage(message);
                    }
                    saveMessages();
                    scrollToBottom();
                } else {
                    // Replace – start a new session with the imported messages
                    if (requireActivity() instanceof MainActivity) {
                        String newSessionId = ((MainActivity) requireActivity())
                                .createNewSession(result.sessionTitle != null
                                        ? result.sessionTitle : "Imported Chat");
                        currentSessionId = newSessionId;
                        chatAdapter.setMessages(result.messages);
                        saveMessages();
                        updateToolbarTitle();
                        scrollToBottom();
                    }
                }
                Toast.makeText(requireContext(),
                        getString(R.string.import_success, result.messages.size()),
                        Toast.LENGTH_SHORT).show();
            })
            .show();
    }


    @Nullable
    private ChatSession getCurrentSession() {
        if (currentSessionId == null) return null;
        List<ChatSession> sessions = chatRepository.loadSessions();
        for (ChatSession s : sessions) {
            if (currentSessionId.equals(s.getId())) return s;
        }
        return null;
    }


    private void loadChatHistory() {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Log.w(TAG, "loadChatHistory: No session ID, cannot load messages");
            return;
        }

        List<ChatMessage> savedMessages = chatRepository.loadMessages(currentSessionId);
        if (!savedMessages.isEmpty()) {
            chatAdapter.setMessages(savedMessages);
            Log.d(TAG, "loadChatHistory: Loaded " + savedMessages.size()
                    + " messages for session: " + currentSessionId);
            scrollToBottom();
            updateToolbarTitle();
        } else {
            Log.d(TAG, "loadChatHistory: No saved messages for session: " + currentSessionId);

            if (pendingTaskResult != null) {
                Log.d(TAG, "loadChatHistory: Adding task result context messages");
                addTaskResultContext(pendingTaskResult);
                pendingTaskResult = null;
            }

            // No saved messages – new chat
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

        ChatMessage contextCard = continuationService.createContextMessage(taskResult);
        messages.add(contextCard);
        chatAdapter.addMessage(contextCard);

        ChatMessage agentPrompt = new ChatMessage(
            String.format("I've added the %s results above. What would you like to clarify or explore?",
                         TaskResult.typeToString(taskResult.getType()).toLowerCase()),
            ChatMessage.TYPE_SYSTEM
        );
        messages.add(agentPrompt);
        chatAdapter.addMessage(agentPrompt);

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
     */
    private void generateTitleIfNeeded(List<ChatMessage> updatedHistory) {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }

        MainActivity activity = (MainActivity) requireActivity();
        String currentTitle = activity.getCurrentSessionTitle();

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
                            activity.updateSessionTitle(currentSessionId, title);
                            updateToolbarTitle();
                        }
                    }

                    @Override
                    public void onError(String error) {
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
     * Handle the selected file from the picker.
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
                    Log.d(TAG, "File attached: " + result.getOriginalName()
                            + " -> " + result.getFilename());
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
            Toast.makeText(requireContext(), "Please configure API settings first",
                    Toast.LENGTH_LONG).show();
            Navigation.findNavController(requireView()).navigate(R.id.settingsFragment);
            return;
        }

        List<FileAttachment> attachments = consumePendingAttachments();

        ChatMessage userMessage;
        if (!attachments.isEmpty()) {
            userMessage = ChatMessage.createUserMessageWithAttachments(messageText, attachments);
        } else {
            userMessage = new ChatMessage(messageText, ChatMessage.TYPE_USER);
        }
        chatAdapter.addMessage(userMessage);
        scrollToBottom();

        saveMessages();
        updateSessionMetadata(messageText);
        Log.d(TAG, "sendMessage: Added and saved user message. Total: "
                + chatAdapter.getItemCount());

        messageInput.setText("");
        setLoading(true);

        List<ChatMessage> conversationHistory = new ArrayList<>(chatAdapter.getMessages());

        agentLoop.start(conversationHistory, new AgentLoop.AgentCallback() {
            @Override
            public void onProgress(String status) {
                updateStatus(status, null);
            }

            @Override
            public void onToolCall(String toolName, String arguments) {
                Log.d(TAG, "Tool call: " + toolName + " with args: " + arguments);

                boolean isBackground = arguments.contains("\"background\":true")
                        || arguments.contains("\"background\": true");
                String statusMessage = isBackground
                        ? "Dispatching " + formatToolName(toolName) + " to background..."
                        : getToolStatusMessage(toolName, arguments);
                String iterationInfo = "Step " + agentLoop.getIterationCount() + "/20";
                updateStatus(statusMessage, iterationInfo);
            }

            @Override
            public void onToolResult(String toolName, String result) {
                Log.d(TAG, "Tool result: " + toolName + " -> "
                        + result.substring(0, Math.min(100, result.length())));
                String iterationInfo = "Step " + agentLoop.getIterationCount() + "/20";
                updateStatus("✓ " + formatToolName(toolName) + " completed", iterationInfo);
            }

            @Override
            public void onComplete(String finalResponse, List<ChatMessage> updatedHistory) {
                setLoading(false);

                chatAdapter.setMessages(updatedHistory);
                scrollToBottom();

                saveMessages();
                updateSessionMetadata(null);
                Log.d(TAG, "onComplete: Agent completed. Total messages: "
                        + chatAdapter.getItemCount());

                generateTitleIfNeeded(updatedHistory);
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
            public void onApprovalRequired(String toolName, String description,
                                           JsonObject arguments,
                                           AgentLoop.ApprovalCallback approvalCallback) {
                requireActivity().runOnUiThread(() ->
                    showApprovalDialog(toolName, description, approvalCallback));
            }
        });
    }

    /**
     * Show a dialog asking user to approve or deny a tool execution.
     */
    private void showApprovalDialog(String toolName, String description,
                                    AgentLoop.ApprovalCallback approvalCallback) {
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
        if (toolName.equals("list_files") && arguments.contains(".agent/skills")) {
            return "Discovering available skills...";
        } else if (toolName.equals("read_file") && arguments.contains(".agent/skills")) {
            return "Loading skill definition...";
        } else if (toolName.equals("read_file") && arguments.contains("SKILL.md")) {
            return "Reading skill instructions...";
        }

        switch (toolName) {
            case "execute_shell":     return "Running shell command...";
            case "execute_python":    return "Executing Python script...";
            case "pip_install":       return "Installing Python package...";
            case "read_file":         return "Reading file...";
            case "write_file":        return "Writing file...";
            case "edit_file":         return "Editing file...";
            case "list_files":        return "Listing directory...";
            case "search_files":      return "Searching files...";
            case "delete_file":       return "Deleting file...";
            case "file_info":         return "Getting file info...";
            case "kill_background_process":  return "Killing background process...";
            case "list_background_processes": return "Listing background processes...";
            default:                  return "Executing: " + formatToolName(toolName);
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

    private int getModelContextWindow() {
        Object[] selected = settingsManager.getSelectedProviderAndModel();
        if (selected != null && selected[1] instanceof io.finett.droidclaw.model.Model) {
            io.finett.droidclaw.model.Model model = (io.finett.droidclaw.model.Model) selected[1];
            int contextWindow = model.getContextWindow();
            Log.d(TAG, "Using model context window: " + contextWindow);
            return contextWindow;
        }
        Log.w(TAG, "Model not configured, using default context window: 4096");
        return 4096;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload settings in case the user changed provider/model in the Settings screen
        settingsManager = new SettingsManager(requireContext());
        // Refresh the Activity-scoped API service with new settings
        if (requireActivity() instanceof MainActivity) {
            apiService = ((MainActivity) requireActivity()).getApiService();
        }
        // Propagate the refreshed service to the agent loop components
        int contextWindow = getModelContextWindow();
        io.finett.droidclaw.agent.ConversationSummarizer summarizer =
                new io.finett.droidclaw.agent.ConversationSummarizer(
                        apiService, memoryRepository, contextWindow);
        io.finett.droidclaw.agent.MemoryContextBuilder memoryContext =
                new io.finett.droidclaw.agent.MemoryContextBuilder(memoryRepository);
        agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager, summarizer, memoryContext);
        loadIdentityContext();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Do NOT cancel requests here — the API service is Activity-scoped
        // and requests should survive chat switches. Only shut down tools.
        if (toolRegistry != null) {
            toolRegistry.shutdown();
        }
    }
}