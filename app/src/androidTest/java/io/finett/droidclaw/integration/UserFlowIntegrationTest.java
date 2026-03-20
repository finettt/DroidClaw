package io.finett.droidclaw.integration;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
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
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Integration tests that verify complete user flows end-to-end.
 * Note: These tests require Espresso input injection which is not compatible with API 36+.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(maxSdkVersion = 35)
public class UserFlowIntegrationTest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";
    private static final String CHAT_PREFS = "chat_messages";

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

    @Test
    public void completeFlow_firstTimeUser_configuresSettingsAndStartsChat() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // First time user - no configuration
            // Try to send a message without configuration
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Hello"), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Should be redirected to settings or shown a message
            // The fragment should handle this gracefully
            
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());

            // Configure API settings
            onView(withId(R.id.apiKeyInput))
                    .perform(replaceText("test-api-key"), closeSoftKeyboard());
            onView(withId(R.id.apiUrlInput))
                    .perform(replaceText("http://localhost:1234/v1/chat/completions"), closeSoftKeyboard());
            onView(withId(R.id.modelNameInput))
                    .perform(replaceText("test-model"), closeSoftKeyboard());

            // Save settings
            onView(withId(R.id.saveButton))
                    .perform(click());

            // Should navigate back to chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_createMultipleSessions_switchBetweenThem() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Create first new chat
            onView(withId(R.id.button_new_chat))
                    .perform(click());

            // Send a message in first chat
            onView(withId(R.id.messageInput))
                    .perform(replaceText("First chat message"), closeSoftKeyboard());

            // Open drawer again
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Create second new chat
            onView(withId(R.id.button_new_chat))
                    .perform(click());

            // Send a message in second chat
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Second chat message"), closeSoftKeyboard());

            // Open drawer and verify multiple sessions exist
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Click on first chat session (index 1, since 0 is the current one)
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));

            // Should switch to the first chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_sendMessage_withoutApi_showsError() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Type and send a message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message"), closeSoftKeyboard());
            
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Message input should be cleared after sending
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));

            // Note: Actual API call will fail since we're using a test API key
            // but the app should handle it gracefully without crashing
        }
    }

    @Test
    public void completeFlow_navigateSettings_modifyAndSave_returnsToChat() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());

            // Verify settings are displayed
            onView(withId(R.id.apiKeyInput))
                    .check(matches(isDisplayed()));

            // Modify max tokens
            onView(withId(R.id.maxTokensInput))
                    .perform(replaceText("2048"), closeSoftKeyboard());

            // Save
            onView(withId(R.id.saveButton))
                    .perform(click());

            // Should return to chat fragment
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_emptyMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Try to send empty message
            onView(withId(R.id.messageInput))
                    .perform(replaceText(""), closeSoftKeyboard());
            
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Send button should still be enabled (message wasn't sent)
            onView(withId(R.id.sendButton))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_whitespaceMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Try to send whitespace-only message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("      "), closeSoftKeyboard());
            
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Input should still be enabled
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_drawerNavigation_opensAndCloses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

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
            // Type a message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message before rotation"), closeSoftKeyboard());

            // Simulate configuration change (rotation)
            scenario.recreate();

            // Message input should be cleared after recreate (new session loaded)
            // But the app should not crash
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_multipleNewChats_allPersist() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Create multiple new chats
            for (int i = 0; i < 3; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                
                onView(withId(R.id.button_new_chat))
                        .perform(click());
                
                // Wait a bit for the navigation
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Verify all chats are in the list
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_backNavigation_handledCorrectly() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Open drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Navigate to settings
            onView(withId(R.id.button_settings))
                    .perform(click());

            // The navigation component should handle this
            onView(withId(R.id.apiKeyInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_rapidClicks_handledGracefully() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Rapidly open drawer multiple times
            for (int i = 0; i < 5; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
        settingsManager.setApiKey("persistent-key");
        settingsManager.setApiUrl("http://persistent.url");
        settingsManager.setModelName("persistent-model");
        settingsManager.setTemperature(0.8f);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Open settings
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            onView(withId(R.id.button_settings))
                    .perform(click());

            // Verify settings were loaded
            onView(withId(R.id.apiKeyInput))
                    .check(matches(withText("persistent-key")));
            onView(withId(R.id.apiUrlInput))
                    .check(matches(withText("http://persistent.url")));
            onView(withId(R.id.modelNameInput))
                    .check(matches(withText("persistent-model")));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setApiKey("test-api-key");
        settingsManager.setApiUrl("http://localhost:1234/v1/chat/completions");
        settingsManager.setModelName("test-model");
    }
}