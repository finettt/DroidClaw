package io.finett.droidclaw.tool.impl;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.web.SearchUnavailable;
import io.finett.droidclaw.web.SearxngSearchProvider;
import io.finett.droidclaw.web.SearxngSearchProvider.SearchHit;

/**
 * {@code web_search} tool: run a search against the user's own SearXNG instance and return
 * results with titles, URLs, and snippets.
 *
 * <p>The SearXNG URL comes from the {@code searxngUrl} Agent Settings field (not an environment
 * variable); the tool is registered only when web tools are enabled and that field is non-empty.
 * Network work runs on a background thread (blocked with a latch so the {@link Tool} interface
 * stays synchronous), following the same pattern as {@link FetchPageTool}.
 */
public class WebSearchTool implements Tool {

    private static final String TAG = "WebSearchTool";
    private static final String TOOL_NAME = "web_search";

    private static final int DEFAULT_MAX_RESULTS = 10;
    static final int HARD_MAX_RESULTS = 30;

    private static final int LATCH_TIMEOUT_SECONDS = 30;

    private final SearxngSearchProvider provider;

    public WebSearchTool(String searxngUrl) {
        this(new SearxngSearchProvider(searxngUrl));
    }

    /** Constructor for tests that inject a controllable provider. */
    WebSearchTool(SearxngSearchProvider provider) {
        this.provider = provider;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("query", "The search query string.", true)
                .addInteger("max_results",
                        "Maximum number of results to return (hard cap " + HARD_MAX_RESULTS
                                + ", clamped). Default " + DEFAULT_MAX_RESULTS + ".", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Search the web using the user's own SearXNG instance. Returns results with "
                        + "titles, URLs, and snippets. The SearXNG URL is configured in Agent "
                        + "Settings, and the tool is only available when Web tools are enabled.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("query") || arguments.get("query").isJsonNull()) {
                return ToolResult.error("Missing required parameter: query");
            }
            String query = arguments.get("query").getAsString().trim();
            if (query.isEmpty()) {
                return ToolResult.error("query must not be empty");
            }
            int maxResults = resolveMaxResults(arguments);
            return runAsync(query, maxResults);
        } catch (Exception e) {
            Log.e(TAG, "web_search failed", e);
            String msg = e.getMessage();
            return ToolResult.error(msg != null ? msg : "Search failed: " + e.getClass().getSimpleName());
        }
    }

    private ToolResult runAsync(String query, int maxResults) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ToolResult> resultRef = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                List<SearchHit> hits = provider.search(query, maxResults);
                resultRef.set(buildResult(query, hits));
            } catch (SearchUnavailable e) {
                Log.d(TAG, "web_search unavailable: " + e.getMessage());
                resultRef.set(ToolResult.error(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error searching", e);
                resultRef.set(ToolResult.error(
                        e.getMessage() != null ? e.getMessage() : "Search failed: " + e.getClass().getSimpleName()));
            } finally {
                latch.countDown();
            }
        });
        worker.start();

        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                worker.interrupt();
                return ToolResult.error("web_search timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("web_search was interrupted");
        }

        ToolResult result = resultRef.get();
        return result != null ? result : ToolResult.error("No response from web_search");
    }

    private ToolResult buildResult(String query, List<SearchHit> hits) {
        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("result_count", hits.size());
        JsonArray arr = new JsonArray();
        for (SearchHit hit : hits) {
            JsonObject h = new JsonObject();
            h.addProperty("title", hit.getTitle());
            h.addProperty("url", hit.getUrl());
            h.addProperty("snippet", hit.getSnippet());
            arr.add(h);
        }
        result.add("results", arr);
        Log.d(TAG, "web_search ok: " + hits.size() + " hits for '" + query + "'");
        return ToolResult.success(result.toString());
    }

    private int resolveMaxResults(JsonObject arguments) {
        int requested = DEFAULT_MAX_RESULTS;
        if (arguments.has("max_results") && !arguments.get("max_results").isJsonNull()) {
            requested = arguments.get("max_results").getAsInt();
        }
        return Math.min(Math.max(requested, 1), HARD_MAX_RESULTS);
    }
}
