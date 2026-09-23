package io.finett.droidclaw.workflow;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(RobolectricTestRunner.class)
public class WorkflowRunnerExecutionTest {
    @Mock LlmApiService api;
    @Mock ToolRegistry tools;
    @Mock SettingsManager settings;
    @Mock AgentConfig config;

    private WorkflowRunner runner;

    @Before public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tools.getToolDefinitions()).thenReturn(new JsonArray());
        when(tools.getAllTools()).thenReturn(new ArrayList<>());
        when(settings.getMaxAgentIterations()).thenReturn(20);
        when(settings.isRequireApproval()).thenReturn(true);
        when(settings.getAgentConfig()).thenReturn(config);
        when(config.isStreamResponses()).thenReturn(false);
        runner = new WorkflowRunner(api, tools, settings, null, null);
    }

    private void replySequence(String... replies) {
        final int[] n = {0};
        doAnswer(inv -> {
            LlmApiService.ChatCallbackWithTools cb = inv.getArgument(4);
            String reply = replies[Math.min(n[0]++, replies.length - 1)];
            if (reply.startsWith("ERROR:")) cb.onError(reply.substring(6));
            else cb.onSuccess(new LlmApiService.LlmResponse(reply, null));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void retryUsesProductionPathAndEventuallySucceeds() {
        replySequence("ERROR:network unavailable", "recovered");
        String json = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"retry\":{\"max_attempts\":2,\"backoff_ms\":10}},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}";
        List<String> progress = new ArrayList<>();
        WorkflowRunResult result = runner.run(json, null, new RecordingCallback(progress));
        assertTrue(result.getError(), result.isSuccess());
        assertEquals("recovered", result.getOutput());
        assertTrue(progress.toString(), progress.toString().contains("Retrying 'a' in 10ms"));
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void falseGuardSkipsNodeAndItsDependents() {
        replySequence("no", "independent");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{c.output}}\",\"agents\":{" +
                "\"a\":{\"prompt\":{\"text\":\"a\"}}," +
                "\"b\":{\"prompt\":{\"text\":\"b\"},\"depends_on\":[\"a\"],\"when\":\"{{a.output}} == 'yes'\"}," +
                "\"d\":{\"prompt\":{\"text\":\"d\"},\"depends_on\":[\"b\"]}," +
                "\"c\":{\"prompt\":{\"text\":\"c\"}}}}";
        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        assertEquals(WorkflowNodeStatus.SKIPPED, result.getNodeResults().get("b").getStatus());
        assertEquals(WorkflowNodeStatus.SKIPPED, result.getNodeResults().get("d").getStatus());
        assertEquals("independent", result.getOutput());
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void skipPolicyReportsSkippedAndContinuesIndependentBranch() {
        replySequence("ERROR:network unavailable", "ok-c");
        List<String> completions = new ArrayList<>();
        WorkflowRunCallback callback = new WorkflowRunCallback() {
            public void onNodeStart(String key) {}
            public void onNodeComplete(String key, WorkflowNodeStatus status) { completions.add(key + ":" + status); }
            public void onProgress(String message) {}
            public void onComplete(String output, java.util.Map<String, TemplateResolver.NodeResult> results) {}
            public void onError(String error) {}
        };
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{c.output}}\",\"agents\":{" +
                "\"a\":{\"prompt\":{\"text\":\"a\"},\"on_error\":\"skip\"}," +
                "\"b\":{\"prompt\":{\"text\":\"b\"},\"depends_on\":[\"a\"]}," +
                "\"c\":{\"prompt\":{\"text\":\"c\"}}}}";
        WorkflowRunResult result = runner.run(json, null, callback);
        assertTrue(result.getError(), result.isSuccess());
        assertEquals(WorkflowNodeStatus.SKIPPED, result.getNodeResults().get("a").getStatus());
        assertEquals(WorkflowNodeStatus.SKIPPED, result.getNodeResults().get("b").getStatus());
        assertTrue(completions.contains("a:SKIPPED"));
        assertFalse(completions.contains("a:ERROR"));
        assertEquals("ok-c", result.getOutput());
    }

    @Test public void nodeTimeoutCancelsAndStopsRetriesAtNodeDeadline() {
        doNothing().when(api).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        String json = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"timeout_ms\":1000,"
                + "\"retry\":{\"max_attempts\":5,\"backoff_ms\":10}},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}";
        long start = System.nanoTime();
        WorkflowRunResult result = runner.run(json, null, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertFalse(result.isSuccess());
        assertTrue(result.getError(), result.getError().contains("timed out"));
        assertTrue("elapsed=" + elapsedMs, elapsedMs < 1500);
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }
    @Test public void failPolicyStopsWorkflowImmediately() {
        replySequence("ERROR:network unavailable");
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{" +
                "\"a\":{\"prompt\":{\"text\":\"a\"}}," +
                "\"b\":{\"prompt\":{\"text\":\"b\"}}}}";
        WorkflowRunResult result = runner.run(json, null, null);
        assertFalse(result.isSuccess());
        assertEquals(WorkflowNodeStatus.ERROR, result.getNodeResults().get("a").getStatus());
        assertNull(result.getNodeResults().get("b"));
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void continuePolicyRunsDependentAfterError() {
        replySequence("ERROR:network unavailable", "continued");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{b.output}}\",\"agents\":{" +
                "\"a\":{\"prompt\":{\"text\":\"a\"},\"on_error\":\"continue\"}," +
                "\"b\":{\"prompt\":{\"template\":\"status={{a.status}}\"}}}}";
        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        assertEquals(WorkflowNodeStatus.ERROR, result.getNodeResults().get("a").getStatus());
        assertEquals("continued", result.getOutput());
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    private static class RecordingCallback implements WorkflowRunCallback {
        private final List<String> progress;
        RecordingCallback(List<String> progress) { this.progress = progress; }
        public void onNodeStart(String key) {}
        public void onNodeComplete(String key, WorkflowNodeStatus status) {}
        public void onProgress(String message) { progress.add(message); }
        public void onComplete(String output, java.util.Map<String, TemplateResolver.NodeResult> results) {}
        public void onError(String error) {}
    }

}
