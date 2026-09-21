package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.workflow.WorkflowRunner;

@RunWith(RobolectricTestRunner.class)
public class RunWorkflowApprovalTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LlmApiService api;
    private ToolRegistry registry;
    private SettingsManager settings;
    private AgentConfig config;
    private RunWorkflowTool tool;
    private File workflowFile;
    private JsonObject args;
    private static final String JSON = "{\"version\":1,\"goal\":\"Reviewed goal\","
            + "\"agents\":{\"researcher\":{\"prompt\":{\"text\":\"hello\"}}}}";

    @Before public void setUp() throws Exception {
        api = mock(LlmApiService.class);
        registry = mock(ToolRegistry.class);
        settings = mock(SettingsManager.class);
        config = new AgentConfig();
        config.setStreamResponses(false);
        when(settings.getAgentConfig()).thenReturn(config);
        when(settings.getMaxAgentIterations()).thenReturn(20);
        when(settings.getDefaultModel()).thenReturn("provider/global");
        when(registry.getToolDefinitions()).thenReturn(new JsonArray());
        File root = temporary.newFolder();
        File directory = new File(root, ".agent/workflows");
        assertTrue(directory.mkdirs());
        workflowFile = new File(directory, "example.json");
        write(JSON);
        when(registry.getWorkspaceRoot()).thenReturn(root);
        tool = new RunWorkflowTool(resourceContext("values"), api, registry, settings);
        when(registry.getTool("run_workflow")).thenReturn(tool);
        when(registry.getAllTools()).thenReturn(Collections.singletonList(tool));
        args = new JsonObject();
        args.addProperty("workflow", "example");
    }

    // This project does not package Android resources in JVM tests. Read the actual
    // resource templates, while leaving workflow parsing and approval code real.
    private android.content.Context resourceContext(String locale) throws Exception {
        android.content.Context context = mock(android.content.Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        org.w3c.dom.NodeList strings = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new File("src/main/res/" + locale + "/strings.xml"))
                .getElementsByTagName("string");
        java.util.Map<Integer, String> templates = new java.util.HashMap<>();
        for (int i = 0; i < strings.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) strings.item(i);
            String name = element.getAttribute("name");
            if (name.startsWith("workflow_review_")) {
                int id = io.finett.droidclaw.R.string.class.getField(name).getInt(null);
                templates.put(id, element.getTextContent().replace("\\n", "\n"));
            }
        }
        when(context.getString(anyInt())).thenAnswer(inv -> templates.get(inv.getArgument(0)));
        when(context.getString(anyInt(), any(Object[].class))).thenAnswer(inv -> {
            Object[] values = Arrays.copyOfRange(inv.getArguments(), 1, inv.getArguments().length);
            return String.format(java.util.Locale.ROOT, templates.get(inv.getArgument(0)), values);
        });
        return context;
    }

    private void write(String json) throws Exception {
        Files.write(workflowFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private AtomicInteger repliesWithTool(String name, JsonObject arguments) {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            LlmApiService.ChatCallbackWithTools callback = inv.getArgument(3);
            if (calls.getAndIncrement() == 0) {
                callback.onSuccess(new LlmApiService.LlmResponse(null, Collections.singletonList(
                        new LlmApiService.ToolCall("call", name, arguments))));
            } else callback.onSuccess(new LlmApiService.LlmResponse("finished", null));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        return calls;
    }

    private AgentLoop.AgentCallback launch(boolean required, ToolApprovalMode mode, boolean background) {
        when(settings.isRequireApproval()).thenReturn(required);
        config.setBackgroundExecEnabled(background);
        config.setToolApprovalOverrides(Collections.singletonMap("run_workflow", mode.name()));
        args.addProperty("background", background);
        repliesWithTool("run_workflow", args);
        AgentLoop.AgentCallback callback = mock(AgentLoop.AgentCallback.class);
        new AgentLoop(api, registry, settings, null, null, Runnable::run).start(new ArrayList<>(Collections.singletonList(
                new ChatMessage("run", ChatMessage.TYPE_USER))), callback);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return callback;
    }

    @Test public void globalApprovalDisabledStillPrompts() { assertPrompts(false, ToolApprovalMode.DEFAULT, false); }
    @Test public void alwaysApproveStillPrompts() { assertPrompts(true, ToolApprovalMode.ALWAYS_APPROVE, false); }
    @Test public void backgroundStillPrompts() { assertPrompts(true, ToolApprovalMode.DEFAULT, true); }
    @Test public void allBypassesCombinedStillPrompt() { assertPrompts(false, ToolApprovalMode.ALWAYS_APPROVE, true); }

    private void assertPrompts(boolean required, ToolApprovalMode mode, boolean background) {
        AgentLoop.AgentCallback callback = launch(required, mode, background);
        verify(callback).onApprovalRequired(eq("run_workflow"), contains("SHA-256:"), eq(args), any());
        verify(registry, never()).executeTool(anyString(), any());
        verify(registry, never()).getContext();
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void alwaysRejectWinsOverMandatoryApprovalAndBackground() {
        AgentLoop.AgentCallback callback = launch(false, ToolApprovalMode.ALWAYS_REJECT, true);
        verify(callback, never()).onApprovalRequired(anyString(), anyString(), any(), any());
        verify(callback).onToolResult(eq("run_workflow"), contains("blocked"));
        verify(registry, never()).executeTool(anyString(), any());
        verify(registry, never()).getContext();
    }

    @Test public void denialCannotLaterBeApproved() {
        AgentLoop.AgentCallback callback = launch(false, ToolApprovalMode.ALWAYS_APPROVE, true);
        ArgumentCaptor<AgentLoop.ApprovalCallback> approval = ArgumentCaptor.forClass(AgentLoop.ApprovalCallback.class);
        verify(callback).onApprovalRequired(anyString(), anyString(), any(), approval.capture());
        approval.getValue().onDenied();
        approval.getValue().onApproved();
        verify(callback, times(1)).onToolResult(eq("run_workflow"), contains("denied"));
        verify(registry, never()).executeTool(anyString(), any());
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void unchangedApprovedWorkflowRunsExactlyOnceThroughRealRunner() {
        AgentLoop.AgentCallback callback = launch(false, ToolApprovalMode.ALWAYS_APPROVE, false);
        ArgumentCaptor<AgentLoop.ApprovalCallback> approval = ArgumentCaptor.forClass(AgentLoop.ApprovalCallback.class);
        verify(callback).onApprovalRequired(anyString(), anyString(), any(), approval.capture());
        approval.getValue().onApproved();
        approval.getValue().onApproved();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        verify(callback, times(1)).onToolResult(eq("run_workflow"), contains("success"));
        verify(api, times(3)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void changedBytesAfterDialogFailBeforeRunner() throws Exception {
        AgentLoop.AgentCallback callback = launch(false, ToolApprovalMode.DEFAULT, false);
        ArgumentCaptor<AgentLoop.ApprovalCallback> approval = ArgumentCaptor.forClass(AgentLoop.ApprovalCallback.class);
        verify(callback).onApprovalRequired(anyString(), anyString(), any(), approval.capture());
        write(JSON.replace("hello", "changed"));
        approval.getValue().onApproved();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        verify(callback).onToolResult(eq("run_workflow"), contains("changed after approval"));
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void llmHashAndDescriptionCannotAuthorizeDirectExecution() {
        args.addProperty("_approved_workflow_sha256", RunWorkflowTool.sha256(JSON.getBytes(StandardCharsets.UTF_8)));
        tool.getApprovalDescription(args);
        assertFalse(tool.execute(args).isSuccess());
        verifyNoInteractions(api);
    }

    @Test public void reviewFreezesArgumentsAndIsSingleUse() throws Exception {
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        args.addProperty("workflow", "different");
        doAnswer(inv -> {
            ((LlmApiService.ChatCallbackWithTools) inv.getArgument(3)).onSuccess(
                    new LlmApiService.LlmResponse("ok", null));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
        assertTrue(review.execute().isSuccess());
        assertFalse(review.execute().isSuccess());
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), any(), any());
    }

    @Test public void parsedSummaryResolvesDefaultsDenialsModelsAndWarning() throws Exception {
        Tool read = mock(Tool.class);
        Tool write = mock(Tool.class);
        when(read.getName()).thenReturn("read_file");
        when(write.getName()).thenReturn("write_file");
        when(registry.getAllTools()).thenReturn(Arrays.asList(read, write, tool));
        when(registry.hasToolWithName(anyString())).thenReturn(true);
        write("{\"version\":1,\"goal\":\"Inspect code\",\"defaults\":{\"model\":\"p/default\","
                + "\"allowed_tools\":[\"read_file\",\"write_file\",\"run_workflow\"],\"denied_tools\":[\"write_file\"]},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"},\"approval\":\"auto_approve\"},"
                + "\"b\":{\"prompt\":{\"text\":\"go\"},\"model\":\"p/node\",\"approval\":\"inherit\"}}}");
        String summary = tool.prepareApproval(args).getDescription();
        assertTrue(summary, summary.contains("Goal: Inspect code"));
        assertTrue(summary, summary.contains("Agents: 2"));
        assertTrue(summary, summary.contains("Agent: a"));
        assertTrue(summary, summary.contains("Agent: b"));
        assertTrue(summary, summary.contains("Requested model: p/default"));
        assertTrue(summary, summary.contains("Requested model: p/node"));
        assertTrue(summary, summary.contains("Execution model: provider/global"));
        assertTrue(summary, summary.contains("Node-specific model routing is not supported"));
        assertTrue(summary, summary.contains("Effective allowed tools: [read_file]"));
        assertTrue(summary, summary.contains("Denied/excluded tools: [run_workflow, write_file]"));
        assertTrue(summary, summary.contains("inherit (effective: deny_writes)"));
        assertTrue(summary, summary.contains("WARNING: auto_approve"));
        assertTrue(summary, summary.contains("SHA-256: " + RunWorkflowTool.sha256(Files.readAllBytes(workflowFile.toPath()))));
    }

    @Test public void inheritRejectsWritesWithPermissiveGlobalsAndBackground() {
        Tool write = mock(Tool.class);
        when(write.getName()).thenReturn("write_file");
        when(write.requiresApproval()).thenReturn(true);
        when(registry.getAllTools()).thenReturn(Collections.singletonList(write));
        when(registry.getTool("write_file")).thenReturn(write);
        when(registry.hasToolWithName("write_file")).thenReturn(true);
        when(settings.isRequireApproval()).thenReturn(false);
        config.setBackgroundExecEnabled(true);
        config.setToolApprovalOverrides(Collections.singletonMap("write_file", "ALWAYS_APPROVE"));
        JsonObject writeArgs = new JsonObject();
        writeArgs.addProperty("background", true);
        repliesWithTool("write_file", writeArgs);
        String json = JSON.replace("\"prompt\":", "\"approval\":\"inherit\",\"prompt\":");
        assertTrue(new WorkflowRunner(api, registry, settings, null, null).run(json, null, null).isSuccess());
        verify(registry, never()).executeTool(anyString(), any());
        verify(registry, never()).getContext();
    }

    @Test public void summaryDeniesWriteToolsUnderInheritAndAllToolsUnderStrict() throws Exception {
        Tool read = mock(Tool.class);
        Tool write = mock(Tool.class);
        when(read.getName()).thenReturn("read_file");
        when(write.getName()).thenReturn("write_file");
        when(write.requiresApproval()).thenReturn(true);
        when(registry.getAllTools()).thenReturn(Arrays.asList(read, write, tool));
        when(registry.hasToolWithName(anyString())).thenReturn(true);
        for (String policy : Arrays.asList("inherit", "deny_writes", "strict")) {
            write(JSON.replace("\"prompt\":", "\"approval\":\"" + policy + "\",\"prompt\":"));
            String summary = tool.prepareApproval(args).getDescription();
            String allowed = policy.equals("strict") ? "[]" : "[read_file]";
            assertTrue(summary, summary.contains("Effective allowed tools: " + allowed));
            assertTrue(summary, summary.contains("write_file]"));
            assertFalse(summary, summary.contains("WARNING"));
        }
    }

    @Test public void changedGlobalPolicyAfterReviewFailsClosed() throws Exception {
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        config.setToolApprovalOverrides(Collections.singletonMap("write_file", "ALWAYS_APPROVE"));
        assertTrue(review.execute().getError().contains("execution policy changed"));
        verifyNoInteractions(api);
    }

    @Test public void changedGlobalModelAfterReviewFailsClosed() throws Exception {
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        when(settings.getDefaultModel()).thenReturn("other/model");
        assertTrue(review.execute().getError().contains("execution policy changed"));
        verifyNoInteractions(api);
    }

    @Test public void changedProviderDestinationAfterReviewFailsClosed() throws Exception {
        when(settings.getApiUrl()).thenReturn("https://reviewed.example/chat/completions");
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        when(settings.getApiUrl()).thenReturn("https://different.example/chat/completions");
        assertTrue(review.execute().getError().contains("execution policy changed"));
        verifyNoInteractions(api);
    }

    @Test public void changedRegistryAfterReviewFailsClosed() throws Exception {
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        Tool read = mock(Tool.class);
        when(read.getName()).thenReturn("read_file");
        when(registry.getAllTools()).thenReturn(Arrays.asList(read, tool));
        assertTrue(review.execute().getError().contains("execution policy changed"));
        verifyNoInteractions(api);
    }

    @Test public void summaryHonorsInheritedGlobalReadRejections() throws Exception {
        Tool read = mock(Tool.class);
        when(read.getName()).thenReturn("read_file");
        when(registry.getAllTools()).thenReturn(Arrays.asList(read, tool));
        config.setToolApprovalOverrides(Collections.singletonMap("read_file", "ALWAYS_REJECT"));
        String summary = tool.prepareApproval(args).getDescription();
        assertTrue(summary, summary.contains("Effective allowed tools: []"));
        assertTrue(summary, summary.contains("Denied/excluded tools: [read_file, run_workflow]"));
    }

    @Test public void summaryAndRevalidationRespectLiveShellGate() throws Exception {
        Tool shell = mock(Tool.class);
        when(shell.getName()).thenReturn("execute_shell");
        when(shell.requiresApproval()).thenReturn(true);
        when(registry.getAllTools()).thenReturn(Arrays.asList(shell, tool));
        when(registry.requiresShellAccess("execute_shell")).thenReturn(true);
        when(registry.isShellAccessEnabled()).thenReturn(false);
        write(JSON.replace("\"prompt\":", "\"approval\":\"auto_approve\",\"prompt\":"));
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        assertTrue(review.getDescription(), review.getDescription().contains("Effective allowed tools: []"));
        assertTrue(review.getDescription(), review.getDescription().contains("Denied/excluded tools: [execute_shell, run_workflow]"));
        when(registry.isShellAccessEnabled()).thenReturn(true);
        assertTrue(review.execute().getError().contains("execution policy changed"));
        verifyNoInteractions(api);
    }

    @Test public void russianReviewUsesLocalizedTemplates() throws Exception {
        tool = new RunWorkflowTool(resourceContext("values-ru"), api, registry, settings);
        write(JSON.replace("\"prompt\":", "\"approval\":\"auto_approve\",\"prompt\":"));
        String summary = tool.prepareApproval(args).getDescription();
        assertTrue(summary, summary.contains("Цель: Reviewed goal"));
        assertTrue(summary, summary.contains("Агентов: 1"));
        assertTrue(summary, summary.contains("ВНИМАНИЕ: auto_approve"));
    }

    @Test public void invalidWorkflowCannotOpenApprovalDialog() throws Exception {
        write("{\"version\":1}");
        AgentLoop.AgentCallback callback = launch(false, ToolApprovalMode.ALWAYS_APPROVE, true);
        verify(callback, never()).onApprovalRequired(anyString(), anyString(), any(), any());
        verify(callback).onToolResult(eq("run_workflow"), contains("Cannot safely review workflow"));
        verify(registry, never()).executeTool(anyString(), any());
    }

    @Test public void unchangedReviewDoesNotAuthorizeSeparateCall() throws Exception {
        tool.prepareApproval(args);
        JsonObject forged = args.deepCopy();
        forged.addProperty("_approved_workflow_sha256", RunWorkflowTool.sha256(Files.readAllBytes(workflowFile.toPath())));
        assertFalse(tool.execute(forged).isSuccess());
        assertFalse(tool.execute(args).isSuccess());
        verifyNoInteractions(api);
    }

    @Test public void unsafePathsAndOversizedFilesCannotBeReviewed() throws Exception {
        for (String name : Arrays.asList("../example", "/example", "a/b", "a\\b")) {
            args.addProperty("workflow", name);
            try {
                tool.prepareApproval(args);
                fail("Accepted unsafe workflow name " + name);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Invalid workflow name"));
            }
        }
        args.addProperty("workflow", "example");
        Files.write(workflowFile.toPath(), new byte[io.finett.droidclaw.workflow.Workflow.MAX_FILE_BYTES + 1]);
        try {
            tool.prepareApproval(args);
            fail("Accepted oversized file");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("byte cap"));
        }
    }

    @Test public void symlinkReplacementAfterReviewIsRejected() throws Exception {
        RunWorkflowTool.ApprovalReview review = tool.prepareApproval(args);
        File other = temporary.newFile();
        Files.write(other.toPath(), JSON.getBytes(StandardCharsets.UTF_8));
        Files.delete(workflowFile.toPath());
        Files.createSymbolicLink(workflowFile.toPath(), other.toPath());
        assertFalse(review.execute().isSuccess());
        verifyNoInteractions(api);
    }
}
