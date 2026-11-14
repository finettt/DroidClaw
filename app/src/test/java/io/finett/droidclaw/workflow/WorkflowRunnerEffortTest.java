package io.finett.droidclaw.workflow;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.Effort;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Unit tests for per-node effort in the workflow runner, mirroring the #156 model
 * family: the effort override reaches the run\'s request scope, precedence is
 * node &gt; defaults &gt; global, invalid names are a fail-fast hard error, the override
 * never leaks between nodes, and the agent-side turn budget applies regardless of
 * the model flag (only the reasoning API param is gated downstream).
 */
@RunWith(RobolectricTestRunner.class)
public class WorkflowRunnerEffortTest {
    @Mock LlmApiService api;
    @Mock ToolRegistry tools;
    @Mock SettingsManager settings;
    @Mock AgentConfig config;

    @Captor ArgumentCaptor<RequestScope> scopeCaptor;

    private WorkflowRunner runner;

    @Before public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tools.getToolDefinitions()).thenReturn(new JsonArray());
        when(tools.getAllTools()).thenReturn(new ArrayList<>());
        when(settings.getMaxAgentIterations()).thenReturn(20);
        when(settings.isRequireApproval()).thenReturn(true);
        when(settings.getAgentConfig()).thenReturn(config);
        when(config.isStreamResponses()).thenReturn(false);
        // config.getEffort() is left unstubbed (null -> MEDIUM) unless a test needs a
        // different global effort; the loop resolves it in its constructor.
        runner = new WorkflowRunner(api, tools, settings, null, null);
    }

    // ==================== helpers ===

    /** Reply tool calls for the first {@code n} LLM calls, then a plain "done". */
    private void replySequence(int toolCallCount) {
        final int[] n = {0};
        doAnswer(inv -> {
            int call = n[0]++;
            String reply = call <= toolCallCount ? "tc" : "done";
            LlmApiService.ChatCallbackWithTools cb = inv.getArgument(4);
            if ("tc".equals(reply)) {
                cb.onSuccess(new LlmApiService.LlmResponse(null,
                        java.util.Collections.singletonList(
                                new LlmApiService.ToolCall("c" + call, "file_read", new com.google.gson.JsonObject()))));
            } else {
                cb.onSuccess(new LlmApiService.LlmResponse("done", null));
            }
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    // ==================== per-node reaches request layer ===

    @Test public void perNodeEffortReachesRequestScope() {
        when(config.getEffort()).thenReturn("high"); // global HIGH (budget 40)
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},"
                + "\"effort\":\"low\"}}}";

        runner.run(json, null, null);

        // The effort override was carried on the run\'s request scope that the loop hands
        // to the API, so it reaches the request layer.
        verify(api, atLeastOnce()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                scopeCaptor.capture(), any(LlmApiService.ChatCallbackWithTools.class));
        for (RequestScope scope : scopeCaptor.getAllValues()) {
            assertEquals("the per-node effort override rides the request scope", Effort.LOW, scope.getEffort());
        }
    }

    // ==================== precedence: node > global ===

    @Test public void nodeEffortOverridesGlobalBudget() {
        // Global is MEDIUM (unstubbed config -> MEDIUM, budget 20); node overrides LOW.
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},"
                + "\"effort\":\"low\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);

        assertTrue("the LOW override caps the loop at its budget, not the global MEDIUM budget",
                result.getError().contains("Maximum iterations (8)"));
    }

    // ==================== precedence: defaults > global ===

    @Test public void defaultsEffortAppliesToNodeWithoutEffort() {
        when(config.getEffort()).thenReturn("high"); // global HIGH
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"effort\":\"low\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"}}}}";

        WorkflowRunResult result = runner.run(json, null, null);

        assertTrue("the defaults.effort level caps the node when it sets none",
                result.getError().contains("Maximum iterations (8)"));
    }

    // ==================== fail-fast naming the node id ===

    @Test public void invalidEffortFailsBeforeAnyRequest() {
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"alpha\":{\"prompt\":{\"text\":\"a\"},"
                + "\"effort\":\"bogus\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);

        assertFalse("an invalid effort must fail the run", result.getError() == null
                || result.isSuccess());
        assertTrue("the error must name the node id", result.getError().contains("alpha"));
        assertTrue("the error must name the invalid value", result.getError().contains("bogus"));
        // The malformed node never reaches the LLM.
        verify(api, never()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                any(RequestScope.class), any(LlmApiService.ChatCallbackWithTools.class));
    }

    // ==================== override cleared between nodes ===

    @Test public void effortOverrideNotLeakedToSubsequentNode() {
        when(config.getEffort()).thenReturn("high"); // global HIGH (budget 40)
        // 8 tool calls: node a (LOW) exhausts them and caps at 8; node b then gets "done".
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"entry\":[\"a\"],\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"effort\":\"low\",\"on_error\":\"continue\"},\"b\":{\"prompt\":{\"text\":\"b\"},\"depends_on\":[\"a\"]}}}";

        WorkflowRunResult result = runner.run(json, null, null);

        // Node a carried its LOW override on its scope; node b fell back to the global HIGH
        // — a per-node override must never leak into the next node.
        verify(api, atLeastOnce()).sendMessageWithTools(anyList(), any(JsonArray.class), anyList(),
                scopeCaptor.capture(), any(LlmApiService.ChatCallbackWithTools.class));
        List<RequestScope> scopes = scopeCaptor.getAllValues();
        assertFalse("both nodes must issue a request", scopes.isEmpty());
        assertEquals("the overriding node uses its own effort", Effort.LOW, scopes.get(0).getEffort());
        assertEquals("a later node uses the global effort, not the earlier override",
                Effort.HIGH, scopes.get(scopes.size() - 1).getEffort());
    }

    // ==================== flag gating: budget always applies agent-side ===

    @Test public void effortBudgetAppliesWhenGlobalIsUncapped() {
        when(config.getEffort()).thenReturn("max"); // global MAX (uncapped)
        replySequence(8);
        String json = "{\"version\":1,\"goal\":\"g\",\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},"
                + "\"effort\":\"low\"}}}";

        WorkflowRunResult result = runner.run(json, null, null);

        // The node effort\'s turn budget is enforced agent-side and caps the run even when
        // the global effort is unbounded. Only the reasoning API param is gated by the
        // model flag, downstream in LlmApiService.
        assertTrue("an explicit effort budget caps the loop even against an uncapped global",
                result.getError().contains("Maximum iterations (8)"));
    }

    // ==================== inherit sentinel ===

    @Test public void inheritSentinelBehavesLikeAbsent() {
        // Global is HIGH (budget 40); defaults.effort is LOW. A node that says "inherit",
        // or omits effort entirely, must take the defaults effort (LOW), not the global HIGH.
        when(config.getEffort()).thenReturn("high"); // global HIGH (budget 40)

        String inherit = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"effort\":\"low\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"},\"effort\":\"inherit\"}}}";
        String absent = "{\"version\":1,\"goal\":\"g\",\"defaults\":{\"effort\":\"low\"},"
                + "\"agents\":{\"a\":{\"prompt\":{\"text\":\"a\"}}}}";

        // Run each case on a fresh mock reply stream so the two runs do not interfere.
        replySequence(8);
        WorkflowRunResult viaInherit = runner.run(inherit, null, null);
        assertTrue("inherit must fall back to defaults.effort (LOW => cap 8), not the global HIGH",
                viaInherit.getError().contains("Maximum iterations (8)"));

        replySequence(8);
        WorkflowRunResult viaAbsent = runner.run(absent, null, null);
        assertTrue("an omitted effort must also fall back to defaults.effort (LOW => cap 8)",
                viaAbsent.getError().contains("Maximum iterations (8)"));
        assertTrue("inherit and absent must behave identically",
                viaInherit.getError().equals(viaAbsent.getError()));
    }

}
