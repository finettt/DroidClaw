package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;

public class ChatModelTest {

    @Test
    public void chatMessage_constructor_setsContentTypeAndTimestamp() {
        long beforeCreation = System.currentTimeMillis();

        ChatMessage message = new ChatMessage("Hello", ChatMessage.TYPE_USER);

        long afterCreation = System.currentTimeMillis();

        assertEquals("Hello", message.getContent());
        assertEquals(ChatMessage.TYPE_USER, message.getType());
        assertTrue(message.getTimestamp() >= beforeCreation);
        assertTrue(message.getTimestamp() <= afterCreation);
    }

    @Test
    public void chatMessage_setters_updateAllProperties() {
        ChatMessage message = new ChatMessage("Initial", ChatMessage.TYPE_USER);

        message.setContent("Updated");
        message.setType(ChatMessage.TYPE_ASSISTANT);
        message.setTimestamp(123456789L);

        assertEquals("Updated", message.getContent());
        assertEquals(ChatMessage.TYPE_ASSISTANT, message.getType());
        assertEquals(123456789L, message.getTimestamp());
    }

    @Test
    public void chatMessage_isUser_returnsTrueOnlyForUserType() {
        ChatMessage message = new ChatMessage("Hello", ChatMessage.TYPE_USER);

        assertTrue(message.isUser());
        assertFalse(message.isAssistant());
        assertFalse(message.isToolCall());
        assertFalse(message.isToolResult());

        message.setType(ChatMessage.TYPE_ASSISTANT);

        assertFalse(message.isUser());
        assertTrue(message.isAssistant());
        assertFalse(message.isToolCall());
        assertFalse(message.isToolResult());
    }

    // ========== NEW TESTS FOR TOOL MESSAGE TYPES ==========

    @Test
    public void chatMessage_typeConstants_haveCorrectValues() {
        assertEquals(0, ChatMessage.TYPE_USER);
        assertEquals(1, ChatMessage.TYPE_ASSISTANT);
        assertEquals(2, ChatMessage.TYPE_TOOL_CALL);
        assertEquals(3, ChatMessage.TYPE_TOOL_RESULT);
    }

    @Test
    public void chatMessage_isToolCall_returnsTrueOnlyForToolCallType() {
        ChatMessage message = new ChatMessage("", ChatMessage.TYPE_TOOL_CALL);

        assertFalse(message.isUser());
        assertFalse(message.isAssistant());
        assertTrue(message.isToolCall());
        assertFalse(message.isToolResult());
    }

    @Test
    public void chatMessage_isToolResult_returnsTrueOnlyForToolResultType() {
        ChatMessage message = new ChatMessage("", ChatMessage.TYPE_TOOL_RESULT);

        assertFalse(message.isUser());
        assertFalse(message.isAssistant());
        assertFalse(message.isToolCall());
        assertTrue(message.isToolResult());
    }

