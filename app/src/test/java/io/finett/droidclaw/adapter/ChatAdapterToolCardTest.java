package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.text.style.BackgroundColorSpan;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

import io.finett.droidclaw.R;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;

/**
 * Binding states for the live tool-card machinery: RUNNING vs done vs failed
 * tool-call cards, the inline system view type, and search highlighting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class)
public class ChatAdapterToolCardTest {

    private static LlmApiService.ToolCall call(String id, String name, String argValue) {
        JsonObject args = new JsonObject();
        args.addProperty("path", argValue);
        return new LlmApiService.ToolCall(id, name, args);
    }

    private static ChatMessage toolCallCard(String id, String name) {
        return ChatMessage.createToolCallMessage(Collections.singletonList(call(id, name, "a.txt")));
    }

    private static ChatAdapter.ToolCallMessageViewHolder toolCallHolder() {
        Context appContext = RuntimeEnvironment.getApplication();
        ImageView icon = new ImageView(appContext);
        icon.setId(R.id.toolCallIcon);
        ProgressBar spinner = new ProgressBar(appContext);
        spinner.setId(R.id.toolCallSpinner);
        TextView title = new TextView(appContext);
        title.setId(R.id.toolCallText);
        TextView args = new TextView(appContext);
        args.setId(R.id.toolCallArgs);
        android.widget.LinearLayout row = new android.widget.LinearLayout(appContext);
        row.addView(icon);
        row.addView(spinner);
        row.addView(title);
        row.addView(args);
        return new ChatAdapter.ToolCallMessageViewHolder(row);
    }

    private static ChatAdapter.SystemMessageViewHolder systemHolder() {
        return new ChatAdapter.SystemMessageViewHolder(
                new TextView(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void viewTypes_systemMatchesMessageTypeTypingDoesNotCollide() {
        ChatAdapter adapter = new ChatAdapter();
        adapter.addMessage(ChatMessage.createSystemMessage("notice"));
        assertEquals("system view type must render as the message type",
                ChatMessage.TYPE_SYSTEM, adapter.getItemViewType(0));
        adapter.addMessage(ChatAdapter.TYPING_PLACEHOLDER);
        assertEquals(ChatAdapter.VIEW_TYPE_TYPING, adapter.getItemViewType(1));
        assertTrue("the typing view type must not match any message type",
                ChatAdapter.VIEW_TYPE_TYPING > ChatMessage.TYPE_CONTEXT_CARD);
    }

    @Test
    public void toolCard_runningState_spinnerVisibleIconHidden() {
        ChatMessage card = toolCallCard("call_1", "read_file");
        card.setRunning(true);

        ChatAdapter.ToolCallMessageViewHolder holder = toolCallHolder();
        holder.bind(card, new ChatAdapter());

        ImageView icon = (ImageView) holder.itemView.findViewById(R.id.toolCallIcon);
        ProgressBar spinner = (ProgressBar) holder.itemView.findViewById(R.id.toolCallSpinner);
        TextView title = (TextView) holder.itemView.findViewById(R.id.toolCallText);

        assertEquals(View.VISIBLE, spinner.getVisibility());
        assertTrue("the spinner must be indeterminate while running", spinner.isIndeterminate());
        assertEquals(View.GONE, icon.getVisibility());
        assertTrue("running label shows the in-progress dots",
                title.getText().toString().endsWith("..."));
    }

    @Test
    public void toolCard_doneState_iconVisibleSpinnerGone() {
        ChatMessage card = toolCallCard("call_1", "read_file");

        ChatAdapter.ToolCallMessageViewHolder holder = toolCallHolder();
        holder.bind(card, new ChatAdapter());

        ImageView icon = (ImageView) holder.itemView.findViewById(R.id.toolCallIcon);
        ProgressBar spinner = (ProgressBar) holder.itemView.findViewById(R.id.toolCallSpinner);
        TextView title = (TextView) holder.itemView.findViewById(R.id.toolCallText);
        TextView args = (TextView) holder.itemView.findViewById(R.id.toolCallArgs);

        assertEquals(View.GONE, spinner.getVisibility());
        assertEquals(View.VISIBLE, icon.getVisibility());
        assertEquals("Read File", title.getText().toString());
        assertEquals("{\"path\":\"a.txt\"}", args.getText().toString());
    }

    @Test
    public void toolCard_failedState_errorIconAndLabel() {
        ChatMessage card = toolCallCard("call_1", "execute_shell");
        card.setContent("interrupted");

        ChatAdapter.ToolCallMessageViewHolder holder = toolCallHolder();
        holder.bind(card, new ChatAdapter());

        ImageView icon = (ImageView) holder.itemView.findViewById(R.id.toolCallIcon);
        ProgressBar spinner = (ProgressBar) holder.itemView.findViewById(R.id.toolCallSpinner);
        TextView title = (TextView) holder.itemView.findViewById(R.id.toolCallText);

        // No drawable resources resolve for a bare test view; assert the icon slot is
        // used (not the spinner) and the failed label is rendered.
        assertEquals(View.VISIBLE, icon.getVisibility());
        assertEquals("Execute Shell — interrupted", title.getText().toString());
        assertEquals(View.GONE, spinner.getVisibility());
    }

    @Test
    public void toolCard_emptyToolCalls_bindsBlankWithoutCrash() {
        ChatMessage card = new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL);

        ChatAdapter.ToolCallMessageViewHolder holder = toolCallHolder();
        holder.bind(card, new ChatAdapter());

        ImageView icon = (ImageView) holder.itemView.findViewById(R.id.toolCallIcon);
        ProgressBar spinner = (ProgressBar) holder.itemView.findViewById(R.id.toolCallSpinner);
        TextView title = (TextView) holder.itemView.findViewById(R.id.toolCallText);
        TextView args = (TextView) holder.itemView.findViewById(R.id.toolCallArgs);

        assertEquals("", title.getText().toString());
        assertEquals("", args.getText().toString());
        assertEquals(View.GONE, spinner.getVisibility());
        assertEquals(View.GONE, icon.getVisibility());
    }

    @Test
    public void systemLine_bindsContentAndSearchHighlight() {
        ChatMessage system = ChatMessage.createSystemMessage("Context limit approaching — summarizing...");
        ChatAdapter.SystemMessageViewHolder holder = systemHolder();
        TextView textView = (TextView) holder.itemView;

        ChatAdapter adapter = new ChatAdapter();
        holder.bind(system, adapter);
        assertEquals("Context limit approaching — summarizing...", textView.getText().toString());

        adapter.setSearchQuery("summarizing");
        holder.bind(system, adapter);
        assertTrue("highlight keeps the raw characters",
                textView.getText().toString().contains("summarizing"));
        android.text.Spanned spanned = (android.text.Spanned) textView.getText();
        assertTrue("the highlight span covers the match",
                spanned.getSpans(0, spanned.length(),
                        BackgroundColorSpan.class).length > 0);
    }

    @Test
    public void typingPlaceholder_mapsToTypingViewType() {
        ChatAdapter adapter = new ChatAdapter();
        adapter.addMessage(ChatAdapter.TYPING_PLACEHOLDER);
        assertEquals(ChatAdapter.VIEW_TYPE_TYPING, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_ASSISTANT, new ChatMessage("x", ChatMessage.TYPE_ASSISTANT).getType());
    }
}
