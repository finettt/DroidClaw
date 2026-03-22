package io.finett.droidclaw.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;

public class LlmApiServiceTest {

    private final Gson gson = new Gson();

    @Test
    public void buildRequestBody_includesSystemPromptAndConversationHistory() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "Be concise.",
                256,
                0.25f
        );

        ChatMessage userMessage = new ChatMessage("Hello", ChatMessage.TYPE_USER);
        ChatMessage assistantMessage = new ChatMessage("Hi there", ChatMessage.TYPE_ASSISTANT);

        JsonObject requestBody = invokeBuildRequestBody(
                service,
                Arrays.asList(userMessage, assistantMessage)
        );

        assertEquals("test-model", requestBody.get("model").getAsString());
        assertEquals(256, requestBody.get("max_tokens").getAsInt());
        assertEquals(0.25f, requestBody.get("temperature").getAsFloat(), 0.0001f);

        JsonArray messages = requestBody.getAsJsonArray("messages");
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Be concise.", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("Hello", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("Hi there", messages.get(2).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void parseResponse_returnsAssistantMessageContent() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{"
                + "\"choices\":[{"
                + "\"message\":{\"content\":\"Generated reply\"}"
                + "}]"
                + "}";

        String parsed = invokeParseResponse(service, responseJson);

        assertEquals("Generated reply", parsed);
    }

    @Test
    public void parseResponse_returnsFallbackWhenChoicesMissing() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String parsed = invokeParseResponse(service, "{}");

        assertEquals("No response received", parsed);
    }

    @Test
    public void configurationErrorMessage_matchesExpectedText() {
        TestableLlmApiService service = new TestableLlmApiService(
                "",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        assertEquals(
                "API key not configured. Please set it in Settings.",
                service.buildConfigurationError()
        );
    }

    @Test
    public void requestOmitsAuthorizationHeader_forLmStudioApiKey() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "lm-studio",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        okhttp3.Request request = service.buildRequest(Collections.singletonList(
                new ChatMessage("Ping", ChatMessage.TYPE_USER)
        ));

        assertFalse(request.headers().names().contains("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("https://example.com/v1/chat/completions", request.url().toString());

        JsonObject requestJson = gson.fromJson(readBody(request), JsonObject.class);
        assertEquals("test-model", requestJson.get("model").getAsString());
    }

    @Test
    public void requestIncludesAuthorizationHeader_forRegularApiKey() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "real-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        okhttp3.Request request = service.buildRequest(Collections.singletonList(
                new ChatMessage("Ping", ChatMessage.TYPE_USER)
        ));

        assertTrue(request.headers().names().contains("Authorization"));
        assertEquals("Bearer real-key", request.header("Authorization"));
    }

    // ========== NEW TESTS FOR TOOL SUPPORT ==========

    @Test
    public void testToolCall_getters() {
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");
        
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
            "call_123",
            "file_read",
            args
        );
        
        assertEquals("call_123", toolCall.getId());
        assertEquals("file_read", toolCall.getName());
        assertEquals(args, toolCall.getArguments());
        assertEquals("test.txt", toolCall.getArguments().get("path").getAsString());
    }

    @Test
    public void testLlmResponse_withContentOnly() {
        LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
            "Hello, how can I help?",
            null
        );
        
        assertEquals("Hello, how can I help?", response.getContent());
        assertFalse("Should not have tool calls", response.hasToolCalls());
        assertTrue("Tool calls list should be empty", response.getToolCalls().isEmpty());
    }

    @Test
    public void testLlmResponse_withToolCalls() {
        JsonObject args = new JsonObject();
        args.addProperty("path", ".");
        
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
            "call_456",
            "file_list",
            args
        );
        
        List<LlmApiService.ToolCall> toolCalls = Arrays.asList(toolCall);
        
        LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
            null,
            toolCalls
        );
        
        assertNull("Content should be null", response.getContent());
        assertTrue("Should have tool calls", response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("file_list", response.getToolCalls().get(0).getName());
    }

    @Test
    public void testLlmResponse_withBothContentAndToolCalls() {
        JsonObject args = new JsonObject();
        
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall(
            "call_789",
            "shell",
            args
        );
        
        LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
            "Let me check that for you",
            Arrays.asList(toolCall)
        );
        
        assertEquals("Let me check that for you", response.getContent());
        assertTrue("Should have tool calls", response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
    }

    @Test
    public void testLlmResponse_emptyToolCallsList() {
        LlmApiService.LlmResponse response = new LlmApiService.LlmResponse(
            "Just text",
            new ArrayList<>()
        );
        
        assertEquals("Just text", response.getContent());
        assertFalse("Should not have tool calls", response.hasToolCalls());
    }

    @Test
    public void parseResponseWithTools_textOnlyResponse() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{"
                + "\"choices\":[{"
                + "\"message\":{\"content\":\"Simple text response\"}"
                + "}]"
                + "}";

        LlmApiService.LlmResponse response = invokeParseResponseWithTools(service, responseJson);

        assertEquals("Simple text response", response.getContent());
        assertFalse("Should not have tool calls", response.hasToolCalls());
    }

    @Test
    public void parseResponseWithTools_withToolCalls() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{"
                + "\"choices\":[{"
                + "\"message\":{"
                + "\"content\":null,"
                + "\"tool_calls\":[{"
                + "\"id\":\"call_abc123\","
                + "\"type\":\"function\","
                + "\"function\":{"
                + "\"name\":\"file_read\","
                + "\"arguments\":\"{\\\"path\\\":\\\"test.txt\\\"}\""
                + "}"
                + "}]"
                + "}"
                + "}]"
                + "}";

        LlmApiService.LlmResponse response = invokeParseResponseWithTools(service, responseJson);

        assertNull("Content should be null for tool call response", response.getContent());
        assertTrue("Should have tool calls", response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        
        LlmApiService.ToolCall toolCall = response.getToolCalls().get(0);
        assertEquals("call_abc123", toolCall.getId());
        assertEquals("file_read", toolCall.getName());
        assertEquals("test.txt", toolCall.getArguments().get("path").getAsString());
    }

    @Test
    public void parseResponseWithTools_multipleToolCalls() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{"
                + "\"choices\":[{"
                + "\"message\":{"
                + "\"content\":null,"
                + "\"tool_calls\":["
                + "{"
                + "\"id\":\"call_1\","
                + "\"type\":\"function\","
                + "\"function\":{"
                + "\"name\":\"file_read\","
                + "\"arguments\":\"{\\\"path\\\":\\\"file1.txt\\\"}\""
                + "}"
                + "},"
                + "{"
                + "\"id\":\"call_2\","
                + "\"type\":\"function\","
                + "\"function\":{"
                + "\"name\":\"file_list\","
                + "\"arguments\":\"{\\\"path\\\":\\\".\\\",\\\"recursive\\\":true}\""
                + "}"
                + "}"
                + "]"
                + "}"
                + "}]"
                + "}";

        LlmApiService.LlmResponse response = invokeParseResponseWithTools(service, responseJson);

        assertTrue("Should have tool calls", response.hasToolCalls());
        assertEquals(2, response.getToolCalls().size());
        
        assertEquals("file_read", response.getToolCalls().get(0).getName());
        assertEquals("file_list", response.getToolCalls().get(1).getName());
        assertTrue(response.getToolCalls().get(1).getArguments().get("recursive").getAsBoolean());
    }

    @Test
    public void parseResponseWithTools_emptyChoices() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{\"choices\":[]}";

        LlmApiService.LlmResponse response = invokeParseResponseWithTools(service, responseJson);

        assertEquals("No response received", response.getContent());
        assertFalse(response.hasToolCalls());
    }

    @Test
    public void parseResponseWithTools_noMessage() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                128,
                0.5f
        );

        String responseJson = "{\"choices\":[{}]}";

        LlmApiService.LlmResponse response = invokeParseResponseWithTools(service, responseJson);

        assertEquals("No message in response", response.getContent());
        assertFalse(response.hasToolCalls());
    }

    @Test
    public void buildRequestBody_withTools() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "Be concise.",
                256,
                0.25f
        );

        ChatMessage userMessage = new ChatMessage("Read test.txt", ChatMessage.TYPE_USER);
        
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", "file_read");
        function.addProperty("description", "Read a file");
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        function.add("parameters", params);
        tool.add("function", function);
        tools.add(tool);

        JsonObject requestBody = invokeBuildRequestBodyWithTools(service,
            Collections.singletonList(userMessage), tools);

        assertTrue("Should have tools field", requestBody.has("tools"));
        assertEquals(1, requestBody.getAsJsonArray("tools").size());
        assertTrue("Should have tool_choice field", requestBody.has("tool_choice"));
        assertEquals("auto", requestBody.get("tool_choice").getAsString());
    }

    @Test
    public void buildRequestBody_withToolCallMessage() throws Exception {
        TestableLlmApiService service = new TestableLlmApiService(
                "secret-key",
                "https://example.com/v1/chat/completions",
                "test-model",
                "System prompt",
                256,
                0.25f
        );

        ChatMessage userMessage = new ChatMessage("Read file", ChatMessage.TYPE_USER);
        
        // Create tool call message
        JsonObject args = new JsonObject();
        args.addProperty("path", "test.txt");
        LlmApiService.ToolCall toolCall = new LlmApiService.ToolCall("call_123", "file_read", args);
        ChatMessage toolCallMessage = ChatMessage.createToolCallMessage(Arrays.asList(toolCall));
        
        // Create tool result message
        ChatMessage toolResultMessage = ChatMessage.createToolResultMessage(
            "call_123", "file_read", "File content here"
        );

        JsonObject requestBody = invokeBuildRequestBody(service,
            Arrays.asList(userMessage, toolCallMessage, toolResultMessage));

        JsonArray messages = requestBody.getAsJsonArray("messages");
        // System + user + tool_call + tool_result = 4 messages
        assertEquals(4, messages.size());
        
        // Verify tool call message format
        JsonObject toolCallMsg = messages.get(2).getAsJsonObject();
        assertEquals("assistant", toolCallMsg.get("role").getAsString());
        assertTrue("Should have tool_calls", toolCallMsg.has("tool_calls"));
        
        // Verify tool result message format
        JsonObject toolResultMsg = messages.get(3).getAsJsonObject();
        assertEquals("tool", toolResultMsg.get("role").getAsString());
        assertEquals("call_123", toolResultMsg.get("tool_call_id").getAsString());
        assertEquals("File content here", toolResultMsg.get("content").getAsString());
    }

    private JsonObject invokeBuildRequestBody(TestableLlmApiService service, java.util.List<ChatMessage> messages) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("buildRequestBody", java.util.List.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(service, messages);
    }

    private JsonObject invokeBuildRequestBodyWithTools(TestableLlmApiService service,
            java.util.List<ChatMessage> messages, JsonArray tools) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("buildRequestBody",
            java.util.List.class, JsonArray.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(service, messages, tools);
    }

    private String invokeParseResponse(TestableLlmApiService service, String responseBody) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, responseBody);
    }

    private LlmApiService.LlmResponse invokeParseResponseWithTools(TestableLlmApiService service,
            String responseBody) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("parseResponseWithTools", String.class);
        method.setAccessible(true);
        return (LlmApiService.LlmResponse) method.invoke(service, responseBody);
    }

    private String readBody(okhttp3.Request request) throws Exception {
        okio.Buffer buffer = new okio.Buffer();
        if (request.body() != null) {
            request.body().writeTo(buffer);
        }
        return buffer.readUtf8();
    }

    private static final class TestableLlmApiService {
        private static final okhttp3.MediaType JSON =
                okhttp3.MediaType.get("application/json; charset=utf-8");

        private final Gson gson = new Gson();
        private final String apiKey;
        private final String apiUrl;
        private final String modelName;
        private final String systemPrompt;
        private final int maxTokens;
        private final float temperature;

        private TestableLlmApiService(
                String apiKey,
                String apiUrl,
                String modelName,
                String systemPrompt,
                int maxTokens,
                float temperature
        ) {
            this.apiKey = apiKey;
            this.apiUrl = apiUrl;
            this.modelName = modelName;
            this.systemPrompt = systemPrompt;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        private JsonObject buildRequestBody(java.util.List<ChatMessage> conversationHistory) {
            return buildRequestBody(conversationHistory, null);
        }

        private JsonObject buildRequestBody(java.util.List<ChatMessage> conversationHistory, JsonArray tools) {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", modelName);
            requestBody.addProperty("max_tokens", maxTokens);
            requestBody.addProperty("temperature", temperature);

            JsonArray messages = new JsonArray();

            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);

            for (ChatMessage chatMessage : conversationHistory) {
                messages.add(chatMessage.toApiMessage());
            }

            requestBody.add("messages", messages);
            
            // Add tools if provided
            if (tools != null && tools.size() > 0) {
                requestBody.add("tools", tools);
                requestBody.addProperty("tool_choice", "auto");
            }
            
            return requestBody;
        }

        private String parseResponse(String responseBody) {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                if (message != null && message.has("content") && !message.get("content").isJsonNull()) {
                    return message.get("content").getAsString();
                }
            }
            return "No response received";
        }

        private LlmApiService.LlmResponse parseResponseWithTools(String responseBody) {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            
            if (choices == null || choices.size() == 0) {
                return new LlmApiService.LlmResponse("No response received", null);
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            
            if (message == null) {
                return new LlmApiService.LlmResponse("No message in response", null);
            }

            // Extract content (may be null if there are tool calls)
            String content = null;
            if (message.has("content") && !message.get("content").isJsonNull()) {
                content = message.get("content").getAsString();
            }

            // Extract tool calls if present
            List<LlmApiService.ToolCall> toolCalls = null;
            if (message.has("tool_calls")) {
                JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                toolCalls = new ArrayList<>();
                
                for (com.google.gson.JsonElement toolCallElement : toolCallsArray) {
                    JsonObject toolCallObj = toolCallElement.getAsJsonObject();
                    String id = toolCallObj.get("id").getAsString();
                    JsonObject function = toolCallObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    String argumentsStr = function.get("arguments").getAsString();
                    
                    // Parse arguments string to JsonObject
                    JsonObject arguments = gson.fromJson(argumentsStr, JsonObject.class);
                    
                    toolCalls.add(new LlmApiService.ToolCall(id, name, arguments));
                }
            }

            return new LlmApiService.LlmResponse(content, toolCalls);
        }

        private okhttp3.Request buildRequest(java.util.List<ChatMessage> messages) {
            JsonObject requestBody = buildRequestBody(messages);
            String jsonBody = gson.toJson(requestBody);

            okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json");

            if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            }

            return requestBuilder
                    .post(okhttp3.RequestBody.create(jsonBody, JSON))
                    .build();
        }

        private String buildConfigurationError() {
            if (apiKey == null || apiKey.isEmpty()) {
                return "API key not configured. Please set it in Settings.";
            }
            return null;
        }
    }
}