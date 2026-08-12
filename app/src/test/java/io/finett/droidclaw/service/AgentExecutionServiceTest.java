package io.finett.droidclaw.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.model.ChatMessage;

/**
 * Unit tests for AgentExecutionService session/notification paths.
 *
 * Covers the regression areas from the background-lifecycle fixes:
 * buffered completion/error delivery when the UI reattaches after the
 * loop finished in the background, session activity tracking, and the
 * markdown stripping used for completion-notification previews.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentExecutionServiceTest {

    private AgentExecutionService service;

    @Before
    public void setUp() {
        service = Robolectric.buildService(AgentExecutionService.class).create().get();
    }

    /** UICallback that records what it was invoked with. */
    private static class RecordingCallback implements AgentExecutionService.UICallback {
        int completeCalls = 0;
        int errorCalls = 0;
        String completeResponse;
        List<ChatMessage> completeHistory;
        String error;

        @Override public void onProgress(String status) {}
        @Override public void onToolCall(String toolName, String arguments) {}
        @Override public void onToolResult(String toolName, String result) {}

        @Override
        public void onComplete(String response, List<ChatMessage> history) {
            completeCalls++;
            completeResponse = response;
            completeHistory = history;
        }

        @Override
        public void onError(String err) {
            errorCalls++;
            error = err;
        }

        @Override
        public void onApprovalRequired(String toolName, String description,
                                       JsonObject arguments,
                                       AgentLoop.ApprovalCallback approvalCallback) {}
    }

    /** Reflective access to the private sessions map (test-only seam). */
    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, AgentExecutionService.AgentSession> sessionsOf(
            AgentExecutionService svc) throws Exception {
        Field f = AgentExecutionService.class.getDeclaredField("sessions");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, AgentExecutionService.AgentSession>) f.get(svc);
    }

    private static AgentExecutionService.AgentSession seedSession(
            AgentExecutionService svc, String sessionId,
            AgentExecutionService.AgentSession.State state) throws Exception {
        AgentExecutionService.AgentSession session =
                new AgentExecutionService.AgentSession(sessionId);
        session.state = state;
        sessionsOf(svc).put(sessionId, session);
        return session;
    }

    // ==================== buffered completion delivery ====================

    @Test
    public void registerUICallback_completedSession_deliversBufferedResult() throws Exception {
        AgentExecutionService.AgentSession session = seedSession(
                service, "s1", AgentExecutionService.AgentSession.State.COMPLETED);
        session.finalResponse = "All done.";
        session.finalHistory = new ArrayList<>();

        RecordingCallback cb = new RecordingCallback();
        service.registerUICallback("s1", cb);

        assertEquals(1, cb.completeCalls);
        assertEquals("All done.", cb.completeResponse);
        assertNotNull(cb.completeHistory);
        assertEquals(0, cb.errorCalls);
        // Session must be cleaned up after delivery.
        assertNull(service.getSession("s1"));
    }

    @Test
    public void registerUICallback_errorSession_deliversBufferedError() throws Exception {
        AgentExecutionService.AgentSession session = seedSession(
                service, "s2", AgentExecutionService.AgentSession.State.ERROR);
        session.errorMessage = "API unreachable";

        RecordingCallback cb = new RecordingCallback();
        service.registerUICallback("s2", cb);

        assertEquals(1, cb.errorCalls);
        assertEquals("API unreachable", cb.error);
        assertEquals(0, cb.completeCalls);
        assertNull(service.getSession("s2"));
    }

    @Test
    public void registerUICallback_errorSession_nullMessage_deliversUnknownError() throws Exception {
        seedSession(service, "s3", AgentExecutionService.AgentSession.State.ERROR);
        // errorMessage left null.

        RecordingCallback cb = new RecordingCallback();
        service.registerUICallback("s3", cb);

        assertEquals(1, cb.errorCalls);
        assertEquals("Unknown error", cb.error);
    }

    @Test
    public void registerUICallback_unknownSession_registersWithoutDelivery() {
        RecordingCallback cb = new RecordingCallback();
        service.registerUICallback("missing", cb);

        assertEquals(0, cb.completeCalls);
        assertEquals(0, cb.errorCalls);
    }

    @Test
    public void registerUICallback_runningSession_doesNotDeliver() throws Exception {
        seedSession(service, "s4", AgentExecutionService.AgentSession.State.RUNNING);

        RecordingCallback cb = new RecordingCallback();
        service.registerUICallback("s4", cb);

        assertEquals(0, cb.completeCalls);
        assertEquals(0, cb.errorCalls);
        // Running session stays registered.
        assertNotNull(service.getSession("s4"));
    }

    // ==================== isSessionActive ====================

    @Test
    public void isSessionActive_running_returnsTrue() throws Exception {
        seedSession(service, "a", AgentExecutionService.AgentSession.State.RUNNING);
        assertTrue(service.isSessionActive("a"));
    }

    @Test
    public void isSessionActive_pausedApproval_returnsTrue() throws Exception {
        seedSession(service, "a", AgentExecutionService.AgentSession.State.PAUSED_APPROVAL);
        assertTrue(service.isSessionActive("a"));
    }

    @Test
    public void isSessionActive_completed_returnsFalse() throws Exception {
        seedSession(service, "a", AgentExecutionService.AgentSession.State.COMPLETED);
        assertFalse(service.isSessionActive("a"));
    }

    @Test
    public void isSessionActive_error_returnsFalse() throws Exception {
        seedSession(service, "a", AgentExecutionService.AgentSession.State.ERROR);
        assertFalse(service.isSessionActive("a"));
    }

    @Test
    public void isSessionActive_missing_returnsFalse() {
        assertFalse(service.isSessionActive("never-seen"));
    }

    // ==================== stripMarkdown (notification preview) ====================

    @Test
    public void stripMarkdown_null_returnsEmpty() {
        assertEquals("", AgentExecutionService.stripMarkdown(null));
    }

    @Test
    public void stripMarkdown_plainText_unchanged() {
        assertEquals("Task finished successfully.",
                AgentExecutionService.stripMarkdown("Task finished successfully."));
    }

    @Test
    public void stripMarkdown_bold_removed() {
        assertEquals("done", AgentExecutionService.stripMarkdown("**done**"));
    }

    @Test
    public void stripMarkdown_headingMarker_removed() {
        assertEquals("Title", AgentExecutionService.stripMarkdown("# Title"));
    }

    @Test
    public void stripMarkdown_link_keepsText() {
        assertEquals("see docs for more",
                AgentExecutionService.stripMarkdown("see [docs](https://example.com) for more"));
    }

    @Test
    public void stripMarkdown_inlineCode_removed() {
        String out = AgentExecutionService.stripMarkdown("ran `ls -la` ok");
        assertFalse(out.contains("`"));
        assertTrue(out.contains("ran"));
        assertTrue(out.contains("ok"));
    }

    @Test
    public void stripMarkdown_listMarkers_removed() {
        assertEquals("item one", AgentExecutionService.stripMarkdown("- item one"));
    }

    @Test
    public void stripMarkdown_blockquote_removed() {
        assertEquals("quoted text", AgentExecutionService.stripMarkdown("> quoted text"));
    }

    @Test
    public void stripMarkdown_excessiveNewlines_collapsed() {
        assertEquals("a\n\nb", AgentExecutionService.stripMarkdown("a\n\n\n\nb"));
    }
}
