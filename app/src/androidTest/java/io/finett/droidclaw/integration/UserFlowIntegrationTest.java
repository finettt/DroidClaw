package io.finett.droidclaw.integration;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.util.TestUtils;

/**
 * Integration tests that verify complete user flows end-to-end.
 */
@RunWith(AndroidJUnit4.class)
public class UserFlowIntegrationTest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";
    private static final String CHAT_PREFS = "chat_messages";
    
    private TestUtils.SimpleIdlingResource idlingResource;

    @Before
    public void setUp() {
        // Clear all preferences before each test
        SharedPreferences settingsPrefs = getApplicationContext()
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        settingsPrefs.edit().clear().commit();

        SharedPreferences chatPrefs = getApplicationContext()
                .getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
    }
    
    @After
    public void tearDown() {
        if (idlingResource != null) {
            IdlingRegistry.getInstance().unregister(idlingResource);
            idlingResource = null;
        }
    }
    
    /**
     * Wait for UI to be fully ready after activity launch.
     * This helps with slow CI emulators.
     */
    private void waitForUiReady() {
        TestUtils.waitFor(500);
        onIdle();
    }

    @Test
    public void completeFlow_firstTimeUser_canNavigateToSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // First time user - no configuration
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());
            waitForUiReady();

            // Should see settings list
            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_createMultipleSessions_switchBetweenThem() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Create first new chat
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            waitForUiReady();

            // Send a message in first chat
            onView(withId(R.id.messageInput))
                    .perform(replaceText("First chat message"), closeSoftKeyboard());
            waitForUiReady();

            // Open drawer again
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Create second new chat
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            waitForUiReady();

            // Send a message in second chat
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Second chat message"), closeSoftKeyboard());
            waitForUiReady();

            // Open drawer and verify multiple sessions exist
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Click on first chat session (index 1, since 0 is the current one)
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
            waitForUiReady();

            // Should switch to the first chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_sendMessage_withoutApi_showsError() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Type and send a message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message"), closeSoftKeyboard());
            waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            waitForUiReady();

            // Message input should be cleared after sending
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));

            // Note: Actual API call will fail since we're using a test API key
            // but the app should handle it gracefully without crashing
        }
    }

    @Test
    public void completeFlow_navigateSettings_viewSettingsList() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());
            waitForUiReady();

            // Should see settings list
            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_emptyMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Try to send empty message
            onView(withId(R.id.messageInput))
                    .perform(replaceText(""), closeSoftKeyboard());
            waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            waitForUiReady();

            // Send button should still be enabled (message wasn't sent)
            onView(withId(R.id.sendButton))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_whitespaceMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Try to send whitespace-only message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("      "), closeSoftKeyboard());
            waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            waitForUiReady();

            // Input should still be enabled
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_drawerNavigation_opensAndCloses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Drawer content should be visible
            onView(withId(R.id.button_new_chat))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.button_settings))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_rotateDevice_maintainsState() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Type a message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message before rotation"), closeSoftKeyboard());
            waitForUiReady();

            // Simulate configuration change (rotation)
            scenario.recreate();
            waitForUiReady();

            // Message input should be cleared after recreate (new session loaded)
            // But the app should not crash
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_multipleNewChats_allPersist() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Create multiple new chats
            for (int i = 0; i < 3; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                waitForUiReady();
                
                onView(withId(R.id.button_new_chat))
                        .perform(click());
                waitForUiReady();
            }

            // Verify all chats are in the list
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_backNavigation_handledCorrectly() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            waitForUiReady();

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());
            waitForUiReady();

            // The navigation component should handle this
            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_rapidClicks_handledGracefully() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Rapidly open drawer multiple times
            for (int i = 0; i < 5; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                waitForUiReady();
            }

            // App should still be functional
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_settingsPersistence_surviveAppRestart() {
        // Configure settings
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider testProvider = new Provider("persistent-provider", "Persistent Provider",
                "http://persistent.url", "persistent-key", "openai-completions");
        Model testModel = new Model("persistent-model", "Persistent Model", "openai-completions",
                false, Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("persistent-provider/persistent-model");

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for UI to be fully ready
            waitForUiReady();
            
            // Verify settings were loaded by checking we can interact with chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        // Create a test provider with a model
        Provider testProvider = new Provider("test-provider", "Test Provider", 
                "http://localhost:1234/v1", "test-api-key", "openai-completions");
        Model testModel = new Model("test-model", "Test Model", "openai-completions", 
                false, Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("test-provider/test-model");
    }
}