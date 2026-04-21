package io.finett.droidclaw.repository;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.SessionType;

@RunWith(AndroidJUnit4.class)
public class ChatRepositoryHiddenSessionInstrumentedTest {

    private ChatRepository repository;

    @Before
    public void setUp() {
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        repository = new ChatRepository(getApplicationContext());
    }

    @After
    public void tearDown() {
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== SESSION TYPE TESTS ====================

    @Test
    public void saveAndLoadSession_withNormalSessionType_hasCorrectType() {
        ChatSession session = new ChatSession("session-normal", "Normal Chat", 1000L);
        session.setSessionType(SessionType.NORMAL);
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals(SessionType.NORMAL, loadedSessions.get(0).getSessionType());
        assertFalse("Normal session should not be hidden", loadedSessions.get(0).isHidden());
    }

    @Test
    public void saveAndLoadSession_withHiddenHeartbeatSession_hasCorrectType() {
        ChatSession session = new ChatSession("session-heartbeat", "Heartbeat Check", 1000L);
        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session.setParentTaskId("heartbeat-task-1");
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals(SessionType.HIDDEN_HEARTBEAT, loadedSessions.get(0).getSessionType());
        assertTrue("Heartbeat session should be hidden", loadedSessions.get(0).isHidden());
        assertEquals("heartbeat-task-1", loadedSessions.get(0).getParentTaskId());
    }

    @Test
    public void saveAndLoadSession_withHiddenCronSession_hasCorrectType() {
        ChatSession session = new ChatSession("session-cron", "Cron Job Execution", 1000L);
        session.setSessionType(SessionType.HIDDEN_CRON);
        session.setParentTaskId("cron-job-1");
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals(SessionType.HIDDEN_CRON, loadedSessions.get(0).getSessionType());
        assertTrue("Cron session should be hidden", loadedSessions.get(0).isHidden());
        assertEquals("cron-job-1", loadedSessions.get(0).getParentTaskId());
    }

    @Test
    public void saveAndLoadSession_withoutSessionType_defaultsToNormal() {
        ChatSession session = new ChatSession("session-default", "Default Chat", 1000L);
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals(SessionType.NORMAL, loadedSessions.get(0).getSessionType());
        assertFalse("Default session should not be hidden", loadedSessions.get(0).isHidden());
    }

    // ==================== VISIBLE SESSIONS TESTS ====================

    @Test
    public void getVisibleSessions_returnsOnlyNormalSessions() {
        ChatSession normalSession1 = new ChatSession("normal-1", "Normal Chat 1", 1000L);
        normalSession1.setSessionType(SessionType.NORMAL);

        ChatSession normalSession2 = new ChatSession("normal-2", "Normal Chat 2", 2000L);
        normalSession2.setSessionType(SessionType.NORMAL);

        ChatSession hiddenSession1 = new ChatSession("hidden-1", "Heartbeat Check", 3000L);
        hiddenSession1.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        ChatSession hiddenSession2 = new ChatSession("hidden-2", "Cron Job", 4000L);
        hiddenSession2.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(normalSession1, normalSession2, hiddenSession1, hiddenSession2));

        List<ChatSession> visibleSessions = repository.getVisibleSessions();

        assertEquals("Should have 2 visible sessions", 2, visibleSessions.size());
        for (ChatSession session : visibleSessions) {
            assertFalse("Visible session should not be hidden", session.isHidden());
            assertEquals(SessionType.NORMAL, session.getSessionType());
        }
    }

    @Test
    public void getVisibleSessions_withAllHiddenSessions_returnsEmptyList() {
        ChatSession hiddenSession1 = new ChatSession("hidden-1", "Heartbeat Check", 1000L);
        hiddenSession1.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        ChatSession hiddenSession2 = new ChatSession("hidden-2", "Cron Job", 2000L);
        hiddenSession2.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(hiddenSession1, hiddenSession2));

        List<ChatSession> visibleSessions = repository.getVisibleSessions();

