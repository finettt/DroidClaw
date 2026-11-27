package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.chat.SlashCommand;
import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.FileUploadManager;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.service.AgentExecutionService;
import io.finett.droidclaw.service.ChatContinuationService;
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
    /** True while an agent request is in flight; the send button doubles as stop. */
    private boolean loading = false;
    /** Currently visible tool-approval dialog, if any. */
    private AlertDialog approvalDialog;
    private ImageButton attachButton;
    private HorizontalScrollView attachmentBarContainer;
    private LinearLayout attachmentBar;
    private View commandHintsBar;
    private LinearLayout commandHintsRow;
    /** Last slash-command echo bubble; transient — never saved or sent to the LLM. */
    private ChatMessage pendingCommandEcho;

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
    private WorkspaceManager workspaceManager;
    private FileUploadManager fileUploadManager;
    private ChatContinuationService continuationService;
    private String currentSessionId;
    private TaskResult pendingTaskResult;
    private AgentExecutionService agentExecutionService;
    private boolean serviceBound;
    private boolean serviceBinding;
    private List<ChatMessage> pendingServiceConversationHistory;
    private int pendingServiceContextWindow;

    // ==================== SSE streaming live-render state ====================

    /** Interval between streaming UI refreshes; deltas accumulate in between. */
    private static final long STREAM_FLUSH_INTERVAL_MS = 80;

    /** Accumulated text of the currently streaming assistant bubble. */
    private final StringBuilder streamingBuffer = new StringBuilder();

    /** True while a temporary streaming bubble is present in the adapter. */
    private boolean streamingBubbleActive = false;

    /** The temporary bubble itself, so its current index can be resolved at flush time. */
    private ChatMessage streamingBubble;

    /** Whether the user was at the bottom of the chat before a background flush. */
    private boolean atBottom = true;

    // ==================== Live tool-card / typing-indicator state ====================

    /**
     * Tool-call cards inserted during a live run, in insertion order. Reconciled by
     * {@code setMessages(updatedHistory)} at onComplete/onCancelled — the canonical
     * history now carries one tool-call message per executed call.
     */
    private final List<ChatMessage> liveToolCallCards = new ArrayList<>();

    /** Transient typing placeholder present in the adapter while an LLM phase is in flight. */
    private boolean typingIndicatorActive = false;

    /** Throttled flush runnable (posted to the RecyclerView). */
    private final Runnable streamFlushRunnable = this::flushStreamingBubble;
    private boolean streamFlushScheduled = false;

    private final AgentExecutionService.UICallback agentUiCallback = new AgentExecutionService.UICallback() {
        @Override
        public void onStreamDelta(String delta) {
            if (!isAdded() || delta == null || delta.isEmpty()) {
                return;
            }
            removeTypingIndicator();
            if (!streamingBubbleActive) {
                // New streamed assistant segment — add a temporary bubble that is
                // replaced by the canonical history in onComplete().
                ChatMessage streamingMessage = ChatMessage.createStreamingAssistantMessage();
                chatAdapter.addMessage(streamingMessage);
                streamingBubble = streamingMessage;
                streamingBuffer.setLength(0);
                streamingBubbleActive = true;
                scrollToBottom();
            }
            streamingBuffer.append(delta);
            scheduleStreamFlush();
        }

        @Override
        public void onToolCall(String toolName, String arguments) {
            if (!isAdded()) {
                return;
            }
            // The current streamed text segment is finished; render its last deltas
            // now, then cancel the throttled flush so it cannot touch this segment
            // (or the next one) after the tool boundary.
            flushStreamingBubble();
            resetStreamingState();
            // The first tool card replaces the typing placeholder — the chat now
            // shows what the agent is doing, so the "waiting" dots are done.
            removeTypingIndicator();
            Log.d(TAG, "Tool call: " + toolName + " with args: " + arguments);

            // Live tool card in RUNNING state; replaced by the canonical history at
            // onComplete. Args are truncated the same way the holder renders them.
            JsonObject parsedArgs;
            try {
                parsedArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            } catch (Exception e) {
                parsedArgs = new JsonObject();
            }
            ChatMessage liveCard = ChatMessage.createToolCallMessage(
                    java.util.Collections.singletonList(
                            new LlmApiService.ToolCall(null, toolName, truncateArgs(parsedArgs))));
            liveCard.setRunning(true);
            liveToolCallCards.add(liveCard);
            chatAdapter.addMessage(liveCard);
            scrollToBottom();
        }

        @Override
        public void onToolResult(String toolName, String result) {
            if (!isAdded()) {
                return;
            }
            Log.d(TAG, "Tool result: " + toolName + " -> "
                    + result.substring(0, Math.min(100, result.length())));

            // Pair FIFO within the current segment: the oldest running card for this
            // tool name is the one that just finished (execution is serial in practice).
            ChatMessage matched = null;
            for (ChatMessage card : liveToolCallCards) {
                if (card.isRunning() && toolName.equals(toolNameOf(card))) {
                    matched = card;
                    break;
                }
            }
            if (matched != null) {
                matched.setRunning(false);
                int index = indexOfMessage(matched);
                if (index >= 0) {
                    chatAdapter.notifyItemChanged(index);
                }
            }

            ChatMessage resultCard = ChatMessage.createToolResultMessage(null, toolName, result);
            chatAdapter.addMessage(resultCard);
            scrollToBottom();
        }

        @Override
        public void onComplete(String finalResponse, List<ChatMessage> updatedHistory) {
            if (!isAdded() || getContext() == null) {
                return;
            }

            clearLiveEphemera();
            resetStreamingState();
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
            // Flip any still-running tool cards to a failed state before dropping them:
            // the canonical history has no entry for calls that never produced a result.
            for (ChatMessage card : liveToolCallCards) {
                if (card.isRunning()) {
                    card.setRunning(false);
                    card.setContent("interrupted");
                    int index = indexOfMessage(card);
                    if (index >= 0) {
                        chatAdapter.notifyItemChanged(index);
                    }
                }
            }
            // Drop the partial streaming bubble — its text never made it into history.
            boolean hadPartialBubble = streamingBubbleActive;
            resetStreamingState();
            if (hadPartialBubble) {
                chatAdapter.removeLastMessage();
            }
            dismissApprovalDialog();
            setLoading(false);
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCancelled(List<ChatMessage> history) {
            if (!isAdded() || getContext() == null) {
                return;
            }
            // The service already persisted `history` (including any partial streamed
            // text); just re-render it in place of the temporary ephemeral items.
            clearLiveEphemera();
            resetStreamingState();
            dismissApprovalDialog();
            setLoading(false);
            chatAdapter.setMessages(history);
            scrollToBottom();
            updateToolbarTitle();
            Toast.makeText(requireContext(), R.string.response_stopped, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onApprovalRequired(String toolName, String description,
                                       JsonObject arguments,
                                       AgentLoop.ApprovalCallback approvalCallback) {
            if (!isAdded() || getContext() == null) {
                approvalCallback.onDenied();
                return;
            }
            requireActivity().runOnUiThread(() ->
                    showApprovalDialog(toolName, description, approvalCallback));
        }
    };

    private final ServiceConnection agentServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AgentExecutionService.LocalBinder binder = (AgentExecutionService.LocalBinder) service;
            agentExecutionService = binder.getService();
            serviceBound = true;
            serviceBinding = false;

            if (currentSessionId != null) {
                agentExecutionService.registerUICallback(currentSessionId, agentUiCallback);
                if (agentExecutionService.isSessionActive(currentSessionId)) {
                    setLoading(true);
                } else {
                    // Session completed or errored while UI was detached — the service
                    // already persisted the final messages.  Reload from repository and
                    // clear the stale loading spinner so the user sees the response.
                    setLoading(false);
                    List<ChatMessage> savedMessages = chatRepository.loadMessages(currentSessionId);
                    if (!savedMessages.isEmpty()) {
                        chatAdapter.setMessages(savedMessages);
                        scrollToBottom();
                    }
                    updateToolbarTitle();
                }
            }

            runPendingAgentRequest();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            serviceBinding = false;
            agentExecutionService = null;
        }
    };

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

    // Notification permission launcher (Android 13+)
    private ActivityResultLauncher<String> notificationPermissionLauncher;

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

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> Log.d(TAG, "POST_NOTIFICATIONS permission: "
                    + (granted ? "granted" : "denied"))
        );

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

    private void bindAgentExecutionService() {
        if (!isAdded() || serviceBound || serviceBinding) {
            return;
        }

        Intent intent = new Intent(requireContext(), AgentExecutionService.class);
        serviceBinding = true;
        boolean bound = requireContext().bindService(
                intent,
                agentServiceConnection,
                Context.BIND_AUTO_CREATE
        );
        if (!bound) {
            serviceBinding = false;
            Log.w(TAG, "Failed to bind AgentExecutionService");
        }
    }

    private void startAgentExecutionServiceIfNeeded() {
        if (!isAdded()) {
            return;
        }

        Intent intent = new Intent(requireContext(), AgentExecutionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private void dispatchAgentRequest(List<ChatMessage> conversationHistory) {
        pendingServiceConversationHistory = new ArrayList<>(conversationHistory);
        pendingServiceContextWindow = getModelContextWindow();

        startAgentExecutionServiceIfNeeded();

        if (serviceBound && agentExecutionService != null) {
            runPendingAgentRequest();
        } else {
            bindAgentExecutionService();
        }
    }

    private void runPendingAgentRequest() {
        if (!serviceBound || agentExecutionService == null || currentSessionId == null
                || pendingServiceConversationHistory == null) {
            return;
        }

        List<ChatMessage> history = pendingServiceConversationHistory;
        int contextWindow = pendingServiceContextWindow;
        pendingServiceConversationHistory = null;

        // The service drops the UI callback when a run reaches a terminal state,
        // so re-register before every dispatch — otherwise responses for the
        // second and later messages in the same screen session never reach the UI.
        agentExecutionService.registerUICallback(currentSessionId, agentUiCallback);
        agentExecutionService.startAgentLoop(currentSessionId, history, contextWindow);
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
        commandHintsBar        = view.findViewById(R.id.commandHintsBar);
        commandHintsRow        = view.findViewById(R.id.commandHintsRow);
        attachmentBarContainer = view.findViewById(R.id.attachmentBarContainer);
        attachmentBar          = view.findViewById(R.id.attachmentBar);

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

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerView.setItemAnimator(itemAnimator);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                atBottom = isLastItemVisible();
            }
        });

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
        sendButton.setOnClickListener(v -> {
            if (loading) {
                stopResponse();
            } else {
                sendMessage();
            }
        });
        attachButton.setOnClickListener(v -> launchFilePicker());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCommandHints(s.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    /** Show matching slash command chips above the input while typing a "/" prefix. */
    private void updateCommandHints(String text) {
        if (commandHintsBar == null || commandHintsRow == null) {
            return;
        }
        String trimmed = text.trim();
        List<SlashCommand.Command> matches = null;
        if (trimmed.startsWith("/") && !trimmed.contains("\n")
                && trimmed.indexOf(' ', 1) < 0) {
            matches = new ArrayList<>();
            for (SlashCommand.Command command : SlashCommand.all()) {
                if (command.displayName().toLowerCase(Locale.ROOT)
                        .startsWith(trimmed.toLowerCase(Locale.ROOT))) {
                    matches.add(command);
                }
            }
        }
        if (matches == null || matches.isEmpty()) {
            commandHintsBar.setVisibility(View.GONE);
            return;
        }
        commandHintsRow.removeAllViews();
        for (SlashCommand.Command command : matches) {
            TextView chip = new TextView(requireContext());
            chip.setText(command.displayName() + "  \u2014  " + command.usage);
            int pad = (int) (12 * requireContext().getResources().getDisplayMetrics().density);
            chip.setPadding(pad, pad / 2, pad, pad / 2);
            chip.setTextSize(13f);
            chip.setTextColor(chip.getCurrentTextColor());
            chip.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(Color.TRANSPARENT), null, null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = (int) (2 * requireContext().getResources().getDisplayMetrics().density);
            lp.setMargins(0, margin, 0, margin);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                messageInput.setText(command.displayName() + " ");
                messageInput.setSelection(command.displayName().length() + 1);
            });
            commandHintsRow.addView(chip);
        }
        commandHintsBar.setVisibility(View.VISIBLE);
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
        if (loading) {
            return;
        }
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }

        dropPendingCommandEcho();
        if (messageText.startsWith("/")) {
            String reply = SlashCommand.tryHandle(messageText, settingsManager);
            if (reply != null) {
                showSlashCommandResult(reply);
                messageInput.setText("");
                return;
            }
            // Unknown "/..." input is a regular message.
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
        dropPendingCommandEcho();
        setLoading(true);

        // Three-dot placeholder while the first LLM response segment is in flight;
        // replaced by the first text delta or tool card, dropped at any terminal state.
        showTypingIndicator();

        List<ChatMessage> conversationHistory = new ArrayList<>();
        for (ChatMessage message : chatAdapter.getMessages()) {
            if (message != ChatAdapter.TYPING_PLACEHOLDER) {
                conversationHistory.add(message);
            }
        }

        dispatchAgentRequest(conversationHistory);
    }

    /**
     * Show a slash command result as a temporary assistant bubble: visible in the
     * UI but removed before persistence and before any agent request, so command
     * output never enters the saved session or the LLM conversation.
     */
    private void showSlashCommandResult(String text) {
        pendingCommandEcho = new ChatMessage(text, ChatMessage.TYPE_ASSISTANT);
        chatAdapter.addMessage(pendingCommandEcho);
        scrollToBottom();
    }

    /** Drop a pending command echo if one is still showing (e.g. user sent nothing after). */
    private void dropPendingCommandEcho() {
        if (pendingCommandEcho == null) {
            return;
        }
        List<ChatMessage> messages = chatAdapter.getMessages();
        if (!messages.isEmpty() && messages.get(messages.size() - 1) == pendingCommandEcho) {
            chatAdapter.removeLastMessage();
        }
        pendingCommandEcho = null;
    }

    /**
     * Show a dialog asking user to approve or deny a tool execution.
     */
    private void showApprovalDialog(String toolName, String description,
                                    AgentLoop.ApprovalCallback approvalCallback) {
        approvalDialog = new AlertDialog.Builder(requireContext())
            .setTitle("Approve Tool Execution?")
            .setMessage("Tool: " + formatToolName(toolName) + "\n\n" + description)
            .setPositiveButton("Approve", (dialog, which) -> {
                Log.d(TAG, "User approved tool: " + toolName);
                approvalDialog = null;
                approvalCallback.onApproved();
            })
            .setNegativeButton("Deny", (dialog, which) -> {
                Log.d(TAG, "User denied tool: " + toolName);
                approvalDialog = null;
                approvalCallback.onDenied();
            })
            .setCancelable(false)
            .show();
    }

    /** Dismiss the tool-approval dialog if it is still showing (e.g. on stop). */
    private void dismissApprovalDialog() {
        if (approvalDialog != null && approvalDialog.isShowing()) {
            approvalDialog.dismiss();
        }
        approvalDialog = null;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        updateSendButton();
        messageInput.setEnabled(!loading);
    }

    /**
     * While idle the button sends; while a request is in flight it morphs into a
     * stop button so the user can abort the response. It stays enabled in both
     * states.
     */
    private void updateSendButton() {
        if (sendButton == null) {
            return;
        }
        sendButton.setImageResource(loading ? R.drawable.ic_stop : R.drawable.ic_send);
        sendButton.setContentDescription(
                getString(loading ? R.string.stop_response : R.string.send));
        sendButton.setEnabled(true);
    }

    /**
     * Stop the in-flight response: cancels the agent loop and aborts any HTTP
     * requests. The final UI reset happens in {@code agentUiCallback.onCancelled()};
     * the partial streamed text is preserved by the agent loop.
     */
    private void stopResponse() {
        Log.d(TAG, "stopResponse: user requested cancellation");
        if (serviceBound && agentExecutionService != null && currentSessionId != null
                && agentExecutionService.isSessionActive(currentSessionId)) {
            agentExecutionService.cancelAgentLoop(currentSessionId);
        } else {
            // No active service/session — reset the UI directly.
            clearLiveEphemera();
            resetStreamingState();
            setLoading(false);
        }
    }

    private void scrollToBottom() {
        atBottom = true;
        if (chatAdapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private boolean isLastItemVisible() {
        if (chatAdapter == null || chatAdapter.getItemCount() == 0) {
            return true;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        return layoutManager instanceof LinearLayoutManager
                && ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition()
                        >= chatAdapter.getItemCount() - 1;
    }

    // ==================== SSE streaming live-render helpers ====================

    /**
     * Throttled flush of accumulated stream deltas into the temporary bubble.
     * Re-rendering markdown on every token is wasteful, so deltas are batched
     * and applied at most once per {@link #STREAM_FLUSH_INTERVAL_MS}.
     */
    private void scheduleStreamFlush() {
        if (streamFlushScheduled || recyclerView == null) {
            return;
        }
        streamFlushScheduled = true;
        recyclerView.postDelayed(streamFlushRunnable, STREAM_FLUSH_INTERVAL_MS);
    }

    /**
     * Apply buffered text to the current bubble by identity, and auto-scroll only
     * if the user was already at the bottom before the flush.
     */
    private void flushStreamingBubble() {
        streamFlushScheduled = false;
        if (!streamingBubbleActive || streamingBubble == null || !isAdded()) {
            return;
        }
        boolean updated = chatAdapter.updateStreamingMessage(
                streamingBubble.getStreamingId(), streamingBuffer.toString());
        if (!updated) {
            resetStreamingState();
            return;
        }
        if (atBottom) {
            scrollToBottom();
        }
    }

    /**
     * Clear all streaming state and cancel any pending flush. Called at tool
     * boundaries and when the agent completes or errors; the final history
     * render replaces the bubble.
     */
    private void resetStreamingState() {
        streamingBubbleActive = false;
        streamingBubble = null;
        streamingBuffer.setLength(0);
        if (recyclerView != null) {
            recyclerView.removeCallbacks(streamFlushRunnable);
        }
        streamFlushScheduled = false;
    }

    /**
     * Show the three-dot typing placeholder while the first LLM response segment is
     * in flight. Replaced by the first text delta or the first tool card.
     */
    private void showTypingIndicator() {
        if (typingIndicatorActive || chatAdapter == null) {
            return;
        }
        chatAdapter.addMessage(ChatAdapter.TYPING_PLACEHOLDER);
        typingIndicatorActive = true;
        scrollToBottom();
    }

    /** Remove the typing placeholder if it is still present. */
    private void removeTypingIndicator() {
        if (!typingIndicatorActive || chatAdapter == null) {
            return;
        }
        int index = indexOfMessage(ChatAdapter.TYPING_PLACEHOLDER);
        if (index >= 0) {
            chatAdapter.removeAt(index);
        }
        typingIndicatorActive = false;
    }

    /** Tool name of a live tool-call card, for FIFO result pairing. */
    private static String toolNameOf(ChatMessage card) {
        List<LlmApiService.ToolCall> calls = card.getToolCalls();
        return calls != null && !calls.isEmpty() ? calls.get(0).getName() : null;
    }

    /** Index of a message object in the adapter, or -1 when absent. */
    private int indexOfMessage(ChatMessage message) {
        List<ChatMessage> messages = chatAdapter.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == message) {
                return i;
            }
        }
        return -1;
    }

    /** Truncate tool args to the display bound before they enter any rendered card. */
    private static JsonObject truncateArgs(JsonObject arguments) {
        String args = arguments.toString();
        if (args.length() <= 200) {
            return arguments;
        }
        try {
            return com.google.gson.JsonParser.parseString(args.substring(0, 197) + "...").getAsJsonObject();
        } catch (Exception e) {
            return arguments;
        }
    }

    /**
     * Drop all ephemeral live-run items (typing indicator, live tool cards, live
     * result cards) before re-rendering the canonical history. Must run before
     * {@code setMessages} so reconciliation never leaves duplicates.
     */
    private void clearLiveEphemera() {
        removeTypingIndicator();
        if (chatAdapter != null && !liveToolCallCards.isEmpty()) {
            List<ChatMessage> messages = chatAdapter.getMessages();
            // Remove from the end so earlier indices stay valid.
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if (message == ChatAdapter.TYPING_PLACEHOLDER
                        || liveToolCallCards.contains(message)
                        || message.isToolResult()) {
                    chatAdapter.removeAt(i);
                }
            }
        }
        liveToolCallCards.clear();
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
    public void onStart() {
        super.onStart();
        bindAgentExecutionService();
    }

    @Override
    public void onStop() {
        if (serviceBound && agentExecutionService != null) {
            if (currentSessionId != null) {
                agentExecutionService.unregisterUICallback(currentSessionId);
            }
            requireContext().unbindService(agentServiceConnection);
            serviceBound = false;
            agentExecutionService = null;
        }
        super.onStop();
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Requests now live in AgentExecutionService and continue after the UI is destroyed.
    }
}