    @Test
    public void createToolCallMessage_createsCorrectMessage() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");
        
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
            "call_123",
            "file_read",
            args
        );
        
        ChatMessage message = ChatMessage.createToolCallMessage(Arrays.asList(toolCall));

        assertTrue(message.isToolCall());
        assertNull("Content should be null for tool call message", message.getContent());
        assertNotNull("Tool calls should not be null", message.getToolCalls());
        assertEquals(1, message.getToolCalls().size());
        assertEquals("call_123", message.getToolCalls().get(0).getId());
        assertEquals("file_read", message.getToolCalls().get(0).getName());
    }

    @Test
    public void createToolCallMessage_withMultipleToolCalls() {
        JsonObject args1 = new JsonObject();
        args1.addProperty("path", "file1.txt");
        
        JsonObject args2 = new JsonObject();
        args2.addProperty("path", ".");
        
        List<LlmApiService.ToolCall> toolCalls = Arrays.asList(
            new LlmApiService.ToolCall("call_1", "file_read", args1),
            new LlmApiService.ToolCall("call_2", "file_list", args2)
        );
        
        ChatMessage message = ChatMessage.createToolCallMessage(toolCalls);

        assertTrue(message.isToolCall());
        assertEquals(2, message.getToolCalls().size());
        assertEquals("file_read", message.getToolCalls().get(0).getName());
        assertEquals("file_list", message.getToolCalls().get(1).getName());
    }

    @Test
    public void createToolResultMessage_createsCorrectMessage() {
        ChatMessage message = ChatMessage.createToolResultMessage(
            "call_456",
            "file_read",
            "File content here"
        );

        assertTrue(message.isToolResult());
        assertEquals("File content here", message.getContent());
        assertEquals("call_456", message.getToolCallId());
        assertEquals("file_read", message.getToolName());
    }

    @Test
    public void chatMessage_toolCallGettersSetters() {
        ChatMessage message = new ChatMessage("", ChatMessage.TYPE_TOOL_CALL);
        
        JsonObject args = new JsonObject();
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall("id", "name", args);
        List<LlmApiService.ToolCall> toolCalls = Collections.singletonList(toolCall);
        
        message.setToolCalls(toolCalls);
        
        assertEquals(toolCalls, message.getToolCalls());
    }

    @Test
    public void chatMessage_toolResultGettersSetters() {
        ChatMessage message = new ChatMessage("result", ChatMessage.TYPE_TOOL_RESULT);
        
        message.setToolCallId("call_789");
        message.setToolName("shell");
        
        assertEquals("call_789", message.getToolCallId());
        assertEquals("shell", message.getToolName());
    }

    // ========== TESTS FOR toApiMessage() ==========

    @Test
    public void toApiMessage_userMessage_correctFormat() {
        ChatMessage message = new ChatMessage("Hello, assistant", ChatMessage.TYPE_USER);

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("user", apiMessage.get("role").getAsString());
        assertEquals("Hello, assistant", apiMessage.get("content").getAsString());
    }

    @Test
    public void toApiMessage_assistantMessage_correctFormat() {
        ChatMessage message = new ChatMessage("Hello, user", ChatMessage.TYPE_ASSISTANT);

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("assistant", apiMessage.get("role").getAsString());
        assertEquals("Hello, user", apiMessage.get("content").getAsString());
    }

    @Test
    public void toApiMessage_assistantMessage_withNullContent() {
        ChatMessage message = new ChatMessage(null, ChatMessage.TYPE_ASSISTANT);

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("assistant", apiMessage.get("role").getAsString());
        assertFalse("Should not have content field when null", apiMessage.has("content"));
    }

    @Test
    public void toApiMessage_toolCallMessage_correctFormat() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");
        
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
            "call_abc",
            "file_read",
            args
        );
        
        ChatMessage message = ChatMessage.createToolCallMessage(Arrays.asList(toolCall));

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("assistant", apiMessage.get("role").getAsString());
        assertTrue("Content should be null", apiMessage.get("content").isJsonNull());
        assertTrue("Should have tool_calls", apiMessage.has("tool_calls"));
        
        JsonArray toolCallsArray = apiMessage.getAsJsonArray("tool_calls");
        assertEquals(1, toolCallsArray.size());
        
        JsonObject toolCallObj = toolCallsArray.get(0).getAsJsonObject();
        assertEquals("call_abc", toolCallObj.get("id").getAsString());
        assertEquals("function", toolCallObj.get("type").getAsString());
        
        JsonObject function = toolCallObj.getAsJsonObject("function");
        assertEquals("file_read", function.get("name").getAsString());
        assertTrue(function.get("arguments").getAsString().contains("path"));
    }

    @Test
    public void toApiMessage_toolCallMessage_multipleToolCalls() {
        JsonObject args1 = new JsonObject();
        args1.addProperty("path", "file1.txt");
        
        JsonObject args2 = new JsonObject();
        args2.addProperty("command", "ls -la");
        
        List<LlmApiService.ToolCall> toolCalls = Arrays.asList(
            new LlmApiService.ToolCall("call_1", "file_read", args1),
            new LlmApiService.ToolCall("call_2", "shell", args2)
        );
        
        ChatMessage message = ChatMessage.createToolCallMessage(toolCalls);

        JsonObject apiMessage = message.toApiMessage();

        JsonArray toolCallsArray = apiMessage.getAsJsonArray("tool_calls");
        assertEquals(2, toolCallsArray.size());
        
        assertEquals("file_read",
            toolCallsArray.get(0).getAsJsonObject()
                .getAsJsonObject("function").get("name").getAsString());
        assertEquals("shell",
            toolCallsArray.get(1).getAsJsonObject()
                .getAsJsonObject("function").get("name").getAsString());
    }

    @Test
    public void toApiMessage_toolCallMessage_emptyToolCalls() {
        ChatMessage message = new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL);
        message.setToolCalls(Collections.emptyList());

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("assistant", apiMessage.get("role").getAsString());
        assertTrue(apiMessage.has("tool_calls"));
        assertEquals(0, apiMessage.getAsJsonArray("tool_calls").size());
    }

    @Test
    public void toApiMessage_toolResultMessage_correctFormat() {
        ChatMessage message = ChatMessage.createToolResultMessage(
            "call_xyz",
            "file_read",
            "File content"
        );

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("tool", apiMessage.get("role").getAsString());
        assertEquals("call_xyz", apiMessage.get("tool_call_id").getAsString());
        assertEquals("File content", apiMessage.get("content").getAsString());
    }

    @Test
    public void toApiMessage_toolResultMessage_withNullContent() {
        ChatMessage message = ChatMessage.createToolResultMessage(
            "call_123",
            "shell",
            null
        );

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("tool", apiMessage.get("role").getAsString());
        assertEquals("call_123", apiMessage.get("tool_call_id").getAsString());
        assertEquals("", apiMessage.get("content").getAsString());
    }

    @Test
    public void toApiMessage_toolResultMessage_withEmptyContent() {
        ChatMessage message = ChatMessage.createToolResultMessage(
            "call_empty",
            "python",
            ""
        );

        JsonObject apiMessage = message.toApiMessage();

        assertEquals("", apiMessage.get("content").getAsString());
    }

    @Test
    public void chatSession_constructor_setsAllProperties() {
        ChatSession session = new ChatSession("session-1", "My Chat", 987654321L);

        assertEquals("session-1", session.getId());
        assertEquals("My Chat", session.getTitle());
        assertEquals(987654321L, session.getUpdatedAt());
    }

    @Test
    public void chatSession_setters_updateAllProperties() {
        ChatSession session = new ChatSession("session-1", "Original", 100L);

        session.setId("session-2");
        session.setTitle("Renamed");
        session.setUpdatedAt(200L);

        assertEquals("session-2", session.getId());
        assertEquals("Renamed", session.getTitle());
        assertEquals(200L, session.getUpdatedAt());
    }
}