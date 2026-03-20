package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;

@RunWith(RobolectricTestRunner.class)
public class ChatRepositoryTest {

    private ChatRepository repository;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        repository = new ChatRepository(context);
        sharedPreferences = context.getSharedPreferences("chat_messages", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
    }

    @Test
    public void saveSessions_andLoadSessions_persistsAndSortsByUpdatedAtDescending() {
        ChatSession oldest = new ChatSession("1", "Oldest", 100L);
        ChatSession newest = new ChatSession("2", "Newest", 300L);
        ChatSession middle = new ChatSession("3", "Middle", 200L);

        repository.saveSessions(Arrays.asList(oldest, newest, middle));

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(3, loaded.size());
        assertEquals("2", loaded.get(0).getId());
        assertEquals("3", loaded.get(1).getId());
        assertEquals("1", loaded.get(2).getId());
    }

    @Test
    public void loadSessions_returnsEmptyList_whenNoSessionsExist() {
        List<ChatSession> loaded = repository.loadSessions();

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void loadSessions_clearsCorruptedStoredData_andReturnsEmptyList() {
        sharedPreferences.edit().putString("chat_sessions", "{not-valid-json").commit();

        List<ChatSession> loaded = repository.loadSessions();

        assertTrue(loaded.isEmpty());
        assertFalse(sharedPreferences.contains("chat_sessions"));
    }

    @Test
    public void updateSession_updatesMatchingSessionAndPersistsChanges() {
        ChatSession sessionOne = new ChatSession("1", "Original", 100L);
        ChatSession sessionTwo = new ChatSession("2", "Other", 200L);
        List<ChatSession> sessions = Arrays.asList(sessionOne, sessionTwo);

        repository.updateSession("1", "Updated", 999L, sessions);

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals("Updated", sessionOne.getTitle());
        assertEquals(999L, sessionOne.getUpdatedAt());
        assertEquals("1", loaded.get(0).getId());
        assertEquals("Updated", loaded.get(0).getTitle());
        assertEquals(999L, loaded.get(0).getUpdatedAt());
    }

    @Test
    public void deleteSession_deletesMessagesAndSavesRemainingSessions() {
        String deletedSessionId = "delete-me";
        String keptSessionId = "keep-me";

        repository.saveMessages(deletedSessionId, Arrays.asList(
                createMessage("First", ChatMessage.TYPE_USER, 10L)
        ));
        repository.saveMessages(keptSessionId, Arrays.asList(
                createMessage("Second", ChatMessage.TYPE_ASSISTANT, 20L)
        ));

        ChatSession keptSession = new ChatSession(keptSessionId, "Kept", 20L);
        repository.deleteSession(deletedSessionId, Arrays.asList(keptSession));

        assertTrue(repository.loadMessages(deletedSessionId).isEmpty());

        List<ChatMessage> keptMessages = repository.loadMessages(keptSessionId);
        assertEquals(1, keptMessages.size());
        assertEquals("Second", keptMessages.get(0).getContent());

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals(keptSessionId, loadedSessions.get(0).getId());
    }

    @Test
    public void generateTitleFromMessage_returnsDefaultForNullOrBlank() {
        assertEquals("New Chat", repository.generateTitleFromMessage(null));
        assertEquals("New Chat", repository.generateTitleFromMessage("   "));
    }

    @Test
    public void generateTitleFromMessage_returnsTrimmedMessage_whenShortEnough() {
        assertEquals("Hello world", repository.generateTitleFromMessage("  Hello world  "));
    }

    @Test
    public void generateTitleFromMessage_truncatesAtWordBoundary_whenPossible() {
        String title = repository.generateTitleFromMessage("This is a long message title for truncation");

        assertEquals("This is a long message title...", title);
    }

    @Test
    public void generateTitleFromMessage_truncatesHard_whenNoGoodWordBoundaryExists() {
        String title = repository.generateTitleFromMessage("SupercalifragilisticexpialidociousMessage");

        assertEquals("Supercalifragilisticexpiali...", title);
    }

    @Test
    public void saveMessages_andLoadMessages_persistsContentTypeAndTimestamp() {
        ChatMessage userMessage = createMessage("Hello", ChatMessage.TYPE_USER, 111L);
        ChatMessage assistantMessage = createMessage("Hi", ChatMessage.TYPE_ASSISTANT, 222L);

        repository.saveMessages("session-1", Arrays.asList(userMessage, assistantMessage));

        List<ChatMessage> loaded = repository.loadMessages("session-1");

        assertEquals(2, loaded.size());

        assertEquals("Hello", loaded.get(0).getContent());
        assertEquals(ChatMessage.TYPE_USER, loaded.get(0).getType());
        assertEquals(111L, loaded.get(0).getTimestamp());

        assertEquals("Hi", loaded.get(1).getContent());
        assertEquals(ChatMessage.TYPE_ASSISTANT, loaded.get(1).getType());
        assertEquals(222L, loaded.get(1).getTimestamp());
    }

    @Test
    public void loadMessages_returnsEmptyList_whenNoMessagesExist() {
        List<ChatMessage> loaded = repository.loadMessages("missing-session");

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void loadMessages_returnsEmptyList_whenStoredMessagesAreCorrupted() {
        sharedPreferences.edit().putString("session_bad", "{not-valid-json").commit();

        List<ChatMessage> loaded = repository.loadMessages("bad");

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void deleteMessages_removesOnlyTargetSessionMessages() {
        repository.saveMessages("session-1", Arrays.asList(
                createMessage("Delete me", ChatMessage.TYPE_USER, 1L)
        ));
        repository.saveMessages("session-2", Arrays.asList(
                createMessage("Keep me", ChatMessage.TYPE_ASSISTANT, 2L)
        ));

        repository.deleteMessages("session-1");

        assertTrue(repository.loadMessages("session-1").isEmpty());
        assertEquals(1, repository.loadMessages("session-2").size());
    }

    @Test
    public void clearAllMessages_removesSessionsAndMessages() {
        repository.saveSessions(Arrays.asList(
                new ChatSession("1", "Session", 123L)
        ));
        repository.saveMessages("session-1", Arrays.asList(
                createMessage("Message", ChatMessage.TYPE_USER, 10L)
        ));

        repository.clearAllMessages();

        assertTrue(repository.loadSessions().isEmpty());
        assertTrue(repository.loadMessages("session-1").isEmpty());
    }

    private ChatMessage createMessage(String content, int type, long timestamp) {
        ChatMessage message = new ChatMessage(content, type);
        message.setTimestamp(timestamp);
        return message;
    }

}