        assertEquals("Should have 0 visible sessions", 0, visibleSessions.size());
    }

    @Test
    public void getVisibleSessions_withAllNormalSessions_returnsAllSessions() {
        ChatSession normalSession1 = new ChatSession("normal-1", "Normal Chat 1", 1000L);
        normalSession1.setSessionType(SessionType.NORMAL);

        ChatSession normalSession2 = new ChatSession("normal-2", "Normal Chat 2", 2000L);
        normalSession2.setSessionType(SessionType.NORMAL);

        ChatSession normalSession3 = new ChatSession("normal-3", "Normal Chat 3", 3000L);
        normalSession3.setSessionType(SessionType.NORMAL);

        repository.saveSessions(Arrays.asList(normalSession1, normalSession2, normalSession3));

        List<ChatSession> visibleSessions = repository.getVisibleSessions();

        assertEquals("Should have 3 visible sessions", 3, visibleSessions.size());
    }

    // ==================== HIDDEN SESSIONS TESTS ====================

    @Test
    public void getHiddenSession_withValidTaskId_returnsSession() {
        ChatSession hiddenSession = new ChatSession("session-hidden", "Heartbeat Check", 1000L);
        hiddenSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        hiddenSession.setParentTaskId("heartbeat-task-1");
        repository.saveSessions(Arrays.asList(hiddenSession));

        ChatSession retrieved = repository.getHiddenSession("heartbeat-task-1");

        assertNotNull("Should find hidden session", retrieved);
        assertEquals("session-hidden", retrieved.getId());
        assertTrue("Retrieved session should be hidden", retrieved.isHidden());
        assertEquals("heartbeat-task-1", retrieved.getParentTaskId());
    }

    @Test
    public void getHiddenSession_withNonExistentTaskId_returnsNull() {
        ChatSession hiddenSession = new ChatSession("session-hidden", "Heartbeat Check", 1000L);
        hiddenSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        hiddenSession.setParentTaskId("heartbeat-task-1");
        repository.saveSessions(Arrays.asList(hiddenSession));

        ChatSession retrieved = repository.getHiddenSession("non-existent-task");

        assertNull("Should return null for non-existent task ID", retrieved);
    }

    @Test
    public void getHiddenSession_withNormalSessionTaskId_returnsNull() {
        ChatSession normalSession = new ChatSession("session-normal", "Normal Chat", 1000L);
        normalSession.setSessionType(SessionType.NORMAL);
        normalSession.setParentTaskId("some-task-id");
        repository.saveSessions(Arrays.asList(normalSession));

        ChatSession retrieved = repository.getHiddenSession("some-task-id");

        assertNull("Should not return normal session as hidden", retrieved);
    }

    @Test
    public void getHiddenSession_withMultipleHiddenSessions_returnsCorrectOne() {
        ChatSession heartbeatSession = new ChatSession("session-hb", "Heartbeat", 1000L);
        heartbeatSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        heartbeatSession.setParentTaskId("heartbeat-1");

        ChatSession cronSession = new ChatSession("session-cron", "Cron Job", 2000L);
        cronSession.setSessionType(SessionType.HIDDEN_CRON);
        cronSession.setParentTaskId("cron-1");

        repository.saveSessions(Arrays.asList(heartbeatSession, cronSession));

        ChatSession retrieved = repository.getHiddenSession("cron-1");

        assertNotNull("Should find cron session", retrieved);
        assertEquals("session-cron", retrieved.getId());
        assertEquals(SessionType.HIDDEN_CRON, retrieved.getSessionType());
    }

    @Test
    public void getAllSessionsIncludingHidden_returnsAllSessions() {
        ChatSession normalSession = new ChatSession("normal", "Normal Chat", 1000L);
        normalSession.setSessionType(SessionType.NORMAL);

        ChatSession heartbeatSession = new ChatSession("heartbeat", "Heartbeat", 2000L);
        heartbeatSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);

        ChatSession cronSession = new ChatSession("cron", "Cron Job", 3000L);
        cronSession.setSessionType(SessionType.HIDDEN_CRON);

        repository.saveSessions(Arrays.asList(normalSession, heartbeatSession, cronSession));

        List<ChatSession> allSessions = repository.getAllSessionsIncludingHidden();

        assertEquals("Should have 3 total sessions", 3, allSessions.size());
    }

    @Test
    public void getAllSessionsIncludingHidden_withNoSessions_returnsEmptyList() {
        List<ChatSession> allSessions = repository.getAllSessionsIncludingHidden();
        assertEquals("Should have 0 sessions", 0, allSessions.size());
    }

    @Test
    public void session_parentTaskId_persistsCorrectly() {
        ChatSession session = new ChatSession("session-1", "Task Session", 1000L);
        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session.setParentTaskId("parent-task-123");
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals("parent-task-123", loadedSessions.get(0).getParentTaskId());
    }

    @Test
    public void session_withoutParentTaskId_loadsAsNull() {
        ChatSession session = new ChatSession("session-1", "Normal Session", 1000L);
        session.setSessionType(SessionType.NORMAL);
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertNull("Parent task ID should be null for normal session",
                loadedSessions.get(0).getParentTaskId());
    }

    @Test
    public void session_updateParentTaskId_persistsUpdate() {
        ChatSession session = new ChatSession("session-1", "Task Session", 1000L);
        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session.setParentTaskId("old-task-id");
        repository.saveSessions(Arrays.asList(session));

        session.setParentTaskId("new-task-id");
        repository.saveSessions(Arrays.asList(session));

        List<ChatSession> loadedSessions = repository.loadSessions();
        assertEquals(1, loadedSessions.size());
        assertEquals("new-task-id", loadedSessions.get(0).getParentTaskId());
    }

    // ==================== MIXED SESSION TYPES TESTS ====================

    @Test
    public void mixedSessionTypes_filterAndRetrieveCorrectly() {
        ChatSession normal1 = new ChatSession("normal-1", "Normal 1", 1000L);
        normal1.setSessionType(SessionType.NORMAL);

        ChatSession normal2 = new ChatSession("normal-2", "Normal 2", 2000L);
        normal2.setSessionType(SessionType.NORMAL);

        ChatSession heartbeat1 = new ChatSession("hb-1", "Heartbeat 1", 3000L);
        heartbeat1.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        heartbeat1.setParentTaskId("heartbeat-1");

        ChatSession heartbeat2 = new ChatSession("hb-2", "Heartbeat 2", 4000L);
        heartbeat2.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        heartbeat2.setParentTaskId("heartbeat-2");

        ChatSession cron1 = new ChatSession("cron-1", "Cron 1", 5000L);
        cron1.setSessionType(SessionType.HIDDEN_CRON);
        cron1.setParentTaskId("cron-1");

        repository.saveSessions(Arrays.asList(normal1, normal2, heartbeat1, heartbeat2, cron1));

        List<ChatSession> visibleSessions = repository.getVisibleSessions();
        assertEquals("Should have 2 visible sessions", 2, visibleSessions.size());

        ChatSession hb1 = repository.getHiddenSession("heartbeat-1");
        assertNotNull("Should find heartbeat-1", hb1);
        assertTrue("Should be hidden", hb1.isHidden());

        ChatSession cron1Retrieved = repository.getHiddenSession("cron-1");
        assertNotNull("Should find cron-1", cron1Retrieved);
        assertTrue("Should be hidden", cron1Retrieved.isHidden());

        List<ChatSession> allSessions = repository.getAllSessionsIncludingHidden();
        assertEquals("Should have 5 total sessions", 5, allSessions.size());
    }

    @Test
    public void sessionTypePersists_acrossRepositoryInstances() {
        ChatSession session = new ChatSession("session-persist", "Heartbeat Check", 1000L);
        session.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session.setParentTaskId("heartbeat-persist");
        repository.saveSessions(Arrays.asList(session));

        ChatRepository newRepository = new ChatRepository(getApplicationContext());

        List<ChatSession> visibleSessions = newRepository.getVisibleSessions();
        assertEquals("Should have 0 visible sessions", 0, visibleSessions.size());

        ChatSession hiddenSession = newRepository.getHiddenSession("heartbeat-persist");
        assertNotNull("Hidden session should persist", hiddenSession);
        assertEquals(SessionType.HIDDEN_HEARTBEAT, hiddenSession.getSessionType());
    }

    @Test
    public void deleteSession_withHiddenSession_removesCorrectly() {
        ChatSession normalSession = new ChatSession("normal", "Normal Chat", 1000L);
        normalSession.setSessionType(SessionType.NORMAL);

        ChatSession hiddenSession = new ChatSession("hidden", "Heartbeat Check", 2000L);
        hiddenSession.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        hiddenSession.setParentTaskId("heartbeat-delete-test");

        repository.saveSessions(Arrays.asList(normalSession, hiddenSession));

        List<ChatSession> sessionsBefore = repository.getAllSessionsIncludingHidden();
        assertEquals("Should have 2 sessions before deletion", 2, sessionsBefore.size());

        List<ChatSession> currentSessions = repository.loadSessions();
        ChatSession toDelete = null;
        for (ChatSession s : currentSessions) {
            if ("hidden".equals(s.getId())) {
                toDelete = s;
                break;
            }
        }
        currentSessions.remove(toDelete);
        repository.deleteSession("hidden", currentSessions);

        List<ChatSession> visibleSessions = repository.getVisibleSessions();
        assertEquals("Should still have 1 visible session", 1, visibleSessions.size());
        assertEquals("Visible session should be normal", "normal", visibleSessions.get(0).getId());

        List<ChatSession> sessionsAfter = repository.getAllSessionsIncludingHidden();
        assertEquals("Should have 1 session after deletion", 1, sessionsAfter.size());
        assertEquals("Remaining session should be normal", "normal", sessionsAfter.get(0).getId());
    }

    @Test
    public void sessionVisibility_afterMultipleSaves_maintainsCorrectly() {
        ChatSession session1 = new ChatSession("session-1", "Normal", 1000L);
        session1.setSessionType(SessionType.NORMAL);

        ChatSession session2 = new ChatSession("session-2", "Heartbeat", 2000L);
        session2.setSessionType(SessionType.HIDDEN_HEARTBEAT);
        session2.setParentTaskId("heartbeat-1");

        repository.saveSessions(Arrays.asList(session1, session2));

        session1.setUpdatedAt(3000L);
        session2.setUpdatedAt(4000L);
        repository.saveSessions(Arrays.asList(session1, session2));

        List<ChatSession> visibleSessions = repository.getVisibleSessions();
        assertEquals("Should have 1 visible session", 1, visibleSessions.size());
        assertEquals("session-1", visibleSessions.get(0).getId());

        ChatSession hiddenSession = repository.getHiddenSession("heartbeat-1");
        assertNotNull("Hidden session should still exist", hiddenSession);
        assertEquals(4000L, hiddenSession.getUpdatedAt());
    }
}
