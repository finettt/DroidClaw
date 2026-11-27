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
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recyclerView);
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

                // After onToolCall: the streaming bubble is flushed and reset, a
                // typing placeholder is shown (LLM is busy with the tool call),
                // and a live RUNNING tool card is appended.
                messages = adapter.getMessages();
                assertEquals(5, messages.size());
                assertEquals("Earlier note", messages.get(0).getContent());
                assertEquals("Hello world", messages.get(1).getContent());
                assertEquals("Trailing note", messages.get(2).getContent());
                String firstBubbleId = messages.get(1).getStreamingId();
                // The typing placeholder and live tool card are the last two items.
                assertEquals(ChatAdapter.TYPING_PLACEHOLDER, messages.get(3));
                assertEquals(ChatMessage.TYPE_TOOL_CALL, messages.get(4).getType());

                callback.onStreamDelta("Second segment");
                flush(fragment);

                // The next delta removes the typing placeholder and starts a new
                // streaming bubble; the live tool card stays.
                messages = adapter.getMessages();
                assertEquals(5, messages.size());
                assertEquals("Earlier note", messages.get(0).getContent());
                assertEquals("Hello world", messages.get(1).getContent());
                assertEquals("Trailing note", messages.get(2).getContent());
                // The tool card and the new bubble are at positions 3 and 4 in some order;
                // the new bubble has a different streaming id from the first.
                ChatMessage newBubble = null;
                for (ChatMessage m : messages) {
                    if ("Second segment".equals(m.getContent())) {
                        newBubble = m;
                        break;
                    }
                }
                assertNotNull("the second-segment bubble must be present", newBubble);
                assertNotNull(firstBubbleId);
                assertNotNull(newBubble.getStreamingId());
                assertNotEquals(firstBubbleId, newBubble.getStreamingId());
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
