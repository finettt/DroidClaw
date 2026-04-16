package io.finett.droidclaw.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Model;
import okhttp3.Request;

/**
 * Provider implementation for OpenAI-compatible APIs.
 * This includes OpenAI, Groq, DeepSeek, and any other API that follows the OpenAI API specification.
 */
public class OpenAICompatibleProvider implements Provider {
    private static final Gson gson = new Gson();

    private final String id;
    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String apiType;
    private final Model model;

    public OpenAICompatibleProvider(String id, String name, String baseUrl, String apiKey,
                                    String apiType, Model model) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiType = apiType;
        this.model = model;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String getApiType() {
        return apiType;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public JsonObject buildRequestBody(List<ChatMessage> conversationHistory, JsonArray tools,
                                       List<ChatMessage> identityMessages) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model.getId());
        requestBody.addProperty("max_tokens", model.getMaxTokens());
        requestBody.addProperty("temperature", 0.7f);

        // For OpenAI-compatible APIs, all messages (including system) go in the messages array
        JsonArray messages = new JsonArray();

        // Add identity messages FIRST (system messages with soul.md and user.md)
        if (identityMessages != null) {
            for (ChatMessage identityMessage : identityMessages) {
                messages.add(identityMessage.toApiMessage());
            }
        }

        // Add conversation history
        for (ChatMessage chatMessage : conversationHistory) {
            messages.add(chatMessage.toApiMessage());
        }

        requestBody.add("messages", messages);

        // Add tools if provided (OpenAI format)
        if (tools != null && tools.size() > 0) {
            requestBody.add("tools", tools);
            requestBody.addProperty("tool_choice", "auto");
        }

        return requestBody;
    }

    @Override
    public JsonObject buildRequestBodyWithStructuredOutput(List<ChatMessage> conversationHistory,
                                                           JsonArray tools,
                                                           List<ChatMessage> identityMessages,
                                                           JsonObject responseSchema) {
        JsonObject requestBody = buildRequestBody(conversationHistory, tools, identityMessages);

        // Add Structured Outputs response_format
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");

        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", responseSchema);
        responseFormat.add("json_schema", jsonSchema);

        requestBody.add("response_format", responseFormat);

        return requestBody;
    }

    @Override
    public void addRequestHeaders(Request.Builder requestBuilder) {
        requestBuilder.addHeader("Content-Type", "application/json");

        if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
    }

    @Override
    public String parseResponse(String responseBody) {
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

    @Override
    public LlmApiService.LlmResponse parseResponseWithTools(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        JsonArray choices = jsonResponse.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            return new LlmApiService.LlmResponse("No response received", null, null);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");

        if (message == null) {
            return new LlmApiService.LlmResponse("No message in response", null, null);
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

            for (JsonElement toolCallElement : toolCallsArray) {
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

        // Extract token usage information (Last Usage algorithm)
        LlmApiService.TokenUsage usage = null;
        if (jsonResponse.has("usage")) {
            JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
            int totalTokens = usageObj.has("total_tokens") ? usageObj.get("total_tokens").getAsInt() : 0;
            int promptTokens = usageObj.has("prompt_tokens") ? usageObj.get("prompt_tokens").getAsInt() : 0;
            int completionTokens = usageObj.has("completion_tokens") ? usageObj.get("completion_tokens").getAsInt() : 0;
            usage = new LlmApiService.TokenUsage(totalTokens, promptTokens, completionTokens);
        }

        return new LlmApiService.LlmResponse(content, toolCalls, usage);
    }

    @Override
    public LlmApiService.StructuredResponse parseStructuredResponse(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        JsonArray choices = jsonResponse.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            return new LlmApiService.StructuredResponse("No response received", null, null, null);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");

        if (message == null) {
            return new LlmApiService.StructuredResponse("No message in response", null, null, null);
        }

        // Extract content (may be null if there are tool calls or refusal)
        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        // Extract refusal if present
        String refusal = null;
        if (message.has("refusal") && !message.get("refusal").isJsonNull()) {
            refusal = message.get("refusal").getAsString();
        }

        // Extract tool calls if present
        List<LlmApiService.ToolCall> toolCalls = null;
        if (message.has("tool_calls")) {
            JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
            toolCalls = new ArrayList<>();

            for (JsonElement toolCallElement : toolCallsArray) {
                JsonObject toolCallObj = toolCallElement.getAsJsonObject();
                String id = toolCallObj.get("id").getAsString();
                JsonObject function = toolCallObj.getAsJsonObject("function");
                String name = function.get("name").getAsString();
                String argumentsStr = function.get("arguments").getAsString();

                JsonObject arguments = gson.fromJson(argumentsStr, JsonObject.class);
                toolCalls.add(new LlmApiService.ToolCall(id, name, arguments));
            }
        }

        // Extract token usage
        LlmApiService.TokenUsage usage = null;
        if (jsonResponse.has("usage")) {
            JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
            int totalTokens = usageObj.has("total_tokens") ? usageObj.get("total_tokens").getAsInt() : 0;
            int promptTokens = usageObj.has("prompt_tokens") ? usageObj.get("prompt_tokens").getAsInt() : 0;
            int completionTokens = usageObj.has("completion_tokens") ? usageObj.get("completion_tokens").getAsInt() : 0;
            usage = new LlmApiService.TokenUsage(totalTokens, promptTokens, completionTokens);
        }

        return new LlmApiService.StructuredResponse(content, refusal, toolCalls, usage);
    }

    @Override
    public JsonArray convertTools(JsonArray openaiTools) {
        // OpenAI-compatible APIs use the standard OpenAI tool format
        return openaiTools;
    }
}
