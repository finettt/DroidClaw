package io.finett.droidclaw.agent;

import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.util.TokenEstimator;

/**
 * Handles conversation summarization when token limits are approached.
 * 
 * Flow:
 * 1. Monitor token count in conversation
 * 2. When threshold exceeded, summarize older messages
 * 3. Save summary to today's daily note
 * 4. Return compressed conversation (keep recent messages)
 */
public class ConversationSummarizer {
    private static final String TAG = "ConversationSummarizer";
    private static final int KEEP_RECENT_MESSAGES = 8;
    private static final double COMPRESSION_THRESHOLD = 0.80; // 80% of context length
    
    private final LlmApiService apiService;
    private final MemoryRepository memoryRepository;
    private final int contextWindow;
    
    public ConversationSummarizer(LlmApiService apiService, MemoryRepository memoryRepository) {
        this(apiService, memoryRepository, 4096); // Default context window
    }
    
    public ConversationSummarizer(LlmApiService apiService, MemoryRepository memoryRepository, int contextWindow) {
        this.apiService = apiService;
        this.memoryRepository = memoryRepository;
        this.contextWindow = contextWindow;
    }
    
    /**
     * Check if conversation needs summarization based on current context tokens.
     * Uses the "Last Usage" algorithm - checks actual context size from API.
     *
     * @param currentContextTokens Actual token count from last API response
     * @return true if token count exceeds threshold (80% of context window)
     */
    public boolean needsSummarization(int currentContextTokens) {
        int threshold = (int) (contextWindow * COMPRESSION_THRESHOLD);
        boolean needs = currentContextTokens >= threshold;
        
        if (needs) {
            Log.d(TAG, "Summarization needed: " + currentContextTokens + " tokens >= " +
                  threshold + " (" + (COMPRESSION_THRESHOLD * 100) + "% of " + contextWindow + ")");
        }
        
        return needs;
    }
    
    /**
     * Check if conversation needs summarization (deprecated - use currentContextTokens version).
     * This method uses estimation and should be replaced with the actual token count.
     *
     * @param messages Current conversation history
     * @return true if estimated token count exceeds threshold
     * @deprecated Use {@link #needsSummarization(int)} with actual context tokens instead
     */
    @Deprecated
    public boolean needsSummarization(List<ChatMessage> messages) {
        int estimatedTokens = TokenEstimator.estimateTokens(messages);
        int threshold = (int) (contextWindow * COMPRESSION_THRESHOLD);
        boolean needs = estimatedTokens >= threshold;
        
        if (needs) {
            Log.d(TAG, "Summarization needed (estimated): " + estimatedTokens + " tokens >= " + threshold);
        }
        
        return needs;
    }
    
    /**
     * Callback for summarizeAndSave operation.
     */
    public interface SummarizeCallback {
        void onResult(List<ChatMessage> compressedHistory);
        void onError(Throwable error);
    }
    
