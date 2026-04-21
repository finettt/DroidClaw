package io.finett.droidclaw;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.finett.droidclaw.adapter.ChatSessionAdapter;
import io.finett.droidclaw.fragment.ChatFragment;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.util.SettingsManager;

import org.woheller69.freeDroidWarn.FreeDroidWarn;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DrawerLayout drawerLayout;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private ChatSessionAdapter chatSessionAdapter;
    private final List<ChatSession> chatSessions = new ArrayList<>();
    private ChatRepository chatRepository;
    private SettingsManager settingsManager;
    private String currentSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        if (!isInstrumentationTest()) {
            FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        chatRepository = new ChatRepository(this);
        settingsManager = new SettingsManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupDrawerContent();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.chatFragment,
                    R.id.fileBrowserFragment,
                    R.id.memoryBrowserFragment,
                    R.id.cronJobListFragment
            )
                    .setOpenableLayout(drawerLayout)
                    .build();

            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                    this,
                    drawerLayout,
                    toolbar,
                    R.string.menu,
                    R.string.menu
            );
            drawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();


            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (appBarConfiguration.getTopLevelDestinations().contains(destination.getId())) {

                    drawerToggle.setDrawerIndicatorEnabled(true);
                } else {

                    drawerToggle.setDrawerIndicatorEnabled(false);
                    toolbar.setNavigationIcon(com.google.android.material.R.drawable.abc_ic_ab_back_material);
                    toolbar.setNavigationOnClickListener(v -> {
                        NavController nc = controller;
                        if (nc.getPreviousBackStackEntry() != null) {
                            nc.navigateUp();
                        } else {

                            drawerLayout.openDrawer(GravityCompat.START);
                        }
                    });
                }
            });



            if (savedInstanceState == null) {
                drawerLayout.post(() -> {
                    if (!settingsManager.isOnboardingCompleted()) {

                        Log.d(TAG, "Onboarding not completed, navigating to onboarding screen");
                        navController.navigate(R.id.onboardingFragment);
                    } else {

                        TaskResult deepLinkTask = getDeepLinkTaskFromIntent(getIntent());
                        if (deepLinkTask != null) {
                            Log.d(TAG, "Launched from notification deep link, navigating to ZenResultFragment");
                            navigateToZenResult(deepLinkTask);
                        } else {

                            ChatSession initialSession;
                            if (chatSessions.isEmpty()) {
                                initialSession = addNewChatSession();
                                Log.d(TAG, "No saved sessions found. Created initial session: " + initialSession.getId());
                            } else {
                                initialSession = chatSessions.get(0);
                                Log.d(TAG, "Opening most recent saved session: " + initialSession.getId());
                            }

                            currentSessionId = initialSession.getId();
                            Bundle args = new Bundle();
                            args.putString(ChatFragment.ARG_SESSION_ID, currentSessionId);
                            navController.navigate(R.id.chatFragment, args);
                        }
                    }
                });
            }
        }
    }

    /**
     * Handle deep link intents from notifications.
     * Called when activity is already running and receives a new intent.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        TaskResult deepLinkTask = getDeepLinkTaskFromIntent(intent);
        if (deepLinkTask != null) {
            Log.d(TAG, "Received deep link intent while activity is running, navigating to ZenResultFragment");
            navigateToZenResult(deepLinkTask);
        }
    }

    /**
     * Extract TaskResult from deep link intent.
     */
    private TaskResult getDeepLinkTaskFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String destination = intent.getStringExtra("deep_link_destination");
        if (!"zen_result".equals(destination)) {
            return null;
        }
        return (TaskResult) intent.getSerializableExtra("task_result");
    }

    /**
     * Navigate to ZenResultFragment with the task result.
     */
    private void navigateToZenResult(TaskResult taskResult) {
        if (navController == null || taskResult == null) {
            Log.w(TAG, "Cannot navigate to ZenResultFragment: navController or taskResult is null");
            return;
        }

        Bundle args = new Bundle();
        args.putSerializable("task_result", taskResult);
        navController.navigate(R.id.zenResultFragment, args);
    }

    private boolean isInstrumentationTest() {
        try {
            Class<?> instrumentationRegistryClass =
                    Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            Object instrumentation = instrumentationRegistryClass
                    .getMethod("getInstrumentation")
                    .invoke(null);
            return instrumentation != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private void setupDrawerContent() {
        MaterialButton newChatButton = findViewById(R.id.button_new_chat);
        MaterialButton filesButton = findViewById(R.id.button_files);
        MaterialButton memoryButton = findViewById(R.id.button_memory);
        MaterialButton scheduledTasksButton = findViewById(R.id.button_scheduled_tasks);
        MaterialButton settingsButton = findViewById(R.id.button_settings);
        RecyclerView chatSessionsRecyclerView = findViewById(R.id.recycler_chat_sessions);

        chatSessionAdapter = new ChatSessionAdapter(new ChatSessionAdapter.OnChatSessionClickListener() {
            @Override
            public void onChatSessionClick(ChatSession session) {
                Log.d(TAG, "Chat session clicked: id=" + session.getId() + ", title=" + session.getTitle());

                if (navController != null) {
                    currentSessionId = session.getId();
                    Bundle args = new Bundle();
                    args.putString(ChatFragment.ARG_SESSION_ID, currentSessionId);
                    navController.navigate(R.id.chatFragment, args);
                    Log.d(TAG, "Navigating to chatFragment with session_id: " + currentSessionId);
                }
                drawerLayout.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onChatSessionLongClick(ChatSession session) {
                showChatSessionMenu(session);
            }
        });

        chatSessionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatSessionsRecyclerView.setAdapter(chatSessionAdapter);

        loadPersistedChatSessions();

        newChatButton.setOnClickListener(v -> {
            ChatSession newSession = addNewChatSession();
            
            if (navController != null) {

                currentSessionId = newSession.getId();
                Bundle args = new Bundle();
                args.putString(ChatFragment.ARG_SESSION_ID, currentSessionId);
                navController.navigate(R.id.chatFragment, args);
                Log.d(TAG, "New chat created with session_id: " + currentSessionId);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        filesButton.setOnClickListener(v -> {
            if (navController != null) {
                navController.navigate(R.id.fileBrowserFragment);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        memoryButton.setOnClickListener(v -> {
            if (navController != null) {
                navController.navigate(R.id.memoryBrowserFragment);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        scheduledTasksButton.setOnClickListener(v -> {
            if (navController != null) {
                navController.navigate(R.id.cronJobListFragment);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        settingsButton.setOnClickListener(v -> {
            if (navController != null) {
                navController.navigate(R.id.settingsFragment);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });
    }

    private void loadPersistedChatSessions() {
        chatSessions.clear();
        chatSessions.addAll(chatRepository.loadSessions());
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Loaded " + chatSessions.size() + " persisted chat sessions");
    }

    private ChatSession addNewChatSession() {
        ChatSession newSession = new ChatSession(
                UUID.randomUUID().toString(),
                getString(R.string.new_chat),
                System.currentTimeMillis()
        );
        chatSessions.add(0, newSession);
        chatRepository.saveSessions(chatSessions);
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Created and saved new chat session: " + newSession.getId());
        return newSession;
    }

    /**
     * Create a new chat session linked to a task result.
     * Used for chat continuation from task results.
     */
    public ChatSession addNewChatSessionWithTask(TaskResult taskResult, String title) {
        ChatSession newSession = new ChatSession(
                UUID.randomUUID().toString(),
                title,
                System.currentTimeMillis()
        );
        

        if (taskResult != null) {
            newSession.setParentTaskId(taskResult.getId());

            if (taskResult.getType() == TaskResult.TYPE_HEARTBEAT) {
                newSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);
            } else if (taskResult.getType() == TaskResult.TYPE_CRON_JOB) {
                newSession.setSessionType(SessionType.HIDDEN_CRON);
            }
        }
        
        chatSessions.add(0, newSession);
        chatRepository.saveSessions(chatSessions);
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Created task-linked chat session: " + newSession.getId());
        return newSession;
    }

    /**
     * Get the most recent chat session for continuation.
     */
    public ChatSession getMostRecentChatSession() {
        if (chatSessions.isEmpty()) {
            return null;
        }
        return chatSessions.get(0);
    }

    /**
     * Add a chat session to the list (used by continuation service).
     */
    public void addChatSessionToList(ChatSession session) {
        chatSessions.add(0, session);
        chatRepository.saveSessions(chatSessions);
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Added chat session to list: " + session.getId());
    }
    
    public void updateSessionMetadata(String sessionId, String firstUserMessage, long updatedAt) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        String updatedTitle = null;
        for (ChatSession session : chatSessions) {
            if (session.getId().equals(sessionId)) {
                boolean shouldGenerateTitle = session.getTitle() == null
                        || session.getTitle().trim().isEmpty()
                        || getString(R.string.new_chat).equals(session.getTitle());

                if (shouldGenerateTitle && firstUserMessage != null && !firstUserMessage.trim().isEmpty()) {
                    updatedTitle = chatRepository.generateTitleFromMessage(firstUserMessage);
                } else {
                    updatedTitle = session.getTitle();
                }

                session.setTitle(updatedTitle);
                session.setUpdatedAt(updatedAt);
                break;
            }
        }

        if (updatedTitle != null) {
            Collections.sort(chatSessions, (s1, s2) -> Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt()));
            chatRepository.saveSessions(chatSessions);
            chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
            Log.d(TAG, "Updated session metadata for: " + sessionId + ", title=" + updatedTitle);
        }
    }

    private void showChatSessionMenu(ChatSession session) {
        if (session == null) {
            return;
        }

        View anchorView = findViewById(R.id.recycler_chat_sessions);
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_chat_session_actions, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> handleChatSessionMenuAction(item, session));
        popupMenu.show();
    }

    private boolean handleChatSessionMenuAction(MenuItem item, ChatSession session) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_rename_chat) {
            showRenameChatDialog(session);
            return true;
        } else if (itemId == R.id.action_delete_chat) {
            deleteChatSession(session);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    private void showRenameChatDialog(ChatSession session) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(session.getTitle());
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rename_chat)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        renameChatSession(session, newTitle);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renameChatSession(ChatSession session, String newTitle) {
        if (session == null || newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }

        session.setTitle(newTitle.trim());
        session.setUpdatedAt(System.currentTimeMillis());
        Collections.sort(chatSessions, (s1, s2) -> Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt()));
        chatRepository.saveSessions(chatSessions);
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Renamed chat session: " + session.getId() + " to " + newTitle);
    }

    private void deleteChatSession(ChatSession session) {
        if (session == null) {
            return;
        }

        chatSessions.remove(session);
        chatRepository.deleteSession(session.getId(), new ArrayList<>(chatSessions));
        Log.d(TAG, "Deleted chat session: " + session.getId());

        if (chatSessions.isEmpty()) {
            ChatSession newSession = addNewChatSession();
            currentSessionId = newSession.getId();
        } else if (session.getId().equals(currentSessionId)) {
            currentSessionId = chatSessions.get(0).getId();
        }

        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));

        if (navController != null && currentSessionId != null) {
            Bundle args = new Bundle();
            args.putString(ChatFragment.ARG_SESSION_ID, currentSessionId);
            navController.navigate(R.id.chatFragment, args);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadPersistedChatSessions();
    }

    /**
     * Update the toolbar title to reflect the current chat session name.
     * Called from ChatFragment when a session is loaded or renamed.
     */
    public void setToolbarTitle(String title) {
        if (title != null && !title.isEmpty()) {
            getSupportActionBar().setTitle(title);
        } else {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    /**
     * Get the title of the current chat session for LLM-based title generation.
     */
    public String getCurrentSessionTitle() {
        if (currentSessionId == null) return null;
        for (ChatSession session : chatSessions) {
            if (session.getId().equals(currentSessionId)) {
                return session.getTitle();
            }
        }
        return null;
    }

    /**
     * Get the messages of the current chat session for LLM-based title generation.
     */
    public List<ChatMessage> getCurrentSessionMessages() {
        if (currentSessionId == null) return null;
        return chatRepository.loadMessages(currentSessionId);
    }

    /**
     * Update the title of a specific session and notify the adapter.
     */
    public void updateSessionTitle(String sessionId, String newTitle) {
        if (sessionId == null || newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }

        for (ChatSession session : chatSessions) {
            if (session.getId().equals(sessionId)) {
                session.setTitle(newTitle.trim());
                session.setUpdatedAt(System.currentTimeMillis());
                break;
            }
        }

        Collections.sort(chatSessions, (s1, s2) -> Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt()));
        chatRepository.saveSessions(chatSessions);
        chatSessionAdapter.submitList(new ArrayList<>(chatSessions));
        Log.d(TAG, "Updated session title: " + sessionId + " -> " + newTitle);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return NavigationUI.navigateUp(navController, appBarConfiguration)
                    || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}