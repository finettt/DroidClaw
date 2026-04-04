package io.finett.droidclaw.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for ChatMessage model.
 * Tests context support for background task results.
 */
@RunWith(RobolectricTestRunner.class)
public class ChatMessageTest {
    
    @Test
    public void testCreateContextMessage() {
        String taskResultId = "task-123";
        String taskName = "Server Log Checker";
        String content = "Found 3 critical errors in server logs";
        
        ChatMessage message = ChatMessage.createContextMessage(taskResultId, taskName, content);
        
        assertNotNull(message);
        assertEquals(ChatMessage.TYPE_ASSISTANT, message.getType());
        assertEquals(content, message.getContent());
        assertTrue(message.isContext());
        assertEquals(taskResultId, message.getContextSourceId());
        assertEquals(taskName, message.getContextTaskName());
    }
    
    @Test
    public void testContextMessageDefaults() {
        ChatMessage message = new ChatMessage("Hello", ChatMessage.TYPE_ASSISTANT);
        
        assertFalse(message.isContext());
        assertNull(message.getContextSourceId());
        assertNull(message.getContextTaskName());
    }
    
    @Test
    public void testSetContextProperties() {
        ChatMessage message = new ChatMessage("Test", ChatMessage.TYPE_ASSISTANT);
        
        message.setIsContext(true);
        message.setContextSourceId("source-123");
        message.setContextTaskName("Test Task");
        
        assertTrue(message.isContext());
        assertEquals("source-123", message.getContextSourceId());
        assertEquals("Test Task", message.getContextTaskName());
    }
    
    @Test
    public void testContextMessageToApiMessage() {
        ChatMessage message = ChatMessage.createContextMessage(
            "task-123", 
            "Test Task", 
            "Context content"
        );
        
        // Context messages convert to standard assistant messages for API
        com.google.gson.JsonObject apiMessage = message.toApiMessage();
        
        assertEquals("assistant", apiMessage.get("role").getAsString());
        assertEquals("Context content", apiMessage.get("content").getAsString());
    }
    
    @Test
    public void testRegularAssistantMessageIsNotContext() {
        ChatMessage message = new ChatMessage("Regular response", ChatMessage.TYPE_ASSISTANT);
        
        assertFalse(message.isContext());
        assertNull(message.getContextSourceId());
        assertNull(message.getContextTaskName());
    }
    
    @Test
    public void testContextMessageWithNullValues() {
        ChatMessage message = ChatMessage.createContextMessage(null, null, "Content");
        
        assertTrue(message.isContext());
        assertNull(message.getContextSourceId());
        assertNull(message.getContextTaskName());
        assertEquals("Content", message.getContent());
    }
    
    @Test
    public void testContextMessageWithEmptyStrings() {
        ChatMessage message = ChatMessage.createContextMessage("", "", "");
        
        assertTrue(message.isContext());
        assertEquals("", message.getContextSourceId());
        assertEquals("", message.getContextTaskName());
        assertEquals("", message.getContent());
    }
}