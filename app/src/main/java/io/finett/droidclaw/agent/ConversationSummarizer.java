package io.finett.droidclaw.agent;

import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
    private static final int TOKEN_THRESHOLD = 3000;
    private static final int KEEP_RECENT_MESSAGES = 8;
    
    private final LlmApiService apiService;
    private final MemoryRepository memoryRepository;
    
    public ConversationSummarizer(LlmApiService apiService, MemoryRepository memoryRepository) {
        this.apiService = apiService;
        this.memoryRepository = memoryRepository;
    }
    
    /**
     * Check if conversation needs summarization.
     * 
     * @param messages Current conversation history
     * @return true if token count exceeds threshold
     */
    public boolean needsSummarization(List<ChatMessage> messages) {
        int tokens = TokenEstimator.estimateTokens(messages);
        boolean needs = tokens >= TOKEN_THRESHOLD;
        
        if (needs) {
            Log.d(TAG, "Summarization needed: " + tokens + " tokens >= " + TOKEN_THRESHOLD);
        }
        
        return needs;
    }
    
    /**
     * Summarize conversation and save to daily note.
     * Returns compressed conversation with recent messages only.
     * 
     * @param messages Full conversation history
     * @return CompletableFuture with compressed message list
     */
    public CompletableFuture<List<ChatMessage>> summarizeAndSave(List<ChatMessage> messages) {
        CompletableFuture<List<ChatMessage>> future = new CompletableFuture<>();
        
        if (messages.isEmpty()) {
            future.complete(messages);
            return future;
        }
        
        // Determine split point
        int totalMessages = messages.size();
        int keepCount = Math.min(KEEP_RECENT_MESSAGES, totalMessages / 3);
        int summarizeCount = totalMessages - keepCount;
        
        if (summarizeCount <= 0) {
            Log.d(TAG, "Too few messages to summarize, keeping all");
            future.complete(messages);
            return future;
        }
        
        // Split messages
        List<ChatMessage> toSummarize = messages.subList(0, summarizeCount);
        List<ChatMessage> toKeep = messages.subList(summarizeCount, totalMessages);
        
        Log.d(TAG, "Summarizing " + summarizeCount + " messages, keeping " + keepCount + " recent");
        
        // Generate summary using LLM
        generateSummary(toSummarize)
            .thenAccept(summary -> {
                try {
                    // Save to daily note with timestamp
                    String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                    String entry = "## " + timestamp + " - Conversation Summary\n\n" + summary;
                    memoryRepository.appendToDailyNote(entry);
                    
                    Log.d(TAG, "Saved summary to daily note: " + summary.length() + " chars");
                    
                    // Return compressed conversation
                    future.complete(toKeep);
                    
                } catch (IOException e) {
                    Log.e(TAG, "Failed to save summary to daily note", e);
                    // Still return compressed conversation even if save failed
                    future.complete(toKeep);
                }
            })
            .exceptionally(e -> {
                Log.e(TAG, "Failed to generate summary", e);
                // On failure, return full conversation
                future.complete(messages);
                return null;
            });
        
        return future;
    }
    
    /**
     * Generate summary of messages using LLM.
     * 
     * @param messages Messages to summarize
     * @return CompletableFuture with summary text
     */
    private CompletableFuture<String> generateSummary(List<ChatMessage> messages) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
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
                future.complete(response.trim());
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Summary generation failed: " + error);
                // Fallback to simple concatenation
                String fallback = createFallbackSummary(messages);
                future.complete(fallback);
            }
        });
        
        return future;
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
     * Get current token threshold.
     * 
     * @return Token threshold value
     */
    public int getTokenThreshold() {
        return TOKEN_THRESHOLD;
    }
}