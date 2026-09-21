package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.workflow.TemplateResolver;
import io.finett.droidclaw.workflow.WorkflowNodeStatus;
import io.finett.droidclaw.workflow.WorkflowRunCallback;
import io.finett.droidclaw.workflow.WorkflowRunResult;
import io.finett.droidclaw.workflow.WorkflowRunner;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AgentLoopWorkflowDispatchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final LlmApiService api = mock(LlmApiService.class);
    private final ToolRegistry tools = mock(ToolRegistry.class);
    private final SettingsManager settings = mock(SettingsManager.class);
    private final AgentLoop.AgentCallback callback = mock(AgentLoop.AgentCallback.class);
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final Executor executor = pending::add;
    private AgentLoop loop;

    @Before public void setUp() {
        when(tools.getToolDefinitions()).thenReturn(new JsonArray());
        when(tools.getAllTools()).thenReturn(Collections.emptyList());
        when(settings.getMaxAgentIterations()).thenReturn(20);
        when(settings.isRequireApproval()).thenReturn(true);
        when(settings.getAgentConfig()).thenReturn(new AgentConfig());
        // Keep this test on the normal non-streaming API callback path.
        settings.getAgentConfig().setStreamResponses(false);
        Tool workflow = mock(Tool.class);
        when(workflow.requiresApproval()).thenReturn(true);
        when(workflow.getApprovalDescription(any())).thenReturn("Run workflow");
        when(tools.getTool("run_workflow")).thenReturn(workflow);
        loop = new AgentLoop(api, tools, settings, null, null, executor);
    }

    private void startWithTools(String... names) {
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);
        ArgumentCaptor<LlmApiService.ChatCallbackWithTools> response =
                ArgumentCaptor.forClass(LlmApiService.ChatCallbackWithTools.class);
        verify(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(), response.capture());
        List<LlmApiService.ToolCall> calls = new ArrayList<>();
        for (String name : names) {
            JsonObject arguments = new JsonObject();
            arguments.addProperty("workflow", "demo");
            calls.add(new LlmApiService.ToolCall("call-" + name, name, arguments));
        }
        response.getValue().onSuccess(new LlmApiService.LlmResponse(null, calls));
    }

    private AgentLoop.ApprovalCallback approval() {
        ArgumentCaptor<AgentLoop.ApprovalCallback> approval =
                ArgumentCaptor.forClass(AgentLoop.ApprovalCallback.class);
        verify(callback).onApprovalRequired(eq("run_workflow"), anyString(), any(), approval.capture());
        return approval.getValue();
    }

    private FutureTask<Void> runQueuedWork() {
        FutureTask<Void> worker = new FutureTask<>(pending.remove(), null);
        new Thread(worker, "workflow-test-worker").start();
        return worker;
    }

    @Test public void approvedWorkflowLeavesMainResponsiveAndContinuesOnMain() throws Exception {
        CountDownLatch nodeRequested = new CountDownLatch(1);
        Handler main = new Handler(Looper.getMainLooper());
        List<String> events = new ArrayList<>();
        WorkflowRunCallback notifications = new WorkflowRunCallback() {
            private void record(String event) {
                assertSame(Looper.getMainLooper(), Looper.myLooper());
                events.add(event);
            }
            public void onNodeStart(String key) { record("start:" + key); }
            public void onNodeComplete(String key, WorkflowNodeStatus status) { record("end:" + status); }
            public void onProgress(String message) { record("progress"); }
            public void onComplete(String output, Map<String, TemplateResolver.NodeResult> results) {
                assertEquals("node output", output);
                assertEquals(WorkflowNodeStatus.OK, results.get("a").getStatus());
                record("complete");
            }
            public void onError(String error) { fail(error); }
        };
        when(tools.executeTool(eq("run_workflow"), any())).thenAnswer(inv -> {
            assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            WorkflowRunResult result = new WorkflowRunner(api, tools, settings, null, null).run(
                    "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}",
                    null, notifications);
            assertTrue(result.getError(), result.isSuccess());
            return ToolResult.success(result.getOutput());
        });
        when(tools.executeTool(eq("ordinary"), any())).thenAnswer(inv -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            return ToolResult.success("ordinary output");
        });
        doAnswer(inv -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            return null;
        }).when(callback).onToolResult(anyString(), anyString());

        startWithTools("run_workflow", "ordinary");
        assertTrue("No work before approval", pending.isEmpty());
        verify(tools, never()).executeTool(anyString(), any());
        approval().onApproved();
        assertEquals(1, pending.size());
        verify(callback, never()).onToolResult(anyString(), anyString());

        // The real runner waits for this main-looper API callback on its worker.
        doAnswer(inv -> {
            LlmApiService.ChatCallbackWithTools response = inv.getArgument(3);
            if (Looper.myLooper() != Looper.getMainLooper()) {
                main.post(() -> response.onSuccess(new LlmApiService.LlmResponse("node output", null)));
                nodeRequested.countDown();
            } else {
                response.onSuccess(new LlmApiService.LlmResponse("chat complete", null));
            }
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
        FutureTask<Void> worker = runQueuedWork();
        try {
            assertTrue(nodeRequested.await(5, TimeUnit.SECONDS));
            assertFalse("Worker waits without blocking the UI", worker.isDone());
            boolean[] uiResponsive = {false};
            main.post(() -> uiResponsive[0] = true);
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(uiResponsive[0]);
            worker.get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
        } finally {
            worker.cancel(true);
        }
        verify(callback).onToolResult("run_workflow", "node output");
        verify(callback).onToolResult("ordinary", "ordinary output");
        ArgumentCaptor<List<ChatMessage>> history = ArgumentCaptor.forClass(List.class);
        verify(callback).onComplete(eq("chat complete"), history.capture());
        List<ChatMessage> toolResults = new ArrayList<>();
        for (ChatMessage message : history.getValue()) {
            if (message.getType() == ChatMessage.TYPE_TOOL_RESULT) toolResults.add(message);
        }
        assertEquals(2, toolResults.size());
        assertEquals("call-run_workflow", toolResults.get(0).getToolCallId());
        assertEquals("node output", toolResults.get(0).getContent());
        assertEquals("call-ordinary", toolResults.get(1).getToolCallId());
        assertTrue(events.containsAll(Arrays.asList("start:a", "end:OK", "complete", "progress")));
        assertTrue(events.indexOf("start:a") < events.indexOf("end:OK"));
        assertTrue(events.indexOf("end:OK") < events.indexOf("complete"));
        assertTrue(pending.isEmpty());
    }

    @Test public void deniedWorkflowNeverReachesExecutor() {
        startWithTools("run_workflow");
        approval().onDenied();
        assertTrue(pending.isEmpty());
        verify(tools, never()).executeTool(anyString(), any());
        verify(callback).onToolResult("run_workflow", "Tool execution denied by user");
    }

    @Test public void ordinaryToolKeepsInlineExecution() {
        when(tools.executeTool(eq("ordinary"), any())).thenReturn(ToolResult.success("ok"));
        startWithTools("ordinary");
        verify(tools).executeTool(eq("ordinary"), any());
        verify(callback).onToolResult("ordinary", "ok");
        assertTrue(pending.isEmpty());
    }

    @Test public void workflowFailureIsDeliveredOnMainBeforeContinuation() throws Exception {
        when(tools.executeTool(eq("run_workflow"), any())).thenReturn(ToolResult.error("failed"));
        startWithTools("run_workflow");
        approval().onApproved();
        FutureTask<Void> worker = runQueuedWork();
        worker.get(5, TimeUnit.SECONDS);
        verify(callback, never()).onToolResult(anyString(), anyString());
        doAnswer(inv -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            return null;
        }).when(callback).onToolResult(anyString(), anyString());
        shadowOf(Looper.getMainLooper()).idle();
        verify(callback).onToolResult("run_workflow", "Error: failed");
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void workerExceptionBecomesToolErrorOnMain() throws Exception {
        when(tools.executeTool(eq("run_workflow"), any())).thenThrow(new IllegalStateException("broken"));
        startWithTools("run_workflow");
        approval().onApproved();
        runQueuedWork().get(5, TimeUnit.SECONDS);
        verify(callback, never()).onToolResult(anyString(), anyString());
        shadowOf(Looper.getMainLooper()).idle();
        verify(callback).onToolResult("run_workflow", "Error: Tool execution failed: broken");
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void rejectedDispatchBecomesToolErrorOnMain() {
        loop = new AgentLoop(api, tools, settings, null, null, work -> {
            throw new java.util.concurrent.RejectedExecutionException("closed");
        });
        startWithTools("run_workflow");
        approval().onApproved();
        verify(callback, never()).onToolResult(anyString(), anyString());
        shadowOf(Looper.getMainLooper()).idle();
        verify(callback).onToolResult("run_workflow", "Error: Workflow execution unavailable: closed");
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
        verify(tools, never()).executeTool(anyString(), any());
    }

    @Test public void cancelledQueuedWorkflowDoesNotStart() throws Exception {
        startWithTools("run_workflow");
        approval().onApproved();
        loop.cancel();
        runQueuedWork().get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        verify(tools, never()).executeTool(anyString(), any());
        verify(callback, never()).onToolResult(anyString(), anyString());
    }

    @Test public void realWorkflowToolRunsAfterApprovalWithoutBlockingMain() throws Exception {
        java.io.File root = temporary.newFolder();
        java.io.File directory = new java.io.File(root, ".agent/workflows");
        assertTrue(directory.mkdirs());
        java.nio.file.Files.write(new java.io.File(directory, "demo.json").toPath(),
                ("{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(tools.getWorkspaceRoot()).thenReturn(root);
        when(settings.getDefaultModel()).thenReturn("provider/global");
        // Resources are not packaged in JVM tests. Only presentation strings are
        // mocked; the actual workflow tool, approval route and runner stay real.
        android.content.Context context = mock(android.content.Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getString(anyInt())).thenReturn("Workflow review");
        when(context.getString(anyInt(), any(Object[].class))).thenReturn("Workflow review");
        io.finett.droidclaw.tool.impl.RunWorkflowTool workflow =
                new io.finett.droidclaw.tool.impl.RunWorkflowTool(context, api, tools, settings);
        when(tools.getTool("run_workflow")).thenReturn(workflow);
        when(tools.getAllTools()).thenReturn(Collections.singletonList(workflow));
        startWithTools("run_workflow");
        assertTrue(pending.isEmpty());
        approval().onApproved();
        assertEquals(1, pending.size());

        CountDownLatch nodeRequested = new CountDownLatch(1);
        Handler main = new Handler(Looper.getMainLooper());
        doAnswer(inv -> {
            LlmApiService.ChatCallbackWithTools response = inv.getArgument(3);
            if (Looper.myLooper() != Looper.getMainLooper()) {
                main.post(() -> response.onSuccess(new LlmApiService.LlmResponse("real node output", null)));
                nodeRequested.countDown();
            } else {
                response.onSuccess(new LlmApiService.LlmResponse("chat complete", null));
            }
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
        doAnswer(inv -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            return null;
        }).when(callback).onToolResult(anyString(), anyString());
        FutureTask<Void> worker = runQueuedWork();
        try {
            assertTrue("Real workflow reaches its node", nodeRequested.await(5, TimeUnit.SECONDS));
            assertFalse(worker.isDone());
            boolean[] uiResponsive = {false};
            main.post(() -> uiResponsive[0] = true);
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(uiResponsive[0]);
            worker.get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
        } finally {
            worker.cancel(true);
        }
        // Execution must use the approved capability, never direct registry execution.
        verify(tools, never()).executeTool(eq("run_workflow"), any());
        verify(callback).onToolResult(eq("run_workflow"), contains("real node output"));
        verify(callback).onComplete(eq("chat complete"), anyList());
        verify(api, times(3)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void cancellationBeforePostedResultSuppressesContinuation() throws Exception {
        when(tools.executeTool(eq("run_workflow"), any())).thenReturn(ToolResult.success("done"));
        startWithTools("run_workflow");
        approval().onApproved();
        runQueuedWork().get(5, TimeUnit.SECONDS);
        loop.cancel();
        shadowOf(Looper.getMainLooper()).idle();
        verify(callback, never()).onToolResult(anyString(), anyString());
        verify(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void workflowValidationErrorNotificationIsPostedToMain() throws Exception {
        WorkflowRunCallback notifications = mock(WorkflowRunCallback.class);
        doAnswer(inv -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            return null;
        }).when(notifications).onError(anyString());
        FutureTask<WorkflowRunResult> worker = new FutureTask<>(() ->
                new WorkflowRunner(api, tools, settings, null, null).run("{}", null, notifications));
        new Thread(worker, "workflow-validation-worker").start();
        assertFalse(worker.get(5, TimeUnit.SECONDS).isSuccess());
        verifyNoInteractions(notifications);
        shadowOf(Looper.getMainLooper()).idle();
        verify(notifications).onError(contains("Workflow validation failed"));
    }
}
