package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonParser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;

@RunWith(RobolectricTestRunner.class)
public class ChatAdapterRemoveMessageTest {

    @Test
    public void removeMessage_removesByIdentityAndNotifiesPosition() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage first = new ChatMessage("First", ChatMessage.TYPE_USER);
        ChatMessage second = new ChatMessage("Second", ChatMessage.TYPE_ASSISTANT);
        ChatMessage third = new ChatMessage("Third", ChatMessage.TYPE_ASSISTANT);
        adapter.addMessage(first);
        adapter.addMessage(second);
        adapter.addMessage(third);

        List<Integer> removedPositions = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                removedPositions.add(positionStart);
            }
        });

        adapter.removeMessage(second);

        assertEquals(Collections.singletonList(1), removedPositions);
        assertEquals(2, adapter.getItemCount());
        assertEquals("First", adapter.getMessages().get(0).getContent());
        assertEquals("Third", adapter.getMessages().get(1).getContent());
    }

    @Test
    public void removeMessage_unknownMessage_isNoop() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage kept = new ChatMessage("Kept", ChatMessage.TYPE_USER);
        ChatMessage foreign = new ChatMessage("Foreign", ChatMessage.TYPE_USER);
        adapter.addMessage(kept);

        List<Integer> removedPositions = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                removedPositions.add(positionStart);
            }
        });

        adapter.removeMessage(foreign);

        assertTrue(removedPositions.isEmpty());
        assertEquals(1, adapter.getItemCount());
        assertEquals("Kept", adapter.getMessages().get(0).getContent());
    }

    @Test
    public void removeMessage_afterSetMessagesReplacedList_isNoop() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage replaced = ChatMessage.createStreamingAssistantMessage();
        adapter.addMessage(replaced);
        adapter.setMessages(Collections.singletonList(
                new ChatMessage("Final history", ChatMessage.TYPE_ASSISTANT)));

        adapter.removeMessage(replaced);

        assertEquals(1, adapter.getItemCount());
        assertEquals("Final history", adapter.getMessages().get(0).getContent());
    }

    @Test
    public void removeMessage_dropsAllLiveBubblesFromFailedRun() {
        // Simulates the error path: a failed run leaves a streaming bubble plus
        // live tool-call and tool-result bubbles, all removed by identity.
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage user = new ChatMessage("Question", ChatMessage.TYPE_USER);
        adapter.addMessage(user);

        ChatMessage streaming = ChatMessage.createStreamingAssistantMessage();
        ChatMessage toolCall = ChatMessage.createToolCallMessage(
                Collections.singletonList(new LlmApiService.ToolCall(
                        "live-1", "execute_shell",
                        JsonParser.parseString("{\"command\":\"ls\"}").getAsJsonObject())));
        ChatMessage toolResult = ChatMessage.createToolResultMessage(
                null, "execute_shell", "total 0");
        adapter.addMessage(streaming);
        adapter.addMessage(toolCall);
        adapter.addMessage(toolResult);

        List<ChatMessage> live = new ArrayList<>();
        live.add(streaming);
        live.add(toolCall);
        live.add(toolResult);
        for (ChatMessage message : live) {
            adapter.removeMessage(message);
        }

        assertEquals(1, adapter.getItemCount());
        assertTrue(user == adapter.getMessages().get(0));
        assertFalse(adapter.getMessages().contains(toolCall));
    }
}
