package io.finett.droidclaw.workflow;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Looper;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Regression tests for issue #144: a cancelled workflow node delivers
 * {@code onCancelled} (not onComplete/onError), which previously never counted
 * the node latch down and could leave {@link WorkflowRunner#run} stuck forever.
 */
@RunWith(RobolectricTestRunner.class)
public class WorkflowRunnerCancellationLatchTest {
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

    /** Stub a request that never responds: the node stays in flight until cancelled. */
    private void stubHangingNode() {
        doNothing().when(api).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    /**
     * A node whose only terminal signal after cancel() is onCancelled must not
     * hang run(): the runner-level cancel unblocks the latch via onCancelled.
     */
    @Test public void cancelWithOnlyOnCancelledSignalDoesNotHangRun() throws Exception {
        stubHangingNode();
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}";

        AtomicReference<WorkflowRunResult> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(runner.run(json, null, null)));
        worker.start();

        // Wait until the node request is actually in flight.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(api, atLeastOnce()).sendMessageWithTools(anyList(), any(JsonArray.class),
                        any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
                break;
            } catch (AssertionError notYet) {
                Thread.sleep(10);
            }
        }

        runner.cancel();
        // AgentLoop finalizes cancellation (and fires onCancelled) on the main looper.
        deadline = System.currentTimeMillis() + 5000;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        worker.join(1000);

        assertFalse("run() must not hang after cancellation", worker.isAlive());
        assertNotNull(result.get());
        assertFalse(result.get().isSuccess());
    }

    /**
     * A node timeout cancels the node loop; run() must return the timeout error
     * promptly even though the loop only ever signals onCancelled.
     */
    @Test public void nodeTimeoutReturnsErrorInsteadOfHanging() throws Exception {
        stubHangingNode();
        String json = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"timeout_ms\":1000,"
                + "\"retry\":{\"max_attempts\":1,\"backoff_ms\":1}},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"go\"}}}}";

        AtomicReference<WorkflowRunResult> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(runner.run(json, null, null)));
        worker.start();

        long deadline = System.currentTimeMillis() + 8000;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        worker.join(1000);

        assertFalse("run() must not hang on node timeout", worker.isAlive());
        assertNotNull(result.get());
        assertFalse(result.get().isSuccess());
        assertTrue(result.get().getError(), result.get().getError().contains("timed out"));
    }
}
