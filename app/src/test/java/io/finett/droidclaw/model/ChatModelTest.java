package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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

        message.setType(ChatMessage.TYPE_ASSISTANT);

        assertFalse(message.isUser());
        assertTrue(message.isAssistant());
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