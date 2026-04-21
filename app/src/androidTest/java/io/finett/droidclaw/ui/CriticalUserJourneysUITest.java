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

@RunWith(AndroidJUnit4.class)
@LargeTest
public class CriticalUserJourneysUITest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";
    private static final String CHAT_PREFS = "chat_messages";
    
    private TestUtils.SimpleIdlingResource idlingResource;

    @Before
    public void setUp() {
        SharedPreferences settingsPrefs = getApplicationContext()
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        settingsPrefs.edit().clear().commit();

        SharedPreferences chatPrefs = getApplicationContext()
                .getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
        
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
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

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
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Hello, how are you?"), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_createMultipleChats_switchBetween() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_navigateToSettings_viewSettingsList() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            // User goes to settings
            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_drawerInteraction_openCloseMultipleTimes() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            for (int i = 0; i < 3; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
                
                onView(withId(R.id.drawer_layout))
                        .check(matches(isOpen(GravityCompat.START)));

                onView(withId(R.id.drawer_layout))
                        .perform(close());
                TestUtils.waitForUiReady();
                
                onView(withId(R.id.drawer_layout))
                        .check(matches(isClosed(GravityCompat.START)));
            }

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_emptyMessageAttempt_validation() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));

            onView(withId(R.id.messageInput))
                    .perform(replaceText("   "), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void userJourney_settingsWithoutApiKey_showsValidation() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message"), closeSoftKeyboard());
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            
        }
    }

    @Test
    public void userJourney_rapidActions_stressTest() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            for (int i = 0; i < 5; i++) {
                onView(withId(R.id.messageInput))
                        .perform(replaceText("Quick " + i), closeSoftKeyboard());
                TestUtils.waitForUiReady();
                onView(withId(R.id.messageInput))
                        .perform(replaceText(""));
                TestUtils.waitForUiReady();

                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
                onView(withId(R.id.drawer_layout))
                        .perform(close());
                TestUtils.waitForUiReady();
            }

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void userJourney_longTextInput_handlesCorrectly() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            String longMessage = "This is a very long message that tests the input handling capabilities. " +
                    "It contains multiple sentences and should be processed correctly by the application. " +
                    "The system should handle this gracefully without any performance issues or UI glitches. " +
                    "Long messages are common in chat applications and must be supported properly.";

            onView(withId(R.id.messageInput))
                    .perform(replaceText(longMessage), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    @Test
    public void userJourney_sessionPersistence_acrossAppRestart() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario1 = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();
        }

        try (ActivityScenario<MainActivity> scenario2 = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_settingsNavigation_backButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            pressBack();
            TestUtils.waitForUiReady();

        }
    }

    @Test
    public void userJourney_multipleSessionsWorkflow_comprehensive() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.button_new_chat)).perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));

            onView(withId(R.id.drawer_layout)).perform(open());
            TestUtils.waitForUiReady();
            onView(withId(R.id.recycler_chat_sessions))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void userJourney_toolbarNavigation_hamburgerMenu() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .check(matches(isClosed(GravityCompat.START)));

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout))
                    .check(matches(isOpen(GravityCompat.START)));

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
            TestUtils.waitForChatFragment();
            
            String specialMessage = "Hello! Test message with numbers 123";

            onView(withId(R.id.messageInput))
                    .perform(replaceText(specialMessage), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider testProvider = new Provider("test-provider", "Test Provider",
                "http://localhost:1234/v1", "test-api-key", "openai-completions");
        Model testModel = new Model("test-model", "Test Model", "openai-completions",
                false, java.util.Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("test-provider/test-model");
        settingsManager.setOnboardingCompleted(true);
    }
}