package io.finett.droidclaw.api;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
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

    public interface ChatCallback {
        void onSuccess(String response);
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
        if (!settingsManager.isConfigured()) {
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        JsonObject requestBody = buildRequestBody(conversationHistory);
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

    private JsonObject buildRequestBody(List<ChatMessage> conversationHistory) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", settingsManager.getModelName());
        requestBody.addProperty("max_tokens", settingsManager.getMaxTokens());
        requestBody.addProperty("temperature", settingsManager.getTemperature());

        JsonArray messages = new JsonArray();

        // Add system prompt
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", settingsManager.getSystemPrompt());
        messages.add(systemMessage);

        // Add conversation history
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

    public void cancelAllRequests() {
        client.dispatcher().cancelAll();
    }
}