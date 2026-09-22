package io.finett.droidclaw.agent;

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
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Regression tests for issue #144: cancelling an {@link AgentLoop} run must only
 * cancel that run's own {@link RequestScope}, never the shared OkHttp client via
 * {@link LlmApiService#cancelAllRequests()}.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentLoopCancelScopeTest {

    @Mock private LlmApiService mockApiService;
    @Mock private ToolRegistry mockToolRegistry;
    @Mock private SettingsManager mockSettingsManager;
    @Mock private AgentLoop.AgentCallback mockCallback;

    private AgentConfig agentConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        agentConfig = new AgentConfig();
        agentConfig.setStreamResponses(false);
        when(mockSettingsManager.getMaxAgentIterations()).thenReturn(20);
        when(mockSettingsManager.isRequireApproval()).thenReturn(false);
        when(mockSettingsManager.getAgentConfig()).thenReturn(agentConfig);
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
    }

    private AgentLoop createLoop() {
        return new AgentLoop(mockApiService, mockToolRegistry, mockSettingsManager);
    }

    private List<ChatMessage> conversation() {
        List<ChatMessage> c = new ArrayList<>();
        c.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        return c;
    }

    /** Stub a request that stays in flight forever (callback never fires). */
    private void stubHangingRequest() {
        doAnswer(inv -> null).when(mockApiService).sendMessageWithTools(anyList(),
                any(JsonArray.class), any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test
    public void cancel_doesNotCancelSharedClient() {
        stubHangingRequest();

        AgentLoop loop = createLoop();
        loop.start(conversation(), mockCallback);
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        verify(mockApiService, never()).cancelAllRequests();
        verify(mockCallback).onCancelled(anyList());
    }

    @Test
    public void cancel_cancelsOnlyTheRunScope() {
        stubHangingRequest();

        AgentLoop loop = createLoop();
        loop.start(conversation(), mockCallback);

        RequestScope scope = loop.getRunScope();
        assertNotNull("start() must create a per-run scope", scope);
        assertFalse(scope.isCancelled());

        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue("cancel() must cancel the run's own scope", scope.isCancelled());
        verify(mockApiService, never()).cancelAllRequests();
    }

    @Test
    public void start_passesRunScopeToApi() {
        stubHangingRequest();

        AgentLoop loop = createLoop();
        loop.start(conversation(), mockCallback);

        verify(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class), any(),
                same(loop.getRunScope()), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test
    public void newRun_getsFreshScope() {
        doAnswer(inv -> {
            LlmApiService.ChatCallbackWithTools cb = inv.getArgument(4);
            cb.onSuccess(new LlmApiService.LlmResponse("done", null));
            return null;
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        AgentLoop loop = createLoop();
        loop.start(conversation(), mockCallback);
        RequestScope first = loop.getRunScope();
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        loop.start(conversation(), mockCallback);
        RequestScope second = loop.getRunScope();

        assertNotSame(first, second);
        assertFalse("a new run must not inherit the cancelled scope", second.isCancelled());
    }
}
