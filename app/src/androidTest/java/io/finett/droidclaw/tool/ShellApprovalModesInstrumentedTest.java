package io.finett.droidclaw.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.Flaky;
import io.finett.droidclaw.util.FlakyTestRule;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Instrumented (device) tests for the three shell-approval modes, driven end to
 * end through {@link AgentLoop} so the whole path is exercised: the fake LLM,
 * the {@code auto} command judge, the approval dialog gate, real {@code ShellTool}
 * execution, and the append-only audit log.
 *
 * <ul>
 *   <li>{@code ask_all} opens an approval dialog before {@code echo hello}.</li>
 *   <li>{@code auto} runs {@code echo hello} silently (no dialog) yet refuses a
 *       destructive command with a reason, also without a dialog.</li>
 *   <li>{@code full_access} runs silently and still records an audit line.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class ShellApprovalModesInstrumentedTest {

    private Context context;
    private SettingsManager settings;

    /** Append-only audit file shared with the agent loop's ApprovalAuditLog. */
    private File auditLog;

    /** Retries on the flaky Android emulator (boot/adb/timing), like other instrumented tests. */
    @Rule
    public FlakyTestRule flakyTestRule = new FlakyTestRule(5);

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        settings = new SettingsManager(context);
        settings.clear();
        auditLog = new File(context.getFilesDir(), "audit/shell.log");
        if (auditLog.exists()) {
            auditLog.delete();
        }
    }

    // ==================== ask_all ====================

    @Test
    @Flaky(maxAttempts = 5)
    public void askAll_opensDialogBeforeEchoHello() {
        AgentLoop loop = newLoop("ask_all", "echo hello", c -> false);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("hi", ChatMessage.TYPE_USER)), callback);

        // The dialog gate must fire before the command runs.
        assertTrue("ask_all must request approval for the shell command", callback.approvalRequested);
        assertEquals("the prompting tool is the shell tool", "execute_shell", callback.approvalToolName);
        assertNotNull("the approval callback must be delivered", callback.approvalCallback);
        // Nothing has executed yet.
        assertNull("no tool result before approval", callback.lastToolResult);

        // Approve: the command should then run silently and surface its output.
        callback.approvalCallback.onApproved();

        assertTrue("echoed text should be returned as the tool result",
                callback.lastToolResult != null && callback.lastToolResult.contains("hello"));
        assertTrue("an approved run must be audited with its command", auditContains("cmd=echo hello"));
        assertTrue("an approved run must be audited as dialog-approved",
                auditContains("disposition=dialog-approved"));
    }

    // ==================== auto ====================

    @Test
    @Flaky(maxAttempts = 5)
    public void auto_runsEchoHelloSilentlyWithoutDialog() {
        AgentLoop loop = newLoop("auto", "echo hello", c -> false);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertFalse("auto must not open a dialog for a benign command", callback.approvalRequested);
        assertNotNull("auto must still return the tool result", callback.lastToolResult);
        assertTrue("echoed text should be returned", callback.lastToolResult.contains("hello"));
        assertTrue("an allowed auto command must be audited", auditContains("cmd=echo hello"));
        assertTrue("an allowed auto command must be audited as allow",
                auditContains("disposition=allow"));
    }

    @Test
    @Flaky(maxAttempts = 5)
    public void auto_refusesDestructiveCommandWithReason() {
        // mkfs on a device path is destructive; the judge must refuse it before it runs.
        AgentLoop loop =
                newLoop("auto", "mkfs.ext4 /dev/droidclaw_test_xyz", c -> c.contains("mkfs"));

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertFalse("a refused command must not open a dialog", callback.approvalRequested);
        assertNotNull("the refusal must come back as a tool result", callback.lastToolResult);
        assertTrue("the refusal must carry the judge reason",
                callback.lastToolResult.contains("denied by command judge"));
        assertFalse("the destructive command must never actually run",
                callback.lastToolResult.contains("hello"));
        assertTrue("a denied command must be audited", auditContains("cmd=mkfs.ext4"));
        assertTrue("a denied command must be audited as deny", auditContains("disposition=deny"));
    }

    // ==================== full_access ====================

    @Test
    @Flaky(maxAttempts = 5)
    public void fullAccess_runsSilentlyAndAudits() {
        AgentLoop loop = newLoop("full_access", "echo hello", c -> false);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertFalse("full_access must never open a dialog", callback.approvalRequested);
        assertNotNull("full_access must return the tool result", callback.lastToolResult);
        assertTrue("echoed text should be returned", callback.lastToolResult.contains("hello"));
        assertTrue("full_access runs must still be audited", auditContains("cmd=echo hello"));
        assertTrue("full_access runs must be audited as silent", auditContains("disposition=silent"));
    }

    // ==================== helpers ====================

    private AgentLoop newLoop(String mode, String command, Predicate<String> denyWhen) {
        return newLoop(mode, command, null, denyWhen);
    }

    private AgentLoop newLoop(String mode, String command, String workingDirectory,
                              Predicate<String> denyWhen) {
        AgentConfig config = settings.getAgentConfig();
        config.setSandboxMode("relaxed");   // relaxed/full allow the shell tool to register
        config.setShellAccess(true);
        config.setShellApprovalMode(mode);
        config.setRequireApproval(true);
        config.setStreamResponses(false);   // force the non-streaming sendMessageWithTools path
        settings.setAgentConfig(config);

        FakeLlm fakeLlm = new FakeLlm(settings, command, workingDirectory, denyWhen);
        ToolRegistry registry = new ToolRegistry(context, settings);
        AgentLoop loop = new AgentLoop(fakeLlm, registry, settings, null, null, Runnable::run);
        loop.setMaxIterations(50);
        return loop;
    }

    @Test
    @Flaky(maxAttempts = 5)
    public void auto_auditsFailedCommandWithErrorDisposition() {
        // A command that fails to start (invalid working directory) must still land in the
        // audit log, with disposition "error", even though it never produced a success result.
        AgentLoop loop =
                newLoop("auto", "echo hello", "/nonexistent/droidclaw_audit_dir_xyz", c -> false);

        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertFalse("auto must not open a dialog for a benign command", callback.approvalRequested);
        assertNotNull("the failed command must still return a tool result", callback.lastToolResult);
        assertTrue("the failure must be surfaced as an error result",
                callback.lastToolResult.contains("Error:"));
        assertTrue("a failed command must still be audited",
                auditContains("cmd=echo hello"));
        assertTrue("a failed command must be audited with disposition error",
                auditContains("disposition=error"));
    }

    private boolean auditContains(String needle) {
        try {
            if (!auditLog.exists()) {
                return false;
            }
            String contents = new String(Files.readAllBytes(auditLog.toPath()), StandardCharsets.UTF_8);
            return contents.contains(needle);
        } catch (IOException e) {
            return false;
        }
    }

    /** Records the events the tests assert on, without any mocking framework. */
    private static final class RecordingCallback implements AgentLoop.AgentCallback {
        boolean approvalRequested;
        String approvalToolName;
        AgentLoop.ApprovalCallback approvalCallback;
        String lastToolName;
        String lastToolResult;
        String completion;
        String error;

        @Override public void onProgress(String status) {}
        @Override public void onToolCall(String name, String arguments) {}
        @Override public void onToolResult(String name, String result) {
            lastToolName = name;
            lastToolResult = result;
        }
        @Override public void onComplete(String finalResponse, List<ChatMessage> history) {
            completion = finalResponse;
        }
        @Override public void onError(String error) {
            this.error = error;
        }
        @Override public void onApprovalRequired(String name, String description, JsonObject arguments,
                                                 AgentLoop.ApprovalCallback approvalCallback) {
            approvalRequested = true;
            approvalToolName = name;
            this.approvalCallback = approvalCallback;
        }
        @Override public void onStreamDelta(String delta) {}
        @Override public void onCancelled(List<ChatMessage> history) {}
    }

    /**
     * A hand-written {@link LlmApiService} double. It answers the two call paths the
     * agent loop exercises: the main loop (a single {@code execute_shell} tool call
     * then a final message) and the {@code auto} command judge (a verdict derived
     * from the command the judge inspects).
     */
    private static final class FakeLlm extends LlmApiService {
        private final String command;
        private final String workingDirectory;
        private final Predicate<String> denyWhen;
        private final AtomicInteger mainLoopCalls = new AtomicInteger(0);

        FakeLlm(SettingsManager settings, String command, String workingDirectory,
                Predicate<String> denyWhen) {
            super(settings);
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.denyWhen = denyWhen;
        }

        @Override
        public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                         List<ChatMessage> identityMessages, RequestScope scope,
                                         LlmApiService.ChatCallbackWithTools callback) {
            if (mainLoopCalls.getAndIncrement() == 0) {
                JsonObject args = new JsonObject();
                args.addProperty("command", command);
                if (workingDirectory != null) {
                    args.addProperty("working_directory", workingDirectory);
                }
                callback.onSuccess(new LlmApiService.LlmResponse(null,
                        Collections.singletonList(new LlmApiService.ToolCall("call_1", "execute_shell", args))));
            } else {
                callback.onSuccess(new LlmApiService.LlmResponse("done", null));
            }
        }

        @Override
        public void sendMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                List<ChatMessage> identityMessages, RequestScope scope,
                                LlmApiService.ChatCallback callback) {
            // This is the command judge's one-shot call: read the command out of the
            // prompt text the judge built, then decide the verdict from it.
            String command = extractCommand(conversationHistory);
            JsonObject verdict = new JsonObject();
            if (denyWhen.test(command)) {
                verdict.addProperty("verdict", "deny");
                verdict.addProperty("reason", "destructive command refused by judge");
            } else {
                verdict.addProperty("verdict", "allow");
                verdict.addProperty("reason", "benign command");
            }
            callback.onSuccess(verdict.toString());
        }

        private String extractCommand(List<ChatMessage> conversationHistory) {
            if (conversationHistory == null || conversationHistory.isEmpty()) {
                return "";
            }
            String prompt = conversationHistory.get(0).getContent();
            if (prompt == null) {
                return "";
            }
            int start = prompt.indexOf("Command: ");
            if (start < 0) {
                return "";
            }
            start += "Command: ".length();
            int end = prompt.indexOf("Working directory", start);
            if (end < 0) {
                end = prompt.length();
            }
            return prompt.substring(start, end).trim();
        }
    }
}
