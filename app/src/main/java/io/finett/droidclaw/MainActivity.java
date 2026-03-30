package io.finett.droidclaw;

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
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.util.SettingsManager;

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
            appBarConfiguration = new AppBarConfiguration.Builder(R.id.chatFragment)
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
            
            // On fresh app launch, check onboarding status
            // Post navigation to ensure NavController is fully initialized
            if (savedInstanceState == null) {
                drawerLayout.post(() -> {
                    if (!settingsManager.isOnboardingCompleted()) {
                        // Navigate to onboarding
                        Log.d(TAG, "Onboarding not completed, navigating to onboarding screen");
                        navController.navigate(R.id.onboardingFragment);
                    } else {
                        // Open the most recent persisted session or create one if none exist
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
                });
            }
        }
    }

    private void setupDrawerContent() {
        MaterialButton newChatButton = findViewById(R.id.button_new_chat);
        MaterialButton filesButton = findViewById(R.id.button_files);
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
                // Set current session and navigate with session ID
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
        // Reload chat sessions when returning to the activity
        loadPersistedChatSessions();
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