package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

                // Raw list: live cards are ephemeral and filtered out of getMessages().
                List<ChatMessage> messages = adapter.getMessagesRaw();
                assertEquals("flushed segment + running tool card", 2, messages.size());
                ChatMessage runningCard = messages.get(1);
                assertEquals(ChatMessage.TYPE_TOOL_CALL, runningCard.getType());
                assertTrue("live card starts in RUNNING state", runningCard.isRunning());
                assertTrue("live card is ephemeral", runningCard.isEphemeral());

                // Tool result: the running card flips to done and a result card is appended.
                callback.onToolResult("read_file", "{\"content\":\"hello\"}");
                messages = adapter.getMessagesRaw();
                assertEquals("segment + done card + result card", 3, messages.size());
                assertFalse("running flag cleared on result",
                        messages.get(1).isRunning());
                assertEquals(ChatMessage.TYPE_TOOL_RESULT, messages.get(2).getType());
                assertTrue("live result card is ephemeral", messages.get(2).isEphemeral());

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

                // A live run in flight: the typing placeholder was replaced by the
                // running tool card when the first call arrived.
                showTypingIndicator(fragment);
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());
                assertEquals(1, adapter.getItemCount());
                assertFalse(containsPlaceholder(adapter));

                // The service persists the partial history and delivers it on cancel;
                // reconciliation must replace every ephemeral item with exactly that list.
                List<ChatMessage> persisted = new ArrayList<>();
                persisted.add(new ChatMessage("hi", ChatMessage.TYPE_USER));
                persisted.add(ChatMessage.createToolCallMessage(Collections.singletonList(
                        new LlmApiService.ToolCall("call_1", "read_file", toolArgs))));
                callback.onCancelled(persisted);

                List<ChatMessage> messages = adapter.getMessagesRaw();
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

                // Simulate a live run: the typing placeholder is replaced by the
                // running tool card when the first call arrives.
                showTypingIndicator(fragment);
                AgentExecutionService.UICallback callback = uiCallback(fragment);
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());
                assertEquals(1, adapter.getItemCount());
                assertFalse(containsPlaceholder(adapter));
                ChatMessage card = adapter.getMessagesRaw().get(0);
                assertTrue("live card is ephemeral and filtered from getMessages()",
                        card.isEphemeral() && adapter.getMessages().isEmpty());

                // The stop button only acts while a run is in flight.
                setField(fragment, "loading", true);

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

                List<ChatMessage> messages = adapter.getMessagesRaw();
                assertEquals(1, messages.size());
                assertTrue(messages.get(0).isRunning());
                assertTrue("running live card is ephemeral", messages.get(0).isEphemeral());

                callback.onError("boom");

                messages = adapter.getMessagesRaw();
                assertEquals("the failed card stays visible as an interrupted entry",
                        1, messages.size());
                assertFalse("spinner must not spin forever after error",
                        messages.get(0).isRunning());
                assertEquals("interrupted", messages.get(0).getContent());
            });
        }
    }

    @Test
    public void midRunSave_persistsNoEphemeralAndNoNullToolCallIds() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-live-tool-midrun-save");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView()
                        .findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();
                AgentExecutionService.UICallback callback = uiCallback(fragment);

                // A live run in flight: typing placeholder, one RUNNING tool card,
                // one ephemeral result card, and a streaming bubble.
                showTypingIndicator(fragment);
                JsonObject toolArgs = new JsonObject();
                toolArgs.addProperty("path", "test.txt");
                callback.onToolCall("read_file", toolArgs.toString());
                callback.onToolResult("read_file", "{\"content\":\"hello\"}");
                callback.onStreamDelta("partial answer");
                try {
                    java.lang.reflect.Method flush = ChatFragment.class
                            .getDeclaredMethod("flushStreamingBubble");
                    flush.setAccessible(true);
                    flush.invoke(fragment);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                assertEquals("all four ephemeral kinds present internally",
                        4, adapter.getItemCount());

                // Mid-run save (the task-context-add path ends in saveMessages()):
                // whatever getMessages() yields here is what hits disk.
                List<ChatMessage> persisted = adapter.getMessages();
                for (ChatMessage message : persisted) {
                    assertFalse("no ephemeral item may reach persistence",
                            message.isEphemeral());
                    assertFalse("no typing row may reach persistence",
                            message.getType() == ChatMessage.TYPE_TYPING);
                    if (message.isToolResult()) {
                        assertNotNull("persisted tool_result must carry a real tool_call_id",
                                message.getToolCallId());
                    }
                }
                assertEquals("adapter view for persistence is empty mid-run",
                        0, persisted.size());

                // The repository round-trip must agree: load what a save of this
                // view would have written — nothing to corrupt with null ids.
                io.finett.droidclaw.repository.ChatRepository repository =
                        new io.finett.droidclaw.repository.ChatRepository(
                                fragment.requireContext());
                repository.saveMessages("session-live-tool-midrun-save", persisted);
                List<ChatMessage> loaded = repository.loadMessages("session-live-tool-midrun-save");
                assertEquals(0, loaded.size());
            });
        }
    }

    private static boolean containsPlaceholder(ChatAdapter adapter) {
        for (ChatMessage message : adapter.getMessagesRaw()) {
            if (message.getType() == ChatMessage.TYPE_TYPING) {
                return true;
            }
        }
        return false;
    }

    private static void setField(io.finett.droidclaw.fragment.ChatFragment fragment, String name, Object value) {
        try {
            java.lang.reflect.Field f = ChatFragment.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(fragment, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
