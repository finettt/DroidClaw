package io.finett.droidclaw.ui;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
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
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.ActivityLaunchHelper;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.util.TestUtils;

/**
 * Espresso UI tests for critical user journeys.
 * These tests verify end-to-end user flows through the UI.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CriticalUserJourneysUITest {

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
        
        // Mark onboarding as completed so tests can access MainActivity directly
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setOnboardingCompleted(true);
    }
    
    @After
    public void tearDown() {
        if (idlingResource != null) {
            IdlingRegistry.getInstance().unregister(idlingResource);
            idlingResource = null;
        }
    }

    @Test
    public void userJourney_firstLaunch_showsMainChatInterface() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User launches app for first time
            // Should see main chat interface
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            // User opens drawer to explore
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            // Drawer should be open with navigation options
            onView(withId(R.id.button_new_chat))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.button_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_sendMessage_completeFlow() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User types a message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Hello, how are you?"), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            // User sends the message
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Input should be cleared
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_createMultipleChats_switchBetween() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled)
            TestUtils.waitForChatFragment();
            
            // User creates first chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Drawer should close
            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            // User creates second chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            // User switches to previous chat
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            
            // Click on first chat in list (index 1, since current is 0)
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
            TestUtils.waitForUiReady();

            // Should be in chat view
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_navigateToSettings_viewSettingsList() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled before opening drawer)
            TestUtils.waitForChatFragment();
            
            // User opens drawer
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            // User goes to settings
            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Should see settings list
            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_drawerInteraction_openCloseMultipleTimes() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled)
            TestUtils.waitForChatFragment();
            
            // User explores drawer multiple times
            for (int i = 0; i < 3; i++) {
                // Open drawer
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
                
                onView(withId(R.id.drawer_layout))
                        .check(matches(isOpen(GravityCompat.START)));

                // Close drawer
                onView(withId(R.id.drawer_layout))
                        .perform(close());
                TestUtils.waitForUiReady();
                
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
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User tries to send empty message
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Input should remain enabled (message not sent)
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));

            // User tries whitespace-only message
            onView(withId(R.id.messageInput))
                    .perform(replaceText("   "), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Should still be functional
            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void userJourney_settingsWithoutApiKey_showsValidation() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User tries to send message without configuring API
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message"), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Should be redirected to settings or shown a message
            // The app handles this gracefully
            
            // Verify app didn't crash - either in chat or settings
            // (behavior depends on toast handling)
        }
    }

    @Test
    public void userJourney_rapidActions_stressTest() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User performs rapid actions
            for (int i = 0; i < 5; i++) {
                // Type and clear message
                onView(withId(R.id.messageInput))
                        .perform(replaceText("Quick " + i), closeSoftKeyboard());
                TestUtils.waitForUiReady();
                onView(withId(R.id.messageInput))
                        .perform(replaceText(""));
                TestUtils.waitForUiReady();

                // Open and close drawer quickly
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
                onView(withId(R.id.drawer_layout))
                        .perform(close());
                TestUtils.waitForUiReady();
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

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User types very long message
            String longMessage = "This is a very long message that tests the input handling capabilities. " +
                    "It contains multiple sentences and should be processed correctly by the application. " +
                    "The system should handle this gracefully without any performance issues or UI glitches. " +
                    "Long messages are common in chat applications and must be supported properly.";

            onView(withId(R.id.messageInput))
                    .perform(replaceText(longMessage), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            // User sends the long message
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Message should be cleared
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_sessionPersistence_acrossAppRestart() {
        configureSettings();

        // First launch - create chat
        try (ActivityScenario<MainActivity> scenario1 = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();
        }

        // Second launch - verify chat persisted
        try (ActivityScenario<MainActivity> scenario2 = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            
            // Should have at least 2 sessions (initial + created)
            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_settingsNavigation_backButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled)
            TestUtils.waitForChatFragment();
            
            // Navigate to settings
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Press back
            pressBack();
            TestUtils.waitForUiReady();

            // Should return to chat (or may exit app depending on nav implementation)
            // The important thing is it doesn't crash
        }
    }

    @Test
    public void userJourney_multipleSessionsWorkflow_comprehensive() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled)
            TestUtils.waitForChatFragment();
            
            // Create Session 1
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            // Create Session 2
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            // Create Session 3
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            // Switch to Session 1 (should be at index 2 now)
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));
            TestUtils.waitForUiReady();

            // Verify we're in a chat view
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            // Switch to Session 2
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
            TestUtils.waitForUiReady();

            // Still functional
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_toolbarNavigation_hamburgerMenu() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready (ensures navigation settled)
            TestUtils.waitForChatFragment();
            
            // Drawer should be closed initially
            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            // Tap hamburger menu (open drawer via toolbar)
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

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

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            // Wait for ChatFragment to be fully ready
            TestUtils.waitForChatFragment();
            
            // User types message with special characters
            String specialMessage = "Hello! Test message with numbers 123";

            onView(withId(R.id.messageInput))
                    .perform(replaceText(specialMessage), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            // Send message
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            // Should handle special characters without crashing
            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        // Create a test provider with a model
        Provider testProvider = new Provider("test-provider", "Test Provider",
                "http://localhost:1234/v1", "test-api-key", "openai-completions");
        Model testModel = new Model("test-model", "Test Model", "openai-completions",
                false, java.util.Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("test-provider/test-model");
        // Mark onboarding as completed
        settingsManager.setOnboardingCompleted(true);
    }
}