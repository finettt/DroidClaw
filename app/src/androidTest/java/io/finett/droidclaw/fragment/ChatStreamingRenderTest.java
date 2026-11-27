package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ChatAdapter;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.service.AgentExecutionService;

@RunWith(AndroidJUnit4.class)
public class ChatStreamingRenderTest {

    @Before
    public void setUp() {
        SharedPreferences chatPrefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE);
        chatPrefs.edit().clear().commit();
    }

    @Test
    public void streamingDeltasFollowBubbleIdentityAcrossInsertionAndToolBoundary() {
        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, "session-streaming-render");

        try (FragmentScenario<ChatFragment> scenario = FragmentScenario.launchInContainer(
                ChatFragment.class, args, R.style.Theme_DroidClaw)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.chatFragment);
                RecyclerView recyclerView = (RecyclerView) fragment.requireView().findViewById(R.id.recyclerView);
                ChatAdapter adapter = (ChatAdapter) recyclerView.getAdapter();
                AgentExecutionService.UICallback callback = uiCallback(fragment);

                callback.onStreamDelta("Hel");

                List<ChatMessage> withInsertion = new ArrayList<>(adapter.getMessages());
                withInsertion.add(0, new ChatMessage("Earlier note", ChatMessage.TYPE_USER));
                withInsertion.add(new ChatMessage("Trailing note", ChatMessage.TYPE_TOOL_RESULT));
                adapter.setMessages(withInsertion);

                callback.onStreamDelta("lo ");
                flush(fragment);

                List<ChatMessage> messages = adapter.getMessages();
                assertEquals(3, messages.size());
                assertEquals("Earlier note", messages.get(0).getContent());
                assertEquals("Hello ", messages.get(1).getContent());
                assertEquals("Trailing note", messages.get(2).getContent());

                callback.onStreamDelta("world");
                callback.onToolCall("shell", "{}");
                flush(fragment);

                // After onToolCall: the streaming bubble is flushed and reset, and a
                // live RUNNING tool card is appended. No typing placeholder — the
                // first tool card replaces it.
                messages = adapter.getMessages();
                assertEquals(4, messages.size());
                assertEquals("Earlier note", messages.get(0).getContent());
                assertEquals("Hello world", messages.get(1).getContent());
                assertEquals("Trailing note", messages.get(2).getContent());
                assertEquals(ChatMessage.TYPE_TOOL_CALL, messages.get(3).getType());
                String firstBubbleId = messages.get(1).getStreamingId();

                callback.onStreamDelta("Second segment");
                flush(fragment);

                // The next delta starts a new streaming bubble after the tool card.
                messages = adapter.getMessages();
                assertEquals(5, messages.size());
                assertEquals("Earlier note", messages.get(0).getContent());
                assertEquals("Hello world", messages.get(1).getContent());
                assertEquals("Trailing note", messages.get(2).getContent());
                assertEquals(ChatMessage.TYPE_TOOL_CALL, messages.get(3).getType());
                assertEquals("Second segment", messages.get(4).getContent());
                assertNotNull(firstBubbleId);
                assertNotNull(messages.get(4).getStreamingId());
                assertNotEquals(firstBubbleId, messages.get(4).getStreamingId());
            });
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

    private static void flush(ChatFragment fragment) {
        try {
            Method method = ChatFragment.class.getDeclaredMethod("flushStreamingBubble");
            method.setAccessible(true);
            method.invoke(fragment);
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
