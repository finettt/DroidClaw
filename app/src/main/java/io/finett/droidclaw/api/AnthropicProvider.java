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
 * Provider implementation for Anthropic API (Claude).
 * Supports the Anthropic messages API with proper handling of:
 * - Separate system field for system messages
 * - input_schema instead of parameters for tools
 * - tool_use content type for tool calls
 */
public class AnthropicProvider implements Provider {
    private static final Gson gson = new Gson();

    // Anthropic API constants
    private static final String ANTHROPIC_API_VERSION = "2023-06-01";
    private static final String ANTHROPIC_API_BASE_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_TOOLS_BETA = "tools-2024-12-16";

    private final String id;
    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String apiType;
    private final Model model;

    public AnthropicProvider(String id, String name, String baseUrl, String apiKey,
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

        // For Anthropic API, add system messages as a separate field instead of in messages array
        JsonArray messages = new JsonArray();
        JsonArray anthropicSystemMessages = new JsonArray();

        // Add identity messages FIRST (system messages with soul.md and user.md)
        if (identityMessages != null) {
            for (ChatMessage identityMessage : identityMessages) {
                // For Anthropic, system messages go in a separate field
                anthropicSystemMessages.add(identityMessage.toApiMessage());
            }
        }

        // Add conversation history
        for (ChatMessage chatMessage : conversationHistory) {
            messages.add(chatMessage.toApiMessage());
        }

        requestBody.add("messages", messages);

        // Add system messages for Anthropic API
        if (anthropicSystemMessages.size() > 0) {
            requestBody.add("system", anthropicSystemMessages);
        }

        // Add tools if provided
        if (tools != null && tools.size() > 0) {
            // Anthropic uses "tools" array with specific format
            // Convert OpenAI-style tools to Anthropic format
            requestBody.add("tools", convertTools(tools));
            requestBody.add("tool_choice", new JsonObject());
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
        requestBuilder.addHeader("anthropic-version", ANTHROPIC_API_VERSION);
        requestBuilder.addHeader("anthropic-beta", ANTHROPIC_TOOLS_BETA);

        if (apiKey != null && !apiKey.trim().isEmpty() && !"lm-studio".equalsIgnoreCase(apiKey.trim())) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
    }

    @Override
    public String parseResponse(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        // Extract content from Anthropic content array
        String content = null;
        JsonArray contentArray = jsonResponse.getAsJsonArray("content");
        if (contentArray != null && contentArray.size() > 0) {
            JsonObject firstContent = contentArray.get(0).getAsJsonObject();
            if (firstContent.has("text")) {
                content = firstContent.get("text").getAsString();
            }
        }

        if (content == null) {
            return "No response received";
        }
        return content;
    }

    @Override
    public LlmApiService.LlmResponse parseResponseWithTools(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        // Extract content from Anthropic content array
        String content = null;
        JsonArray contentArray = jsonResponse.getAsJsonArray("content");
        if (contentArray != null && contentArray.size() > 0) {
            JsonObject firstContent = contentArray.get(0).getAsJsonObject();
            if (firstContent.has("text")) {
                content = firstContent.get("text").getAsString();
            }
        }

        // Extract tool calls from Anthropic response
        List<LlmApiService.ToolCall> toolCalls = null;
        if (contentArray != null) {
            toolCalls = new ArrayList<>();
            for (JsonElement contentElement : contentArray) {
                JsonObject contentObj = contentElement.getAsJsonObject();
                String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";

                if ("tool_use".equals(type)) {
                    String id = contentObj.has("id") ? contentObj.get("id").getAsString() : "";
                    String name = contentObj.has("name") ? contentObj.get("name").getAsString() : "";
                    JsonObject inputObj = contentObj.getAsJsonObject("input");

                    toolCalls.add(new LlmApiService.ToolCall(id, name, inputObj));
                }
            }
        }

        // Extract stop reason (Anthropic-specific field)
        String stopReason = null;
        if (jsonResponse.has("stop_reason")) {
            stopReason = jsonResponse.get("stop_reason").getAsString();
        }

        // Extract token usage - Anthropic uses different field names
        LlmApiService.TokenUsage usage = null;
        if (jsonResponse.has("usage")) {
            JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
            int inputTokens = usageObj.has("input_tokens") ? usageObj.get("input_tokens").getAsInt() : 0;
            int outputTokens = usageObj.has("output_tokens") ? usageObj.get("output_tokens").getAsInt() : 0;
            usage = new LlmApiService.TokenUsage(inputTokens + outputTokens, inputTokens, outputTokens);
        }

        return new LlmApiService.LlmResponse(content, toolCalls, usage);
    }

    @Override
    public LlmApiService.StructuredResponse parseStructuredResponse(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        // Extract content from Anthropic content array
        String content = null;
        JsonArray contentArray = jsonResponse.getAsJsonArray("content");
        if (contentArray != null && contentArray.size() > 0) {
            JsonObject firstContent = contentArray.get(0).getAsJsonObject();
            if (firstContent.has("text")) {
                content = firstContent.get("text").getAsString();
            }
        }

        // Extract tool calls from Anthropic response
        List<LlmApiService.ToolCall> toolCalls = null;
        if (contentArray != null) {
            toolCalls = new ArrayList<>();
            for (JsonElement contentElement : contentArray) {
                JsonObject contentObj = contentElement.getAsJsonObject();
                String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";

                if ("tool_use".equals(type)) {
                    String id = contentObj.has("id") ? contentObj.get("id").getAsString() : "";
                    String name = contentObj.has("name") ? contentObj.get("name").getAsString() : "";
                    JsonObject inputObj = contentObj.getAsJsonObject("input");

                    toolCalls.add(new LlmApiService.ToolCall(id, name, inputObj));
                }
            }
        }

        // Extract token usage - Anthropic uses different field names
        LlmApiService.TokenUsage usage = null;
        if (jsonResponse.has("usage")) {
            JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
            int inputTokens = usageObj.has("input_tokens") ? usageObj.get("input_tokens").getAsInt() : 0;
            int outputTokens = usageObj.has("output_tokens") ? usageObj.get("output_tokens").getAsInt() : 0;
            usage = new LlmApiService.TokenUsage(inputTokens + outputTokens, inputTokens, outputTokens);
        }

        return new LlmApiService.StructuredResponse(content, null, toolCalls, usage);
    }

    @Override
    public JsonArray convertTools(JsonArray openaiTools) {
        JsonArray anthropicTools = new JsonArray();
        for (JsonElement toolElement : openaiTools) {
            JsonObject openaiTool = toolElement.getAsJsonObject();
            JsonObject anthropicTool = new JsonObject();

            // Copy name
            if (openaiTool.has("name")) {
                anthropicTool.addProperty("name", openaiTool.get("name").getAsString());
            }

            // Copy description
            if (openaiTool.has("description")) {
                anthropicTool.addProperty("description", openaiTool.get("description").getAsString());
            }

            // Copy and convert parameters (OpenAI uses "parameters", Anthropic uses "input_schema")
            if (openaiTool.has("parameters")) {
                anthropicTool.add("input_schema", openaiTool.get("parameters"));
            }

            // Add tool_type for Anthropic (optional but can help with tool validation)
            anthropicTool.addProperty("tool_type", "computer_20241022");

            anthropicTools.add(anthropicTool);
        }
        return anthropicTools;
    }

    @Override
    public boolean isAnthropic() {
        return true;
    }
}
