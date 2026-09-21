package io.finett.droidclaw.workflow;

/**
 * Retry policy for a node. {@code maxAttempts} includes the first try, so the
 * default of 1 means "no retry".
 *
 * <p>Retries apply only to transport and timeout failures, never to validation
 * or content errors — retrying a malformed request just burns tokens.
 */
public final class WorkflowRetry {

    public static final int MIN_ATTEMPTS = 1;
    public static final int MAX_ATTEMPTS = 5;
    public static final int MAX_BACKOFF_MS = 30_000;

    private final int maxAttempts;
    private final int backoffMs;

    public WorkflowRetry(int maxAttempts, int backoffMs) {
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public static WorkflowRetry defaults() {
        return new WorkflowRetry(1, 0);
    }

    public int getMaxAttempts() { return maxAttempts; }
    public int getBackoffMs() { return backoffMs; }

    public boolean willRetry() { return maxAttempts > 1; }

    /**
     * Delay before attempt {@code n} (1-based index of the upcoming retry).
     * Backoff doubles per attempt and is capped at {@link #MAX_BACKOFF_MS}.
     */
    public long delayBeforeAttempt(int n) {
        if (n < 1 || backoffMs <= 0) return 0L;
        long delay = (long) backoffMs;
        for (int i = 1; i < n && delay < MAX_BACKOFF_MS; i++) {
            delay *= 2;
        }
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    public static boolean isValidAttempts(int v) { return v >= MIN_ATTEMPTS && v <= MAX_ATTEMPTS; }
    public static boolean isValidBackoff(int v) { return v >= 0 && v <= MAX_BACKOFF_MS; }
}
