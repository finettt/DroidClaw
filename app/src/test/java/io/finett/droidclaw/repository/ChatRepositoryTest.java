package io.finett.droidclaw.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;

import static org.junit.Assert.*;

/**
 * Unit tests for ChatRepository.
 * Tests hidden session support and session filtering.
 */
@RunWith(RobolectricTestRunner.class)
public class ChatRepositoryTest {
    
    private ChatRepository repository;
    private Context context;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        repository = new ChatRepository(context);
        // Clear any existing data
        repository.clearAllMessages();
    }
    
    @Test
    public void testSaveAndLoadVisibleSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        
        // Create user sessions
        ChatSession session1 = new ChatSession("user-1", "Chat 1", System.currentTimeMillis());
        ChatSession session2 = new ChatSession("user-2", "Chat 2", System.currentTimeMillis());
        sessions.add(session1);
        sessions.add(session2);
        
        repository.saveSessions(sessions);
        List<ChatSession> loaded = repository.loadVisibleSessions();
        
        assertEquals(2, loaded.size());
    }
    
    @Test
    public void testSaveAndLoadAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        
        // Create user session
        ChatSession userSession = new ChatSession("user-1", "User Chat", System.currentTimeMillis());
        sessions.add(userSession);
        
        // Create cron job session
        ChatSession cronSession = ChatSession.createCronJobSession("cron-123", "task-456");
        sessions.add(cronSession);
        
        repository.saveSessions(sessions);
        List<ChatSession> allLoaded = repository.loadAllSessions();
        
        assertEquals(2, allLoaded.size());
    }
    
    @Test
    public void testLoadVisibleSessionsFiltersHidden() {
        List<ChatSession> sessions = new ArrayList<>();
        
        // Create user sessions
        ChatSession userSession1 = new ChatSession("user-1", "User Chat 1", System.currentTimeMillis());
        ChatSession userSession2 = new ChatSession("user-2", "User Chat 2", System.currentTimeMillis());
        sessions.add(userSession1);
        sessions.add(userSession2);
        
        // Create cron job sessions (hidden)
        ChatSession cronSession1 = ChatSession.createCronJobSession("cron-1", "task-1");
        ChatSession cronSession2 = ChatSession.createCronJobSession("cron-2", "task-2");
        sessions.add(cronSession1);
        sessions.add(cronSession2);
        
        repository.saveSessions(sessions);
        
        List<ChatSession> visibleSessions = repository.loadVisibleSessions();
        List<ChatSession> allSessions = repository.loadAllSessions();
        
        assertEquals(2, visibleSessions.size());
        assertEquals(4, allSessions.size());
        
        // Verify only user sessions in visible list
        for (ChatSession session : visibleSessions) {
            assertTrue(session.isUserSession());
            assertFalse(session.isHidden());
        }
    }
    
    @Test
    public void testSaveHiddenSession() {
        ChatSession cronSession = ChatSession.createCronJobSession("cron-123", "task-456");
        
        repository.saveHiddenSession(cronSession);
        
        List<ChatSession> allSessions = repository.loadAllSessions();
        List<ChatSession> visibleSessions = repository.loadVisibleSessions();
        
        assertEquals(1, allSessions.size());
        assertEquals(0, visibleSessions.size());
        
        ChatSession loaded = allSessions.get(0);
        assertTrue(loaded.isCronJobSession());
        assertTrue(loaded.isHidden());
        assertEquals("cron-123", loaded.getCronJobId());
        assertEquals("task-456", loaded.getTaskRecordId());
    }
    
    @Test
    public void testGetSessionById() {
        ChatSession session = new ChatSession("test-123", "Test Session", System.currentTimeMillis());
        List<ChatSession> sessions = new ArrayList<>();
        sessions.add(session);
        
        repository.saveSessions(sessions);
        
        ChatSession retrieved = repository.getSession("test-123");
        
        assertNotNull(retrieved);
        assertEquals("test-123", retrieved.getId());
        assertEquals("Test Session", retrieved.getTitle());
    }
    
    @Test
    public void testGetSessionByIdNotFound() {
        ChatSession retrieved = repository.getSession("non-existent");
        assertNull(retrieved);
    }
    
    @Test
    public void testGetMainSession() {
        ChatSession mainSession = repository.getMainSession();
        
        assertNotNull(mainSession);
        assertTrue(mainSession.isUserSession());
        assertFalse(mainSession.isHidden());
    }
    
    @Test
    public void testSaveSessionMetadata() {
        List<ChatSession> sessions = new ArrayList<>();
        ChatSession session = ChatSession.createCronJobSession("cron-123", "task-456");
        sessions.add(session);
        
        repository.saveSessions(sessions);
        
        List<ChatSession> loaded = repository.loadAllSessions();
        assertEquals(1, loaded.size());
        
        ChatSession loadedSession = loaded.get(0);
        assertEquals(ChatSession.TYPE_CRON_JOB, loadedSession.getSessionType());
        assertEquals("cron-123", loadedSession.getCronJobId());
        assertEquals("task-456", loadedSession.getTaskRecordId());
    }
    
    @Test
    public void testSaveContextMessage() {
        ChatSession session = new ChatSession("session-123", "Test", System.currentTimeMillis());
        
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage contextMsg = ChatMessage.createContextMessage(
            "task-result-123",
            "Background Task",
            "Task completed successfully"
        );
        messages.add(contextMsg);
        
        repository.saveMessages(session.getId(), messages);
        
        List<ChatMessage> loaded = repository.loadMessages(session.getId());
        assertEquals(1, loaded.size());
        
        ChatMessage loadedMsg = loaded.get(0);
        assertTrue(loadedMsg.isContext());
        assertEquals("task-result-123", loadedMsg.getContextSourceId());
        assertEquals("Background Task", loadedMsg.getContextTaskName());
        assertEquals("Task completed successfully", loadedMsg.getContent());
    }
    
    @Test
    public void testSaveMessagesWithMixedContextAndRegular() {
        ChatSession session = new ChatSession("session-123", "Test", System.currentTimeMillis());
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("User message", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("Assistant response", ChatMessage.TYPE_ASSISTANT));
        messages.add(ChatMessage.createContextMessage("task-1", "Task", "Context"));
        
        repository.saveMessages(session.getId(), messages);
        
        List<ChatMessage> loaded = repository.loadMessages(session.getId());
        assertEquals(3, loaded.size());
        
        assertFalse(loaded.get(0).isContext());
        assertFalse(loaded.get(1).isContext());
        assertTrue(loaded.get(2).isContext());
    }
    
    @Test
    public void testDeleteSession() {
        List<ChatSession> sessions = new ArrayList<>();
        ChatSession session1 = new ChatSession("session-1", "Session 1", System.currentTimeMillis());
        ChatSession session2 = new ChatSession("session-2", "Session 2", System.currentTimeMillis());
        sessions.add(session1);
        sessions.add(session2);
        
        repository.saveSessions(sessions);
        
        // Save messages for session-1
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Test", ChatMessage.TYPE_USER));
        repository.saveMessages("session-1", messages);
        
        // Delete session-1
        List<ChatSession> remaining = new ArrayList<>();
        remaining.add(session2);
        repository.deleteSession("session-1", remaining);
        
        // Verify session deleted
        List<ChatSession> loaded = repository.loadAllSessions();
        assertEquals(1, loaded.size());
        assertEquals("session-2", loaded.get(0).getId());
        
        // Verify messages deleted
        List<ChatMessage> loadedMessages = repository.loadMessages("session-1");
        assertEquals(0, loadedMessages.size());
    }
    
    @Test
    public void testUpdateHiddenSessionDoesNotDuplicate() {
        ChatSession cronSession = ChatSession.createCronJobSession("cron-123", "task-456");
        
        // Save once
        repository.saveHiddenSession(cronSession);
        assertEquals(1, repository.loadAllSessions().size());
        
        // Update and save again
        cronSession.setTitle("Updated Title");
        repository.saveHiddenSession(cronSession);
        
        List<ChatSession> sessions = repository.loadAllSessions();
        assertEquals(1, sessions.size());
        assertEquals("Updated Title", sessions.get(0).getTitle());
    }
}