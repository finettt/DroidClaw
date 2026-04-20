package io.finett.droidclaw.api;

public class TokenUsage {
    private final int totalTokens;
    private final int promptTokens;
    private final int completionTokens;

    public TokenUsage(int totalTokens, int promptTokens, int completionTokens) {
        this.totalTokens = totalTokens;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

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