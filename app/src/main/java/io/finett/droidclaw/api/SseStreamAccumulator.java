package io.finett.droidclaw.api;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incremental Server-Sent-Events (SSE) parser for streaming LLM responses.
 *
 * <p>Feed raw stream lines via {@link #feedLine(String)}, call {@link #finish()}
 * when the stream ends, then obtain the assembled result with
 * {@link #buildResponse()}. Text deltas are surfaced live through the
 * {@link Listener} so the UI can render tokens as they arrive.</p>
 *
 * <p>Supports both API dialects:
 * <ul>
 *   <li><b>OpenAI Chat Completions</b> — {@code data: {chunk}} lines with
 *       {@code choices[0].delta.content} text deltas, {@code delta.tool_calls}
 *       fragments indexed by position, the {@code [DONE]} sentinel, and an
 *       optional trailing usage chunk (requires {@code stream_options.include_usage}).</li>
 *   <li><b>Anthropic Messages</b> — typed events ({@code message_start},
 *       {@code content_block_start}, {@code content_block_delta},
 *       {@code message_delta}, {@code error}) with {@code text_delta} and
 *       {@code input_json_delta} payloads.</li>
 * </ul>
 * </p>
 *
 * <p>This class is NOT thread-safe: feed lines from a single reader thread.</p>
 */
public class SseStreamAccumulator {
    private static final String TAG = "SseStreamAccumulator";

    /** Receives incremental text deltas for live rendering. */
    public interface Listener {
        void onTextDelta(String text);
    }

    private final String apiType;
    private final Listener listener;

    // ==================== SSE framing state ====================

    private final StringBuilder dataBuffer = new StringBuilder();
    private boolean hasData = false;

    // ==================== Accumulated response state ====================

    private final StringBuilder content = new StringBuilder();

    /** OpenAI-style tool calls, ordered by stream index. */
    private final List<ToolCallAccumulator> openAiToolCalls = new ArrayList<>();

    /** Anthropic tool_use blocks keyed by content-block index. */
    private final Map<Integer, ToolCallAccumulator> anthropicToolCalls = new HashMap<>();

    private int promptTokens = 0;
    private int completionTokens = 0;
    private boolean hasUsage = false;

    private String error = null;
    private boolean done = false;

    /** Partial tool-call data accumulated across stream fragments. */
    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }

    public SseStreamAccumulator(String apiType, Listener listener) {
        this.apiType = apiType;
        this.listener = listener;
    }

    /**
     * Feed one raw line from the SSE stream. Handles SSE framing:
     * {@code data:} payloads accumulate until a blank line dispatches the event;
     * comment lines (starting with {@code :}) and {@code event:}/{@code id:}/
     * {@code retry:} fields are ignored (the JSON payloads carry their own type).
     */
    public void feedLine(String line) {
        if (line == null) return;

        if (line.isEmpty()) {
            dispatchEvent();
            return;
        }
        if (line.startsWith(":")) {
            return; // SSE comment (commonly used as keep-alive)
        }
        if (line.startsWith("data:")) {
            String value = line.substring(5);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if (hasData) {
                dataBuffer.append('\n');
            }
            dataBuffer.append(value);
            hasData = true;
        }
        // Any other field (event:, id:, retry:) is intentionally ignored.
    }

    /** Flush a pending event that was not terminated by a blank line (stream end). */
    public void finish() {
        dispatchEvent();
    }

    private void dispatchEvent() {
        if (!hasData) return;
        String data = dataBuffer.toString();
        dataBuffer.setLength(0);
        hasData = false;
        if (data.isEmpty()) return;

        if (LlmApiService.API_ANTHROPIC.equals(apiType)) {
            handleAnthropicEvent(data);
        } else {
            handleOpenAiEvent(data);
        }
    }

    // ==================== OpenAI dialect ====================

    private void handleOpenAiEvent(String data) {
        if ("[DONE]".equals(data.trim())) {
            done = true;
            return;
        }
        try {
            JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();

            // Usage-only chunk (sent last when stream_options.include_usage=true);
            // its "choices" array is empty, so parse usage before the choices check.
            if (chunk.has("usage") && !chunk.get("usage").isJsonNull()
                    && chunk.get("usage").isJsonObject()) {
                parseOpenAiUsage(chunk.getAsJsonObject("usage"));
            }

            if (!chunk.has("choices") || !chunk.get("choices").isJsonArray()) return;
            JsonArray choices = chunk.getAsJsonArray("choices");
            if (choices.size() == 0) return;

            JsonObject choice = choices.get(0).getAsJsonObject();
            if (!choice.has("delta") || choice.get("delta").isJsonNull()) return;
            JsonObject delta = choice.getAsJsonObject("delta");

            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                String text = delta.get("content").getAsString();
                if (!text.isEmpty()) {
                    content.append(text);
                    if (listener != null) listener.onTextDelta(text);
                }
            }

            if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()
                    && delta.get("tool_calls").isJsonArray()) {
                for (JsonElement tcEl : delta.getAsJsonArray("tool_calls")) {
                    JsonObject tc = tcEl.getAsJsonObject();
                    int index = tc.has("index") ? tc.get("index").getAsInt() : 0;
                    while (openAiToolCalls.size() <= index) {
                        openAiToolCalls.add(new ToolCallAccumulator());
                    }
                    ToolCallAccumulator acc = openAiToolCalls.get(index);
                    if (tc.has("id") && !tc.get("id").isJsonNull()) {
                        acc.id = tc.get("id").getAsString();
                    }
                    if (tc.has("function") && tc.get("function").isJsonObject()) {
                        JsonObject fn = tc.getAsJsonObject("function");
                        if (fn.has("name") && !fn.get("name").isJsonNull()) {
                            acc.name = fn.get("name").getAsString();
                        }
                        if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                            acc.arguments.append(fn.get("arguments").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse OpenAI stream chunk: " + truncate(data), e);
        }
    }

    private void parseOpenAiUsage(JsonObject usageObj) {
        if (usageObj.has("prompt_tokens")) {
            promptTokens = usageObj.get("prompt_tokens").getAsInt();
            hasUsage = true;
        }
        if (usageObj.has("completion_tokens")) {
            completionTokens = usageObj.get("completion_tokens").getAsInt();
            hasUsage = true;
        }
    }

    // ==================== Anthropic dialect ====================

    private void handleAnthropicEvent(String data) {
        try {
            JsonObject event = JsonParser.parseString(data).getAsJsonObject();
            String type = event.has("type") ? event.get("type").getAsString() : "";

            switch (type) {
                case "message_start": {
                    if (event.has("message") && event.get("message").isJsonObject()) {
                        JsonObject msg = event.getAsJsonObject("message");
                        if (msg.has("usage") && msg.get("usage").isJsonObject()) {
                            JsonObject usage = msg.getAsJsonObject("usage");
                            if (usage.has("input_tokens")) {
                                promptTokens = usage.get("input_tokens").getAsInt();
                                hasUsage = true;
                            }
                        }
                    }
                    break;
                }
                case "content_block_start": {
                    int index = event.has("index") ? event.get("index").getAsInt() : 0;
                    if (event.has("content_block") && event.get("content_block").isJsonObject()) {
                        JsonObject block = event.getAsJsonObject("content_block");
                        String blockType = block.has("type") ? block.get("type").getAsString() : "";
                        if ("tool_use".equals(blockType)) {
                            ToolCallAccumulator acc = new ToolCallAccumulator();
                            if (block.has("id") && !block.get("id").isJsonNull()) {
                                acc.id = block.get("id").getAsString();
                            }
                            if (block.has("name") && !block.get("name").isJsonNull()) {
                                acc.name = block.get("name").getAsString();
                            }
                            anthropicToolCalls.put(index, acc);
                        }
                    }
                    break;
                }
                case "content_block_delta": {
                    int index = event.has("index") ? event.get("index").getAsInt() : 0;
                    if (!event.has("delta") || !event.get("delta").isJsonObject()) break;
                    JsonObject delta = event.getAsJsonObject("delta");
                    String deltaType = delta.has("type") ? delta.get("type").getAsString() : "";

                    if ("text_delta".equals(deltaType)) {
                        String text = delta.has("text") ? delta.get("text").getAsString() : "";
                        if (!text.isEmpty()) {
                            content.append(text);
                            if (listener != null) listener.onTextDelta(text);
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        ToolCallAccumulator acc = anthropicToolCalls.get(index);
                        if (acc != null && delta.has("partial_json")
                                && !delta.get("partial_json").isJsonNull()) {
                            acc.arguments.append(delta.get("partial_json").getAsString());
                        }
                    }
                    break;
                }
                case "message_delta": {
                    if (event.has("usage") && event.get("usage").isJsonObject()) {
                        JsonObject usage = event.getAsJsonObject("usage");
                        if (usage.has("output_tokens")) {
                            completionTokens = usage.get("output_tokens").getAsInt();
                            hasUsage = true;
                        }
                    }
                    break;
                }
                case "error": {
                    String msg = "unknown stream error";
                    if (event.has("error") && event.get("error").isJsonObject()) {
                        JsonObject err = event.getAsJsonObject("error");
                        if (err.has("message")) {
                            msg = err.get("message").getAsString();
                        }
                    }
                    error = "Anthropic stream error: " + msg;
                    break;
                }
                default:
                    // ping, content_block_stop, message_stop, etc. — nothing to accumulate.
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse Anthropic stream event: " + truncate(data), e);
        }
    }

    // ==================== Result assembly ====================

    /**
     * Assemble the final {@link LlmApiService.LlmResponse} from accumulated state.
     * Shape matches the non-streaming parse path: text content (null when empty
     * and tool calls exist), tool calls, and token usage when reported.
     */
    public LlmApiService.LlmResponse buildResponse() {
        List<LlmApiService.ToolCall> toolCalls = new ArrayList<>();

        for (ToolCallAccumulator acc : openAiToolCalls) {
            LlmApiService.ToolCall tc = toToolCall(acc);
            if (tc != null) toolCalls.add(tc);
        }

        if (!anthropicToolCalls.isEmpty()) {
            List<Integer> indices = new ArrayList<>(anthropicToolCalls.keySet());
            Collections.sort(indices);
            for (Integer idx : indices) {
                LlmApiService.ToolCall tc = toToolCall(anthropicToolCalls.get(idx));
                if (tc != null) toolCalls.add(tc);
            }
        }

        String text = content.length() > 0 ? content.toString() : null;
        TokenUsage usage = hasUsage
                ? new TokenUsage(promptTokens + completionTokens, promptTokens, completionTokens)
                : null;

        if (text == null && toolCalls.isEmpty()) {
            text = "No response received";
        }

        return new LlmApiService.LlmResponse(text, toolCalls.isEmpty() ? null : toolCalls, usage);
    }

    private LlmApiService.ToolCall toToolCall(ToolCallAccumulator acc) {
        if (acc.name == null || acc.name.isEmpty()) {
            Log.w(TAG, "Skipping streamed tool call without a name");
            return null;
        }
        JsonObject args;
        String argsStr = acc.arguments.toString().trim();
        if (argsStr.isEmpty()) {
            args = new JsonObject();
        } else {
            try {
                args = JsonParser.parseString(argsStr).getAsJsonObject();
            } catch (Exception e) {
                Log.w(TAG, "Streamed tool call arguments were not valid JSON: "
                        + truncate(argsStr));
                args = new JsonObject();
            }
        }
        return new LlmApiService.ToolCall(acc.id, acc.name, args);
    }

    // ==================== Accessors ====================

    /** True when a fatal stream error was reported (e.g. Anthropic error event). */
    public boolean hasError() {
        return error != null;
    }

    public String getError() {
        return error;
    }

    /** True when the OpenAI [DONE] sentinel was received. */
    public boolean isDone() {
        return done;
    }

    /** The text accumulated so far (live view of the response). */
    public String getContent() {
        return content.toString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
