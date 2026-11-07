package io.finett.droidclaw.web;

/**
 * Raised when a SearXNG search cannot be completed: the instance is unreachable, returns a
 * non-2xx status, returns a malformed payload, or JSON output is not enabled. The web search
 * tool converts this into a user-facing {@code web_search} error result.
 */
public class SearchUnavailable extends Exception {
    public SearchUnavailable(String message) {
        super(message);
    }
}
