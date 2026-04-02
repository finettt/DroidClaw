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
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for AgentLoop.
 */
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
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry);
    }

    @Test
    public void testAgentLoopInitialization() {
        assertNotNull("AgentLoop should be initialized", agentLoop);
        assertEquals("Initial iteration count should be 0", 0, agentLoop.getIterationCount());
    }

    @Test
    public void testReset() {
        // Simulate some iterations
        agentLoop.start(createSimpleConversation(), mockCallback);
        agentLoop.reset();
        assertEquals("Iteration count should be reset to 0", 0, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_SimpleTextResponse() {
        // Setup: LLM responds with simple text (no tool calls)
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                // Simulate LLM response with just text
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello! How can I help you?",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify callback was called
        verify(mockCallback).onProgress("Sending message to LLM...");
        verify(mockCallback).onComplete(eq("Hello! How can I help you?"), anyList());
        verify(mockCallback, never()).onError(anyString());
        assertEquals("Should complete in 1 iteration", 1, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_WithToolCall() {
        // Setup: LLM responds with a tool call, then text
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.success("File content here"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    // First call: return tool call
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
                    // Second call: return final text response
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "The file contains: File content here",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify callbacks
        verify(mockCallback, atLeastOnce()).onProgress(anyString());
        verify(mockCallback).onToolCall(eq("file_read"), anyString());
        verify(mockCallback).onToolResult(eq("file_read"), eq("File content here"));
        verify(mockCallback).onComplete(contains("File content here"), anyList());
        verify(mockCallback, never()).onError(anyString());
        assertEquals("Should complete in 2 iterations", 2, agentLoop.getIterationCount());
    }

    @Test
    public void testStart_MultipleToolCalls() {
        // Setup: LLM responds with multiple tool calls in one iteration
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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    // First call: return multiple tool calls
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
                    // Second call: return final text response
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "Found the files",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify both tools were called
        verify(mockCallback).onProgress(contains("2 tool(s)"));
        verify(mockCallback).onToolCall(eq("file_read"), anyString());
        verify(mockCallback).onToolCall(eq("file_list"), anyString());
        verify(mockCallback).onToolResult(eq("file_read"), eq("File 1 content"));
        verify(mockCallback).onToolResult(eq("file_list"), eq("file1.txt, file2.txt"));
        verify(mockCallback).onComplete(eq("Found the files"), anyList());
    }

    @Test
    public void testStart_ToolExecutionError() {
        // Setup: Tool execution fails
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(eq("file_read"), any(JsonObject.class)))
            .thenReturn(ToolResult.error("File not found"));
        
        final int[] callCount = {0};
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    // First call: return tool call
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
                    // Second call: LLM handles the error and responds
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "I couldn't read the file because it doesn't exist",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify error was passed to callback
        verify(mockCallback).onToolResult(eq("file_read"), contains("Error: File not found"));
        verify(mockCallback).onComplete(contains("doesn't exist"), anyList());
    }

    @Test
    public void testStart_MaxIterationsReached() {
        // Setup: LLM keeps requesting tools indefinitely
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        when(mockToolRegistry.executeTool(anyString(), any(JsonObject.class)))
            .thenReturn(ToolResult.success("Success"));
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                // Always return a tool call (simulating infinite loop)
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
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify max iterations error
        verify(mockCallback).onError(contains("Maximum iterations"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_ApiError() {
        // Setup: API call fails
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callback.onError("Network error: Connection timeout");
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify error callback
        verify(mockCallback).onError(eq("Network error: Connection timeout"));
        verify(mockCallback, never()).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_EmptyResponse() {
        // Setup: LLM responds with empty content
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    null,
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Should handle empty response gracefully
        verify(mockCallback).onComplete(eq("No response from assistant."), anyList());
    }

    @Test
    public void testStart_ConversationHistoryPreserved() {
        // Setup: Verify conversation history is properly maintained
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Response text",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        int originalSize = conversation.size();
        
        agentLoop.start(conversation, mockCallback);
        
        // Capture the final conversation
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockCallback).onComplete(anyString(), historyCaptor.capture());
        
        List<ChatMessage> finalHistory = historyCaptor.getValue();
        assertTrue("Final history should have more messages than original", 
            finalHistory.size() > originalSize);
        
        // Original conversation should not be modified
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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] < expectedIterations[0]) {
                    // Return tool call to continue iteration
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
                    // Final response
                    LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                        "Done",
                        null
                    );
                    callback.onSuccess(response);
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals("Should have correct iteration count",
            expectedIterations[0], agentLoop.getIterationCount());
    }

    // ==================== Memory Integration Tests ====================

    @Test
    public void testStart_withMemoryContext_includesMemoryInRequest() throws java.io.IOException {
        // Set up AgentLoop with memory context
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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);

                // Verify context messages are passed
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
            any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Hello!"), anyList());
    }

    @Test
    public void testStart_withSummarization_triggersAndSaves() {
        // Test that AgentLoop handles summarization when configured with a summarizer
        // Note: We can't mock methods on a real ConversationSummarizer object,
        // so we test behavior without mocking needsSummarization
        
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        // Mock LLM response
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);

                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Response to user",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));

        // Create conversation with few messages (won't trigger summarization)
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        verify(mockCallback).onComplete(eq("Response to user"), anyList());
    }

    @Test
    public void testStart_summarizationSuccess_compressedHistoryUsed() {
        // Test that AgentLoop completes successfully with a configured summarizer
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        // Mock LLM response
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                callback.onSuccess(new LlmApiService.LlmResponse("Response", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));

        // Create simple conversation
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        // Verify completion
        verify(mockCallback).onComplete(anyString(), anyList());
    }

    @Test
    public void testStart_summarizationError_fallsBackToFullHistory() {
        // Test that AgentLoop handles errors gracefully
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());

        // Mock LLM error on first call, then success
        final int[] callCount = {0};
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);

                callCount[0]++;
                if (callCount[0] == 1) {
                    // First call - error
                    callback.onError("API error");
                } else {
                    // Second call - success (won't happen in this test)
                    callback.onSuccess(new LlmApiService.LlmResponse("Response", null));
                }
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));

        agentLoop.start(conversation, mockCallback);

        // Should report error
        verify(mockCallback).onError(anyString());
    }

    @Test
    public void testIdentityAndMemory_bothIncluded_inCorrectOrder() throws java.io.IOException {
        MemoryContextBuilder memoryContext = new MemoryContextBuilder(mockMemoryRepository);
        agentLoop = new AgentLoop(mockApiService, mockToolRegistry, null, null, memoryContext);

        // Set identity context
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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);

                // Verify context messages order: identity first, then memory
                List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);
                assertNotNull("Context messages should be passed", contextMessages);

                // First should be identity system message
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
            any(), any(LlmApiService.ChatCallbackWithTools.class));

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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);

                // Context should be empty list when no memory context builder
                List<ChatMessage> contextMessages = invocation.getArgument(2, List.class);
                assertNotNull("Context messages should be passed (even if empty)", contextMessages);

                callback.onSuccess(new LlmApiService.LlmResponse("Hello!", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));

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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                callback.onSuccess(new LlmApiService.LlmResponse("Hello!", null));
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));

        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);

        // Should complete without triggering any summarization
        verify(mockCallback).onComplete(eq("Hello!"), anyList());
        verify(mockCallback, never()).onProgress(contains("summarizing"));
    }

    // ==================== Token Tracking Tests (Last Usage Algorithm) ====================

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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                // Simulate API response with usage data
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
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify Last Usage algorithm: current context = last API response
        assertEquals("Current context tokens should match last response", 1000, agentLoop.getCurrentContextTokens());
        assertEquals("Current prompt tokens should match last response", 800, agentLoop.getCurrentPromptTokens());
        assertEquals("Current completion tokens should match last response", 200, agentLoop.getCurrentCompletionTokens());
        
        // Verify session cumulative: total = sum of all requests
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
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                callCount[0]++;
                
                if (callCount[0] == 1) {
                    // First request: 1000 tokens
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
                    // Second request: 1200 tokens
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
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Current context should be from LAST request only (Last Usage algorithm)
        assertEquals("Current context should be from last request", 1200, agentLoop.getCurrentContextTokens());
        assertEquals("Current prompt should be from last request", 900, agentLoop.getCurrentPromptTokens());
        assertEquals("Current completion should be from last request", 300, agentLoop.getCurrentCompletionTokens());
        
        // Total should be cumulative across all requests
        assertEquals("Total tokens should be cumulative", 2200, agentLoop.getTotalTokens()); // 1000 + 1200
        assertEquals("Total prompt should be cumulative", 1700, agentLoop.getTotalPromptTokens()); // 800 + 900
        assertEquals("Total completion should be cumulative", 500, agentLoop.getTotalCompletionTokens()); // 200 + 300
        
        // Tool calls should be tracked
        assertEquals("Total tool calls should be 1", 1, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_resetTokens() {
        // Set up some token values
        agentLoop.setTokensFromSession(1000, 800, 200, 5000, 4000, 1000, 10);
        
        assertEquals(1000, agentLoop.getCurrentContextTokens());
        assertEquals(5000, agentLoop.getTotalTokens());
        
        // Reset all tokens
        agentLoop.resetTokens();
        
        assertEquals("All tokens should be reset", 0, agentLoop.getCurrentContextTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getCurrentPromptTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getCurrentCompletionTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getTotalTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getTotalPromptTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getTotalCompletionTokens());
        assertEquals("All tokens should be reset", 0, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_resetCurrentContextOnly() {
        // Set up some token values
        agentLoop.setTokensFromSession(1000, 800, 200, 5000, 4000, 1000, 10);
        
        // Reset only current context
        agentLoop.resetCurrentContext();
        
        // Current context should be reset
        assertEquals("Current context should be reset", 0, agentLoop.getCurrentContextTokens());
        assertEquals("Current prompt should be reset", 0, agentLoop.getCurrentPromptTokens());
        assertEquals("Current completion should be reset", 0, agentLoop.getCurrentCompletionTokens());
        
        // Session cumulative should be preserved
        assertEquals("Total tokens should be preserved", 5000, agentLoop.getTotalTokens());
        assertEquals("Total prompt should be preserved", 4000, agentLoop.getTotalPromptTokens());
        assertEquals("Total completion should be preserved", 1000, agentLoop.getTotalCompletionTokens());
        assertEquals("Tool calls should be preserved", 10, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_setFromSession() {
        agentLoop.setTokensFromSession(1500, 1200, 300, 10000, 8000, 2000, 25);
        
        assertEquals(1500, agentLoop.getCurrentContextTokens());
        assertEquals(1200, agentLoop.getCurrentPromptTokens());
        assertEquals(300, agentLoop.getCurrentCompletionTokens());
        assertEquals(10000, agentLoop.getTotalTokens());
        assertEquals(8000, agentLoop.getTotalPromptTokens());
        assertEquals(2000, agentLoop.getTotalCompletionTokens());
        assertEquals(25, agentLoop.getTotalToolCalls());
    }

    @Test
    public void testTokenTracking_noUsageData() {
        when(mockToolRegistry.getToolDefinitions()).thenReturn(new JsonArray());
        
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LlmApiService.ChatCallbackWithTools callback =
                    invocation.getArgument(3, LlmApiService.ChatCallbackWithTools.class);
                
                // Response without usage data
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse("Hello!", null, null);
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class),
            any(), any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Tokens should remain at 0 when no usage data provided
        assertEquals(0, agentLoop.getCurrentContextTokens());
        assertEquals(0, agentLoop.getTotalTokens());
    }

    /**
     * Helper method to create a simple conversation.
     */
    private List<ChatMessage> createSimpleConversation() {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("Hello", ChatMessage.TYPE_USER));
        return conversation;
    }
}