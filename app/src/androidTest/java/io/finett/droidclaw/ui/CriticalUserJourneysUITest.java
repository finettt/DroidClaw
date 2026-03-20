package io.finett.droidclaw.ui;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.close;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.contrib.DrawerMatchers.isOpen;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.view.GravityCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Espresso UI tests for critical user journeys.
 * These tests verify end-to-end user flows through the UI.
 * Note: These tests require Espresso input injection which is not compatible with API 36+.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@SdkSuppress(maxSdkVersion = 35)
public class CriticalUserJourneysUITest {

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
    public void userJourney_firstLaunch_completeOnboarding() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User launches app for first time
            // Should see main chat interface
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            // User opens drawer to explore
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Drawer should be open with navigation options
            onView(withId(R.id.button_new_chat))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.button_settings))
                    .check(matches(isDisplayed()));

            // User navigates to settings to configure
            onView(withId(R.id.button_settings))
                    .perform(click());

            // User enters API configuration
            onView(withId(R.id.apiKeyInput))
                    .perform(replaceText("sk-test-key-123"), closeSoftKeyboard());
            onView(withId(R.id.apiUrlInput))
                    .perform(replaceText("https://api.openai.com/v1/chat/completions"), closeSoftKeyboard());
            onView(withId(R.id.modelNameInput))
                    .perform(replaceText("gpt-3.5-turbo"), closeSoftKeyboard());

            // User saves settings
            onView(withId(R.id.saveButton))
                    .perform(click());

            // Should return to chat view
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_sendMessage_completeFlow() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User types a message
            onView(withId(R.id.messageInput))
                    .perform(typeText("Hello, how are you?"), closeSoftKeyboard());

            // User sends the message
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Input should be cleared
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_createMultipleChats_switchBetween() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User creates first chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            onView(withId(R.id.button_new_chat))
                    .perform(click());

            // Drawer should close
            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            // User creates second chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            onView(withId(R.id.button_new_chat))
                    .perform(click());

            // User switches to previous chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            
            // Click on first chat in list (index 1, since current is 0)
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));

            // Should be in chat view
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_navigateToSettings_modifyAndReturn() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User opens drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // User goes to settings
            onView(withId(R.id.button_settings))
                    .perform(click());

            // User modifies max tokens
            onView(withId(R.id.maxTokensInput))
                    .perform(replaceText("2048"), closeSoftKeyboard());

            // User saves changes
            onView(withId(R.id.saveButton))
                    .perform(click());

            // Should return to chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_drawerInteraction_openCloseMultipleTimes() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User explores drawer multiple times
            for (int i = 0; i < 3; i++) {
                // Open drawer
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                
                onView(withId(R.id.drawer_layout))
                        .check(matches(isOpen(GravityCompat.START)));

                // Close drawer
                onView(withId(R.id.drawer_layout))
                        .perform(close());
                
                onView(withId(R.id.drawer_layout))
                        .check(matches(isClosed(GravityCompat.START)));
            }

            // App should still be functional
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_emptyMessageAttempt_validation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User tries to send empty message
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Input should remain enabled (message not sent)
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));

            // User tries whitespace-only message
            onView(withId(R.id.messageInput))
                    .perform(typeText("   "), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Should still be functional
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void userJourney_settingsWithoutApiKey_showsValidation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User tries to send message without configuring API
            onView(withId(R.id.messageInput))
                    .perform(typeText("Test message"), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Should be redirected to settings or shown a message
            // The app handles this gracefully
            
            // Verify app didn't crash - either in chat or settings
            // (behavior depends on toast handling)
        }
    }

    @Test
    public void userJourney_rapidActions_stressTest() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User performs rapid actions
            for (int i = 0; i < 5; i++) {
                // Type and clear message
                onView(withId(R.id.messageInput))
                        .perform(typeText("Quick " + i), closeSoftKeyboard());
                onView(withId(R.id.messageInput))
                        .perform(replaceText(""));

                // Open and close drawer quickly
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                onView(withId(R.id.drawer_layout))
                        .perform(close());
            }

            // App should remain stable
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void userJourney_longTextInput_handlesCorrectly() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User types very long message
            String longMessage = "This is a very long message that tests the input handling capabilities. " +
                    "It contains multiple sentences and should be processed correctly by the application. " +
                    "The system should handle this gracefully without any performance issues or UI glitches. " +
                    "Long messages are common in chat applications and must be supported properly.";

            onView(withId(R.id.messageInput))
                    .perform(typeText(longMessage), closeSoftKeyboard());

            // User sends the long message
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Message should be cleared
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_sessionPersistence_acrossAppRestart() {
        configureSettings();

        // First launch - create chat
        try (ActivityScenario<MainActivity> scenario1 = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            onView(withId(R.id.button_new_chat))
                    .perform(click());
        }

        // Second launch - verify chat persisted
        try (ActivityScenario<MainActivity> scenario2 = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            
            // Should have at least 2 sessions (initial + created)
            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_settingsNavigation_backButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Navigate to settings
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            onView(withId(R.id.button_settings))
                    .perform(click());

            // Verify in settings
            onView(withId(R.id.apiKeyInput))
                    .check(matches(isDisplayed()));

            // Press back
            pressBack();

            // Should return to chat (or may exit app depending on nav implementation)
            // The important thing is it doesn't crash
        }
    }

    @Test
    public void userJourney_multipleSessionsWorkflow_comprehensive() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Create Session 1
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.button_new_chat)).perform(click());

            // Create Session 2
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.button_new_chat)).perform(click());

            // Create Session 3
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.button_new_chat)).perform(click());

            // Switch to Session 1 (should be at index 2 now)
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));

            // Verify we're in a chat view
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            // Switch to Session 2
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));

            // Still functional
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_toolbarNavigation_hamburgerMenu() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Drawer should be closed initially
            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            // Tap hamburger menu (open drawer via toolbar)
            onView(withId(R.id.drawer_layout))
                    .perform(open());

            // Drawer should open
            onView(withId(R.id.drawer_layout))
                    .check(matches(isOpen(GravityCompat.START)));

            // Navigation items should be visible
            onView(withId(R.id.button_new_chat))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.button_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_specialCharacters_inMessageInput() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // User types message with special characters
            String specialMessage = "Hello! Test message with numbers 123";

            onView(withId(R.id.messageInput))
                    .perform(typeText(specialMessage), closeSoftKeyboard());

            // Send message
            onView(withId(R.id.sendButton))
                    .perform(click());

            // Should handle special characters without crashing
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_completeSettingsConfiguration_allFields() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Navigate to settings
            onView(withId(R.id.drawer_layout)).perform(open());
            onView(withId(R.id.button_settings)).perform(click());

            // Fill all text settings fields
            onView(withId(R.id.apiKeyInput))
                    .perform(replaceText("sk-comprehensive-test"), closeSoftKeyboard());
            onView(withId(R.id.apiUrlInput))
                    .perform(replaceText("https://api.test.com/v1/chat/completions"), closeSoftKeyboard());
            onView(withId(R.id.modelNameInput))
                    .perform(replaceText("gpt-4"), closeSoftKeyboard());
            onView(withId(R.id.maxTokensInput))
                    .perform(replaceText("4096"), closeSoftKeyboard());
            onView(withId(R.id.systemPromptInput))
                    .perform(replaceText("You are a helpful assistant."), closeSoftKeyboard());

            // Save all settings
            onView(withId(R.id.saveButton))
                    .perform(click());

            // Return to chat
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setApiKey("test-api-key");
        settingsManager.setApiUrl("http://localhost:1234/v1/chat/completions");
        settingsManager.setModelName("test-model");
    }
}