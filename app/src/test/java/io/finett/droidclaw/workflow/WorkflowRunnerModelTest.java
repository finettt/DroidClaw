package io.finett.droidclaw.workflow;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Looper;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Unit tests for per-node model selection in the workflow runner (issue #156):
 * model resolution at run start, per-node override application, precedence over
 * {@code defaults.model}, the first-slash split, fail-fast validation, and the
 * #147 regression family (override cleared on every terminal path).
 */
@RunWith(RobolectricTestRunner.class)
public class WorkflowRunnerModelTest {
    @Mock LlmApiService api;
    @Mock ToolRegistry tools;
    @Mock SettingsManager settings;
    @Mock AgentConfig config;

    @Captor ArgumentCaptor<Provider> providerCaptor;
    @Captor ArgumentCaptor<Model> modelCaptor;

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

    // ==================== helpers ===

    private static Provider provider(String id) {
        Provider p = new Provider();
        p.setId(id);
        p.setBaseUrl("https://" + id + "/v1");
        return p;
    }

    private static Model model(String id) {
        Model m = new Model();
        m.setId(id);
        return m;
    }

    /** Reply to both the global (5-arg) and per-model (7-arg) overloads in call order. */
    private void replySequence(String... replies) {
        final int[] n = {0};
        String[] r = replies;
        doAnswer(inv -> {
            String reply = r[Math.min(n[0]++, r.length - 1)];
            LlmApiService.ChatCallbackWithTools cb = inv.getArgument(4);
            if (reply.startsWith("ERROR:")) cb.onError(reply.substring(6));
            else cb.onSuccess(new LlmApiService.LlmResponse(reply, null));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));

        doAnswer(inv -> {
            String reply = r[Math.min(n[0]++, r.length - 1)];
            LlmApiService.ChatCallbackWithTools cb = inv.getArgument(6);
            if (reply.startsWith("ERROR:")) cb.onError(reply.substring(6));
            else cb.onSuccess(new LlmApiService.LlmResponse(reply, null));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(RequestScope.class),
                any(LlmApiService.ChatCallbackWithTools.class));
    }

    // ==================== Deliverable 3: valid override reaches the LLM ===

    @Test public void perNodeModelsReachRequestLayerNotGlobal() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "claude-sonnet-4")).thenReturn(model("claude-sonnet-4"));
        when(settings.getProvider("openai")).thenReturn(provider("openai"));
        when(settings.getModel("openai", "gpt-4o-mini")).thenReturn(model("gpt-4o-mini"));

        replySequence("out-a", "out-b");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}} + {{b.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/claude-sonnet-4\"},"
                + "\"b\":{\"prompt\":{\"text\":\"b\"},\"model\":\"openai/gpt-4o-mini\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        assertEquals("out-a + out-b", result.getOutput());

        // Each node used its own configured model, in execution order, not the global default.
        verify(api, times(2)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                providerCaptor.capture(), modelCaptor.capture(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        List<Provider> provs = providerCaptor.getAllValues();
        List<Model> models = modelCaptor.getAllValues();
        assertEquals("acme", provs.get(0).getId());
        assertEquals("claude-sonnet-4", models.get(0).getId());
        assertEquals("openai", provs.get(1).getId());
        assertEquals("gpt-4o-mini", models.get(1).getId());

        // And the global (no-override) overload was never used.
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    // ==================== Deliverable 3: fail fast on unknown provider/model ===

    @Test public void unknownProviderFailsBeforeAnyRequest() {
        replySequence("should-not-happen");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"nope/claude-sonnet-4\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertFalse(result.getError(), result.isSuccess());
        assertTrue("error must name the raw providerId/modelId",
                result.getError().contains("nope/claude-sonnet-4"));
        // The model must never be asked to respond.
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(), any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void unknownModelIdFailsBeforeAnyRequest() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        replySequence("should-not-happen");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/does-not-exist\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertFalse(result.getError(), result.isSuccess());
        assertTrue("error must name the raw providerId/modelId",
                result.getError().contains("acme/does-not-exist"));
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void validationErrorNamesNodeAndRawModel() {
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"agents\":{\"alpha\":{\"prompt\":{\"text\":\"a\"},\"model\":\"nope/claude-sonnet-4\"}}}";
        WorkflowRunResult result = runner.run(json, null, null);
        assertFalse(result.getError(), result.isSuccess());
        assertTrue("error must name the node id", result.getError().contains("alpha"));
        assertTrue("error must name the raw model string",
                result.getError().contains("nope/claude-sonnet-4"));
    }

    // ==================== Deliverable 3: defaults.model precedence ===

    @Test public void defaultsModelAppliesToNodeWithoutOwnModel() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "default-model")).thenReturn(model("default-model"));
        replySequence("out");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"defaults\":{\"model\":\"acme/default-model\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"}}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                providerCaptor.capture(), modelCaptor.capture(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        assertEquals("acme", providerCaptor.getValue().getId());
        assertEquals("default-model", modelCaptor.getValue().getId());
    }

    @Test public void nodeModelOverridesDefaults() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "default-model")).thenReturn(model("default-model"));
        when(settings.getModel("acme", "node-model")).thenReturn(model("node-model"));
        replySequence("out");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"defaults\":{\"model\":\"acme/default-model\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/node-model\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                providerCaptor.capture(), modelCaptor.capture(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        assertEquals("node-model", modelCaptor.getValue().getId());
    }

    @Test public void absentModelAndDefaultsUseGlobalOverload() {
        replySequence("out");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"}}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        // No per-model override was applied: the global overload was used and never the per-model one.
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    // ==================== Deliverable 3: first-slash split ===

    @Test public void firstSlashSplitKeepsModelIdWithSlashes() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "some/model/id")).thenReturn(model("some/model/id"));
        replySequence("out");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/some/model/id\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                providerCaptor.capture(), modelCaptor.capture(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        assertEquals("acme", providerCaptor.getValue().getId());
        assertEquals("some/model/id", modelCaptor.getValue().getId());
    }

    // ==================== Deliverable 3: #147 regression family ===

    @Test public void overrideNotLeakedAfterSuccessfulNode() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "model-x")).thenReturn(model("model-x"));
        replySequence("out-a", "out-b");
        // a overrides the model; b omits it and must fall back to the global default.
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{a.output}}{{b.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/model-x\"},"
                + "\"b\":{\"prompt\":{\"text\":\"b\"}}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        // Only node a used the per-model overload; node b must not have leaked it.
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void overrideNotLeakedAfterErroredNode() {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "model-x")).thenReturn(model("model-x"));
        // on_error=continue so node b runs after node a's content error.
        replySequence("ERROR:content boom", "out-b");
        String json = "{\"version\":1,\"goal\":\"g\",\"output\":\"{{b.output}}\","
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"model\":\"acme/model-x\","
                + "\"on_error\":\"continue\"},\"b\":{\"prompt\":{\"text\":\"b\"}}}}";

        WorkflowRunResult result = runner.run(json, null, null);
        assertTrue(result.getError(), result.isSuccess());
        // Node a consumed the per-model override; node b must use the global default.
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, times(1)).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test public void overrideAppliedBeforeCancelAndClearsOnFinalization() throws Exception {
        when(settings.getProvider("acme")).thenReturn(provider("acme"));
        when(settings.getModel("acme", "model-x")).thenReturn(model("model-x"));

        // The per-model request hangs (never fires its callback) until cancelled, and
        // signals that it was issued so the test can cancel deterministically.
        final CountDownLatch issued = new CountDownLatch(1);
        doAnswer(inv -> {
            issued.countDown();
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(Provider.class), any(Model.class), any(RequestScope.class),
                any(LlmApiService.ChatCallbackWithTools.class));
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},"
                + "\"model\":\"acme/model-x\"}}}";

        AtomicReference<WorkflowRunResult> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(runner.run(json, null, null)));
        worker.start();

        assertTrue("the per-model request must be issued before cancel",
                issued.await(5, TimeUnit.SECONDS));

        runner.cancel();
        long deadline = System.currentTimeMillis() + 5000;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        worker.join(1000);

        assertFalse("run() must not hang after cancellation", worker.isAlive());
        assertNotNull(result.get());
        assertFalse("a cancelled node must not succeed", result.get().isSuccess());

        // The override was applied before the node ran: the per-model overload was
        // invoked with the resolved acme/model-x pair.
        verify(api, atLeastOnce()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                providerCaptor.capture(), modelCaptor.capture(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        assertEquals("acme", providerCaptor.getValue().getId());
        assertEquals("model-x", modelCaptor.getValue().getId());
    }

}
