package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Looper;

import com.google.gson.JsonArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Tests cancellation of {@link AgentLoop}: aborting in-flight requests,
 * preserving partially streamed text, and single-delivery guarantees.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentLoopCancellationTest {

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
        agentConfig.setStreamResponses(true);
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

    /** Stub the streaming API to emit deltas but never complete (in-flight stream). */
    private void stubStreamingDeltasOnly(String... deltas) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.StreamingChatCallback callback =
                        invocation.getArgument(3, LlmApiService.StreamingChatCallback.class);
                for (String delta : deltas) {
                    callback.onDelta(delta);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithToolsStreaming(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.StreamingChatCallback.class));
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> captureCancelledHistory() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockCallback).onCancelled(captor.capture());
        return captor.getValue();
    }

    @Test
    public void cancel_duringStreaming_preservesPartialText() {
        stubStreamingDeltasOnly("Hello ", "world");

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);

        assertTrue(loop.isCancelled() == false);
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(loop.isCancelled());
        verify(mockApiService).cancelAllRequests();

        List<ChatMessage> history = captureCancelledHistory();
        ChatMessage last = history.get(history.size() - 1);
        assertEquals("Partial text should be preserved as an assistant message",
                ChatMessage.TYPE_ASSISTANT, last.getType());
        assertEquals("Hello world", last.getContent());

        verify(mockCallback, never()).onComplete(anyString(), anyList());
        verify(mockCallback, never()).onError(anyString());
    }

    @Test
    public void cancel_beforeAnyResponse_dispatchesOnCancelledWithOriginalHistory() {
        stubStreamingDeltasOnly(); // request in flight, no deltas yet

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        verify(mockApiService).cancelAllRequests();

        List<ChatMessage> history = captureCancelledHistory();
        assertEquals(1, history.size());
        assertEquals(ChatMessage.TYPE_USER, history.get(0).getType());

        verify(mockCallback, never()).onComplete(anyString(), anyList());
        verify(mockCallback, never()).onError(anyString());
    }

    @Test
    public void cancel_twice_dispatchesOnCancelledOnlyOnce() {
        stubStreamingDeltasOnly("partial");

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);
        loop.cancel();
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        verify(mockApiService, times(1)).cancelAllRequests();
        verify(mockCallback, times(1)).onCancelled(anyList());
    }

    @Test
    public void cancel_afterCompletion_doesNotDispatchOnCancelled() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.StreamingChatCallback callback =
                        invocation.getArgument(3, LlmApiService.StreamingChatCallback.class);
                callback.onDelta("Done");
                callback.onSuccess(new LlmApiService.LlmResponse("Done", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithToolsStreaming(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.StreamingChatCallback.class));

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);
        verify(mockCallback).onComplete(eq("Done"), anyList());

        // User taps stop a moment after the response finished.
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        verify(mockCallback, never()).onCancelled(anyList());
    }

    @Test
    public void cancel_stopsFurtherIterations_afterToolCalls() {
        // First response requests a tool; after the tool result the loop would
        // normally send a follow-up request — cancellation must prevent it.
        List<LlmApiService.ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new LlmApiService.ToolCall(
                "call-1", "list_files", new com.google.gson.JsonObject()));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.StreamingChatCallback callback =
                        invocation.getArgument(3, LlmApiService.StreamingChatCallback.class);
                callback.onSuccess(new LlmApiService.LlmResponse("", toolCalls));
                return null;
            }
        }).doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                // Second request "hangs" in flight — cancellation aborts it.
                return null;
            }
        }).when(mockApiService).sendMessageWithToolsStreaming(anyList(), any(JsonArray.class),
                any(), any(LlmApiService.StreamingChatCallback.class));

        when(mockToolRegistry.getTool(anyString())).thenReturn(null);
        when(mockToolRegistry.executeTool(anyString(), any()))
                .thenReturn(io.finett.droidclaw.tool.ToolResult.success("ok"));

        AgentLoop loop = createLoop();
        loop.start(simpleConversation(), mockCallback);

        // The tool executed synchronously; cancel before the next request goes out.
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        verify(mockApiService, times(1)).cancelAllRequests();
        verify(mockCallback).onCancelled(anyList());
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }
}
