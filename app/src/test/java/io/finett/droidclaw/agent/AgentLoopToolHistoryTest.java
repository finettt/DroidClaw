package io.finett.droidclaw.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.tool.impl.FileReadTool;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Verifies the canonical history now carries tool activity: one TYPE_TOOL_CALL per
 * executed call and one TYPE_TOOL_RESULT per completed call, in order around the
 * assistant segments, plus inline TYPE_SYSTEM notices for context-limit events.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentLoopToolHistoryTest {

    private Context context;
    private SettingsManager settings;
    private ToolRegistry registry;
    private LlmApiService fakeLlm;

    /** Real temp dir: Robolectric's in-memory FS does not support mkdirs. */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File workspaceRoot;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        settings = new SettingsManager(context);
        settings.clear();

        workspaceRoot = tempFolder.newFolder("tool-history-workspace");
        File testFile = new File(workspaceRoot, "test.txt");
        java.nio.file.Files.write(testFile.toPath(), "hello from disk".getBytes(StandardCharsets.UTF_8));

        AgentConfig config = settings.getAgentConfig();
        config.setShellAccess(false);
        config.setSandboxMode("relaxed");
        config.setRequireApproval(false);
        config.setStreamResponses(false);
        config.setBackgroundExecEnabled(true);
        settings.setAgentConfig(config);

        registry = new ToolRegistry(context, settings) {
            @Override
            public ToolResult executeTool(String toolName, JsonObject arguments) {
                if ("read_file".equals(toolName)) {
                    return new FileReadTool(new VirtualFileSystem(
                            new PathValidator(workspaceRoot))).execute(arguments);
                }
                return super.executeTool(toolName, arguments);
            }
        };

        fakeLlm = new LlmApiService(settings) {
            private int calls = 0;

            @Override
            public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                             List<ChatMessage> identityMessages, RequestScope scope,
                                             ChatCallbackWithTools callback) {
                if (calls++ == 0) {
                    JsonObject args = new JsonObject();
                    args.addProperty("path", "test.txt");
                    callback.onSuccess(new LlmApiService.LlmResponse(null,
                            Collections.singletonList(
                                    new LlmApiService.ToolCall("call_1", "read_file", args))));
                } else {
                    callback.onSuccess(new LlmApiService.LlmResponse("Done.", null));
                }
            }
        };
    }

    @Test
    public void toolRun_historyCarriesToolCallAndResultInOrder() {
        AgentLoop loop = new AgentLoop(fakeLlm, registry, settings, null, null, Runnable::run);
        loop.setMaxIterations(20);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("read it", ChatMessage.TYPE_USER)), callback);

        assertNotNull("the run must complete", callback.completion);
        List<ChatMessage> history = callback.history;

        // user → tool_call → tool_result → assistant
        assertEquals(ChatMessage.TYPE_USER, history.get(0).getType());
        assertEquals(ChatMessage.TYPE_TOOL_CALL, history.get(1).getType());
        assertEquals(ChatMessage.TYPE_TOOL_RESULT, history.get(2).getType());
        assertEquals(ChatMessage.TYPE_ASSISTANT, history.get(history.size() - 1).getType());

        ChatMessage toolCall = history.get(1);
        assertEquals("read_file", toolCall.getToolCalls().get(0).getName());
        assertEquals("call_1", toolCall.getToolCalls().get(0).getId());
        assertFalse("canonical cards are never left in a running state", toolCall.isRunning());

        ChatMessage toolResult = history.get(2);
        assertEquals("read_file", toolResult.getToolName());
        assertEquals("call_1", toolResult.getToolCallId());
        assertTrue(toolResult.getContent().contains("hello from disk"));
    }

    @Test
    public void backgroundedTool_historyHasNoDanglingRunningEntry() {
        // A background:true shell dispatch returns a process_id immediately; the
        // canonical entry must be a plain done card, not a spinner that never resolves.
        AgentConfig config = settings.getAgentConfig();
        config.setShellAccess(true);
        settings.setAgentConfig(config);
        ToolRegistry shellRegistry = new ToolRegistry(context, settings);

        LlmApiService bgLlm = new LlmApiService(settings) {
            private int calls = 0;

            @Override
            public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                             List<ChatMessage> identityMessages, RequestScope scope,
                                             ChatCallbackWithTools callback) {
                if (calls++ == 0) {
                    JsonObject args = new JsonObject();
                    args.addProperty("command", "echo bg");
                    args.addProperty("background", true);
                    callback.onSuccess(new LlmApiService.LlmResponse(null,
                            Collections.singletonList(
                                    new LlmApiService.ToolCall("call_bg", "execute_shell", args))));
                } else {
                    callback.onSuccess(new LlmApiService.LlmResponse("ok", null));
                }
            }
        };

        AgentLoop loop = new AgentLoop(bgLlm, shellRegistry, settings, null, null, Runnable::run);
        loop.setMaxIterations(20);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertNotNull("the run must complete", callback.completion);
        List<ChatMessage> history = callback.history;

        int toolCallIndex = -1;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getType() == ChatMessage.TYPE_TOOL_CALL) {
                toolCallIndex = i;
                break;
            }
        }
        assertTrue("history must contain the tool-call entry", toolCallIndex >= 0);
        ChatMessage toolCall = history.get(toolCallIndex);
        assertFalse("a backgrounded tool must not leave a dangling running card",
                toolCall.isRunning());
        assertNull("no failure marker on a dispatched background tool", toolCall.getContent());

        // The result is the immediate dispatch acknowledgement.
        ChatMessage toolResult = history.get(toolCallIndex + 1);
        assertEquals(ChatMessage.TYPE_TOOL_RESULT, toolResult.getType());
        assertTrue(toolResult.getContent().contains("process_id"));
    }

    /** Records the final history delivered to onComplete. */
    private static final class RecordingCallback implements AgentLoop.AgentCallback {
        String completion;
        List<ChatMessage> history;
        String error;

        @Override public void onToolCall(String name, String arguments) {}
        @Override public void onToolResult(String name, String result) {}

        @Override
        public void onComplete(String finalResponse, List<ChatMessage> conversationHistory) {
            completion = finalResponse;
            history = conversationHistory;
        }

        @Override
        public void onError(String errorMessage) {
            error = errorMessage;
        }

        @Override
        public void onApprovalRequired(String toolName, String description, JsonObject arguments,
                                       AgentLoop.ApprovalCallback approvalCallback) {
            throw new AssertionError("approval must not be requested in this test setup");
        }
    }

}
