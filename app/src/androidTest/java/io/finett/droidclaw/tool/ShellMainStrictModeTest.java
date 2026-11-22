package io.finett.droidclaw.tool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.os.StrictMode;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.shell.distro.DistroException;
import io.finett.droidclaw.shell.distro.DistroManager;
import io.finett.droidclaw.shell.distro.DistroSpec;
import io.finett.droidclaw.shell.distro.OkHttpDistroFetcher;
import io.finett.droidclaw.util.Flaky;
import io.finett.droidclaw.util.FlakyTestRule;
import io.finett.droidclaw.util.SettingsManager;

/**
 * StrictMode regression guard for the shell execution path. Drives a minimal
 * {@code echo} scenario through {@link AgentLoop} the way production does it —
 * the loop starts on the main thread and the approval dialog decision is made on
 * the main thread — with a {@code StrictMode.ThreadPolicy} watching the main
 * thread for the whole scenario. The test fails on any main-thread violation, so
 * reintroducing blocking process execution, disk I/O, or network calls on the
 * main thread turns CI red.
 *
 * <p>The scenario is deliberately minimal ({@code echo hello}, fake LLM, no UI):
 * assertions are about recorded violations, not about timing.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 28)
public class ShellMainStrictModeTest {

    private static final String TAG = "ShellMainStrictMode";

    /** Retries on the flaky Android emulator (boot/adb/timing), like other instrumented tests. */
    @Rule
    public FlakyTestRule flakyTestRule = new FlakyTestRule(5);

    /** Production disk limit (MB) reused for the one-time distro bootstrap below. */
    private static final int DISK_LIMIT_MB = 2048;
    /** Bootstrapped once for the whole class so tests share a ready image. */
    private static volatile boolean distroBootstrapped;
    /** Bootstrap timeout, matching the other distro instrumented tests. */
    private static final long BOOTSTRAP_TIMEOUT_MINUTES = 5;

    private Context context;
    private SettingsManager settings;
    private File auditLog;

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
     * Install the pinned Ubuntu image once, before the test runs, so the shell
     * tool has a ready guest to execute through. Retries a few times to absorb
     * transient network and disk hiccups on the emulator.
     */
    @BeforeClass
    public static void bootstrapDistro() throws Exception {
        if (distroBootstrapped) {
            return;
        }
        Context ctx = ApplicationProvider.getApplicationContext();
        File imageDir = new File(new File(ctx.getFilesDir(), "distro"), DistroSpec.IMAGE_DIR);
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

    @Test
    @Flaky(maxAttempts = 5)
    public void echoScenarioKeepsMainThreadFreeOfStrictModeViolations() throws Exception {
        AgentConfig config = settings.getAgentConfig();
        config.setSandboxMode("relaxed");   // relaxed/full allow the shell tool to register
        config.setShellAccess(true);
        config.setShellApprovalMode("ask_all");
        config.setRequireApproval(true);
        config.setStreamResponses(false);   // force the non-streaming sendMessageWithTools path
        settings.setAgentConfig(config);

        EchoLlm fakeLlm = new EchoLlm(settings, "echo hello");
        ToolRegistry registry = new ToolRegistry(context, settings);
        // The production constructor wires the background work dispatcher.
        AgentLoop loop = new AgentLoop(fakeLlm, registry, settings, null, null);
        loop.setMaxIterations(10);

        RecordingCallback callback = new RecordingCallback();
        List<String> violations = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<StrictMode.ThreadPolicy> previous = new AtomicReference<>();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            previous.set(StrictMode.getThreadPolicy());
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .detectUnbufferedIo()
                    .penaltyListener(Runnable::run, violation -> {
                        String trace = Log.getStackTraceString(violation);
                        violations.add(trace);
                        Log.w(TAG, "main-thread StrictMode violation", violation);
                    })
                    .build());
        });
        try {
            // Start the agent on the main thread, like sending a chat message does.
            instrumentation.runOnMainSync(() -> loop.start(
                    Collections.singletonList(new ChatMessage("go", ChatMessage.TYPE_USER)),
                    callback));
            assertTrue("the echo command must request approval",
                    callback.approvalRequested.await(60, TimeUnit.SECONDS));
            assertTrue("the prompting tool must be the shell tool",
                    "execute_shell".equals(callback.approvalToolName));
            // Approve on the main thread, exactly like the approval dialog does.
            instrumentation.runOnMainSync(() -> callback.approval.onApproved());
            assertTrue("the echo scenario must complete",
                    callback.completed.await(120, TimeUnit.SECONDS));
        } finally {
            instrumentation.runOnMainSync(() -> StrictMode.setThreadPolicy(previous.get()));
        }

        assertNull("the run must not fail: " + callback.error, callback.error);
        assertNotNull("the echo command must return a result", callback.lastToolResult);
        assertTrue("the echo output must come back: " + callback.lastToolResult,
                callback.lastToolResult.contains("hello"));
        assertTrue("an approved run must be audited", auditContains("cmd=echo hello"));
        assertTrue("the main thread must stay free of StrictMode violations during the "
                        + "echo scenario; recorded: " + violations,
                violations.isEmpty());
    }

    /** Poll briefly: the audit line is queued to the background dispatcher. */
    private boolean auditContains(String needle) {
        for (int i = 0; i < 100; i++) {
            try {
                if (auditLog.exists()) {
                    String contents = new String(Files.readAllBytes(auditLog.toPath()),
                            StandardCharsets.UTF_8);
                    if (contents.contains(needle)) {
                        return true;
                    }
                }
            } catch (IOException ignored) {
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Records the events the test asserts on, without any mocking framework. */
    private static final class RecordingCallback implements AgentLoop.AgentCallback {
        final CountDownLatch approvalRequested = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        volatile AgentLoop.ApprovalCallback approval;
        volatile String approvalToolName;
        volatile String lastToolResult;
        volatile String error;

        @Override public void onProgress(String status) {}
        @Override public void onToolCall(String name, String arguments) {}
        @Override public void onToolResult(String name, String result) {
            lastToolResult = result;
        }
        @Override public void onComplete(String finalResponse, List<ChatMessage> history) {
            completed.countDown();
        }
        @Override public void onError(String error) {
            this.error = error;
            completed.countDown();
        }
        @Override public void onApprovalRequired(String name, String description, JsonObject arguments,
                                                 AgentLoop.ApprovalCallback approvalCallback) {
            approvalToolName = name;
            approval = approvalCallback;
            approvalRequested.countDown();
        }
        @Override public void onStreamDelta(String delta) {}
        @Override public void onCancelled(List<ChatMessage> history) {
            error = "cancelled";
            completed.countDown();
        }
    }

    /**
     * A hand-written {@link LlmApiService} double: the main loop answers with one
     * {@code execute_shell} tool call and then a final message; the command judge
     * path allows every command this scenario sends.
     */
    private static final class EchoLlm extends LlmApiService {
        private final String command;
        private final Predicate<String> denyWhen;
        private int mainLoopCalls;

        EchoLlm(SettingsManager settings, String command) {
            this(settings, command, c -> false);
        }

        EchoLlm(SettingsManager settings, String command, Predicate<String> denyWhen) {
            super(settings);
            this.command = command;
            this.denyWhen = denyWhen;
        }

        @Override
        public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                         List<ChatMessage> identityMessages, RequestScope scope,
                                         LlmApiService.ChatCallbackWithTools callback) {
            if (mainLoopCalls++ == 0) {
                JsonObject args = new JsonObject();
                args.addProperty("command", command);
                callback.onSuccess(new LlmApiService.LlmResponse(null,
                        Collections.singletonList(
                                new LlmApiService.ToolCall("call_1", "execute_shell", args))));
            } else {
                callback.onSuccess(new LlmApiService.LlmResponse("done", null));
            }
        }

        @Override
        public void sendMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                                List<ChatMessage> identityMessages, RequestScope scope,
                                LlmApiService.ChatCallback callback) {
            // The command judge's one-shot call: decide from the command it inspects.
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
    }
}
