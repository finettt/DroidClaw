package io.finett.droidclaw.util;

import java.util.List;

import io.finett.droidclaw.model.ChatMessage;

/**
 * Utility class for estimating token counts in text and messages.
 * 
 * Uses a simple heuristic: words * 1.3 ≈ tokens
 * This is approximate but sufficient for context management.
 */
public class TokenEstimator {
    private static final double WORDS_TO_TOKENS = 1.3;
    
    /**
     * Estimate token count for a text string.
     * 
     * @param text Text to estimate tokens for
     * @return Estimated token count
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Split on whitespace and count words
        String[] words = text.trim().split("\\s+");
        int wordCount = words.length;
        
        // Apply conversion factor
        return (int) (wordCount * WORDS_TO_TOKENS);
    }
    
    /**
     * Estimate token count for a list of chat messages.
     * Only counts content from messages (ignores tool calls).
     * 
     * @param messages List of chat messages
     * @return Total estimated token count
     */
    public static int estimateTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int totalTokens = 0;
        for (ChatMessage message : messages) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                totalTokens += estimateTokens(message.getContent());
            }
        }
        
        return totalTokens;
    }
    
    /**
     * Estimate token count for a range of messages.
     * 
     * @param messages List of chat messages
     * @param startIndex Start index (inclusive)
     * @param endIndex End index (exclusive)
     * @return Estimated token count for the range
     */
    public static int estimateTokens(List<ChatMessage> messages, int startIndex, int endIndex) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int validStart = Math.max(0, startIndex);
        int validEnd = Math.min(messages.size(), endIndex);
        
        if (validStart >= validEnd) {
            return 0;
        }
        
        return estimateTokens(messages.subList(validStart, validEnd));
    }
    
    /**
     * Format token count for display.
     * 
     * @param tokens Token count
     * @return Formatted string (e.g., "1.2K tokens", "450 tokens")
     */
    public static String formatTokenCount(int tokens) {
        if (tokens >= 1000) {
            return String.format("%.1fK tokens", tokens / 1000.0);
        } else {
            return tokens + " tokens";
        }
    }
}