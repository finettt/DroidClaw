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
import io.finett.droidclaw.model.ChatMessage;
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
                // Simulate LLM response with just text
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Hello! How can I help you?", 
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class), 
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
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
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
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
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
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
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
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
            any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        // Verify max iterations error
        verify(mockCallback).onError(contains("Maximum iterations reached"));
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
                callback.onError("Network error: Connection timeout");
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class), 
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    null,
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class), 
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
                LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
                    "Response text",
                    null
                );
                callback.onSuccess(response);
                return null;
            }
        }).when(mockApiService).sendMessageWithTools(anyList(), any(JsonArray.class), 
            any(LlmApiService.ChatCallbackWithTools.class));
        
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
                    invocation.getArgument(2, LlmApiService.ChatCallbackWithTools.class);
                
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
            any(LlmApiService.ChatCallbackWithTools.class));
        
        List<ChatMessage> conversation = createSimpleConversation();
        agentLoop.start(conversation, mockCallback);
        
        assertEquals("Should have correct iteration count", 
            expectedIterations[0], agentLoop.getIterationCount());
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