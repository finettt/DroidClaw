package io.finett.droidclaw.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import okhttp3.Request;

/**
 * Interface for API providers (OpenAI-compatible, Anthropic, etc.).
 * Defines the contract for provider-specific API behavior.
 */
public interface Provider {
    /**
     * Get the unique identifier for this provider type.
     */
    String getId();

    /**
     * Get the display name for this provider.
     */
    String getName();

    /**
     * Get the base URL for the API endpoint.
     */
    String getBaseUrl();

    /**
     * Get the API key for authentication.
     */
    String getApiKey();

    /**
     * Get the API type identifier (e.g., "anthropic", "openai-completions").
     */
    String getApiType();

    /**
     * Get the model to use.
     */
    Model getModel();

    /**
     * Build the request body for the API call.
     *
     * @param conversationHistory Full conversation history
     * @param tools Tool definitions (can be null)
     * @param identityMessages System messages for identity context (soul.md, user.md)
     * @return Request body as JsonObject
     */
    JsonObject buildRequestBody(List<ChatMessage> conversationHistory, JsonArray tools,
                                List<ChatMessage> identityMessages);

    /**
     * Build the request body with Structured Outputs support.
     *
     * @param conversationHistory Full conversation history
     * @param tools Tool definitions (can be null)
     * @param identityMessages System messages for identity context
     * @param responseSchema JSON Schema for Structured Outputs
     * @return Request body as JsonObject
     */
    JsonObject buildRequestBodyWithStructuredOutput(List<ChatMessage> conversationHistory,
                                                    JsonArray tools,
                                                    List<ChatMessage> identityMessages,
                                                    JsonObject responseSchema);

    /**
     * Add request headers to the request builder.
     *
     * @param requestBuilder OkHttp Request.Builder
     */
    void addRequestHeaders(Request.Builder requestBuilder);

    /**
     * Parse the API response for standard text responses.
     *
     * @param responseBody JSON response from API
     * @return Extracted content string
     */
    String parseResponse(String responseBody);

    /**
     * Parse the API response with tool calls support.
     *
     * @param responseBody JSON response from API
     * @return LlmResponse with content and tool calls
     */
    LlmApiService.LlmResponse parseResponseWithTools(String responseBody);

    /**
     * Parse the API response with Structured Outputs support.
     *
     * @param responseBody JSON response from API
     * @return StructuredResponse with content, refusal, and tool calls
     */
    LlmApiService.StructuredResponse parseStructuredResponse(String responseBody);

    /**
     * Convert tools from OpenAI format to this provider's format.
     *
     * @param openaiTools Tools in OpenAI format
     * @return Tools in this provider's format
     */
    JsonArray convertTools(JsonArray openaiTools);

    /**
     * Check if this provider is an Anthropic provider.
     *
     * @return true if this is Anthropic, false otherwise
     */
    default boolean isAnthropic() {
        return false;
    }
}
