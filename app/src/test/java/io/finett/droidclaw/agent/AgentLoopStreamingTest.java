package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Tests the streaming (SSE) path of {@link AgentLoop}: selection between the
 * streaming and legacy API calls, live delta forwarding, and final completion.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentLoopStreamingTest {

    @Mock
    private LlmApiService mockApiService;

    @Mock
    private ToolRegistry mockToolRegistry;

    @Mock
    private SettingsManager mockSettingsManager;

    @Mock
    private AgentLoop.AgentCallback mockCallback;

    private AgentConfig agentConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        agentConfig = new AgentConfig();
        when(mockSettingsManager.getMaxAgentIterations()).thenReturn(20);
        when(mockSettingsManager.isRequireApproval()).thenReturn(false);
        when(mockSettingsManager.getAgentConfig()).thenReturn(agentConfig);
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
    }

    private AgentLoop createLoop() {
        return new AgentLoop(mockApiService, mockToolRegistry, mockSettingsManager);
    }

    private List<ChatMessage> simpleConversation() {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        return conversation;
    }

    @Test
    public void streamingEnabledByDefault_fromAgentConfig() {
        AgentLoop loop = createLoop();
        assertTrue("Streaming should be enabled by default", loop.isStreamingEnabled());
    }

    @Test
    public void streamingDisabled_whenConfiguredOff() {
        agentConfig.setStreamResponses(false);
        AgentLoop loop = createLoop();
        assertFalse(loop.isStreamingEnabled());
    }

    @Test
    public void noSettingsManager_fallsBackToLegacyPath() {
        AgentLoop loop = new AgentLoop(mockApiService, mockToolRegistry, null);
        assertFalse("Null settings manager must keep the legacy path",
                loop.isStreamingEnabled());
    }

    @Test
    public void streamingPath_forwardsDeltasAndCompletes() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.StreamingChatCallback callback =
                        invocation.getArgument(3, LlmApiService.StreamingChatCallback.class);
                callback.onDelta("Hello ");
                callback.onDelta("world");
                callback.onSuccess(new LlmApiService.LlmResponse("Hello world", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithToolsStreaming(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.StreamingChatCallback.class));

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);

        verify(mockCallback).onStreamDelta("Hello ");
        verify(mockCallback).onStreamDelta("world");
        verify(mockCallback).onComplete(eq("Hello world"), anyList());
        verify(mockCallback, never()).onError(anyString());
        verify(mockApiService, never()).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test
    public void streamingPath_errorPropagates() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.StreamingChatCallback callback =
                        invocation.getArgument(3, LlmApiService.StreamingChatCallback.class);
                callback.onDelta("par");
                callback.onError("Stream read error: timeout");
                return null;
            }
        }).when(mockApiService).sendMessageWithToolsStreaming(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.StreamingChatCallback.class));

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);

        verify(mockCallback).onStreamDelta("par");
        verify(mockCallback).onError(contains("timeout"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }

    @Test
    public void legacyPath_usedWhenStreamingDisabled() {
        agentConfig.setStreamResponses(false);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                        invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                callback.onSuccess(new LlmApiService.LlmResponse("Legacy response", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.ChatCallbackWithTools.class));

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);

        verify(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(mockApiService, never()).sendMessageWithToolsStreaming(anyList(),
                any(JsonArray.class), any(), any(LlmApiService.StreamingChatCallback.class));
        verify(mockCallback).onComplete(eq("Legacy response"), anyList());
    }
}
