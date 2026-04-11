package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.util.TestThemeHelper;

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
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_USER
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onCreateViewHolder_assistantMessage_createsCorrectViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        
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
        
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
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
        
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
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
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_CALL
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onCreateViewHolder_toolResultMessage_createsCorrectViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

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

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
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

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
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

    // --- TYPE_SYSTEM tests for identity system support ---

    @Test
    public void addMessage_systemMessage_increasesCount() {
        ChatMessage systemMessage = new ChatMessage("System content", ChatMessage.TYPE_SYSTEM);
        adapter.addMessage(systemMessage);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_systemMessage_returnsSystemType() {
        adapter.addMessage(new ChatMessage("System content", ChatMessage.TYPE_SYSTEM));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_SYSTEM, viewType);
    }

    @Test
    public void getItemViewType_mixedMessages_withSystemType_returnsCorrectTypes() {
        adapter.addMessage(new ChatMessage("System msg", ChatMessage.TYPE_SYSTEM));
        adapter.addMessage(new ChatMessage("User msg", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Assistant msg", ChatMessage.TYPE_ASSISTANT));

        assertEquals(ChatMessage.TYPE_SYSTEM, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_USER, adapter.getItemViewType(1));
        assertEquals(ChatMessage.TYPE_ASSISTANT, adapter.getItemViewType(2));
    }

    @Test
    public void setMessages_withSystemMessages_preservesOrder() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatMessage("System context", ChatMessage.TYPE_SYSTEM),
                new ChatMessage("User query", ChatMessage.TYPE_USER),
                new ChatMessage("Assistant response", ChatMessage.TYPE_ASSISTANT)
        );

        adapter.setMessages(messages);

        assertEquals(3, adapter.getItemCount());
        List<ChatMessage> retrieved = adapter.getMessages();
        assertEquals(ChatMessage.TYPE_SYSTEM, retrieved.get(0).getType());
        assertEquals("System context", retrieved.get(0).getContent());
        assertEquals(ChatMessage.TYPE_USER, retrieved.get(1).getType());
        assertEquals(ChatMessage.TYPE_ASSISTANT, retrieved.get(2).getType());
    }

    @Test
    public void systemMessage_isSystem_returnsTrue() {
        ChatMessage systemMessage = new ChatMessage("System content", ChatMessage.TYPE_SYSTEM);
        
        assertTrue("isSystem() should return true for TYPE_SYSTEM", systemMessage.isSystem());
        assertFalse("isUser() should return false for TYPE_SYSTEM", systemMessage.isUser());
        assertFalse("isAssistant() should return false for TYPE_SYSTEM", systemMessage.isAssistant());
    }

    @Test
    public void systemMessage_toApiMessage_hasCorrectFormat() {
        ChatMessage systemMessage = new ChatMessage("System instructions", ChatMessage.TYPE_SYSTEM);
        
        com.google.gson.JsonObject apiMessage = systemMessage.toApiMessage();
        
        assertEquals("system", apiMessage.get("role").getAsString());
        assertEquals("System instructions", apiMessage.get("content").getAsString());
    }

    @Test
    public void allMessageTypes_toApiMessage_hasCorrectRole() {
        ChatMessage systemMessage = new ChatMessage("System", ChatMessage.TYPE_SYSTEM);
        ChatMessage userMessage = new ChatMessage("User", ChatMessage.TYPE_USER);
        ChatMessage assistantMessage = new ChatMessage("Assistant", ChatMessage.TYPE_ASSISTANT);

        assertEquals("system", systemMessage.toApiMessage().get("role").getAsString());
        assertEquals("user", userMessage.toApiMessage().get("role").getAsString());
        assertEquals("assistant", assistantMessage.toApiMessage().get("role").getAsString());
    }

    // ==================== CONTEXT CARD TESTS ====================

    @Test
    public void addMessage_contextCardMessage_increasesCount() {
        ChatMessage contextCard = new ChatMessage("Context content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        adapter.addMessage(contextCard);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_contextCardMessage_returnsContextCardType() {
        adapter.addMessage(new ChatMessage("Context content", ChatMessage.TYPE_CONTEXT_CARD));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_CONTEXT_CARD, viewType);
    }

    @Test
    public void getItemViewType_mixedMessages_withContextCard_returnsCorrectTypes() {
        adapter.addMessage(new ChatMessage("User msg", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Context card", ChatMessage.TYPE_CONTEXT_CARD));
        adapter.addMessage(new ChatMessage("Assistant msg", ChatMessage.TYPE_ASSISTANT));

        assertEquals(ChatMessage.TYPE_USER, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_CONTEXT_CARD, adapter.getItemViewType(1));
        assertEquals(ChatMessage.TYPE_ASSISTANT, adapter.getItemViewType(2));
    }

    @Test
    public void onCreateViewHolder_contextCardMessage_createsCorrectViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_contextCardMessage_bindsCorrectly() {
        ChatMessage contextCard = new ChatMessage("Heartbeat result content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify the view holder has the correct views
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardHeader));
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardIcon));
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardTitle));
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardTimestamp));
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardToggle));
        assertNotNull(viewHolder.itemView.findViewById(R.id.contextCardContent));
    }

    @Test
    public void onBindViewHolder_contextCardMessage_displaysCorrectContent() {
        String taskContent = "# Task Result\n\nDetails here";
        ChatMessage contextCard = new ChatMessage(taskContent, ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView contentText = viewHolder.itemView.findViewById(R.id.contextCardContent);
        assertNotNull("Content text view should exist", contentText);
        // Content may be rendered as markdown, so check it contains key text
        assertTrue("Content should contain task text",
                contentText.getText().toString().contains("Details here") ||
                contentText.getText().toString().contains("Task Result"));
    }

    @Test
    public void onBindViewHolder_contextCardMessage_withHeartbeatType_showsCorrectTitle() {
        ChatMessage contextCard = new ChatMessage("Heartbeat content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.contextCardTitle);
        assertNotNull("Title text view should exist", titleText);
        assertEquals("Title should be 'Heartbeat Check'", "Heartbeat Check", titleText.getText().toString());
    }

    @Test
    public void onBindViewHolder_contextCardMessage_withCronJobType_showsCorrectTitle() {
        ChatMessage contextCard = new ChatMessage("Cron content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("cron_job");
        contextCard.setTimestamp(2000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.contextCardTitle);
        assertNotNull("Title text view should exist", titleText);
        assertEquals("Title should be 'Scheduled Task'", "Scheduled Task", titleText.getText().toString());
    }

    @Test
    public void onBindViewHolder_contextCardMessage_withManualType_showsCorrectTitle() {
        ChatMessage contextCard = new ChatMessage("Manual content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("manual");
        contextCard.setTimestamp(3000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.contextCardTitle);
        assertNotNull("Title text view should exist", titleText);
        assertEquals("Title should be 'Manual Task'", "Manual Task", titleText.getText().toString());
    }

    @Test
    public void onBindViewHolder_contextCardMessage_displaysFormattedTimestamp() {
        ChatMessage contextCard = new ChatMessage("Timestamp content", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(System.currentTimeMillis());
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView timestampText = viewHolder.itemView.findViewById(R.id.contextCardTimestamp);
        assertNotNull("Timestamp text view should exist", timestampText);
        assertTrue("Timestamp should be formatted (not empty)",
                timestampText.getText().toString().length() > 0);
    }

    @Test
    public void contextCardMessage_withNullContent_showsPlaceholder() {
        ChatMessage contextCard = new ChatMessage(null, ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);
        adapter.addMessage(contextCard);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_CONTEXT_CARD
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView contentText = viewHolder.itemView.findViewById(R.id.contextCardContent);
        assertNotNull("Content text view should exist", contentText);
        assertEquals("Should show placeholder", "No content available", contentText.getText().toString());
    }

    @Test
    public void setMessages_withContextCardMessages_replacesExistingMessages() {
        adapter.addMessage(new ChatMessage("Old message", ChatMessage.TYPE_USER));

        ChatMessage contextCard = new ChatMessage("Task result", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);

        List<ChatMessage> newMessages = Arrays.asList(
                new ChatMessage("User request", ChatMessage.TYPE_USER),
                contextCard,
                new ChatMessage("Assistant response", ChatMessage.TYPE_ASSISTANT)
        );

        adapter.setMessages(newMessages);

        assertEquals(3, adapter.getItemCount());
        List<ChatMessage> messages = adapter.getMessages();
        assertEquals("User request", messages.get(0).getContent());
        assertEquals(ChatMessage.TYPE_CONTEXT_CARD, messages.get(1).getType());
        assertEquals("Task result", messages.get(1).getContent());
        assertEquals("Assistant response", messages.get(2).getContent());
    }

    @Test
    public void addMessage_multipleContextCards_allDisplayed() {
        ChatMessage hbCard = new ChatMessage("Heartbeat 1", ChatMessage.TYPE_CONTEXT_CARD);
        hbCard.setIsContextCard(true);
        hbCard.setContextType("heartbeat");
        hbCard.setTimestamp(1000L);

        ChatMessage cronCard = new ChatMessage("Cron 1", ChatMessage.TYPE_CONTEXT_CARD);
        cronCard.setIsContextCard(true);
        cronCard.setContextType("cron_job");
        cronCard.setTimestamp(2000L);

        adapter.addMessage(hbCard);
        adapter.addMessage(cronCard);

        assertEquals(2, adapter.getItemCount());
        assertEquals("Heartbeat 1", adapter.getMessages().get(0).getContent());
        assertEquals("Cron 1", adapter.getMessages().get(1).getContent());
    }

    @Test
    public void mixedMessageTypes_allViewTypesCreatedCorrectly() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // Add all message types
        adapter.addMessage(new ChatMessage("System", ChatMessage.TYPE_SYSTEM));
        adapter.addMessage(new ChatMessage("User", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Assistant", ChatMessage.TYPE_ASSISTANT));
        adapter.addMessage(new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL));
        adapter.addMessage(new ChatMessage("Tool result", ChatMessage.TYPE_TOOL_RESULT));
        
        ChatMessage contextCard = new ChatMessage("Context", ChatMessage.TYPE_CONTEXT_CARD);
        contextCard.setIsContextCard(true);
        contextCard.setContextType("heartbeat");
        contextCard.setTimestamp(1000L);
        adapter.addMessage(contextCard);

        assertEquals(6, adapter.getItemCount());

        // Verify each view type
        assertEquals(ChatMessage.TYPE_SYSTEM, adapter.getItemViewType(0));
        assertEquals(ChatMessage.TYPE_USER, adapter.getItemViewType(1));
        assertEquals(ChatMessage.TYPE_ASSISTANT, adapter.getItemViewType(2));
        assertEquals(ChatMessage.TYPE_TOOL_CALL, adapter.getItemViewType(3));
        assertEquals(ChatMessage.TYPE_TOOL_RESULT, adapter.getItemViewType(4));
        assertEquals(ChatMessage.TYPE_CONTEXT_CARD, adapter.getItemViewType(5));

        // Verify all view holders can be created
        for (int i = 0; i < 6; i++) {
            ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                    recyclerView,
                    adapter.getItemViewType(i)
            );
            assertNotNull("ViewHolder for type " + i + " should not be null", viewHolder);
        }
    }

    // ==================== ATTACHMENT TESTS ====================

    @Test
    public void addMessage_attachmentMessage_increasesCount() {
        ChatMessage attachment = ChatMessage.createAttachmentMessage(
                "/path/to/file.txt", "file.txt", "text/plain");
        adapter.addMessage(attachment);

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_attachmentMessage_returnsAttachmentType() {
        adapter.addMessage(ChatMessage.createAttachmentMessage(
                "/path/to/file.txt", "file.txt", "text/plain"));

        int viewType = adapter.getItemViewType(0);

        assertEquals(ChatMessage.TYPE_ATTACHMENT, viewType);
    }

    @Test
    public void onCreateViewHolder_attachmentMessage_createsCorrectViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_ATTACHMENT
        );

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_attachmentMessage_bindsCorrectly() {
        ChatMessage attachment = ChatMessage.createAttachmentMessage(
                "/path/to/report.pdf", "report.pdf", "application/pdf");
        adapter.addMessage(attachment);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_ATTACHMENT
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // AttachmentMessageViewHolder uses Chip as the root view
        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) viewHolder.itemView;
        assertNotNull(chip.getText());
        assertTrue("Chip text should contain filename",
                chip.getText().toString().contains("report.pdf"));
    }

    @Test
    public void addMessage_userMessageWithAttachments_showsAttachments() {
        java.util.List<io.finett.droidclaw.model.FileAttachment> attachments = new java.util.ArrayList<>();
        attachments.add(new io.finett.droidclaw.model.FileAttachment(
                "abc_test.png", "test.png", "/tmp/abc_test.png", "image/png"));

        ChatMessage message = ChatMessage.createUserMessageWithAttachments("Look at this", attachments);
        adapter.addMessage(message);

        assertEquals(1, adapter.getItemCount());
        assertTrue(adapter.getMessages().get(0).hasAttachments());
    }

    @Test
    public void onBindViewHolder_userMessageWithAttachments_bindsAttachments() {
        java.util.List<io.finett.droidclaw.model.FileAttachment> attachments = new java.util.ArrayList<>();
        attachments.add(new io.finett.droidclaw.model.FileAttachment(
                "abc_test.txt", "test.txt", "/tmp/abc_test.txt", "text/plain"));

        ChatMessage message = ChatMessage.createUserMessageWithAttachments("Check this", attachments);
        adapter.addMessage(message);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_USER
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify attachments container exists
        android.widget.LinearLayout attachmentsContainer = viewHolder.itemView.findViewById(R.id.attachmentsContainer);
        assertNotNull(attachmentsContainer);
    }

    @Test
    public void onBindViewHolder_toolResultMessage_hasFilesContainer() {
        ChatMessage toolResult = ChatMessage.createToolResultMessage("call-123", "write_file",
                "File created: `report.txt`");
        adapter.addMessage(toolResult);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                ChatMessage.TYPE_TOOL_RESULT
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify files container exists in tool result
        android.widget.LinearLayout filesContainer = viewHolder.itemView.findViewById(R.id.toolResultFilesContainer);
        assertNotNull(filesContainer);
    }

    @Test
    public void allMessageTypes_includingAttachment_viewHolderCreation() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // Add all message types
        adapter.addMessage(new ChatMessage("System", ChatMessage.TYPE_SYSTEM));
        adapter.addMessage(new ChatMessage("User", ChatMessage.TYPE_USER));
        adapter.addMessage(new ChatMessage("Assistant", ChatMessage.TYPE_ASSISTANT));
        adapter.addMessage(ChatMessage.createToolCallMessage(null));
        adapter.addMessage(ChatMessage.createToolResultMessage("c1", "tool", "result"));
        ChatMessage ctx = new ChatMessage("Context", ChatMessage.TYPE_CONTEXT_CARD);
        ctx.setIsContextCard(true);
        ctx.setContextType("heartbeat");
        ctx.setTimestamp(1000L);
        adapter.addMessage(ctx);
        adapter.addMessage(ChatMessage.createAttachmentMessage("/path/f.txt", "f.txt", "text/plain"));

        assertEquals(7, adapter.getItemCount());

        // Verify all view types
        int[] expectedTypes = {
                ChatMessage.TYPE_SYSTEM,
                ChatMessage.TYPE_USER,
                ChatMessage.TYPE_ASSISTANT,
                ChatMessage.TYPE_TOOL_CALL,
                ChatMessage.TYPE_TOOL_RESULT,
                ChatMessage.TYPE_CONTEXT_CARD,
                ChatMessage.TYPE_ATTACHMENT
        };

        for (int i = 0; i < 7; i++) {
            assertEquals(expectedTypes[i], adapter.getItemViewType(i));

            ChatAdapter.MessageViewHolder viewHolder = adapter.onCreateViewHolder(
                    recyclerView,
                    adapter.getItemViewType(i)
            );
            assertNotNull("ViewHolder for type " + i + " should not be null", viewHolder);
        }
    }
}