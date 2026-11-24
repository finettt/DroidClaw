package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;

@RunWith(RobolectricTestRunner.class)
public class ChatAdapterStreamingTextTest {

    @Test
    public void updateStreamingMessage_notifiesWithPayloadAndKeepsOtherMessages() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage older = new ChatMessage("Older message", ChatMessage.TYPE_USER);
        ChatMessage bubble = ChatMessage.createStreamingAssistantMessage();
        adapter.addMessage(older);
        adapter.addMessage(bubble);

        List<Integer> changedPositions = new ArrayList<>();
        List<List<Object>> deliveredPayloads = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                changedPositions.add(positionStart);
                deliveredPayloads.add(Collections.singletonList(payload));
            }
        });

        assertTrue(adapter.updateStreamingMessage(bubble.getStreamingId(), "Hello "));

        assertEquals(Collections.singletonList(1), changedPositions);
        assertEquals(Collections.singletonList(Collections.singletonList(
                ChatAdapter.PAYLOAD_STREAMING_TEXT)), deliveredPayloads);
        assertEquals("Hello ", adapter.getMessages().get(1).getContent());
        assertEquals("Older message", adapter.getMessages().get(0).getContent());
    }

    @Test
    public void updateStreamingMessage_bubbleWasReplaced_doesNotNotify() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage replacedBubble = ChatMessage.createStreamingAssistantMessage();
        adapter.addMessage(replacedBubble);
        adapter.setMessages(Collections.singletonList(
                new ChatMessage("Final history", ChatMessage.TYPE_ASSISTANT)));

        List<Integer> changedPositions = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                changedPositions.add(positionStart);
            }
        });

        assertFalse(adapter.updateStreamingMessage(replacedBubble.getStreamingId(), "Late delta"));

        assertEquals(Collections.emptyList(), changedPositions);
        assertEquals("Final history", adapter.getMessages().get(0).getContent());
    }

    @Test
    public void onBindViewHolder_withStreamingPayload_skipsFullBind() {
        ChatAdapter adapter = new ChatAdapter();
        ChatMessage bubble = ChatMessage.createStreamingAssistantMessage();
        adapter.addMessage(bubble);
        adapter.updateStreamingMessage(bubble.getStreamingId(), "Live text");
        FakeViewHolder holder = new FakeViewHolder();

        adapter.onBindViewHolder(holder, 0,
                Collections.<Object>singletonList(ChatAdapter.PAYLOAD_STREAMING_TEXT));

        assertEquals(1, holder.streamingBinds);
        assertEquals("Live text", holder.lastStreamingText);
    }

    private static final class FakeViewHolder extends ChatAdapter.MessageViewHolder {
        private int streamingBinds;
        private String lastStreamingText;

        FakeViewHolder() {
            super(mock(View.class));
        }

        @Override
        void bind(ChatMessage message, ChatAdapter adapter) {
            throw new AssertionError("Full bind must not run for a streaming payload");
        }

        @Override
        void bindStreamingText(ChatMessage message) {
            streamingBinds++;
            lastStreamingText = message.getContent();
        }
    }
}
