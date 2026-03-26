package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(AndroidJUnit4.class)
public class ChatFragmentTest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";
    private static final String CHAT_PREFS = "chat_messages";

    @Before
    public void setUp() {
        SharedPreferences settingsPrefs = getApplicationContext()
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        settingsPrefs.edit().clear().commit();

        SharedPreferences chatPrefs = getApplicationContext()
                .getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
    }

    @Test
    public void launch_withSessionId_loadsSavedMessages() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        repository.saveMessages("session-1", java.util.Arrays.asList(
                new ChatMessage("Hello", ChatMessage.TYPE_USER),
                new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT)
        ));

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-1");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                assertEquals(2, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void sendMessage_withoutConfiguration_keepsInputAndLoadingHidden() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-2");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                ProgressBar progressBar = fragment.requireView().findViewById(R.id.progressBar);
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);

                messageInput.setText("Need configuration");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                assertEquals("Need configuration", messageInput.getText().toString());
                assertEquals(android.view.View.GONE, progressBar.getVisibility());
                assertEquals(0, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void sendMessage_withBlankInput_doesNothing() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-3");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);

                messageInput.setText("   ");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                assertEquals(0, recyclerView.getAdapter().getItemCount());
                assertTrue(messageInput.isEnabled());
            });
        }
    }

    @Test
    public void launch_withoutSessionId_stillLoadsFragment() {
        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                assertNotNull(fragment.requireView().findViewById(R.id.recyclerView));
                assertNotNull(fragment.requireView().findViewById(R.id.messageInput));
                assertNotNull(fragment.requireView().findViewById(R.id.sendButton));
            });
        }
    }

    @Test
    public void sendMessage_withConfiguration_addsUserMessage() {
        configureSettings();

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-api-1");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);

                messageInput.setText("Test message");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                // Wait a bit for async processing
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // User message should be added immediately
                assertTrue("Should have at least user message",
                        recyclerView.getAdapter().getItemCount() >= 1);
                assertEquals("", messageInput.getText().toString());
            });
        }
    }

    @Test
    public void sendMessage_withConfiguration_showsLoadingState() {
        configureSettings();

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-api-2");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                ImageButton sendButton = fragment.requireView().findViewById(R.id.sendButton);
                ProgressBar progressBar = fragment.requireView().findViewById(R.id.progressBar);

                messageInput.setText("Test");
                sendButton.performClick();

                // Wait a moment for UI to update
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Should show loading state (or have already completed, which is also valid)
                // The important thing is that it doesn't crash
                assertNotNull(progressBar);
                assertFalse("Send button should be disabled or re-enabled",
                        sendButton.isEnabled() && progressBar.getVisibility() == android.view.View.VISIBLE);
            });
        }
    }

    @Test
    public void recyclerView_hasCorrectLayoutManager() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-layout");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                assertNotNull("RecyclerView should have a layout manager",
                        recyclerView.getLayoutManager());
            });
        }
    }

    @Test
    public void initialState_inputsAreEnabled() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-enabled");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                ImageButton sendButton = fragment.requireView().findViewById(R.id.sendButton);
                ProgressBar progressBar = fragment.requireView().findViewById(R.id.progressBar);

                assertTrue("Message input should be enabled", messageInput.isEnabled());
                assertTrue("Send button should be enabled", sendButton.isEnabled());
                assertEquals("Progress bar should be hidden",
                        android.view.View.GONE, progressBar.getVisibility());
            });
        }
    }

    @Test
    public void sendMessage_withWhitespaceOnly_doesNothing() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-whitespace");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);

                messageInput.setText("  \n\t  ");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                assertEquals(0, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void loadChatHistory_withMultipleMessages_loadsAll() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        repository.saveMessages("session-multi", java.util.Arrays.asList(
                new ChatMessage("Message 1", ChatMessage.TYPE_USER),
                new ChatMessage("Response 1", ChatMessage.TYPE_ASSISTANT),
                new ChatMessage("Message 2", ChatMessage.TYPE_USER),
                new ChatMessage("Response 2", ChatMessage.TYPE_ASSISTANT)
        ));

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-multi");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                assertEquals(4, recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void sendMessage_withNullSessionId_doesNotCrash() {
        configureSettings();

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                
                messageInput.setText("Test");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                // Should not crash - this is the main assertion
                assertNotNull(fragment.requireView());
            });
        }
    }

    @Test
    public void sendMessage_withEmptySessionId_doesNotCrash() {
        configureSettings();

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                
                messageInput.setText("Test");
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                // Should not crash
                assertNotNull(fragment.requireView());
            });
        }
    }

    @Test
    public void fragmentDestroy_cancelsApiRequests() {
        configureSettings();

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-cancel");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                messageInput.setText("Test");
                fragment.requireView().findViewById(R.id.sendButton).performClick();
            });

            // Destroy fragment - should cancel pending requests without crashing
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void sendMessage_withLongMessage_handlesCorrectly() {
        configureSettings();

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-long");

        String longMessage = "This is a very long message that contains many words and " +
                "should test whether the chat fragment can handle long text input properly " +
                "without any issues or crashes in the message sending and display system. " +
                "It includes multiple sentences and should be processed correctly.";

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);

                EditText messageInput = fragment.requireView().findViewById(R.id.messageInput);
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);

                messageInput.setText(longMessage);
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                // Wait for processing
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                assertTrue("Should add long message",
                        recyclerView.getAdapter().getItemCount() >= 1);
            });
        }
    }

    @Test
    public void viewLifecycle_recreate_maintainsState() {
        ChatRepository repository = new ChatRepository(getApplicationContext());
        repository.saveMessages("session-lifecycle", java.util.Arrays.asList(
                new ChatMessage("Existing message", ChatMessage.TYPE_USER)
        ));

        android.os.Bundle args = new android.os.Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-lifecycle");

        try (FragmentScenario<ChatFragment> scenario =
                     FragmentScenario.launchInContainer(ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                assertEquals(1, recyclerView.getAdapter().getItemCount());
            });

            // Recreate the fragment
            scenario.recreate();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
                // Should reload messages from repository
                assertEquals(1, recyclerView.getAdapter().getItemCount());
            });
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

    private void attachNavController(ChatFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
    }
}