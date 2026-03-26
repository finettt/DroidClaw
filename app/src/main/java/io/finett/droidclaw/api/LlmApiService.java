package io.finett.droidclaw.api;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LlmApiService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final SettingsManager settingsManager;
    private final Handler mainHandler;

    /**
     * Represents a tool call from the LLM.
     */
    public static class ToolCall {
        private final String id;
        private final String name;
        private final JsonObject arguments;

        public ToolCall(String id, String name, JsonObject arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public JsonObject getArguments() {
            return arguments;
        }
    }

    /**
     * Represents the response from the LLM.
     */
    public static class LlmResponse {
        private final String content;
        private final List<ToolCall> toolCalls;
        private final boolean hasToolCalls;

        public LlmResponse(String content, List<ToolCall> toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            this.hasToolCalls = !this.toolCalls.isEmpty();
        }

        public String getContent() {
            return content;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean hasToolCalls() {
            return hasToolCalls;
        }
    }

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public interface ChatCallbackWithTools {
        void onSuccess(LlmResponse response);
        void onError(String error);
    }

    public LlmApiService(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendMessage(List<ChatMessage> conversationHistory, ChatCallback callback) {
        sendMessage(conversationHistory, null, callback);
    }

    public void sendMessage(List<ChatMessage> conversationHistory, JsonArray tools, ChatCallback callback) {
        if (!settingsManager.isConfigured()) {
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        JsonObject requestBody = buildRequestBody(conversationHistory, tools);
        String jsonBody = gson.toJson(requestBody);

        Request.Builder requestBuilder = new Request.Builder()
                .url(settingsManager.getApiUrl())
                .addHeader("Content-Type", "application/json");

        String apiKey = settingsManager.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        Request request = requestBuilder
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("API error: " + response.code() + " - " + responseBody));
                        return;
                    }

                    String assistantMessage = parseResponse(responseBody);
                    mainHandler.post(() -> callback.onSuccess(assistantMessage));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Send message with tool support and get structured response.
     *
     * @param conversationHistory Full conversation history
     * @param tools Tool definitions (can be null)
     * @param callback Callback with LlmResponse containing content and tool calls
     */
    public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools, ChatCallbackWithTools callback) {
        if (!settingsManager.isConfigured()) {
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        JsonObject requestBody = buildRequestBody(conversationHistory, tools);
        String jsonBody = gson.toJson(requestBody);

        Request.Builder requestBuilder = new Request.Builder()
                .url(settingsManager.getApiUrl())
                .addHeader("Content-Type", "application/json");

        String apiKey = settingsManager.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        Request request = requestBuilder
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("API error: " + response.code() + " - " + responseBody));
                        return;
                    }

                    LlmResponse llmResponse = parseResponseWithTools(responseBody);
                    mainHandler.post(() -> callback.onSuccess(llmResponse));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    private JsonObject buildRequestBody(List<ChatMessage> conversationHistory, JsonArray tools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", settingsManager.getModelName());
        requestBody.addProperty("max_tokens", settingsManager.getMaxTokens());
        requestBody.addProperty("temperature", 0.7f);

        JsonArray messages = new JsonArray();

        // Add conversation history (no system prompt in new configuration)
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
            if (message != null && message.has("content")) {
                JsonElement contentElement = message.get("content");
                if (!contentElement.isJsonNull()) {
                    return contentElement.getAsString();
                }
            }
        }
        return "No response received";
    }

    /**
     * Parse response with tool calls support.
     *
     * @param responseBody JSON response from API
     * @return LlmResponse with content and tool calls
     */
    private LlmResponse parseResponseWithTools(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        
        if (choices == null || choices.size() == 0) {
            return new LlmResponse("No response received", null);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        
        if (message == null) {
            return new LlmResponse("No message in response", null);
        }

        // Extract content (may be null if there are tool calls)
        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        // Extract tool calls if present
        List<ToolCall> toolCalls = null;
        if (message.has("tool_calls")) {
            JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
            toolCalls = new ArrayList<>();
            
            for (JsonElement toolCallElement : toolCallsArray) {
                JsonObject toolCallObj = toolCallElement.getAsJsonObject();
                String id = toolCallObj.get("id").getAsString();
                JsonObject function = toolCallObj.getAsJsonObject("function");
                String name = function.get("name").getAsString();
                String argumentsStr = function.get("arguments").getAsString();
                
                // Parse arguments string to JsonObject
                JsonObject arguments = gson.fromJson(argumentsStr, JsonObject.class);
                
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        return new LlmResponse(content, toolCalls);
    }

    public void cancelAllRequests() {
        client.dispatcher().cancelAll();
    }
}