package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatSession;

@RunWith(AndroidJUnit4.class)
public class ChatSessionAdapterTest {

    @Test
    public void submitList_updatesItemCount_andBindsTitle() {
        RecordingListener listener = new RecordingListener();
        ChatSessionAdapter adapter = new ChatSessionAdapter(listener);
        ChatSession first = new ChatSession("1", "First session", 100L);
        ChatSession second = new ChatSession("2", "Second session", 200L);

        adapter.submitList(java.util.Arrays.asList(first, second));

        assertEquals(2, adapter.getItemCount());

        FrameLayout parent = new FrameLayout(
                new ContextThemeWrapper(
                        ApplicationProvider.getApplicationContext(),
                        R.style.Theme_DroidClaw
                )
        );
        ChatSessionAdapter.ChatSessionViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 1);

        TextView titleView = holder.itemView.findViewById(R.id.chatSessionTitle);
        assertEquals("Second session", titleView.getText().toString());
    }

    @Test
    public void bind_setsClickAndLongClickListeners() {
        RecordingListener listener = new RecordingListener();
        ChatSessionAdapter adapter = new ChatSessionAdapter(listener);
        ChatSession session = new ChatSession("1", "Clickable session", 100L);

        adapter.submitList(java.util.Collections.singletonList(session));

        FrameLayout parent = new FrameLayout(
                new ContextThemeWrapper(
                        ApplicationProvider.getApplicationContext(),
                        R.style.Theme_DroidClaw
                )
        );
        ChatSessionAdapter.ChatSessionViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);

        holder.itemView.performClick();
        assertEquals(session, listener.clickedSession);

        boolean longClickHandled = holder.itemView.performLongClick();
        assertTrue(longClickHandled);
        assertEquals(session, listener.longClickedSession);
    }

    private static final class RecordingListener implements ChatSessionAdapter.OnChatSessionClickListener {
        private ChatSession clickedSession;
        private ChatSession longClickedSession;

        @Override
        public void onChatSessionClick(ChatSession session) {
            clickedSession = session;
        }

        @Override
        public void onChatSessionLongClick(ChatSession session) {
            longClickedSession = session;
        }
    }
}