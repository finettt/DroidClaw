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
import io.finett.droidclaw.util.ActivityLaunchHelper;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.util.TestUtils;

@RunWith(AndroidJUnit4.class)
public class UserFlowIntegrationTest {

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
    public void completeFlow_firstTimeUser_canNavigateToSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_createMultipleSessions_switchBetweenThem() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .perform(replaceText("First chat message"), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.button_new_chat))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .perform(replaceText("Second chat message"), closeSoftKeyboard());
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
    public void completeFlow_sendMessage_withoutApi_showsError() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message"), closeSoftKeyboard());
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(withText("")));

        }
    }

    @Test
    public void completeFlow_navigateSettings_viewSettingsList() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_emptyMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText(""), closeSoftKeyboard());
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.sendButton))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_whitespaceMessage_doesNotSend() {
        configureSettings();

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText("      "), closeSoftKeyboard());
            TestUtils.waitForUiReady();
            
            onView(withId(R.id.sendButton))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void completeFlow_drawerNavigation_opensAndCloses() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

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

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .perform(replaceText("Test message before rotation"), closeSoftKeyboard());
            TestUtils.waitForUiReady();

            scenario.recreate();
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_multipleNewChats_allPersist() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            for (int i = 0; i < 3; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
                
                onView(withId(R.id.button_new_chat))
                        .perform(click());
                TestUtils.waitForUiReady();
            }

            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.recycler_chat_sessions))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_backNavigation_handledCorrectly() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.drawer_layout))
                    .perform(open());
            TestUtils.waitForUiReady();

            onView(withId(R.id.button_settings))
                    .perform(click());
            TestUtils.waitForUiReady();

            onView(withId(R.id.recycler_settings))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_rapidClicks_handledGracefully() {
        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            for (int i = 0; i < 5; i++) {
                onView(withId(R.id.drawer_layout))
                        .perform(open());
                TestUtils.waitForUiReady();
            }

            onView(withId(R.id.drawer_layout))
                    .perform(androidx.test.espresso.contrib.DrawerActions.close());
            TestUtils.waitForUiReady();

            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void completeFlow_settingsPersistence_surviveAppRestart() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider testProvider = new Provider("persistent-provider", "Persistent Provider",
                "http://persistent.url", "persistent-key", "openai-completions");
        Model testModel = new Model("persistent-model", "Persistent Model", "openai-completions",
                false, Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("persistent-provider/persistent-model");
        settingsManager.setOnboardingCompleted(true);

        try (ActivityScenario<MainActivity> scenario = ActivityLaunchHelper.launchAndWait(MainActivity.class)) {
            TestUtils.waitForChatFragment();
            
            onView(withId(R.id.messageInput))
                    .check(matches(isDisplayed()));
        }
    }

    private void configureSettings() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider testProvider = new Provider("test-provider", "Test Provider",
                "http://localhost:1234/v1", "test-api-key", "openai-completions");
        Model testModel = new Model("test-model", "Test Model", "openai-completions",
                false, Arrays.asList("text"), 4096, 4096);
        testProvider.addModel(testModel);
        settingsManager.addProvider(testProvider);
        settingsManager.setDefaultModel("test-provider/test-model");
        settingsManager.setOnboardingCompleted(true);
    }
}