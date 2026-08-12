package io.finett.droidclaw.api;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService.LlmResponse;
import io.finett.droidclaw.api.LlmApiService.ToolCall;

/**
 * Unit tests for {@link SseStreamAccumulator} — SSE framing plus both the
 * OpenAI and Anthropic streaming dialects.
 */
@RunWith(RobolectricTestRunner.class)
public class SseStreamAccumulatorTest {

    private List<String> feedAll(SseStreamAccumulator acc, String rawStream) {
        List<String> deltas = new ArrayList<>();
        for (String line : rawStream.split("\n", -1)) {
            acc.feedLine(line);
        }
        acc.finish();
        return deltas;
    }

    private SseStreamAccumulator openAiAccumulator(List<String> deltas) {
        return new SseStreamAccumulator(LlmApiService.API_OPENAI, deltas::add);
    }

    private SseStreamAccumulator anthropicAccumulator(List<String> deltas) {
        return new SseStreamAccumulator(LlmApiService.API_ANTHROPIC, deltas::add);
    }

    // ==================== OpenAI dialect ====================

    @Test
    public void openAi_textDeltas_accumulateAndNotify() {
        List<String> deltas = new ArrayList<>();
        String stream =
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n" +
                "\n" +
                "data: [DONE]\n" +
                "\n";
        feedAll(openAiAccumulator(deltas), stream);

        assertEquals(List.of("Hello", " world"), deltas);
    }

