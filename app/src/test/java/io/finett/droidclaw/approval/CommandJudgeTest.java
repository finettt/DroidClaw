package io.finett.droidclaw.approval;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;

/**
 * Unit tests for {@link CommandJudge}: verdict parsing, fail-closed behaviour
 * on garbage/timeout/error, and the poison-resistance contract (the judge sees
 * only command context, never chat history or web/fetch content).
 *
 * <p>The {@link LlmApiService#sendMessage} transport is mocked to deliver its
 * response synchronously, mirroring the real one-shot contract while letting the
 * test drive allow/deny/ask/garbage/timeout/error paths deterministically.
 */
@RunWith(RobolectricTestRunner.class)
public class CommandJudgeTest {

    @Mock
    private LlmApiService apiService;

    private CommandJudge judge;

    private String pendingResponse;
    private String pendingError;
    private List<ChatMessage> capturedHistory;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doAnswer(inv -> {
            capturedHistory = inv.getArgument(0);
            LlmApiService.ChatCallback cb = inv.getArgument(4);
            if (pendingResponse != null) {
                cb.onSuccess(pendingResponse);
            } else if (pendingError != null) {
                cb.onError(pendingError);
            }
            return null;
        }).when(apiService).sendMessage(any(), any(), any(), any(), any());
        // 1 second keeps the timeout path fast.
        judge = new CommandJudge(apiService, null, 1);
    }

    private CommandJudge.VerdictResult call(String command) {
        return judge.evaluate(command, "/home/user/project", "local", "/home/user/project");
    }

    @Test
    public void evaluate_allow_returnsAllow() {
        pendingResponse = "{\"verdict\":\"allow\",\"reason\":\"safe\"}";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ALLOW, r.verdict);
        assertEquals("safe", r.reason);
        assertEquals(1, capturedHistory.size());
    }

    @Test
    public void evaluate_ask_returnsAsk() {
        pendingResponse = "{\"verdict\":\"ask\",\"reason\":\"needs confirmation\"}";
        CommandJudge.VerdictResult r = call("rm foo.txt");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
        assertEquals("needs confirmation", r.reason);
    }

    @Test
    public void evaluate_deny_returnsDeny() {
        pendingResponse = "{\"verdict\":\"deny\",\"reason\":\"destructive\"}";
        CommandJudge.VerdictResult r = call("dd if=/dev/zero of=/dev/sda");
        assertEquals(CommandJudge.Verdict.DENY, r.verdict);
        assertEquals("destructive", r.reason);
    }

    @Test
    public void evaluate_garbage_failsClosedToAsk() {
        pendingResponse = "this is not json at all";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
        assertTrue(r.reason.toLowerCase().contains("parse"));
    }

    @Test
    public void evaluate_missingVerdict_failsClosedToAsk() {
        pendingResponse = "{\"reason\":\"some reason\"}";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
    }

    @Test
    public void evaluate_unknownVerdict_failsClosedToAsk() {
        pendingResponse = "{\"verdict\":\"maybe\"}";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
    }

    @Test
    public void evaluate_timeout_failsClosedToAsk() {
        // Neither response nor error -> callback never invoked -> future times out.
        pendingResponse = null;
        pendingError = null;
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
        assertTrue(r.reason.toLowerCase().contains("unavailable"));
    }

    @Test
    public void evaluate_error_failsClosedToAsk() {
        pendingError = "network down";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ASK, r.verdict);
        assertTrue(r.reason.toLowerCase().contains("unavailable"));
    }

    @Test
    public void parseVerdict_toleranceFencedJson() {
        pendingResponse = "```json\n{\"verdict\":\"allow\"}\n```";
        CommandJudge.VerdictResult r = call("ls -l");
        assertEquals(CommandJudge.Verdict.ALLOW, r.verdict);
    }

    @Test
    public void judge_seesOnlyCommandContext() {
        pendingResponse = "{\"verdict\":\"allow\"}";
        call("echo secret-payload");

        // doAnswer populated capturedHistory when sendMessage was invoked.
        List<ChatMessage> history = capturedHistory;
        assertNotNull(history);
        assertEquals("system message only", 1, history.size());
        String prompt = history.get(0).getContent();
        assertTrue(prompt.contains("echo secret-payload"));
        assertTrue(prompt.contains("Working directory"));
        assertTrue(prompt.contains("Backend: local"));
        assertTrue(prompt.contains("Workspace root"));
        // Poison resistance: never fed a chat transcript or tool/web content.
        assertFalse(prompt.toLowerCase().contains("previous user message"));
        assertFalse(prompt.toLowerCase().contains("assistant:"));
    }
}
