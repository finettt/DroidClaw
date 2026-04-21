package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import io.finett.droidclaw.model.SessionType;

@RunWith(RobolectricTestRunner.class)
public class ChatRepositoryHiddenSessionTest {

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
    public void saveSessions_andLoadSessions_persistsSessionType() {
        ChatSession normal = new ChatSession("session-1", "Normal", 100L);
        ChatSession hidden = new ChatSession("session-2", "Hidden", 200L);
        hidden.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        repository.saveSessions(Arrays.asList(normal, hidden));

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(2, loaded.size());

        ChatSession loadedNormal = findSessionById(loaded, "session-1");
        ChatSession loadedHidden = findSessionById(loaded, "session-2");

        assertNotNull(loadedNormal);
        assertEquals(SessionType.NORMAL, loadedNormal.getSessionType());
        assertFalse(loadedNormal.isHidden());

        assertNotNull(loadedHidden);
        assertEquals(SessionType.HIDDEN_HEARTBEAT, loadedHidden.getSessionType());
        assertTrue(loadedHidden.isHidden());
    }

    @Test
    public void saveSessions_andLoadSessions_persistsParentTaskId() {
        ChatSession hidden = new ChatSession("session-1", "Cron Session", 100L);
        hidden.setSessionType(SessionType.HIDDEN_CRON);
        hidden.setParentTaskId("cron-job-1");

        repository.saveSessions(Arrays.asList(hidden));

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(1, loaded.size());
        assertEquals(SessionType.HIDDEN_CRON, loaded.get(0).getSessionType());
        assertEquals("cron-job-1", loaded.get(0).getParentTaskId());
    }

    @Test
    public void saveSessions_doesNotSaveParentTaskId_whenNull() {
        ChatSession normal = new ChatSession("session-1", "Normal", 100L);

        repository.saveSessions(Arrays.asList(normal));

        List<ChatSession> loaded = repository.loadSessions();
        assertEquals(1, loaded.size());
        assertNull(loaded.get(0).getParentTaskId());
    }

    @Test
    public void getVisibleSessions_excludesHiddenSessions() {
        ChatSession normal1 = new ChatSession("session-1", "Normal 1", 100L);
        ChatSession normal2 = new ChatSession("session-2", "Normal 2", 200L);
        ChatSession hidden1 = new ChatSession("session-3", "Hidden 1", 300L);
        hidden1.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        ChatSession hidden2 = new ChatSession("session-4", "Hidden 2", 400L);
        hidden2.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(normal1, normal2, hidden1, hidden2));

        List<ChatSession> visible = repository.getVisibleSessions();

