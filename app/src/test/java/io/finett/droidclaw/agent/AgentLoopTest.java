package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.TokenUsage;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.Effort;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.util.SettingsManager;
import io.finett.droidclaw.tool.Tool;
import java.util.Collections;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

@RunWith(RobolectricTestRunner.class)
public class AgentLoopTest {

    @Mock
    private LlmApiService mockApiService;

    @Mock
    private ToolRegistry mockToolRegistry;

    @Mock
    private AgentLoop.AgentCallback mockCallback;

    @Mock
    private MemoryRepository mockMemoryRepository;

    private AgentLoop agentLoop;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null);
    }

    @Test
    public void testAgentLoopInitialization() {
        assertNotNull("AgentLoop should be initialized", agentLoop);
        assertEquals("Initial iteration count should be 0", 0, agentLoop.getIterationCount());
    }

    @Test
    public void testReset() {
        agentLoop.start(createSimpleConversation(), mockCallback);
        agentLoop.reset();
        assertEquals("Iteration count should be reset to 0", 0, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_SimpleTextResponse() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello! How can I help you?",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onProgress("Sending message to LLM...");
        verify(mockCallback).onComplete(eq("Hello! How can I help you?"), anyList());
        verify(mockCallback, never()).onError(anyString());
        assertEquals("Should complete in 1 iteration", 1, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_WithToolCall() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.success("File content here"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    JsonObject args = new JsonObject();
                    args.addProperty("path", "test.txt");
                    
                    LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
                        "call_123",
                        "file_read",
                        args
                    );
                    
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        null,
                        Arrays.asList(toolCall)
                    );
                    callback.onSuccess(response);
                } else {
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "The file contains: File content here",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback, atLeastOnce()).onProgress(anyString());
        verify(mockCallback).onToolCall(eq("file_read"), anyString());
        verify(mockCallback).onToolResult(eq("file_read"), eq("File content here"));
        verify(mockCallback).onComplete(contains("File content here"), anyList());
        verify(mockCallback, never()).onError(anyString());
        assertEquals("Should complete in 2 iterations", 2, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_MultipleToolCalls() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.success("File 1 content"));
        when(mockToolRegistry.executeTool(eq("file_list"), any(JsonObject.class)))
            .thenReturn(ToolResult.success("file1.txt, file2.txt"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    JsonObject args1 = new JsonObject();
                    args1.addProperty("path", "test1.txt");
                    
                    JsonObject args2 = new JsonObject();
                    args2.addProperty("path", ".");
                    
                    List<LlmApiService.ToolCall> toolCalls = Arrays.asList(
                        new LlmApiService.ToolCall("call_1", "file_read", args1),
                        new LlmApiService.ToolCall("call_2", "file_list", args2)
                    );
                    
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        null,
                        toolCalls
                    );
                    callback.onSuccess(response);
                } else {
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "Found the files",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onProgress(contains("2 tool(s)"));
        verify(mockCallback).onToolCall(eq("file_read"), anyString());
        verify(mockCallback).onToolCall(eq("file_list"), anyString());
        verify(mockCallback).onToolResult(eq("file_read"), eq("File 1 content"));
        verify(mockCallback).onToolResult(eq("file_list"), eq("file1.txt, file2.txt"));
        verify(mockCallback).onComplete(eq("Found the files"), anyList());
    }

    @Test
    public void testStart_ToolExecutionError() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.error("File not found"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    JsonObject args = new JsonObject();
                    args.addProperty("path", "nonexistent.txt");
                    
                    LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
                        "call_123",
                        "file_read",
                        args
                    );
                    
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        null,
                        Arrays.asList(toolCall)
                    );
                    callback.onSuccess(response);
                } else {
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "I couldn't read the file because it doesn't exist",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onToolResult(eq("file_read"), contains("Error: File not found"));
        verify(mockCallback).onComplete(contains("doesn't exist"), anyList());
    }

    @Test
    public void testStart_MaxIterationsReached() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(anyString(), any(JsonObject.class)))
            .thenReturn(ToolResult.success("Success"));
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                JsonObject args = new JsonObject();
                LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
                    "call_" + System.currentTimeMillis(),
                    "file_read",
                    args
                );
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    null,
                    Arrays.asList(toolCall)
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onError(contains("Maximum iterations"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_ApiError() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callback.onError("Network error: Connection timeout");
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onError(eq("Network error: Connection timeout"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_EmptyResponse() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    null,
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        verify(mockCallback).onComplete(eq("No response from assistant."), anyList());
    }

    @Test
    public void testStart_ConversationHistoryPreserved() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Response text",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        int originalSize = conversation.size();
        
        agentLoop.start(conversation, mockCallback);
        
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockCallback).onComplete(anyString(), historyCaptor.capture());
        
        List<ChatMessage> finalHistory = historyCaptor.getValue();
        assertTrue("Final history should have more messages than original", 
            finalHistory.size() > originalSize);
        
        assertEquals("Original conversation should be unchanged",
            originalSize, conversation.size());
    }

    @Test
    public void testGetIterationCount() {
        assertEquals("Initial iteration count should be 0", 0, agentLoop.getIterationCount());
    }

    @Test
    public void testIterationCountIncrement() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        final int[] expectedIterations = {3};
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] < expectedIterations[0]) {
                    JsonObject args = new JsonObject();
                    LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
                        "call_" + callCount[0],
                        "file_list",
                        args
                    );
                    
                    when(mockToolRegistry.executeTool(eq("file_list"), any(JsonObject.class)))
                        .thenReturn(ToolResult.success("Files"));
                    
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        null,
                        Arrays.asList(toolCall)
                    );
                    callback.onSuccess(response);
                } else {
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "Done",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals("Should have correct iteration count",
            expectedIterations[0], agentLoop.getIterationCount());
    }

    @Test
    public void testStart_withMemoryContext_includesMemoryInRequest() throws java.io.IOException {
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(mockMemoryRepository);
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, memoryContext);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);

                List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);
                assertNotNull("Context messages should be passed", contextMessages);

                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello!",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Hello!"), anyList());
    }

    @Test
    public void testStart_withSummarization_triggersAndSaves() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);

                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Response to user",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Response to user"), anyList());
    }

    @Test
    public void testStart_summarizationSuccess_compressedHistoryUsed() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                callback.onSuccess(new LlmApiService.LlmResponse("Response", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_summarizationError_fallsBackToFullHistory() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        final int[] callCount = {0};
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);

                callCount[0]++;
                if (callCount[0] == 1) {
                    callback.onError("API error");
                } else {
                    callback.onSuccess(new LlmApiService.LlmResponse("Response", null));
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onError(anyString());
    }

    @Test
    public void testIdentityAndMemory_bothIncluded_inCorrectOrder() throws java.io.IOException {
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(mockMemoryRepository);
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, memoryContext);

        List<ChatMessage> identityMessages = Arrays.asList(
            new ChatMessage("You are a helpful assistant", ChatMessage.TYPE_SYSTEM)
        );
        agentLoop.setIdentityContext(identityMessages);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockMemoryRepository.readLongTermMemory()).thenReturn("");
        when(mockMemoryRepository.readTodayNote()).thenReturn("");
        when(mockMemoryRepository.readYesterdayNote()).thenReturn("");

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);

                List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);
                assertNotNull("Context messages should be passed", contextMessages);

                assertTrue("First context message should be system (identity)",
                    contextMessages.get(0).isSystem());

                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello!",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Hello!"), anyList());
    }

    @Test
    public void testMemoryContextBuilder_null_handledGracefully() {
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, null);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);

                List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);
                assertNotNull("Context messages should be passed (even if empty)", contextMessages);

                callback.onSuccess(new LlmApiService.LlmResponse("Hello!", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Hello!"), anyList());
    }

    @Test
    public void testSummarizer_null_handledGracefully() {
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, null);

        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                callback.onSuccess(new LlmApiService.LlmResponse("Hello!", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Hello!"), anyList());
        verify(mockCallback, never()).onProgress(contains("summarizing"));
    }

    @Test
    public void testTokenTracking_initialState() {
        assertEquals("Initial current context tokens should be 0", 0, agentLoop.getCurrentContextTokens());
        assertEquals("Initial current prompt tokens should be 0", 0, agentLoop.getCurrentPromptTokens());
        assertEquals("Initial current completion tokens should be 0", 0, agentLoop.getCurrentCompletionTokens());
        assertEquals("Initial total tokens should be 0", 0, agentLoop.getTotalTokens());
        assertEquals("Initial total prompt tokens should be 0", 0, agentLoop.getTotalPromptTokens());
        assertEquals("Initial total completion tokens should be 0", 0, agentLoop.getTotalCompletionTokens());
        assertEquals("Initial total tool calls should be 0", 0, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_lastUsageAlgorithm() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                TokenUsage usage = new TokenUsage(1000, 800, 200);
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello!",
                    null,
                    usage
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals("Current context tokens should match last response", 1000, agentLoop.getCurrentContextTokens());
        assertEquals("Current prompt tokens should match last response", 800, agentLoop.getCurrentPromptTokens());
        assertEquals("Current completion tokens should match last response", 200, agentLoop.getCurrentCompletionTokens());
        
        assertEquals("Total tokens should match cumulative", 1000, agentLoop.getTotalTokens());
        assertEquals("Total prompt tokens should match cumulative", 800, agentLoop.getTotalPromptTokens());
        assertEquals("Total completion tokens should match cumulative", 200, agentLoop.getTotalCompletionTokens());
    }

    @Test
    public void testTokenTracking_cumulativeAcrossMultipleRequests() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.success("File content"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    TokenUsage usage = new TokenUsage(1000, 800, 200);
                    JsonObject args = new JsonObject();
                    args.addProperty("path", "test.txt");
                    LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall("call_1", "file_read", args);
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        null,
                        Arrays.asList(toolCall),
                        usage
                    );
                    callback.onSuccess(response);
                } else {
                    TokenUsage usage = new TokenUsage(1200, 900, 300);
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "Done",
                        null,
                        usage
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals("Current context should be from last request", 1200, agentLoop.getCurrentContextTokens());
        assertEquals("Current prompt should be from last request", 900, agentLoop.getCurrentPromptTokens());
        assertEquals("Current completion should be from last request", 300, agentLoop.getCurrentCompletionTokens());
        
        assertEquals("Total tokens should be cumulative", 2200, agentLoop.getTotalTokens()); // 1000 + 1200
        assertEquals("Total prompt should be cumulative", 1700, agentLoop.getTotalPromptTokens()); // 800 + 900
        assertEquals("Total completion should be cumulative", 500, agentLoop.getTotalCompletionTokens()); // 200 + 300
        
        assertEquals("Total tool calls should be 1", 1, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_noUsageData() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse("Hello!", null, null);
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals(0, agentLoop.getCurrentContextTokens());
        assertEquals(0, agentLoop.getTotalTokens());
    }

    private List<ChatMessage> createSimpleConversation() {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        return conversation;
    }
    @Test
    public void scopedRejectBlocksBackgroundToolBeforeDispatch() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        Tool tool = mock(Tool.class);
        when(tool.requiresApproval()).thenReturn(true);
        when(mockToolRegistry.getTool("write_file")).thenReturn(tool);
        io.finett.droidclaw.util.SettingsManager workflowSettings =
                mock(io.finett.droidclaw.util.SettingsManager.class);
        io.finett.droidclaw.model.AgentConfig workflowConfig =
                mock(io.finett.droidclaw.model.AgentConfig.class);
        when(workflowSettings.getMaxAgentIterations()).thenReturn(20);
        when(workflowSettings.isRequireApproval()).thenReturn(true);
        when(workflowSettings.getAgentConfig()).thenReturn(workflowConfig);
        when(workflowConfig.isBackgroundExecEnabled()).thenReturn(true);
        when(workflowConfig.isStreamResponses()).thenReturn(false);
        AgentLoop workflowLoop = new AgentLoop(mockApiService, mockToolRegistry, workflowSettings);
        workflowLoop.setApprovalOverrides(Collections.singletonMap(
                "write_file", ToolApprovalMode.ALWAYS_REJECT));

        final int[] calls = {0};
        doAnswer(invocation -> {
            LlmApiService.ChatCallbackWithTools cb = invocation.getArgument(4);
            if (calls[0]++ == 0) {
                JsonObject args = new JsonObject();
                args.addProperty("background", true);
                cb.onSuccess(new LlmApiService.LlmResponse(null, Collections.singletonList(
                        new LlmApiService.ToolCall("call-bg", "write_file", args))));
            } else {
                cb.onSuccess(new LlmApiService.LlmResponse("done", null));
            }
            return null;
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
                any(), any(), any(LlmApiService.ChatCallbackWithTools.class));

        workflowLoop.start(createSimpleConversation(), mockCallback);

        verify(mockToolRegistry, never()).executeTool(eq("write_file"), any(JsonObject.class));
        verify(mockCallback).onToolResult(eq("write_file"), contains("blocked"));
        verify(mockCallback, never()).onApprovalRequired(anyString(), anyString(),
                any(JsonObject.class), any(AgentLoop.ApprovalCallback.class));
    }

    // ==================== Effort turn-budget stop behavior ====================

    private void stubApiWithToolCallsFor(int toolCallCount) {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(anyString(), any(JsonObject.class)))
                .thenReturn(ToolResult.success("ok"));
        Answer<Void> toolCallAnswer = new Answer<Void>() {
            private int calls = 0;

            @Override
            public Void answer(InvocationOnMock invocation) {
                calls++;
                LlmApiService.ChatCallbackWithTools callback =
                        invocation.getArgument(4, LlmApiService.ChatCallbackWithTools.class);
                if (calls <= toolCallCount) {
                    LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
                            "call_" + calls, "file_read", new JsonObject());
                    callback.onSuccess(new LlmApiService.LlmResponse(null,
                            Collections.singletonList(toolCall)));
                } else {
                    callback.onSuccess(new LlmApiService.LlmResponse("done", null));
                }
                return null;
            }
        };
        doAnswer(toolCallAnswer).when(mockApiService).sendMessageWithTools(
                anyList(), any(JsonArray.class), any(), any(),
                any(LlmApiService.ChatCallbackWithTools.class));
    }

    @Test
    public void effortLowStopsLoopAtTurnBudget() {
        stubApiWithToolCallsFor(100); // would otherwise run forever
        agentLoop.setEffortOverride(Effort.LOW); // budget 8
        agentLoop.start(createSimpleConversation(), mockCallback);

        verify(mockCallback).onError(contains("Maximum iterations"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
        assertEquals("LOW budget of 8 stops the loop at iteration 9", 9, agentLoop.getIterationCount());
    }

    @Test
    public void effortMaxWithNoExplicitCapRunsBeyondBudget() {
        stubApiWithToolCallsFor(12); // tool call for 12 iterations, then a text response
        agentLoop.setEffortOverride(Effort.MAX); // unbounded budget

        agentLoop.start(createSimpleConversation(), mockCallback);

        verify(mockCallback).onComplete(eq("done"), anyList());
        verify(mockCallback, never()).onError(contains("Maximum iterations"));
        assertTrue("MAX must not impose the LOW/MEDIUM budget", agentLoop.getIterationCount() >= 11);
    }

    @Test
    public void explicitMaxIterationsWinsOverTighterBudget() {
        stubApiWithToolCallsFor(100);
        agentLoop.setEffortOverride(Effort.HIGH); // budget 40
        agentLoop.setMaxIterations(5);             // explicit cap tighter than 40
        agentLoop.start(createSimpleConversation(), mockCallback);

        verify(mockCallback).onError(contains("Maximum iterations"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
        assertEquals("explicit cap of 5 stops the loop at iteration 6", 6, agentLoop.getIterationCount());
    }

    // ==================== Chat-switcher override vs. global effort ====================

    /** A real AgentConfig so the test can assert the global config is never mutated. */
    private static SettingsManager settingsManagerWithEffort(String effort) {
        AgentConfig config = new AgentConfig();
        config.setEffort(effort);
        SettingsManager settingsManager = mock(SettingsManager.class);
        when(settingsManager.getAgentConfig()).thenReturn(config);
        return settingsManager;
    }

    @Test
    public void effortOverride_takesPrecedenceOverGlobalConfig() {
        SettingsManager settingsManager = settingsManagerWithEffort("high");
        AgentConfig config = settingsManager.getAgentConfig(); // global = HIGH (budget 40)

        AgentLoop loop = new AgentLoop(mockApiService, mockToolRegistry, settingsManager);
        assertEquals("Global effort resolves from the agent config", Effort.HIGH, loop.getEffort());

        loop.setEffortOverride(Effort.MEDIUM); // chat switcher picks Medium
        assertEquals("Per-session override wins over the global effort", Effort.MEDIUM, loop.getEffort());

        // The override must never mutate the stored global AgentConfig.
        assertEquals("Global AgentConfig.effort is never written by the override",
                "high", config.getEffort());

        loop.setEffortOverride(null);
        assertEquals("Clearing the override falls back to the global effort", Effort.HIGH, loop.getEffort());
    }

    @Test
    public void effortOverride_overGlobal_drives_loop_stop_budget() {
        SettingsManager settingsManager = settingsManagerWithEffort("high");
        AgentLoop loop = new AgentLoop(mockApiService, mockToolRegistry, settingsManager);
        assertEquals("Baseline global effort is High (budget 40)", Effort.HIGH, loop.getEffort());

        stubApiWithToolCallsFor(100); // would otherwise run toward the global budget
        loop.setEffortOverride(Effort.LOW); // switcher picks Low (budget 8)
        loop.start(createSimpleConversation(), mockCallback);

        verify(mockCallback).onError(contains("Maximum iterations"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
        assertEquals("Low override stops the loop at 9 even though the global effort is High",
                9, loop.getIterationCount());
    }

}
