package io.finett.droidclaw.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;

public class ChatSearchManager {

    public static class MatchRange {
        public final int start;
        public final int end;

        public MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class SearchResult {
        public final ChatMessage message;
        public final int position;
        public final List<MatchRange> matches;

        public SearchResult(ChatMessage message, int position, List<MatchRange> matches) {
            this.message = message;
            this.position = position;
            this.matches = matches;
        }
    }

    public List<SearchResult> search(List<ChatMessage> messages, String query, boolean useRegex) {
        List<SearchResult> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        Pattern pattern = buildPattern(query, useRegex);
        if (pattern == null) {
            return results;
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            List<String> searchableTexts = getSearchableTexts(message);

            List<MatchRange> allMatches = new ArrayList<>();
            for (String text : searchableTexts) {
                if (text == null || text.isEmpty()) continue;
                allMatches.addAll(findMatches(pattern, text));
            }

            if (!allMatches.isEmpty()) {
                results.add(new SearchResult(message, i, allMatches));
            }
        }

        return results;
    }

    /**
     * Convenience overload – performs a simple case-insensitive string search.
     */
    public List<SearchResult> search(List<ChatMessage> messages, String query) {
        return search(messages, query, false);
    }

    private Pattern buildPattern(String query, boolean useRegex) {
        try {
            if (useRegex) {
                return Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } else {
                return Pattern.compile(Pattern.quote(query),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            }
        } catch (Exception e) {
            // Malformed regex – fall back to literal search
            return Pattern.compile(Pattern.quote(query),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
    }

    /**
     * Returns all text fields that should be searched for a message.
     */
    private List<String> getSearchableTexts(ChatMessage message) {
        List<String> texts = new ArrayList<>();

        texts.add(message.getContent());

        if (message.getType() == ChatMessage.TYPE_TOOL_CALL
                && message.getToolCalls() != null) {
            for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                texts.add(toolCall.getName());
                if (toolCall.getArguments() != null) {
                    texts.add(toolCall.getArguments().toString());
                }
            }
        }

        if (message.getType() == ChatMessage.TYPE_TOOL_RESULT) {
            texts.add(message.getToolName());
        }

        if (message.getType() == ChatMessage.TYPE_ATTACHMENT) {
            texts.add(message.getDisplayName());
            texts.add(message.getFilePath());
        }

        return texts;
    }

    private List<MatchRange> findMatches(Pattern pattern, String text) {
        List<MatchRange> ranges = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ranges.add(new MatchRange(matcher.start(), matcher.end()));
        }
        return ranges;
    }

    /**
     * Returns the index of the next result position after {@code currentPosition},
     * wrapping around to the beginning.
     */
    public int nextResultPosition(List<SearchResult> results, int currentPosition) {
        if (results.isEmpty()) return -1;
        for (SearchResult result : results) {
            if (result.position > currentPosition) {
                return result.position;
            }
        }
        // Wrap around
        return results.get(0).position;
    }

    /**
     * Returns the index of the previous result position before {@code currentPosition},
     * wrapping around to the end.
     */
    public int previousResultPosition(List<SearchResult> results, int currentPosition) {
        if (results.isEmpty()) return -1;
        for (int i = results.size() - 1; i >= 0; i--) {
            if (results.get(i).position < currentPosition) {
                return results.get(i).position;
            }
        }
        // Wrap around
        return results.get(results.size() - 1).position;
    }
}