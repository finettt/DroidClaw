package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.util.Collections;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AgentLoopRunScopeTest {
    private LlmApiService api;
    private ToolRegistry tools;
    private AgentLoop loop;
    private AgentLoop.AgentCallback callback;
    private Provider provider;
    private Model model;

    @Before
    public void setUp() {
        api = mock(LlmApiService.class);
        tools = mock(ToolRegistry.class);
        callback = mock(AgentLoop.AgentCallback.class);
        when(tools.getToolDefinitions()).thenReturn(new JsonArray());
        Tool write = mock(Tool.class);
        when(write.requiresApproval()).thenReturn(true);
        when(write.getApprovalDescription(any())).thenReturn("Write file");
        when(tools.getTool("write_file")).thenReturn(write);
        loop = new AgentLoop(api, tools, null);
        provider = new Provider();
        provider.setId("workflow-provider");
        model = new Model();
        model.setId("workflow-model");
        loop.setApprovalOverrides(Collections.singletonMap(
                "write_file", ToolApprovalMode.ALWAYS_APPROVE));
        loop.setModelOverride(provider, model);
    }

    @Test
    public void completionClearsOverridesBeforeCallbackAndNextChatAsks() throws Exception {
        LlmApiService.ChatCallbackWithTools pending = startScopedRun();
        doAnswer(invocation -> {
            assertOverridesCleared();
            return null;
        }).when(callback).onComplete(anyString(), anyList());
        pending.onSuccess(new LlmApiService.LlmResponse("done", null));
        verify(callback).onComplete(eq("done"), anyList());
        assertNextChatUsesGlobalModelAndAsks();
    }

    @Test
    public void errorClearsOverridesBeforeCallbackAndNextChatAsks() throws Exception {
        LlmApiService.ChatCallbackWithTools pending = startScopedRun();
        doAnswer(invocation -> {
            assertOverridesCleared();
            return null;
        }).when(callback).onError(anyString());
        pending.onError("network failed");
        verify(callback).onError("network failed");
        assertNextChatUsesGlobalModelAndAsks();
    }

    @Test
    public void cancellationClearsOverridesBeforeCallbackAndNextChatAsks() throws Exception {
        startScopedRun();
        doAnswer(invocation -> {
            assertOverridesCleared();
            return null;
        }).when(callback).onCancelled(anyList());
        loop.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        verify(callback).onCancelled(anyList());
        assertNextChatUsesGlobalModelAndAsks();
    }

    @Test
    public void iterationLimitClearsOverrides() throws Exception {
        when(tools.executeTool(eq("read_file"), any())).thenReturn(ToolResult.success("read"));
        loop.setMaxIterations(1);
        LlmApiService.ChatCallbackWithTools pending = startScopedRun();
        pending.onSuccess(new LlmApiService.LlmResponse(null, Collections.singletonList(
                new LlmApiService.ToolCall("read", "read_file", new JsonObject()))));
        verify(callback).onError(contains("Maximum iterations"));
        assertOverridesCleared();
    }

    @Test
    public void structuredErrorClearsOverrides() throws Exception {
        loop.setResponseSchema(new JsonObject());
        loop.start(Collections.singletonList(new ChatMessage("run", ChatMessage.TYPE_USER)), callback);
        ArgumentCaptor<LlmApiService.StructuredResponseCallback> captor =
                ArgumentCaptor.forClass(LlmApiService.StructuredResponseCallback.class);
        verify(api).sendMessageStructured(anyList(), any(), anyList(), any(),
                same(provider), same(model), any(), captor.capture());
        captor.getValue().onError("invalid schema");
        verify(callback).onError("invalid schema");
        assertOverridesCleared();
    }

    private LlmApiService.ChatCallbackWithTools startScopedRun() {
        assertTrue(loop.hasModelOverride());
        loop.start(Collections.singletonList(new ChatMessage("run", ChatMessage.TYPE_USER)), callback);
        ArgumentCaptor<LlmApiService.ChatCallbackWithTools> captor =
                ArgumentCaptor.forClass(LlmApiService.ChatCallbackWithTools.class);
        verify(api).sendMessageWithTools(anyList(), any(), anyList(),
                same(provider), same(model), any(), captor.capture());
        return captor.getValue();
    }

    private void assertOverridesCleared() throws Exception {
        assertFalse(loop.hasModelOverride());
        for (String name : new String[]{"scopedApprovalOverrides", "modelOverrideProvider", "modelOverrideModel"}) {
            Field field = AgentLoop.class.getDeclaredField(name);
            field.setAccessible(true);
            assertNull(name + " must not survive a terminal callback", field.get(loop));
        }
    }

    private void assertNextChatUsesGlobalModelAndAsks() throws Exception {
        assertOverridesCleared();
        clearInvocations(api, tools);
        AgentLoop.AgentCallback nextCallback = mock(AgentLoop.AgentCallback.class);
        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(4);
            cb.onSuccess(new LlmApiService.LlmResponse(null, Collections.singletonList(
                    new LlmApiService.ToolCall("write", "write_file", new JsonObject()))));
            return null;
        }).when(api).sendMessageWithTools(anyList(), any(), anyList(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        loop.start(Collections.singletonList(new ChatMessage("chat", ChatMessage.TYPE_USER)), nextCallback);
        verify(api).sendMessageWithTools(anyList(), any(), anyList(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
        verify(api, never()).sendMessageWithTools(anyList(), any(), anyList(),
                any(Provider.class), any(Model.class), any(), any(LlmApiService.ChatCallbackWithTools.class));
        verify(nextCallback).onApprovalRequired(eq("write_file"), eq("Write file"),
                any(), any(AgentLoop.ApprovalCallback.class));
        verify(tools, never()).executeTool(anyString(), any());
    }
}