        assertEquals(2, visible.size());
        assertTrue(containsSession(visible, "session-1"));
        assertTrue(containsSession(visible, "session-2"));
        assertFalse(containsSession(visible, "session-3"));
        assertFalse(containsSession(visible, "session-4"));
    }

    @Test
    public void getVisibleSessions_returnsAllSessions_whenNoneAreHidden() {
        ChatSession session1 = new ChatSession("session-1", "Session 1", 100L);
        ChatSession session2 = new ChatSession("session-2", "Session 2", 200L);

        repository.saveSessions(Arrays.asList(session1, session2));

        List<ChatSession> visible = repository.getVisibleSessions();

        assertEquals(2, visible.size());
    }

    @Test
    public void getVisibleSessions_returnsEmptyList_whenAllSessionsAreHidden() {
        ChatSession hidden1 = new ChatSession("session-1", "Hidden 1", 100L);
        hidden1.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        ChatSession hidden2 = new ChatSession("session-2", "Hidden 2", 200L);
        hidden2.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(hidden1, hidden2));

        List<ChatSession> visible = repository.getVisibleSessions();

        assertTrue(visible.isEmpty());
    }

    @Test
    public void getVisibleSessions_sortsByUpdatedAtDescending() {
        ChatSession normal1 = new ChatSession("session-1", "Old", 100L);
        ChatSession normal2 = new ChatSession("session-2", "New", 300L);
        ChatSession normal3 = new ChatSession("session-3", "Middle", 200L);
        ChatSession hidden = new ChatSession("session-4", "Hidden", 400L);
        hidden.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        repository.saveSessions(Arrays.asList(normal1, normal2, normal3, hidden));

        List<ChatSession> visible = repository.getVisibleSessions();

        assertEquals(3, visible.size());
        assertEquals("session-2", visible.get(0).getId()); // Newest
        assertEquals("session-3", visible.get(1).getId()); // Middle
        assertEquals("session-1", visible.get(2).getId()); // Oldest
    }

    @Test
    public void getHiddenSession_returnsSession_whenTaskIdMatches() {
        ChatSession hidden = new ChatSession("session-1", "Hidden Session", 100L);
        hidden.setSessionType(SessionType.HIDDEN_CRON);
        hidden.setParentTaskId("cron-job-1");

        repository.saveSessions(Arrays.asList(hidden));

        ChatSession found = repository.getHiddenSession("cron-job-1");

        assertNotNull(found);
        assertEquals("session-1", found.getId());
        assertTrue(found.isHidden());
        assertEquals("cron-job-1", found.getParentTaskId());
    }

    @Test
    public void getHiddenSession_returnsNull_whenTaskIdDoesNotMatch() {
        ChatSession hidden = new ChatSession("session-1", "Hidden Session", 100L);
        hidden.setSessionType(SessionType.HIDDEN_CRON);
        hidden.setParentTaskId("cron-job-1");

        repository.saveSessions(Arrays.asList(hidden));

        ChatSession found = repository.getHiddenSession("nonexistent-job");

        assertNull(found);
    }

    @Test
    public void getHiddenSession_returnsNull_whenSessionIsNormal() {
        ChatSession normal = new ChatSession("session-1", "Normal Session", 100L);

        repository.saveSessions(Arrays.asList(normal));

        ChatSession found = repository.getHiddenSession(null);

        assertNull(found);
    }

    @Test
    public void getHiddenSession_matchesFirstMatchingSession() {
        ChatSession hidden1 = new ChatSession("session-1", "Hidden 1", 100L);
        hidden1.setSessionType(SessionType.HIDDEN_CRON);
        hidden1.setParentTaskId("cron-job-1");

        ChatSession hidden2 = new ChatSession("session-2", "Hidden 2", 200L);
        hidden2.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        hidden2.setParentTaskId("cron-job-1");

        repository.saveSessions(Arrays.asList(hidden1, hidden2));

        ChatSession found = repository.getHiddenSession("cron-job-1");

        assertNotNull(found);
        assertEquals("cron-job-1", found.getParentTaskId());
    }

    @Test
    public void getAllSessionsIncludingHidden_returnsAllSessions() {
        ChatSession normal1 = new ChatSession("session-1", "Normal 1", 100L);
        ChatSession normal2 = new ChatSession("session-2", "Normal 2", 200L);
        ChatSession hidden1 = new ChatSession("session-3", "Hidden 1", 300L);
        hidden1.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        ChatSession hidden2 = new ChatSession("session-4", "Hidden 2", 400L);
        hidden2.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(normal1, normal2, hidden1, hidden2));

        List<ChatSession> all = repository.getAllSessionsIncludingHidden();

        assertEquals(4, all.size());
    }

    @Test
    public void getAllSessionsIncludingHidden_returnsSameAsLoadSessions() {
        ChatSession normal = new ChatSession("session-1", "Normal", 100L);
        ChatSession hidden = new ChatSession("session-2", "Hidden", 200L);
        hidden.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        repository.saveSessions(Arrays.asList(normal, hidden));

        List<ChatSession> all = repository.getAllSessionsIncludingHidden();
        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(loaded.size(), all.size());
    }

    @Test
    public void loadSessions_handlesOldDataWithoutSessionType() {
        // Simulate old data without sessionType field
        String oldJsonArray = "[{\"id\":\"old-session\",\"title\":\"Old Session\",\"updatedAt\":100,\"currentContextTokens\":0,\"currentPromptTokens\":0,\"currentCompletionTokens\":0,\"totalTokens\":0,\"totalPromptTokens\":0,\"totalCompletionTokens\":0,\"totalToolCalls\":0}]";
        sharedPreferences.edit().putString("chat_sessions", oldJsonArray).commit();

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(1, loaded.size());
        assertEquals("old-session", loaded.get(0).getId());
        assertEquals(SessionType.NORMAL, loaded.get(0).getSessionType()); // Defaults to NORMAL
        assertFalse(loaded.get(0).isHidden());
    }

    @Test
    public void existingSessionsStillWork_afterAddingNewFields() {
        // Save with old-style session
        ChatSession session = new ChatSession("session-1", "Test", 100L);
        session.setCurrentContextTokens(1000);
        session.setTotalTokens(5000);

        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loaded = repository.loadSessions();

        assertEquals(1, loaded.size());
        assertEquals("session-1", loaded.get(0).getId());
        assertEquals(1000, loaded.get(0).getCurrentContextTokens());
        assertEquals(5000, loaded.get(0).getTotalTokens());
        assertEquals(SessionType.NORMAL, loaded.get(0).getSessionType());
        assertFalse(loaded.get(0).isHidden());
    }

    private ChatSession findSessionById(List<ChatSession> sessions, String id) {
        for (ChatSession session : sessions) {
            if (session.getId().equals(id)) {
                return session;
            }
        }
        return null;
    }

    private boolean containsSession(List<ChatSession> sessions, String id) {
        return findSessionById(sessions, id) != null;
    }
}
