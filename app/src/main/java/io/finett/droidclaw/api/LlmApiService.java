package io.finett.droidclaw.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Unified LLM API service supporting both OpenAI Chat Completions and Anthropic Messages APIs.
 *
 * <p>The API type is determined at runtime from {@link SettingsManager#getApiType()}.
 * Use {@link #API_OPENAI} for OpenAI-compatible endpoints and {@link #API_ANTHROPIC}
 * for Anthropic's Messages API.</p>
 */
public class LlmApiService {
    private static final String TAG = "LlmApiService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Anthropic-version header value required by the Anthropic Messages API. */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** API type constant for OpenAI Chat Completions (and compatible) endpoints. */
    public static final String API_OPENAI = "openai-completions";

    /** API type constant for Anthropic Messages API. Matches the value stored by ProviderDetailFragment. */
    public static final String API_ANTHROPIC = "anthropic";

    private final OkHttpClient client;
    private final Gson gson;
    private final SettingsManager settingsManager;
    private final Handler mainHandler;

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
     * Represents the parsed response from the LLM, containing either text content
     * or tool calls (or both for some providers).
     */
    public static class LlmResponse {
        private final String content;
        private final List<ToolCall> toolCalls;
        private final boolean hasToolCalls;
        private final TokenUsage usage;

        public LlmResponse(String content, List<ToolCall> toolCalls) {
            this(content, toolCalls, null);
        }

        public LlmResponse(String content, List<ToolCall> toolCalls, TokenUsage usage) {
            this.content = content;
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            this.hasToolCalls = !this.toolCalls.isEmpty();
            this.usage = usage;
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

        public TokenUsage getUsage() {
            return usage;
        }
    }

    /**
     * Represents a response that may contain a refusal (OpenAI Structured Outputs).
     * Anthropic does not have explicit refusals; that field will always be null.
     */
    public static class StructuredResponse {
        private final String content;
        private final String refusal;
        private final List<ToolCall> toolCalls;
        private final boolean hasToolCalls;
        private final TokenUsage usage;

        public StructuredResponse(String content, String refusal, List<ToolCall> toolCalls, TokenUsage usage) {
            this.content = content;
            this.refusal = refusal;
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            this.hasToolCalls = !this.toolCalls.isEmpty();
            this.usage = usage;
        }

        public String getContent() {
            return content;
        }

        public String getRefusal() {
            return refusal;
        }

        public boolean isRefusal() {
            return refusal != null && !refusal.isEmpty();
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean hasToolCalls() {
            return hasToolCalls;
        }

        public TokenUsage getUsage() {
            return usage;
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

    public interface StructuredResponseCallback {
        void onSuccess(StructuredResponse response);
        void onError(String error);
    }


    public LlmApiService(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }


    public void sendMessage(List<ChatMessage> conversationHistory, ChatCallback callback) {
        sendMessage(conversationHistory, null, null, callback);
    }

    public void sendMessage(List<ChatMessage> conversationHistory, JsonArray tools, ChatCallback callback) {
        sendMessage(conversationHistory, tools, null, callback);
    }

    public void sendMessage(List<ChatMessage> conversationHistory, JsonArray tools,
                            List<ChatMessage> identityMessages, ChatCallback callback) {
        if (!settingsManager.isConfigured()) {
            Log.w(TAG, "sendMessage: isConfigured() returned false");
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        String apiType = settingsManager.getApiType();
        String apiUrl = settingsManager.getApiUrl();
        String apiKey = settingsManager.getApiKey();
        Log.d(TAG, "sendMessage: apiType=" + apiType + ", apiUrl=" + apiUrl
                + ", apiKey=" + (apiKey != null && !apiKey.isEmpty() ? "(set)" : "(empty)")
                + ", model=" + settingsManager.getModelName());

        if (apiUrl == null || apiUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("No API URL configured for selected model. Please check Settings."));
            return;
        }
        String jsonBody;
        Request.Builder requestBuilder;

        if (API_ANTHROPIC.equals(apiType)) {
            jsonBody = gson.toJson(buildAnthropicRequestBody(conversationHistory, tools, identityMessages));
            requestBuilder = buildAnthropicRequestBuilder(jsonBody);
        } else {
            jsonBody = gson.toJson(buildOpenAiRequestBody(conversationHistory, tools, identityMessages));
            requestBuilder = buildOpenAiRequestBuilder(jsonBody);
        }

        Request request = requestBuilder.build();
        Log.d(TAG, "sendMessage: HTTP " + request.method() + " " + request.url());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        String errMsg = parseApiError(responseBody, response.code(), apiType);
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }

                    String assistantMessage;
                    if (API_ANTHROPIC.equals(apiType)) {
                        assistantMessage = parseAnthropicResponseText(responseBody);
                    } else {
                        assistantMessage = parseOpenAiResponseText(responseBody);
                    }
                    mainHandler.post(() -> callback.onSuccess(assistantMessage));
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                     ChatCallbackWithTools callback) {
        sendMessageWithTools(conversationHistory, tools, null, callback);
    }

    public void sendMessageWithTools(List<ChatMessage> conversationHistory, JsonArray tools,
                                     List<ChatMessage> identityMessages, ChatCallbackWithTools callback) {
        if (!settingsManager.isConfigured()) {
            Log.w(TAG, "sendMessageWithTools: isConfigured() returned false");
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        String apiType = settingsManager.getApiType();
        String apiUrl = settingsManager.getApiUrl();
        String apiKey = settingsManager.getApiKey();
        Log.d(TAG, "sendMessageWithTools: apiType=" + apiType + ", apiUrl=" + apiUrl
                + ", apiKey=" + (apiKey != null && !apiKey.isEmpty() ? "(set)" : "(empty)")
                + ", model=" + settingsManager.getModelName());

        if (apiUrl == null || apiUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("No API URL configured for selected model. Please check Settings."));
            return;
        }
        String jsonBody;
        Request.Builder requestBuilder;

        if (API_ANTHROPIC.equals(apiType)) {
            jsonBody = gson.toJson(buildAnthropicRequestBody(conversationHistory, tools, identityMessages));
            requestBuilder = buildAnthropicRequestBuilder(jsonBody);
        } else {
            jsonBody = gson.toJson(buildOpenAiRequestBody(conversationHistory, tools, identityMessages));
            requestBuilder = buildOpenAiRequestBuilder(jsonBody);
        }

        Request request = requestBuilder.build();
        Log.d(TAG, "sendMessageWithTools: HTTP " + request.method() + " " + request.url());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        String errMsg = parseApiError(responseBody, response.code(), apiType);
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }

                    LlmResponse llmResponse;
                    if (API_ANTHROPIC.equals(apiType)) {
                        llmResponse = parseAnthropicResponseWithTools(responseBody);
                    } else {
                        llmResponse = parseOpenAiResponseWithTools(responseBody);
                    }
                    mainHandler.post(() -> callback.onSuccess(llmResponse));
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Send a message with structured outputs support.
     *
     * <p>For OpenAI: uses {@code response_format} with {@code json_schema} and {@code strict: true}
     * to guarantee JSON schema adherence.</p>
     * <p>For Anthropic: structured output schema is not natively supported; the schema is injected
     * as a system instruction instead.</p>
     *
     * @param conversationHistory Full conversation history
     * @param tools               Tool definitions (can be null)
     * @param identityMessages    System messages for identity context (soul.md, user.md)
     * @param responseSchema      JSON Schema for Structured Outputs
     * @param callback            Callback with {@link StructuredResponse}
     */
    public void sendMessageStructured(List<ChatMessage> conversationHistory, JsonArray tools,
                                      List<ChatMessage> identityMessages,
                                      JsonObject responseSchema, StructuredResponseCallback callback) {
        if (!settingsManager.isConfigured()) {
            Log.w(TAG, "sendMessageStructured: isConfigured() returned false");
            mainHandler.post(() -> callback.onError("API key not configured. Please set it in Settings."));
            return;
        }

        String apiType = settingsManager.getApiType();
        String apiUrl = settingsManager.getApiUrl();
        Log.d(TAG, "sendMessageStructured: apiType=" + apiType + ", apiUrl=" + apiUrl
                + ", model=" + settingsManager.getModelName());

        if (apiUrl == null || apiUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("No API URL configured for selected model. Please check Settings."));
            return;
        }

        String jsonBody;
        Request.Builder requestBuilder;

        if (API_ANTHROPIC.equals(apiType)) {
            // Inject schema as a system instruction appended to identity messages
            List<ChatMessage> augmentedIdentity = buildAnthropicSchemaInstructions(
                    identityMessages, responseSchema);
            jsonBody = gson.toJson(buildAnthropicRequestBody(conversationHistory, tools, augmentedIdentity));
            requestBuilder = buildAnthropicRequestBuilder(jsonBody);
        } else {
            JsonObject body = buildOpenAiRequestBodyWithStructuredOutput(
                    conversationHistory, tools, identityMessages, responseSchema);
            jsonBody = gson.toJson(body);
            requestBuilder = buildOpenAiRequestBuilder(jsonBody);
        }

        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        String errMsg = parseApiError(responseBody, response.code(), apiType);
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }

                    StructuredResponse structuredResponse;
                    if (API_ANTHROPIC.equals(apiType)) {
                        // Anthropic has no explicit refusal; wrap LlmResponse
                        LlmResponse llmResp = parseAnthropicResponseWithTools(responseBody);
                        structuredResponse = new StructuredResponse(
                                llmResp.getContent(), null, llmResp.getToolCalls(), llmResp.getUsage());
                    } else {
                        structuredResponse = parseOpenAiStructuredResponse(responseBody);
                    }
                    mainHandler.post(() -> callback.onSuccess(structuredResponse));
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void cancelAllRequests() {
        client.dispatcher().cancelAll();
    }

    private Request.Builder buildOpenAiRequestBuilder(String jsonBody) {
        Request.Builder builder = new Request.Builder()
                .url(settingsManager.getApiUrl())
                .addHeader("Content-Type", "application/json");

        String apiKey = settingsManager.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }

        builder.post(RequestBody.create(jsonBody, JSON));
        return builder;
    }

    private Request.Builder buildAnthropicRequestBuilder(String jsonBody) {
        String apiKey = settingsManager.getApiKey();
        Request.Builder builder = new Request.Builder()
                .url(settingsManager.getApiUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("anthropic-version", ANTHROPIC_VERSION);

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.addHeader("x-api-key", apiKey);
        }

        builder.post(RequestBody.create(jsonBody, JSON));
        return builder;
    }

    private JsonObject buildOpenAiRequestBody(List<ChatMessage> conversationHistory,
                                              JsonArray tools,
                                              List<ChatMessage> identityMessages) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", settingsManager.getModelName());
        requestBody.addProperty("max_tokens", settingsManager.getMaxTokens());
        requestBody.addProperty("temperature", 0.7f);

        JsonArray messages = new JsonArray();

        // Add identity messages first (system messages with soul.md and user.md)
        if (identityMessages != null) {
            for (ChatMessage msg : identityMessages) {
                messages.add(msg.toApiMessage());
            }
        }

        // Add conversation history
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

    private JsonObject buildOpenAiRequestBodyWithStructuredOutput(List<ChatMessage> conversationHistory,
                                                                   JsonArray tools,
                                                                   List<ChatMessage> identityMessages,
                                                                   JsonObject responseSchema) {
        JsonObject requestBody = buildOpenAiRequestBody(conversationHistory, tools, identityMessages);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");

        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", responseSchema);
        responseFormat.add("json_schema", jsonSchema);

        requestBody.add("response_format", responseFormat);
        return requestBody;
    }


    /**
     * Build the JSON request body for the Anthropic Messages API.
     *
     * <p>Key differences from OpenAI:
     * <ul>
     *   <li>System content goes in the top-level {@code system} field (string or array)</li>
     *   <li>Messages only contain {@code user} and {@code assistant} roles</li>
     *   <li>Tool results are sent as {@code user} messages with {@code tool_result} content blocks</li>
     *   <li>Tool definitions use {@code input_schema} instead of {@code parameters}</li>
     * </ul>
     * </p>
     */
    private JsonObject buildAnthropicRequestBody(List<ChatMessage> conversationHistory,
                                                  JsonArray openAiTools,
                                                  List<ChatMessage> identityMessages) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", settingsManager.getModelName());
        requestBody.addProperty("max_tokens", settingsManager.getMaxTokens());

        // Build system prompt from identity messages
        StringBuilder systemBuilder = new StringBuilder();
        if (identityMessages != null) {
            for (ChatMessage msg : identityMessages) {
                if (msg.getType() == ChatMessage.TYPE_SYSTEM && msg.getContent() != null) {
                    if (systemBuilder.length() > 0) systemBuilder.append("\n\n");
                    systemBuilder.append(msg.getContent());
                }
            }
        }
        if (systemBuilder.length() > 0) {
            requestBody.addProperty("system", systemBuilder.toString());
        }

        // Convert conversation history to Anthropic format
        JsonArray messages = new JsonArray();
        for (ChatMessage chatMessage : conversationHistory) {
            JsonObject anthropicMsg = chatMessage.toAnthropicApiMessage();
            if (anthropicMsg != null) {
                messages.add(anthropicMsg);
            }
        }

        // Anthropic requires messages to start with a user turn and alternate roles.
        // Merge consecutive same-role messages to satisfy this constraint.
        messages = mergeConsecutiveSameRoleMessages(messages);

        requestBody.add("messages", messages);

        // Convert OpenAI-format tools to Anthropic format
        if (openAiTools != null && openAiTools.size() > 0) {
            JsonArray anthropicTools = convertToolsToAnthropicFormat(openAiTools);
            if (anthropicTools.size() > 0) {
                requestBody.add("tools", anthropicTools);
                // tool_choice: {"type": "auto"} is the default; explicit for clarity
                JsonObject toolChoice = new JsonObject();
                toolChoice.addProperty("type", "auto");
                requestBody.add("tool_choice", toolChoice);
            }
        }

        return requestBody;
    }

    private List<ChatMessage> buildAnthropicSchemaInstructions(List<ChatMessage> identityMessages,
                                                                JsonObject responseSchema) {
        List<ChatMessage> augmented = new ArrayList<>();
        if (identityMessages != null) {
            augmented.addAll(identityMessages);
        }
        if (responseSchema != null) {
            String schemaInstruction = "IMPORTANT: Your final response MUST be valid JSON that conforms " +
                    "to the following JSON Schema. Output ONLY the JSON object, no other text:\n" +
                    responseSchema.toString();
            augmented.add(new ChatMessage(schemaInstruction, ChatMessage.TYPE_SYSTEM));
        }
        return augmented;
    }

    /**
     * Convert OpenAI-format tool definitions to Anthropic format.
     *
     * <p>OpenAI: {@code {type:"function", function:{name, description, parameters, strict}}}</p>
     * <p>Anthropic: {@code {name, description, input_schema}}</p>
     */
    private JsonArray convertToolsToAnthropicFormat(JsonArray openAiTools) {
        JsonArray anthropicTools = new JsonArray();
        for (JsonElement toolEl : openAiTools) {
            try {
                JsonObject openAiTool = toolEl.getAsJsonObject();
                // Only handle function-type tools
                if (!openAiTool.has("function")) continue;
                JsonObject function = openAiTool.getAsJsonObject("function");

                JsonObject anthropicTool = new JsonObject();
                anthropicTool.addProperty("name", function.get("name").getAsString());
                if (function.has("description")) {
                    anthropicTool.addProperty("description", function.get("description").getAsString());
                }
                // Anthropic uses "input_schema" where OpenAI uses "parameters"
                if (function.has("parameters")) {
                    anthropicTool.add("input_schema", function.get("parameters"));
                } else {
                    // Minimal valid schema
                    JsonObject emptySchema = new JsonObject();
                    emptySchema.addProperty("type", "object");
                    anthropicTool.add("input_schema", emptySchema);
                }

                anthropicTools.add(anthropicTool);
            } catch (Exception e) {
                Log.w(TAG, "Failed to convert tool to Anthropic format", e);
            }
        }
        return anthropicTools;
    }

    private JsonArray mergeConsecutiveSameRoleMessages(JsonArray messages) {
        if (messages.size() == 0) return messages;

        JsonArray merged = new JsonArray();
        String currentRole = null;
        JsonArray currentContent = null;

        for (JsonElement el : messages) {
            JsonObject msg = el.getAsJsonObject();
            String role = msg.has("role") ? msg.get("role").getAsString() : "user";

            if (role.equals(currentRole)) {
                // Merge content into the current message
                appendContentToArray(currentContent, msg);
            } else {
                // Flush previous message
                if (currentRole != null && currentContent != null) {
                    JsonObject mergedMsg = new JsonObject();
                    mergedMsg.addProperty("role", currentRole);
                    if (currentContent.size() == 1 && currentContent.get(0).getAsJsonObject().has("text")
                            && currentContent.get(0).getAsJsonObject().get("type").getAsString().equals("text")) {
                        // Single text block: use string shorthand for cleaner JSON
                        mergedMsg.addProperty("content",
                                currentContent.get(0).getAsJsonObject().get("text").getAsString());
                    } else {
                        mergedMsg.add("content", currentContent);
                    }
                    merged.add(mergedMsg);
                }
                currentRole = role;
                currentContent = new JsonArray();
                appendContentToArray(currentContent, msg);
            }
        }

        // Flush last message
        if (currentRole != null && currentContent != null) {
            JsonObject mergedMsg = new JsonObject();
            mergedMsg.addProperty("role", currentRole);
            if (currentContent.size() == 1 && currentContent.get(0).getAsJsonObject().has("type")
                    && currentContent.get(0).getAsJsonObject().get("type").getAsString().equals("text")) {
                mergedMsg.addProperty("content",
                        currentContent.get(0).getAsJsonObject().get("text").getAsString());
            } else {
                mergedMsg.add("content", currentContent);
            }
            merged.add(mergedMsg);
        }

        return merged;
    }

    private void appendContentToArray(JsonArray contentArray, JsonObject msg) {
        if (msg.has("content")) {
            JsonElement contentEl = msg.get("content");
            if (contentEl.isJsonArray()) {
                // Already an array of blocks
                for (JsonElement block : contentEl.getAsJsonArray()) {
                    contentArray.add(block);
                }
            } else if (!contentEl.isJsonNull()) {
                // String content — wrap in a text block
                JsonObject textBlock = new JsonObject();
                textBlock.addProperty("type", "text");
                textBlock.addProperty("text", contentEl.getAsString());
                contentArray.add(textBlock);
            }
        }

        if (msg.has("tool_calls")) {
            // Already handled via toAnthropicApiMessage(); content should be tool_use blocks
        }
    }

    private String parseOpenAiResponseText(String responseBody) {
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

    private LlmResponse parseOpenAiResponseWithTools(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        JsonArray choices = jsonResponse.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            return new LlmResponse("No response received", null, null);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");

        if (message == null) {
            return new LlmResponse("No message in response", null, null);
        }

        // Extract text content (may be null when there are tool calls)
        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        // Extract tool calls
        List<ToolCall> toolCalls = parseOpenAiToolCalls(message);

        // Extract token usage
        TokenUsage usage = parseOpenAiUsage(jsonResponse);

        return new LlmResponse(content, toolCalls, usage);
    }

    private StructuredResponse parseOpenAiStructuredResponse(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        JsonArray choices = jsonResponse.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            return new StructuredResponse("No response received", null, null, null);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");

        if (message == null) {
            return new StructuredResponse("No message in response", null, null, null);
        }

        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        String refusal = null;
        if (message.has("refusal") && !message.get("refusal").isJsonNull()) {
            refusal = message.get("refusal").getAsString();
        }

        List<ToolCall> toolCalls = parseOpenAiToolCalls(message);
        TokenUsage usage = parseOpenAiUsage(jsonResponse);

        return new StructuredResponse(content, refusal, toolCalls, usage);
    }

    private List<ToolCall> parseOpenAiToolCalls(JsonObject message) {
        if (!message.has("tool_calls")) return null;

        JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonElement toolCallElement : toolCallsArray) {
            try {
                JsonObject toolCallObj = toolCallElement.getAsJsonObject();
                String id = toolCallObj.get("id").getAsString();
                JsonObject function = toolCallObj.getAsJsonObject("function");
                String name = function.get("name").getAsString();
                String argumentsStr = function.get("arguments").getAsString();
                JsonObject arguments = gson.fromJson(argumentsStr, JsonObject.class);
                toolCalls.add(new ToolCall(id, name, arguments));
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse OpenAI tool call", e);
            }
        }

        return toolCalls.isEmpty() ? null : toolCalls;
    }

    private TokenUsage parseOpenAiUsage(JsonObject jsonResponse) {
        if (!jsonResponse.has("usage")) return null;
        JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
        int promptTokens = usageObj.has("prompt_tokens") ? usageObj.get("prompt_tokens").getAsInt() : 0;
        int completionTokens = usageObj.has("completion_tokens") ? usageObj.get("completion_tokens").getAsInt() : 0;
        int totalTokens = usageObj.has("total_tokens") ? usageObj.get("total_tokens").getAsInt()
                : promptTokens + completionTokens;
        return new TokenUsage(totalTokens, promptTokens, completionTokens);
    }

    private String parseAnthropicResponseText(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        return extractAnthropicTextContent(jsonResponse);
    }

    private LlmResponse parseAnthropicResponseWithTools(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        if (!jsonResponse.has("content")) {
            return new LlmResponse("No response received", null, null);
        }

        JsonArray contentBlocks = jsonResponse.getAsJsonArray("content");
        String stopReason = jsonResponse.has("stop_reason")
                ? jsonResponse.get("stop_reason").getAsString() : "";

        StringBuilder textContent = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonElement blockEl : contentBlocks) {
            try {
                JsonObject block = blockEl.getAsJsonObject();
                String blockType = block.has("type") ? block.get("type").getAsString() : "";

                if ("text".equals(blockType)) {
                    String text = block.has("text") ? block.get("text").getAsString() : "";
                    if (!text.isEmpty()) {
                        if (textContent.length() > 0) textContent.append("\n");
                        textContent.append(text);
                    }
                } else if ("tool_use".equals(blockType)) {
                    String id = block.has("id") ? block.get("id").getAsString() : "";
                    String name = block.has("name") ? block.get("name").getAsString() : "";
                    JsonObject input = block.has("input") ? block.getAsJsonObject("input") : new JsonObject();
                    toolCalls.add(new ToolCall(id, name, input));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse Anthropic content block", e);
            }
        }

        String content = textContent.length() > 0 ? textContent.toString() : null;
        TokenUsage usage = parseAnthropicUsage(jsonResponse);

        return new LlmResponse(content, toolCalls.isEmpty() ? null : toolCalls, usage);
    }

    private String extractAnthropicTextContent(JsonObject jsonResponse) {
        if (!jsonResponse.has("content")) return "No response received";

        StringBuilder sb = new StringBuilder();
        for (JsonElement blockEl : jsonResponse.getAsJsonArray("content")) {
            try {
                JsonObject block = blockEl.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString()) && block.has("text")) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(block.get("text").getAsString());
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse Anthropic text block", e);
            }
        }
        return sb.length() > 0 ? sb.toString() : "No response received";
    }

    private TokenUsage parseAnthropicUsage(JsonObject jsonResponse) {
        if (!jsonResponse.has("usage")) return null;
        JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
        int inputTokens = usageObj.has("input_tokens") ? usageObj.get("input_tokens").getAsInt() : 0;
        int outputTokens = usageObj.has("output_tokens") ? usageObj.get("output_tokens").getAsInt() : 0;
        int total = inputTokens + outputTokens;
        // Map to TokenUsage(total, prompt=input, completion=output)
        return new TokenUsage(total, inputTokens, outputTokens);
    }

    private String parseApiError(String responseBody, int httpCode, String apiType) {
        try {
            JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
            if (API_ANTHROPIC.equals(apiType)) {
                // Anthropic: {"type":"error","error":{"type":"...","message":"..."}}
                if (errorJson.has("error")) {
                    JsonObject err = errorJson.getAsJsonObject("error");
                    String msg = err.has("message") ? err.get("message").getAsString() : responseBody;
                    String type = err.has("type") ? err.get("type").getAsString() : "";
                    return "Anthropic API error (" + httpCode + "): " + type + " - " + msg;
                }
            } else {
                // OpenAI: {"error":{"message":"...","type":"...","code":"..."}}
                if (errorJson.has("error")) {
                    JsonObject err = errorJson.getAsJsonObject("error");
                    String msg = err.has("message") ? err.get("message").getAsString() : responseBody;
                    return "API error (" + httpCode + "): " + msg;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse API error body", e);
        }
        return "API error: " + httpCode + " - " + responseBody;
    }
}