    @Test
    public void openAi_finalResponse_assemblesContentAndDoneFlag() {
        List<String> deltas = new ArrayList<>();
        SseStreamAccumulator acc = openAiAccumulator(deltas);
        feedAll(acc,
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{}}]}\n" +
                "\n" +
                "data: [DONE]\n" +
                "\n");

        assertTrue("DONE sentinel should be recorded", acc.isDone());
        LlmResponse response = acc.buildResponse();
        assertEquals("Hi", response.getContent());
        assertFalse("No tool calls expected", response.hasToolCalls());
    }

    @Test
    public void openAi_toolCalls_accumulatedAcrossFragments() {
        List<String> deltas = new ArrayList<>();
        String stream =
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
                "\"function\":{\"name\":\"file_read\",\"arguments\":\"{\\\"pa\"}}]}}]}\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                "\"function\":{\"arguments\":\"th\\\":\\\"a.txt\\\"}\"}}]}}]}\n" +
                "\n" +
                "data: [DONE]\n" +
                "\n";
        feedAll(openAiAccumulator(deltas), stream);

        // Rebuild to inspect tool calls
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        feedAll(acc, stream);
        LlmResponse response = acc.buildResponse();

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        ToolCall tc = response.getToolCalls().get(0);
        assertEquals("call_1", tc.getId());
        assertEquals("file_read", tc.getName());
        assertEquals("a.txt", tc.getArguments().get("path").getAsString());
    }

    @Test
    public void openAi_multipleToolCalls_orderedByIndex() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        // Deliver index 1 before index 0 to verify index-based ordering.
        feedAll(acc,
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_b\"," +
                "\"function\":{\"name\":\"second\",\"arguments\":\"{}\"}}]}}]}\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\"," +
                "\"function\":{\"name\":\"first\",\"arguments\":\"{}\"}}]}}]}\n" +
                "\n" +
                "data: [DONE]\n" +
                "\n");

        LlmResponse response = acc.buildResponse();
        assertEquals(2, response.getToolCalls().size());
        assertEquals("first", response.getToolCalls().get(0).getName());
        assertEquals("second", response.getToolCalls().get(1).getName());
    }

    @Test
    public void openAi_usageChunk_capturedFromTrailingUsageEvent() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n" +
                "\n" +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5," +
                "\"total_tokens\":15}}\n" +
                "\n" +
                "data: [DONE]\n" +
                "\n");

        LlmResponse response = acc.buildResponse();
        assertNotNull("Usage should be present", response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(5, response.getUsage().getCompletionTokens());
        assertEquals(15, response.getUsage().getTotalTokens());
    }

    @Test
    public void openAi_noUsage_usageIsNull() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n" +
                "\n" +
                "data: [DONE]\n\n");

        assertNull(acc.buildResponse().getUsage());
    }

    @Test
    public void openAi_malformedChunk_skippedWithoutCrash() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {not valid json\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"still alive\"}}]}\n" +
                "\n" +
                "data: [DONE]\n\n");

        assertEquals("still alive", acc.buildResponse().getContent());
    }

    // ==================== Anthropic dialect ====================

    @Test
    public void anthropic_textDeltas_accumulateAndNotify() {
        List<String> deltas = new ArrayList<>();
        String stream =
                "event: message_start\n" +
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}\n" +
                "\n" +
                "event: content_block_start\n" +
                "data: {\"type\":\"content_block_start\",\"index\":0," +
                "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n" +
                "\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n" +
                "\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\" there\"}}\n" +
                "\n" +
                "event: message_delta\n" +
                "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":3}," +
                "\"stop_reason\":\"end_turn\"}\n" +
                "\n" +
                "event: message_stop\n" +
                "data: {\"type\":\"message_stop\"}\n" +
                "\n";
        feedAll(anthropicAccumulator(deltas), stream);

        assertEquals(List.of("Hello", " there"), deltas);
    }

    @Test
    public void anthropic_usage_combinedFromMessageStartAndDelta() {
        SseStreamAccumulator acc = anthropicAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":12}}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"x\"}}\n" +
                "\n" +
                "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":4}}\n" +
                "\n");

        LlmResponse response = acc.buildResponse();
        assertNotNull(response.getUsage());
        assertEquals(12, response.getUsage().getPromptTokens());
        assertEquals(4, response.getUsage().getCompletionTokens());
        assertEquals(16, response.getUsage().getTotalTokens());
    }

    @Test
    public void anthropic_toolUse_partialJsonAccumulated() {
        SseStreamAccumulator acc = anthropicAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"type\":\"content_block_start\",\"index\":0," +
                "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"execute_shell\"," +
                "\"input\":{}}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"comm\"}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"and\\\":\\\"ls\\\"}\"}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_stop\",\"index\":0}\n" +
                "\n");

        LlmResponse response = acc.buildResponse();
        assertTrue(response.hasToolCalls());
        ToolCall tc = response.getToolCalls().get(0);
        assertEquals("toolu_1", tc.getId());
        assertEquals("execute_shell", tc.getName());
        assertEquals("ls", tc.getArguments().get("command").getAsString());
    }

    @Test
    public void anthropic_mixedTextAndToolUse_textStreamedToolAccumulated() {
        List<String> deltas = new ArrayList<>();
        SseStreamAccumulator acc = anthropicAccumulator(deltas);
        feedAll(acc,
                "data: {\"type\":\"content_block_start\",\"index\":0," +
                "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"Let me check.\"}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_start\",\"index\":1," +
                "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_2\",\"name\":\"file_read\"," +
                "\"input\":{}}}\n" +
                "\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":1," +
                "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"f.txt\\\"}\"}}\n" +
                "\n");

        assertEquals(List.of("Let me check."), deltas);
        LlmResponse response = acc.buildResponse();
        assertEquals("Let me check.", response.getContent());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("file_read", response.getToolCalls().get(0).getName());
    }

    @Test
    public void anthropic_errorEvent_setsError() {
        SseStreamAccumulator acc = anthropicAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}\n" +
                "\n" +
                "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"," +
                "\"message\":\"Overloaded\"}}\n" +
                "\n");

        assertTrue(acc.hasError());
        assertTrue(acc.getError().contains("Overloaded"));
        // Partial text still accumulated for inspection
        assertEquals("partial", acc.getContent());
    }

    // ==================== SSE framing ====================

    @Test
    public void sseFraming_commentsAndKeepAlivesIgnored() {
        List<String> deltas = new ArrayList<>();
        SseStreamAccumulator acc = openAiAccumulator(deltas);
        feedAll(acc,
                ": keep-alive ping\n" +
                "\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n" +
                "\n" +
                ": another comment\n" +
                "data: [DONE]\n" +
                "\n");

        assertEquals(List.of("a"), deltas);
    }

    @Test
    public void sseFraming_trailingEventWithoutBlankLine_flushedByFinish() {
        List<String> deltas = new ArrayList<>();
        SseStreamAccumulator acc = openAiAccumulator(deltas);
        // No trailing blank line — common when the connection closes right after.
        acc.feedLine("data: {\"choices\":[{\"delta\":{\"content\":\"tail\"}}]}");
        acc.finish();

        assertEquals(List.of("tail"), deltas);
    }

    @Test
    public void sseFraming_dataWithoutSpaceAfterColon() {
        List<String> deltas = new ArrayList<>();
        SseStreamAccumulator acc = openAiAccumulator(deltas);
        // SSE allows "data:" with no space before the payload.
        feedAll(acc,
                "data:{\"choices\":[{\"delta\":{\"content\":\"nospace\"}}]}\n" +
                "\n" +
                "data: [DONE]\n\n");

        assertEquals(List.of("nospace"), deltas);
    }

    @Test
    public void emptyStream_fallbackContent() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        acc.finish();

        LlmResponse response = acc.buildResponse();
        assertEquals("No response received", response.getContent());
        assertFalse(response.hasToolCalls());
        assertNull(response.getUsage());
    }

    @Test
    public void openAi_toolCallWithMalformedArguments_emptyArgsFallback() {
        SseStreamAccumulator acc = openAiAccumulator(new ArrayList<>());
        feedAll(acc,
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_x\"," +
                "\"function\":{\"name\":\"some_tool\",\"arguments\":\"{broken\"}}]}}]}\n" +
                "\n" +
                "data: [DONE]\n\n");

        LlmResponse response = acc.buildResponse();
        assertTrue(response.hasToolCalls());
        assertEquals("some_tool", response.getToolCalls().get(0).getName());
        assertNotNull(response.getToolCalls().get(0).getArguments());
    }
}