    /**
     * Summarize conversation and save to daily note.
     * Returns compressed conversation with recent messages only.
     * 
     * @param messages Full conversation history
     * @param callback Callback receiving compressed message list
     */
    public void summarizeAndSave(List<ChatMessage> messages, SummarizeCallback callback) {
        if (messages.isEmpty()) {
            callback.onResult(messages);
            return;
        }
        
        // Determine split point
        int totalMessages = messages.size();
        int keepCount = Math.min(KEEP_RECENT_MESSAGES, totalMessages / 3);
        int summarizeCount = totalMessages - keepCount;
        
        if (summarizeCount <= 0) {
            Log.d(TAG, "Too few messages to summarize, keeping all");
            callback.onResult(messages);
            return;
        }
        
        // Split messages
        final List<ChatMessage> toSummarize = new ArrayList<>(messages.subList(0, summarizeCount));
        final List<ChatMessage> toKeep = new ArrayList<>(messages.subList(summarizeCount, totalMessages));
        
        Log.d(TAG, "Summarizing " + summarizeCount + " messages, keeping " + keepCount + " recent");
        
        // Generate summary using LLM
        generateSummary(toSummarize, new SummaryCallback() {
            @Override
            public void onResult(String summary) {
                try {
                    // Save to daily note with timestamp
                    String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                    String entry = "## " + timestamp + " - Conversation Summary\n\n" + summary;
                    memoryRepository.appendToDailyNote(entry);
                    
                    Log.d(TAG, "Saved summary to daily note: " + summary.length() + " chars");
                    
                    // Return compressed conversation
                    callback.onResult(toKeep);
                    
                } catch (IOException e) {
                    Log.e(TAG, "Failed to save summary to daily note", e);
                    // Still return compressed conversation even if save failed
                    callback.onResult(toKeep);
                }
            }
            
            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Failed to generate summary", error);
                // On failure, return full conversation
                callback.onResult(messages);
            }
        });
    }
    
    /**
     * Callback for summary generation.
     */
    private interface SummaryCallback {
        void onResult(String summary);
        void onError(Throwable error);
    }
    
    /**
     * Generate summary of messages using LLM.
     * 
     * @param messages Messages to summarize
     * @param callback Callback receiving summary text
     */
    private void generateSummary(List<ChatMessage> messages, SummaryCallback callback) {
        // Build summary prompt
        String prompt = buildSummaryPrompt(messages);
        
        // Create message list for API call
        List<ChatMessage> summaryRequest = new ArrayList<>();
        summaryRequest.add(new ChatMessage(prompt, ChatMessage.TYPE_USER));
        
        // Call LLM
        apiService.sendMessage(summaryRequest, null, new LlmApiService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Summary generated: " + response.length() + " chars");
                callback.onResult(response.trim());
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Summary generation failed: " + error);
                // Fallback to simple concatenation
                String fallback = createFallbackSummary(messages);
                callback.onResult(fallback);
            }
        });
    }
    
    /**
     * Build prompt for LLM to generate summary.
     * 
     * @param messages Messages to summarize
     * @return Formatted prompt
     */
    private String buildSummaryPrompt(List<ChatMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Summarize this conversation segment briefly and concisely.\n\n");
        prompt.append("Focus on:\n");
        prompt.append("- Key topics discussed\n");
        prompt.append("- Important decisions or preferences\n");
        prompt.append("- Facts worth remembering\n");
        prompt.append("- Action items\n\n");
        prompt.append("Format as a brief paragraph or bullet points. Keep under 200 words.\n\n");
        prompt.append("Conversation:\n\n");
        
        // Add messages
        for (ChatMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isEmpty()) {
                continue;
            }
            
            String role = msg.isUser() ? "User" : "Assistant";
            String content = msg.getContent();
            
            // Truncate very long messages
            if (content.length() > 500) {
                content = content.substring(0, 497) + "...";
            }
            
            prompt.append(role).append(": ").append(content).append("\n\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * Create simple fallback summary when LLM fails.
     * 
     * @param messages Messages to summarize
     * @return Simple summary
     */
    private String createFallbackSummary(List<ChatMessage> messages) {
        int userMsgs = 0;
        int assistantMsgs = 0;
        
        for (ChatMessage msg : messages) {
            if (msg.isUser()) userMsgs++;
            else if (msg.isAssistant()) assistantMsgs++;
        }
        
        return "Conversation segment: " + userMsgs + " user messages and " + 
               assistantMsgs + " assistant messages summarized.";
    }
    
    /**
     * Get current token threshold based on context window.
     *
     * @return Token threshold value (80% of context window)
     */
    public int getTokenThreshold() {
        return (int) (contextWindow * COMPRESSION_THRESHOLD);
    }
    
    /**
     * Get context window size.
     *
     * @return Context window size in tokens
     */
    public int getContextWindow() {
        return contextWindow;
    }
}