package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.service.AgentExecutionService;

/**
 * Live tool-card rendering in the chat stream: a run with one tool call shows a
 * RUNNING card during execution, a result card after it, and the typing indicator
 * before the first delta. At onComplete the adapter is reconciled to exactly the
 * canonical history — no live ephemera, no duplicates. The status pill is gone
 * from the layout entirely.
 */
@RunWith(AndroidJUnit4.class)
public class ChatLiveToolCardsTest {

    @Before
    public void setUp() {
        SharedPreferences chatPrefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
    }

    @Test
    public void toolRun_showsRunningCardThenResultAndReconcilesToCanonicalHistory() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-live-tool-cards");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView().findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();
                AgentExecutionService.UICallback callback = uiCallback(fragment);

                // Typing indicator appears on send, before any delta or tool card.
                showTypingIndicator(fragment);
                assertTrue("typing indicator visible before first delta",
                        containsPlaceholder(adapter));

                // First text delta replaces the typing indicator with the streaming bubble.
                callback.onStreamDelta("Reading the file.");
                assertFalse("typing indicator replaced by first delta",
                        containsPlaceholder(adapter));

                // Tool call: the streamed segment flushes and a RUNNING card appears.
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());

                List<ChatMessage> messages = adapter.getMessages();
                assertEquals("flushed segment + running tool card", 2, messages.size());
                ChatMessage runningCard = messages.get(1);
                assertEquals(ChatMessage.TYPE_TOOL_CALL, runningCard.getType());
                assertTrue("live card starts in RUNNING state", runningCard.isRunning());

                // Tool result: the running card flips to done and a result card is appended.
                callback.onToolResult("read_file", "{\"content\":\"hello\"}");
                messages = adapter.getMessages();
                assertEquals("segment + done card + result card", 3, messages.size());
                assertFalse("running flag cleared on result",
                        messages.get(1).isRunning());
                assertEquals(ChatMessage.TYPE_TOOL_RESULT, messages.get(2).getType());

                // Reconciliation: the canonical history carries exactly one tool-call
                // and one tool-result message; live ephemera are dropped without duplicates.
                List<ChatMessage> canonical = new ArrayList<>();
                canonical.add(new ChatMessage("hi", ChatMessage.TYPE_USER));
                canonical.add(new ChatMessage("Reading the file.", ChatMessage.TYPE_ASSISTANT));
                canonical.add(ChatMessage.createToolCallMessage(Collections.singletonList(
                        new LlmApiService.ToolCall("call_1", "read_file", toolArgs))));
                canonical.add(ChatMessage.createToolResultMessage("call_1", "read_file",
                        "{\"content\":\"hello\"}"));
                canonical.add(new ChatMessage("Done.", ChatMessage.TYPE_ASSISTANT));
                callback.onComplete("Done.", canonical);

                messages = adapter.getMessages();
                assertEquals("reconciled to exactly the canonical history",
                        canonical.size(), messages.size());
                for (int i = 0; i < canonical.size(); i++) {
                    assertEquals(canonical.get(i).getType(), messages.get(i).getType());
                }
                assertFalse("no live placeholder survives reconciliation",
                        containsPlaceholder(adapter));
            });
        }
    }

    @Test
    public void onCancelled_reconcilesToPersistedHistory() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-live-tool-cancel");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView()
                        .findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();
                AgentExecutionService.UICallback callback = uiCallback(fragment);

                // A live run in flight: typing indicator + running tool card.
                showTypingIndicator(fragment);
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());
                assertEquals(2, adapter.getItemCount());

                // The service persists the partial history and delivers it on cancel;
                // reconciliation must replace every ephemeral item with exactly that list.
                List<ChatMessage> persisted = new ArrayList<>();
                persisted.add(new ChatMessage("hi", ChatMessage.TYPE_USER));
                persisted.add(ChatMessage.createToolCallMessage(Collections.singletonList(
                        new LlmApiService.ToolCall("call_1", "read_file", toolArgs))));
                callback.onCancelled(persisted);

                List<ChatMessage> messages = adapter.getMessages();
                assertEquals("reconciled to exactly the persisted history",
                        persisted.size(), messages.size());
                assertFalse("no live placeholder survives cancellation",
                        containsPlaceholder(adapter));
                for (ChatMessage message : messages) {
                    assertFalse("no card may keep spinning after cancellation",
                            message.isRunning());
                }
            });
        }
    }

    @Test
    public void stopWithoutActiveSession_clearsLiveEphemera() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-live-tool-stop");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView()
                        .findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();

                // Simulate a live run: typing indicator + a running tool card.
                showTypingIndicator(fragment);
                AgentExecutionService.UICallback callback = uiCallback(fragment);
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());
                assertEquals(2, adapter.getItemCount());

                // No service/session bound in this test: the stop path resets the UI
                // directly and must drop every ephemeral item.
                fragment.requireView().findViewById(R.id.sendButton).performClick();

                assertEquals("all live ephemera cleared on stop", 0, adapter.getItemCount());
            });
        }
    }

    @Test
    public void onError_flipsRunningCardsToFailedState() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-live-tool-error");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView()
                        .findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();
                AgentExecutionService.UICallback callback = uiCallback(fragment);

                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("command", "ls");
                callback.onToolCall("execute_shell", toolArgs.toString());

                List<ChatMessage> messages = adapter.getMessages();
                assertEquals(1, messages.size());
                assertTrue(messages.get(0).isRunning());

                callback.onError("boom");

                messages = adapter.getMessages();
                assertEquals("the failed card stays visible as an interrupted entry",
                        1, messages.size());
                assertFalse("spinner must not spin forever after error",
                        messages.get(0).isRunning());
                assertEquals("interrupted", messages.get(0).getContent());
            });
        }
    }

    private static boolean containsPlaceholder(ChatAdapter adapter) {
        for (ChatMessage message : adapter.getMessages()) {
            if (message == ChatAdapter.TYPING_PLACEHOLDER) {
                return true;
            }
        }
        return false;
    }

    private static void showTypingIndicator(io.finett.droidclaw.fragment.ChatFragment fragment) {
        try {
            java.lang.reflect.Method method = ChatFragment.class
                    .getDeclaredMethod("showTypingIndicator");
            method.setAccessible(true);
            method.invoke(fragment);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static AgentExecutionService.UICallback uiCallback(ChatFragment fragment) {
        try {
            Field field = ChatFragment.class.getDeclaredField("agentUiCallback");
            field.setAccessible(true);
            return (AgentExecutionService.UICallback) field.get(fragment);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void attachNavController(ChatFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
    }
}
