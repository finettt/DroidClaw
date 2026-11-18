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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
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
import io.finett.droidclaw.shell.distro.DistroManager;
import io.finett.droidclaw.shell.distro.DistroException;
import io.finett.droidclaw.shell.distro.DistroSpec;
import io.finett.droidclaw.shell.distro.OkHttpDistroFetcher;
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

    /** Production disk limit (MB) reused for the one-time distro bootstrap below. */
    private static final int DISK_LIMIT_MB = 2048;
    /** Bootstrapped once for the whole class so the mode tests share a ready image. */
    private static volatile boolean distroBootstrapped;
    /** Bootstrap timeout, matching the other distro instrumented test. */
    private static final long BOOTSTRAP_TIMEOUT_MINUTES = 5;

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

    /**
     * Install the pinned Ubuntu image once, before any mode test runs, so the
     * shell tool has a ready guest to execute through. The bootstrap test
     * deletes the image in its own tearDown, so each class must bootstrap again.
     * Retries a few times to absorb transient network and disk hiccups on the
     * emulator.
     */
    @BeforeClass
    public static void bootstrapDistro() throws Exception {
        System.out.println("BEFORE_CLASS distroBootstrapped=" + distroBootstrapped);
        if (distroBootstrapped) {
            return;
        }
        Context ctx = ApplicationProvider.getApplicationContext();
        File imageDir = new File(new File(ctx.getFilesDir(), "distro"), DistroSpec.IMAGE_DIR);
        System.out.println("BEFORE_CLASS markerExists=" + new File(imageDir, DistroSpec.MARKER_FILE).isFile() + " imageDir=" + imageDir);
        if (new File(imageDir, DistroSpec.MARKER_FILE).isFile()) {
            distroBootstrapped = true;
            return;
        }
        final DistroException[] lastHolder = new DistroException[1];
        for (int attempt = 0; attempt < 3 && !distroBootstrapped; attempt++) {
            try {
                DistroManager manager =
                        new DistroManager(ctx, new OkHttpDistroFetcher(), DISK_LIMIT_MB);
                CountDownLatch done = new CountDownLatch(1);
                manager.bootstrap(new DistroManager.Listener() {
                    @Override
                    public void onStateChange(DistroManager.State state) {
                        if (state == DistroManager.State.READY
                                || state == DistroManager.State.FAILED) {
                            done.countDown();
                        }
                    }

                    @Override
                    public void onProgress(long bytesSoFar, long total) {}

                    @Override
                    public void onError(DistroException error) {
                        lastHolder[0] = error;
                    }
                });
                if (!done.await(BOOTSTRAP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    throw new TimeoutException("bootstrap did not finish in time");
                }
                if (manager.getState() == DistroManager.State.READY) {
                    distroBootstrapped = true;
                } else {
                    lastHolder[0] = new DistroException(manager.getError());
                }
            } catch (Exception error) {
                lastHolder[0] = error instanceof DistroException
                        ? (DistroException) error
                        : new DistroException(error.getMessage());
            }
        }
        if (!distroBootstrapped) {
            throw new AssertionError("distro bootstrap failed: " + lastHolder[0]);
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

        System.out.println("DEBUG_RESULT=[" + (callback.lastToolResult == null ? "NULL" : callback.lastToolResult) + "]");
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

    // ==================== distro execution through PRoot ====================

    @Test
    @Flaky(maxAttempts = 5)
    public void distro_runsOsReleaseInShellMode() {
        // sh -c "cat /etc/os-release" exercises the guest-shell path through PRoot.
        AgentLoop loop = newLoop("auto", "sh -c \"cat /etc/os-release\"", c -> false);
        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertNotNull("os-release run must return a tool result", callback.lastToolResult);
        assertFalse("a benign distro command must not open a dialog", callback.approvalRequested);
        assertTrue("os-release must report the Ubuntu distro",
                callback.lastToolResult.contains("Ubuntu"));
    }

    @Test
    @Flaky(maxAttempts = 5)
    public void distro_runsAptVersion() {
        // A real distro binary resolved inside the guest rootfs.
        AgentLoop loop = newLoop("auto", "apt --version", c -> false);
        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertNotNull("apt run must return a tool result", callback.lastToolResult);
        assertTrue("apt must print its version string",
                callback.lastToolResult.toLowerCase().contains("apt"));
    }

    @Test
    @Flaky(maxAttempts = 5)
    public void distro_rejectsHostPathExec() {
        // A host absolute path is not a valid guest executable and must be rejected.
        Context ctx = ApplicationProvider.getApplicationContext();
        String hostPath = ctx.getFilesDir().getAbsolutePath() + "/leak";
        AgentLoop loop = newLoop("auto", hostPath, c -> false);
        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);

        assertNotNull("host-path exec must return a tool result", callback.lastToolResult);
        assertTrue("a host path must be rejected as not valid inside the Linux environment",
                callback.lastToolResult
                        .contains("Host path is not valid inside the Linux environment"));
    }

    @Test
    @Flaky(maxAttempts = 5)
    public void distroMissingYieldsActionableError() throws Exception {
        // With the ready marker absent, planning surfaces an actionable install error.
        Context ctx = ApplicationProvider.getApplicationContext();
        File imageDir = new File(new File(ctx.getFilesDir(), "distro"), DistroSpec.IMAGE_DIR);
        File marker = new File(imageDir, DistroSpec.MARKER_FILE);
        byte[] original = marker.exists() ? java.nio.file.Files.readAllBytes(marker.toPath()) : null;
        if (marker.exists()) {
            marker.delete();
        }
        try {
            AgentLoop loop = newLoop("auto", "echo hello", c -> false);
            RecordingCallback callback = new RecordingCallback();
            loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)),
                    callback);

            assertNotNull("missing-distro run must return a tool result", callback.lastToolResult);
            assertTrue("the error must tell the user to install the environment",
                    callback.lastToolResult.contains("is not installed yet"));
        } finally {
            if (original != null && !marker.exists()) {
                java.nio.file.Files.write(marker.toPath(), original);
            }
        }
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

    @Test
    public void temp_absoluteSymlinkFix() throws Exception {
        String rootfs = "/data/user/0/io.finett.droidclaw/files/distro/ubuntu";
        String interpReal = "/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2";
        String interpViaLib = rootfs + "/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2";
        String symlink = rootfs + "/lib64/ld-linux-x86-64.so.2";
        System.out.println("DEBUG_REAL_INTERP_EXISTS=" + java.nio.file.Files.exists(java.nio.file.Paths.get(interpReal)));
        System.out.println("DEBUG_LIB_IS_LINK=" + java.nio.file.Files.isSymbolicLink(java.nio.file.Paths.get(rootfs + "/lib")));
        // Point the ELF interpreter at the real interpreter.
        Process rm = Runtime.getRuntime().exec(new String[]{"rm", "-f", symlink});
        rm.waitFor();
        Process ln = Runtime.getRuntime().exec(new String[]{"ln", "-s", interpReal, symlink});
        ln.waitFor();
        System.out.println("DEBUG_NEW_TARGET=" + java.nio.file.Files.readSymbolicLink(java.nio.file.Paths.get(symlink)));
        AgentLoop loop = newLoop("auto", "echo HELLO_AFTER_REAL_INTERP", c -> false);
        RecordingCallback callback = new RecordingCallback();
        loop.start(Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)), callback);
        System.out.println("DEBUG_ECHO_RESULT=[" + (callback.lastToolResult == null ? "NULL" : callback.lastToolResult) + "]");
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
