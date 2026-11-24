package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.R;
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
    public void onBindViewHolder_withStreamingPayload_updatesOnlyMessageText() {
        CountingBindAdapter adapter = new CountingBindAdapter();
        ChatMessage bubble = ChatMessage.createStreamingAssistantMessage();
        adapter.addMessage(bubble);
        adapter.updateStreamingMessage(bubble.getStreamingId(), "Live text");

        Context context = ApplicationProvider.getApplicationContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder holder =
                adapter.onCreateViewHolder(recyclerView, ChatMessage.TYPE_ASSISTANT);
        TextView messageText = holder.itemView.findViewById(R.id.messageText);
        messageText.setText("stale");

        adapter.onBindViewHolder(holder, 0,
                Collections.<Object>singletonList(ChatAdapter.PAYLOAD_STREAMING_TEXT));

        assertEquals("Live text", messageText.getText().toString());
        assertEquals(0, adapter.fullBindCount);
        assertEquals(1, adapter.streamingBindCount);
    }

    private static final class CountingBindAdapter extends ChatAdapter {
        private int fullBindCount;
        private int streamingBindCount;

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            fullBindCount++;
            super.onBindViewHolder(holder, position);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position,
                                     @NonNull List<Object> payloads) {
            if (payloads.contains(PAYLOAD_STREAMING_TEXT)) {
                streamingBindCount++;
            }
            super.onBindViewHolder(holder, position, payloads);
        }
    }
}
