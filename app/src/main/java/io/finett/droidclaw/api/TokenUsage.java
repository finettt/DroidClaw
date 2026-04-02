package io.finett.droidclaw.api;

/**
 * Represents token usage information from an API response.
 * This follows the "Last Usage" algorithm where we track:
 * - Current context tokens (from the last API response)
 * - Session cumulative tokens (total spent across all requests)
 */
public class TokenUsage {
    private final int totalTokens;
    private final int promptTokens;
    private final int completionTokens;

    public TokenUsage(int totalTokens, int promptTokens, int completionTokens) {
        this.totalTokens = totalTokens;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    /**
     * Get total tokens from the last API response.
     * This represents the actual current context size.
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * Get prompt tokens (input) from the last API response.
     */
    public int getPromptTokens() {
        return promptTokens;
    }

    /**
     * Get completion tokens (output) from the last API response.
     */
    public int getCompletionTokens() {
        return completionTokens;
    }

    /**
     * Check if usage data is available (non-zero).
     */
    public boolean isAvailable() {
        return totalTokens > 0;
    }

    @Override
    public String toString() {
        return "TokenUsage{" +
                "total=" + totalTokens +
                ", prompt=" + promptTokens +
                ", completion=" + completionTokens +
                '}';
    }
}