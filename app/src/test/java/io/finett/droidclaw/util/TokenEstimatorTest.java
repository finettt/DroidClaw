package io.finett.droidclaw.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;

public class TokenEstimatorTest {

    @Test
    public void testEstimateTokens_emptyString_returnsZero() {
        int tokens = TokenEstimator.estimateTokens("");
        assertEquals("Empty string should return 0 tokens", 0, tokens);
    }

    @Test
    public void testEstimateTokens_nullText_returnsZero() {
        int tokens = TokenEstimator.estimateTokens((String) null);
        assertEquals("Null text should return 0 tokens", 0, tokens);
    }

    @Test
    public void testEstimateTokens_singleWord_correctEstimate() {
        // "hello" = 1 word * 1.3 = 1.3, ceil = 2 tokens
        int tokens = TokenEstimator.estimateTokens("hello");
        assertEquals("Single word should estimate correctly", 2, tokens);
    }

    @Test
    public void testEstimateTokens_multipleWords_correctEstimate() {
        // "hello world test" = 3 words * 1.3 = 3.9, ceil = 4 tokens
        int tokens = TokenEstimator.estimateTokens("hello world test");
        assertEquals("Multiple words should estimate correctly", 4, tokens);
    }

    @Test
    public void testEstimateTokens_withWhitespace_handlesCorrectly() {
        // Should handle extra whitespace - "hello world" = 2 words * 1.3 = 2.6, ceil = 3
        int tokens = TokenEstimator.estimateTokens("  hello   world  ");
        assertEquals("Should handle whitespace correctly", 3, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_aggregatesTokens() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("hello world", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("test message", ChatMessage.TYPE_ASSISTANT));

        int tokens = TokenEstimator.estimateTokens(messages);
        // "hello world" = 2 * 1.3 = 2.6, ceil = 3
        // "test message" = 2 * 1.3 = 2.6, ceil = 3
        // Total = 6
        assertEquals("Should aggregate tokens from all messages", 6, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_withNull_ignoresNull() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("hello", ChatMessage.TYPE_USER));
        messages.add(null);
        messages.add(new ChatMessage("world", ChatMessage.TYPE_ASSISTANT));

        int tokens = TokenEstimator.estimateTokens(messages);
        // "hello" = 1 * 1.3 = 1.3, ceil = 2
        // "world" = 1 * 1.3 = 1.3, ceil = 2
        // Total = 4
        assertEquals("Should ignore null messages", 4, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_withEmptyContent_ignores() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("hello", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("", ChatMessage.TYPE_USER));

        int tokens = TokenEstimator.estimateTokens(messages);
        // Only "hello" counts: 1 * 1.3 = 1.3, ceil = 2
        assertEquals("Should ignore messages with empty content", 2, tokens);
    }

    @Test
    public void testEstimateTokens_withRange_correctRange() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("one", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("two", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("three", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("four", ChatMessage.TYPE_USER));

        // Range [1, 3) = messages "two" and "three"
        int tokens = TokenEstimator.estimateTokens(messages, 1, 3);
        // "two" = 1 * 1.3 = 1.3, ceil = 2
        // "three" = 1 * 1.3 = 1.3, ceil = 2
        // Total = 4
        assertEquals("Should estimate tokens for specified range", 4, tokens);
    }

    @Test
    public void testEstimateTokens_withInvalidRange_returnsZero() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("one", ChatMessage.TYPE_USER));

        // Invalid range: start > end
        int tokens = TokenEstimator.estimateTokens(messages, 5, 3);
        assertEquals("Invalid range should return 0", 0, tokens);
    }

    @Test
    public void testEstimateTokens_withEndBeyondSize_clampsEnd() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("one", ChatMessage.TYPE_USER));
        messages.add(new ChatMessage("two", ChatMessage.TYPE_USER));

        // End beyond size should be clamped
        int tokens = TokenEstimator.estimateTokens(messages, 0, 100);
        // Both messages: "one" = 2 tokens, "two" = 2 tokens = 4 total
        assertEquals("Should clamp end to list size", 4, tokens);
    }

    @Test
    public void testEstimateTokens_withStartBeyondSize_returnsZero() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("one", ChatMessage.TYPE_USER));

        // Start beyond size
        int tokens = TokenEstimator.estimateTokens(messages, 10, 20);
        assertEquals("Start beyond size should return 0", 0, tokens);
    }

    @Test
    public void testFormatTokenCount_smallNumber_returnsTokens() {
        String formatted = TokenEstimator.formatTokenCount(450);
        assertEquals("Small number should show tokens", "450 tokens", formatted);
    }

    @Test
    public void testFormatTokenCount_largeNumber_returnsK() {
        String formatted = TokenEstimator.formatTokenCount(1500);
        assertEquals("Large number should show K", "1.5K tokens", formatted);
    }

    @Test
    public void testFormatTokenCount_exact1000_returnsK() {
        String formatted = TokenEstimator.formatTokenCount(1000);
        assertEquals("Exact 1000 should show K", "1.0K tokens", formatted);
    }

    @Test
    public void testFormatTokenCount_999_returnsTokens() {
        String formatted = TokenEstimator.formatTokenCount(999);
        assertEquals("Below 1000 should show tokens", "999 tokens", formatted);
    }

    @Test
    public void testFormatTokenCount_zero_returnsTokens() {
        String formatted = TokenEstimator.formatTokenCount(0);
        assertEquals("Zero should show tokens", "0 tokens", formatted);
    }

    @Test
    public void testEstimateTokens_withSpecialCharacters_handlesCorrectly() {
        // "hello, world! test..." = 3 words * 1.3 = 3.9, ceil = 4 tokens
        int tokens = TokenEstimator.estimateTokens("hello, world! test...");
        assertEquals("Should handle special characters", 4, tokens);
    }

    @Test
    public void testEstimateTokens_withNewlines_handlesCorrectly() {
        // "hello\nworld\ttest" = 3 words * 1.3 = 3.9, ceil = 4 tokens
        int tokens = TokenEstimator.estimateTokens("hello\nworld\ttest");
        assertEquals("Should handle newlines and tabs", 4, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_emptyList_returnsZero() {
        List<ChatMessage> messages = new ArrayList<>();
        int tokens = TokenEstimator.estimateTokens(messages);
        assertEquals("Empty list should return 0", 0, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_nullList_returnsZero() {
        int tokens = TokenEstimator.estimateTokens((List<ChatMessage>) null);
        assertEquals("Null list should return 0", 0, tokens);
    }

    @Test
    public void testEstimateTokens_ListMessages_withToolType_ignores() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(null, ChatMessage.TYPE_TOOL_CALL));
        messages.add(new ChatMessage(null, ChatMessage.TYPE_TOOL_RESULT));

        int tokens = TokenEstimator.estimateTokens(messages);
        assertEquals("Tool messages with no content should not count", 0, tokens);
    }
}
