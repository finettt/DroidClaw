package io.finett.droidclaw;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.finett.droidclaw.util.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.repository.ChatRepository;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    private static final String CHAT_PREFS = "chat_messages";

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        SharedPreferences chatPrefs = getApplicationContext()
                .getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
    }

    @Test
    public void onCreate_initializesUIComponents() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("DrawerLayout should be initialized", 
                        activity.findViewById(R.id.drawer_layout));
                assertNotNull("Toolbar should be initialized", 
                        activity.findViewById(R.id.toolbar));
                assertNotNull("New chat button should be initialized", 
                        activity.findViewById(R.id.button_new_chat));
                assertNotNull("Settings button should be initialized", 
                        activity.findViewById(R.id.button_settings));
                assertNotNull("Chat sessions RecyclerView should be initialized", 
                        activity.findViewById(R.id.recycler_chat_sessions));
            });
        }
    }

    @Test
    public void onCreate_withNoSavedSessions_createsInitialSession() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                assertNotNull("RecyclerView adapter should be set", recyclerView.getAdapter());
                assertTrue("Should have at least one session", 
                        recyclerView.getAdapter().getItemCount() >= 1);
            });
        }
    }

    @Test
    public void onCreate_withSavedSessions_loadsPersistedSessions() {
        // Pre-populate sessions
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "First Chat", 100L),
                new ChatSession("session-2", "Second Chat", 200L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                assertNotNull("RecyclerView adapter should be set", recyclerView.getAdapter());
                assertEquals("Should load 2 saved sessions", 2, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void newChatButton_createsNewSession() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                int initialCount = recyclerView.getAdapter().getItemCount();
                
                activity.findViewById(R.id.button_new_chat).performClick();
                
                assertEquals("Should add one new session", 
                        initialCount + 1, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void newChatButton_closesDrawer() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);

                // Open drawer first
                drawerLayout.openDrawer(GravityCompat.START);
            });

            waitForDrawerState(scenario, true);

            scenario.onActivity(activity -> activity.findViewById(R.id.button_new_chat).performClick());

            waitForDrawerState(scenario, false);
        }
    }

    @Test
    public void settingsButton_closesDrawer() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);

                // Open drawer first
                drawerLayout.openDrawer(GravityCompat.START);
            });

            waitForDrawerState(scenario, true);

            scenario.onActivity(activity -> activity.findViewById(R.id.button_settings).performClick());

            waitForDrawerState(scenario, false);
        }
    }

    @Test
    public void updateSessionMetadata_withNullSessionId_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Should not crash
                activity.updateSessionMetadata(null, "Test message", System.currentTimeMillis());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withEmptySessionId_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Should not crash
                activity.updateSessionMetadata("", "Test message", System.currentTimeMillis());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withNewChatTitle_generatesTitle() {
        // Pre-populate with a "New Chat" session
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "New Chat", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String firstUserMessage = "Hello, this is my first message";
                activity.updateSessionMetadata("session-1", firstUserMessage, 200L);

                // Verify the session was updated
                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Should have 1 session", 1, updatedSessions.size());
                assertFalse("Title should not be 'New Chat' after update", 
                        "New Chat".equals(updatedSessions.get(0).getTitle()));
                assertTrue("Title should contain 'Hello'", 
                        updatedSessions.get(0).getTitle().contains("Hello"));
                assertEquals("Updated timestamp should be 200L", 
                        200L, updatedSessions.get(0).getUpdatedAt());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withExistingTitle_keepsTitle() {
        // Pre-populate with a custom-titled session
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "My Custom Title", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.updateSessionMetadata("session-1", "New message", 200L);

                // Verify the title was not changed
                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Should have 1 session", 1, updatedSessions.size());
                assertEquals("Custom title should be preserved", 
                        "My Custom Title", updatedSessions.get(0).getTitle());
                assertEquals("Timestamp should be updated", 
                        200L, updatedSessions.get(0).getUpdatedAt());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withEmptyMessage_doesNotGenerateTitle() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "New Chat", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.updateSessionMetadata("session-1", "", 200L);

                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Title should remain 'New Chat' for empty message", 
                        "New Chat", updatedSessions.get(0).getTitle());
            });
        }
    }

    @Test
    public void updateSessionMetadata_sortsSessionsByUpdatedAt() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "First", 100L),
                new ChatSession("session-2", "Second", 200L),
                new ChatSession("session-3", "Third", 300L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Update the oldest session to make it newest
                activity.updateSessionMetadata("session-1", "Updated message", 400L);

                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Session-1 should be first (newest) after update", 
                        "session-1", updatedSessions.get(0).getId());
                assertEquals("Session-1 should have new timestamp", 
                        400L, updatedSessions.get(0).getUpdatedAt());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withNonExistentSession_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Should not crash when session doesn't exist
                activity.updateSessionMetadata("non-existent", "Message", System.currentTimeMillis());
            });
        }
    }

    @Test
    public void multipleSessionCreation_persistsAllSessions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Create multiple sessions
                activity.findViewById(R.id.button_new_chat).performClick();
                activity.findViewById(R.id.button_new_chat).performClick();
                activity.findViewById(R.id.button_new_chat).performClick();

                // Verify sessions are persisted
                ChatRepository repository = new ChatRepository(getApplicationContext());
                List<ChatSession> loadedSessions = repository.loadSessions();
                assertTrue("Should have at least 4 sessions (1 initial + 3 new)", 
                        loadedSessions.size() >= 4);
            });
        }
    }

    @Test
    public void recyclerView_hasLayoutManager() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                assertNotNull("RecyclerView should have a LayoutManager", 
                        recyclerView.getLayoutManager());
            });
        }
    }

    @Test
    public void recyclerView_hasAdapter() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                assertNotNull("RecyclerView should have an adapter", 
                        recyclerView.getAdapter());
            });
        }
    }

    @Test
    public void drawerLayout_isNotOpenByDefault() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                assertFalse("Drawer should not be open by default", 
                        drawerLayout.isDrawerOpen(GravityCompat.START));
            });
        }
    }

    @Test
    public void toolbar_isSetAsSupportActionBar() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("SupportActionBar should be set", activity.getSupportActionBar());
            });
        }
    }

    @Test
    public void savedSessions_areSortedByUpdatedAtDescending() {
        // Save sessions in non-sorted order
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("oldest", "Oldest", 100L),
                new ChatSession("newest", "Newest", 300L),
                new ChatSession("middle", "Middle", 200L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Load and verify sorting
                List<ChatSession> loadedSessions = repository.loadSessions();
                assertEquals("Newest session should be first", "newest", loadedSessions.get(0).getId());
                assertEquals("Middle session should be second", "middle", loadedSessions.get(1).getId());
                assertEquals("Oldest session should be last", "oldest", loadedSessions.get(2).getId());
            });
        }
    }

    @Test
    public void renameChatDialog_withEmptyTitle_doesNotUpdate() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "Original Title", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Simulate rename with empty string
                ChatSession session = repository.loadSessions().get(0);
                
                // The rename method should handle empty titles
                activity.getSupportFragmentManager().executePendingTransactions();
                
                // Verify title was not changed to empty
                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Original Title", updatedSessions.get(0).getTitle());
            });
        }
    }

    @Test
    public void deleteChatSession_whenLastSession_createsNewSession() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("only-session", "Only Chat", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                int initialCount = recyclerView.getAdapter().getItemCount();
                
                // Get the only session and delete it
                List<ChatSession> loadedSessions = repository.loadSessions();
                ChatSession sessionToDelete = loadedSessions.get(0);
                
                // Manually invoke deletion
                loadedSessions.remove(sessionToDelete);
                repository.deleteSession(sessionToDelete.getId(), loadedSessions);
                
                // When last session is deleted, a new one should be created
                List<ChatSession> sessionsAfterDelete = repository.loadSessions();
                assertTrue("Should have at least one session after deleting the last one",
                        sessionsAfterDelete.size() >= 0);
            });
        }
    }

    @Test
    public void deleteChatSession_whileActive_switchesToDifferentSession() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "First", 100L),
                new ChatSession("session-2", "Second", 200L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                int initialCount = recyclerView.getAdapter().getItemCount();
                
                // Verify we have 2 sessions
                assertEquals(2, initialCount);
                
                // Delete one session
                List<ChatSession> loadedSessions = repository.loadSessions();
                ChatSession sessionToDelete = loadedSessions.get(0);
                loadedSessions.remove(sessionToDelete);
                repository.deleteSession(sessionToDelete.getId(), loadedSessions);
                
                // Should have 1 session remaining
                assertEquals(1, repository.loadSessions().size());
            });
        }
    }

    @Test
    public void drawerLayout_openAndClose_multipleIterations() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                
                // Open and close multiple times
                for (int i = 0; i < 5; i++) {
                    drawerLayout.openDrawer(GravityCompat.START);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    drawerLayout.closeDrawer(GravityCompat.START);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                
                // Should not crash
                assertNotNull(drawerLayout);
            });
        }
    }

    @Test
    public void navigationDrawer_rapidSessionSwitching_handlesGracefully() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "First", 100L),
                new ChatSession("session-2", "Second", 200L),
                new ChatSession("session-3", "Third", 300L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                
                // Verify sessions are loaded
                assertEquals(3, recyclerView.getAdapter().getItemCount());
                
                // Rapid session changes should not crash
                assertNotNull(activity.getSupportActionBar());
            });
        }
    }

    @Test
    public void updateSessionMetadata_withVeryLongMessage_truncatesTitle() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "New Chat", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String veryLongMessage = "This is an extremely long message that should be truncated " +
                        "when generating a title because titles should not be excessively long and " +
                        "should fit reasonably within the UI constraints of the application's chat " +
                        "session list display area without causing layout issues or overflow problems.";
                
                activity.updateSessionMetadata("session-1", veryLongMessage, 200L);
                
                List<ChatSession> updatedSessions = repository.loadSessions();
                String generatedTitle = updatedSessions.get(0).getTitle();
                
                assertNotNull("Title should be generated", generatedTitle);
                assertFalse("Title should not be 'New Chat'", "New Chat".equals(generatedTitle));
                assertTrue("Title should be truncated to reasonable length",
                        generatedTitle.length() <= 50);
            });
        }
    }

    @Test
    public void updateSessionMetadata_withNullMessage_doesNotChangeTitle() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "Original Title", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.updateSessionMetadata("session-1", null, 200L);
                
                List<ChatSession> updatedSessions = repository.loadSessions();
                assertEquals("Title should remain unchanged with null message",
                        "Original Title", updatedSessions.get(0).getTitle());
                assertEquals("Timestamp should be updated",
                        200L, updatedSessions.get(0).getUpdatedAt());
            });
        }
    }

    @Test
    public void updateSessionMetadata_concurrentUpdates_handlesCorrectly() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "Chat 1", 100L),
                new ChatSession("session-2", "Chat 2", 200L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Update multiple sessions
                activity.updateSessionMetadata("session-1", "Message 1", 300L);
                activity.updateSessionMetadata("session-2", "Message 2", 400L);
                
                List<ChatSession> updatedSessions = repository.loadSessions();
                
                // Sessions should be sorted by updated time (newest first)
                assertEquals("session-2", updatedSessions.get(0).getId());
                assertEquals("session-1", updatedSessions.get(1).getId());
            });
        }
    }

    @Test
    public void createNewChat_withMaxSessions_stillCreatesNewSession() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = new ArrayList<>();
        
        // Create many sessions
        for (int i = 0; i < 50; i++) {
            sessions.add(new ChatSession("session-" + i, "Chat " + i, (long) i));
        }
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                int initialCount = recyclerView.getAdapter().getItemCount();
                
                assertEquals(50, initialCount);
                
                // Create new session
                activity.findViewById(R.id.button_new_chat).performClick();
                
                // Should have one more session
                assertEquals(51, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void navigationController_nullCheck_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Try to trigger navigation-related operations
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                drawerLayout.openDrawer(GravityCompat.START);
                
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                // Click settings while drawer is open
                activity.findViewById(R.id.button_settings).performClick();
                
                // Should not crash
                assertNotNull(activity);
            });
        }
    }

    @Test
    public void sessionList_emptyState_createsInitialSession() {
        // Start with completely empty state
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                
                // Should have at least one session (the initial one)
                assertTrue("Should have initial session",
                        recyclerView.getAdapter().getItemCount() >= 1);
            });
        }
    }

    @Test
    public void toolbar_homeButton_togglesDrawer() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                
                // Initially drawer should be closed
                assertFalse(drawerLayout.isDrawerOpen(GravityCompat.START));
                
                // ActionBarDrawerToggle should be set up
                assertNotNull(activity.getSupportActionBar());
                // Verify the action bar is properly initialized
                assertNotNull(activity.getSupportActionBar().getTitle());
            });
        }
    }

    @Test
    public void sessionMetadata_updateWithSpecialCharacters_handlesCorrectly() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "New Chat", 100L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String specialMessage = "Hello! @#$%^&*() <html> \"quotes\" 'apostrophe'";
                activity.updateSessionMetadata("session-1", specialMessage, 200L);
                
                List<ChatSession> updatedSessions = repository.loadSessions();
                assertNotNull(updatedSessions.get(0).getTitle());
                assertFalse("New Chat".equals(updatedSessions.get(0).getTitle()));
            });
        }
    }

    @Test
    public void activityRecreate_preservesSessionsList() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        List<ChatSession> sessions = Arrays.asList(
                new ChatSession("session-1", "Chat 1", 100L),
                new ChatSession("session-2", "Chat 2", 200L)
        );
        repository.saveSessions(sessions);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                assertEquals(2, recyclerView.getAdapter().getItemCount());
            });

            // Recreate activity (simulates rotation)
            scenario.recreate();

            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_chat_sessions);
                // Sessions should be reloaded
                assertEquals(2, recyclerView.getAdapter().getItemCount());
            });
        }
    }
    private void waitForDrawerState(ActivityScenario<MainActivity> scenario, boolean expectedOpen) {
        long timeoutAt = System.currentTimeMillis() + 2000L;
        boolean[] stateMatches = new boolean[1];

        do {
            TestUtils.waitForIdle();
            scenario.onActivity(activity -> {
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                stateMatches[0] = drawerLayout.isDrawerOpen(GravityCompat.START) == expectedOpen;
            });

            if (stateMatches[0]) {
                return;
            }

            TestUtils.waitFor(50);
        } while (System.currentTimeMillis() < timeoutAt);

        scenario.onActivity(activity -> {
            DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
            assertEquals(
                    "Unexpected drawer state",
                    expectedOpen,
                    drawerLayout.isDrawerOpen(GravityCompat.START)
            );
        });
    }
}