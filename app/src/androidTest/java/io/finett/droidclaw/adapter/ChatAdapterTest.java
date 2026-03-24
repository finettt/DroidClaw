package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatMessage;

@RunWith(AndroidJUnit4.class)
public class ChatAdapterTest {

    private ChatAdapter adapter;

    @Before
    public void setUp() {
        adapter = new ChatAdapter();
    }

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void addMessage_userMessage_increasesCount() {
        ChatMessage userMessage = new ChatMessage("Hello", ChatMessage.TYPE_USER);
        adapter.addMessage(userMessage);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void addMessage_assistantMessage_increasesCount() {
        ChatMessage assistantMessage = new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT);
        adapter.addMessage(assistantMessage);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void addMultipleMessages_correctCount() {
        adapter.addMessage(new ChatMessage("Message 1", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Response 1", ChatMessage.TYPE_ASSISTANT));
        adapter.addMessage(new ChatMessage("Message 2", ChatMessage.TYPE_USER));

        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_userMessage_returnsUserType() {
        adapter.addMessage(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_USER, viewType);
    }

    @Test
    public void getItemViewType_assistantMessage_returnsAssistantType() {
        adapter.addMessage(new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_ASSISTANT, viewType);
    }

    @Test
    public void getItemViewType_mixedMessages_returnsCorrectTypes() {
        adapter.addMessage(new ChatMessage("User msg", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Assistant msg", ChatMessage.TYPE_ASSISTANT));

        assertEquals(ChatMessage.TYPE_USER, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_ASSISTANT, adapter.getItemViewType(1));
    }

    @Test
    public void updateLastMessage_withMessages_updatesContent() {
        adapter.addMessage(new ChatMessage("Original", ChatMessage.TYPE_ASSISTANT));
        
        adapter.updateLastMessage("Updated content");
        
        List<ChatMessage> messages = adapter.getMessages();
        assertEquals("Updated content", messages.get(0).getContent());
    }

    @Test
    public void updateLastMessage_emptyAdapter_doesNotCrash() {
        // Should not crash when adapter is empty
        adapter.updateLastMessage("Some content");
        
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void updateLastMessage_multipleMessages_updatesOnlyLast() {
        adapter.addMessage(new ChatMessage("First", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Second", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Third", ChatMessage.TYPE_ASSISTANT));
        
        adapter.updateLastMessage("Updated third");
        
        List<ChatMessage> messages = adapter.getMessages();
        assertEquals("First", messages.get(0).getContent());
        assertEquals("Second", messages.get(1).getContent());
        assertEquals("Updated third", messages.get(2).getContent());
    }

    @Test
    public void clearMessages_removesAllMessages() {
        adapter.addMessage(new ChatMessage("Message 1", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Message 2", ChatMessage.TYPE_ASSISTANT));
        adapter.addMessage(new ChatMessage("Message 3", ChatMessage.TYPE_USER));

        adapter.clearMessages();

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void clearMessages_emptyAdapter_doesNotCrash() {
        adapter.clearMessages();

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setMessages_replacesExistingMessages() {
        adapter.addMessage(new ChatMessage("Old message", ChatMessage.TYPE_USER));

        List<ChatMessage> newMessages = Arrays.asList(
                new ChatMessage("New 1", ChatMessage.TYPE_USER),
                new ChatMessage("New 2", ChatMessage.TYPE_ASSISTANT)
        );

        adapter.setMessages(newMessages);

        assertEquals(2, adapter.getItemCount());
        List<ChatMessage> messages = adapter.getMessages();
        assertEquals("New 1", messages.get(0).getContent());
        assertEquals("New 2", messages.get(1).getContent());
    }

    @Test
    public void setMessages_withEmptyList_clearsAdapter() {
        adapter.addMessage(new ChatMessage("Message", ChatMessage.TYPE_USER));

        adapter.setMessages(new ArrayList<>());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setMessages_preservesMessageOrder() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatMessage("First", ChatMessage.TYPE_USER),
                new ChatMessage("Second", ChatMessage.TYPE_ASSISTANT),
                new ChatMessage("Third", ChatMessage.TYPE_USER),
                new ChatMessage("Fourth", ChatMessage.TYPE_ASSISTANT)
        );

        adapter.setMessages(messages);

        List<ChatMessage> retrievedMessages = adapter.getMessages();
        assertEquals("First", retrievedMessages.get(0).getContent());
        assertEquals("Second", retrievedMessages.get(1).getContent());
        assertEquals("Third", retrievedMessages.get(2).getContent());
        assertEquals("Fourth", retrievedMessages.get(3).getContent());
    }

    @Test
    public void getMessages_returnsNewList() {
        adapter.addMessage(new ChatMessage("Message", ChatMessage.TYPE_USER));

        List<ChatMessage> messages1 = adapter.getMessages();
        List<ChatMessage> messages2 = adapter.getMessages();

        // Should return new list instances
        assertNotNull(messages1);
        assertNotNull(messages2);
        assertEquals(messages1.size(), messages2.size());
    }

    @Test
    public void getMessages_modifyingReturnedList_doesNotAffectAdapter() {
        adapter.addMessage(new ChatMessage("Original", ChatMessage.TYPE_USER));

        List<ChatMessage> messages = adapter.getMessages();
        messages.add(new ChatMessage("Should not affect adapter", ChatMessage.TYPE_USER));

        // Adapter should still have only 1 message
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void onCreateViewHolder_userMessage_createsCorrectViewHolder() {
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_USER
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onCreateViewHolder_assistantMessage_createsCorrectViewHolder() {
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_ASSISTANT
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_userMessage_bindsCorrectly() {
        adapter.addMessage(new ChatMessage("Test user message", ChatMessage.TYPE_USER));
        
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_USER
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView messageText = viewHolder.itemView.findViewById(R.id.messageText);
        assertNotNull(messageText);
        assertEquals("Test user message", messageText.getText().toString());
    }

    @Test
    public void onBindViewHolder_assistantMessage_bindsCorrectly() {
        adapter.addMessage(new ChatMessage("Test assistant message", ChatMessage.TYPE_ASSISTANT));
        
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_ASSISTANT
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView messageText = viewHolder.itemView.findViewById(R.id.messageText);
        assertNotNull(messageText);
        assertEquals("Test assistant message", messageText.getText().toString());
    }

    @Test
    public void addMessage_withLongText_handlesCorrectly() {
        String longMessage = "This is a very long message that contains many words and " +
                "should test whether the adapter can handle long text content properly " +
                "without any issues or crashes in the message display system.";
        
        adapter.addMessage(new ChatMessage(longMessage, ChatMessage.TYPE_USER));

        assertEquals(1, adapter.getItemCount());
        assertEquals(longMessage, adapter.getMessages().get(0).getContent());
    }

    @Test
    public void addMessage_withEmptyString_handlesCorrectly() {
        adapter.addMessage(new ChatMessage("", ChatMessage.TYPE_USER));

        assertEquals(1, adapter.getItemCount());
        assertEquals("", adapter.getMessages().get(0).getContent());
    }

    @Test
    public void addMessage_withSpecialCharacters_handlesCorrectly() {
        String specialMessage = "Hello! @#$%^&*() <html> \"quotes\" 'apostrophe' \n newline";
        
        adapter.addMessage(new ChatMessage(specialMessage, ChatMessage.TYPE_USER));

        assertEquals(1, adapter.getItemCount());
        assertEquals(specialMessage, adapter.getMessages().get(0).getContent());
    }

    @Test
    public void sequentialOperations_maintainCorrectState() {
        // Add messages
        adapter.addMessage(new ChatMessage("First", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Second", ChatMessage.TYPE_ASSISTANT));
        assertEquals(2, adapter.getItemCount());

        // Update last message
        adapter.updateLastMessage("Updated second");
        assertEquals("Updated second", adapter.getMessages().get(1).getContent());

        // Add more messages
        adapter.addMessage(new ChatMessage("Third", ChatMessage.TYPE_USER));
        assertEquals(3, adapter.getItemCount());

        // Clear all
        adapter.clearMessages();
        assertEquals(0, adapter.getItemCount());

        // Set new messages
        adapter.setMessages(Arrays.asList(
                new ChatMessage("New message", ChatMessage.TYPE_USER)
        ));
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void addMessage_toolCallMessage_increasesCount() {
        // Create a tool call message
        ChatMessage toolCallMessage = new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL);
        adapter.addMessage(toolCallMessage);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void addMessage_toolResultMessage_increasesCount() {
        // Create a tool result message
        ChatMessage toolResultMessage = new ChatMessage("Result content", ChatMessage.TYPE_TOOL_RESULT);
        adapter.addMessage(toolResultMessage);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_toolCallMessage_returnsToolCallType() {
        adapter.addMessage(new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_TOOL_CALL, viewType);
    }

    @Test
    public void getItemViewType_toolResultMessage_returnsToolResultType() {
        adapter.addMessage(new ChatMessage("Result content", ChatMessage.TYPE_TOOL_RESULT));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_TOOL_RESULT, viewType);
    }

    @Test
    public void getItemViewType_mixedMessages_withToolTypes_returnsCorrectTypes() {
        adapter.addMessage(new ChatMessage("User msg", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL));
        adapter.addMessage(new ChatMessage("Result", ChatMessage.TYPE_TOOL_RESULT));

        assertEquals(ChatMessage.TYPE_USER, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_TOOL_CALL, adapter.getItemViewType(1));
        assertEquals(ChatMessage.TYPE_TOOL_RESULT, adapter.getItemViewType(2));
    }

    @Test
    public void onCreateViewHolder_toolCallMessage_createsCorrectViewHolder() {
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));

        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_CALL
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onCreateViewHolder_toolResultMessage_createsCorrectViewHolder() {
        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));

        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_RESULT
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_toolCallMessage_bindsCorrectly() {
        ChatMessage toolCallMessage = ChatMessage.createToolCallMessage(null);
        adapter.addMessage(toolCallMessage);

        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_CALL
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify the view holder has the correct views
        assertNotNull(viewHolder.itemView.findViewById(R.id.toolCallIcon));
        assertNotNull(viewHolder.itemView.findViewById(R.id.toolCallText));
        assertNotNull(viewHolder.itemView.findViewById(R.id.toolCallArgs));
    }

    @Test
    public void onBindViewHolder_toolResultMessage_bindsCorrectly() {
        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage("call-123", "test_tool", "Result content");
        adapter.addMessage(toolResultMessage);

        RecyclerView recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_RESULT
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify the view holder has the correct views
        assertNotNull(viewHolder.itemView.findViewById(R.id.toolResultLabel));
        assertNotNull(viewHolder.itemView.findViewById(R.id.toolResultContent));
    }

    @Test
    public void addMessage_withToolCallAndToolResult_maintainsCorrectOrder() {
        adapter.addMessage(new ChatMessage("User request", ChatMessage.TYPE_USER));
        adapter.addMessage(ChatMessage.createToolCallMessage(null));
        adapter.addMessage(ChatMessage.createToolResultMessage("call-123", "test_tool", "Tool result"));

        assertEquals(3, adapter.getItemCount());
        assertEquals("User request", adapter.getMessages().get(0).getContent());
        assertEquals(ChatMessage.TYPE_TOOL_CALL, adapter.getMessages().get(1).getType());
        assertEquals("Tool result", adapter.getMessages().get(2).getContent());
    }

    @Test
    public void setMessages_withToolMessages_replacesExistingMessages() {
        adapter.addMessage(new ChatMessage("Old message", ChatMessage.TYPE_USER));

        List<ChatMessage> newMessages = Arrays.asList(
                new ChatMessage("User request", ChatMessage.TYPE_USER),
                ChatMessage.createToolCallMessage(null),
                ChatMessage.createToolResultMessage("call-123", "test_tool", "Result")
        );

        adapter.setMessages(newMessages);

        assertEquals(3, adapter.getItemCount());
        List<ChatMessage> messages = adapter.getMessages();
        assertEquals("User request", messages.get(0).getContent());
        assertEquals(ChatMessage.TYPE_TOOL_CALL, messages.get(1).getType());
        assertEquals("Result", messages.get(2).getContent());
    }
}