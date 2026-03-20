package io.finett.droidclaw.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

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

    private JsonObject invokeBuildRequestBody(TestableLlmApiService service, java.util.List<ChatMessage> messages) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("buildRequestBody", java.util.List.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(service, messages);
    }

    private String invokeParseResponse(TestableLlmApiService service, String responseBody) throws Exception {
        Method method = TestableLlmApiService.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, responseBody);
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
                JsonObject message = new JsonObject();
                message.addProperty("role", chatMessage.isUser() ? "user" : "assistant");
                message.addProperty("content", chatMessage.getContent());
                messages.add(message);
            }

            requestBody.add("messages", messages);
            return requestBody;
        }

        private String parseResponse(String responseBody) {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                if (message != null) {
                    return message.get("content").getAsString();
                }
            }
            return "No response received";
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