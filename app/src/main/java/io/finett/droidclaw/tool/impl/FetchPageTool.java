package io.finett.droidclaw.tool.impl;

import android.util.Log;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.web.HtmlExtractor.ExtractResult;
import io.finett.droidclaw.web.FetchException;
import io.finett.droidclaw.web.HtmlExtractor;
import io.finett.droidclaw.web.WebFetcher;

/**
 * {@code fetch_page} tool: fetch a public page and return readable content as Markdown
 * or plain text, with optional section targeting and context-length truncation.
 *
 * <p>Network work runs on a background thread (blocked with a latch so the {@link Tool}
 * interface stays synchronous), following the same pattern as {@link SearxngSearchTool}.
 */
public class FetchPageTool implements Tool {

    private static final String TAG = "FetchPageTool";
    private static final String TOOL_NAME = "fetch_page";

    private static final int DEFAULT_MAX_CHARS = 20_000;
    private static final int HARD_MAX_CHARS = 50_000;

    /** Appended to truncated content. {@code %s} is the requested section (may be empty). */
    private static final String TRUNCATION_MARKER =
            "[\u2026truncated: showing %d of %d chars. Use section=\"%s\" to target a part.]";

    private static final int LATCH_TIMEOUT_SECONDS = 120;

    private final WebFetcher webFetcher;
    private final HtmlExtractor htmlExtractor = new HtmlExtractor();

    public FetchPageTool() {
        this(new WebFetcher());
    }

    /** Constructor for tests that inject a controllable fetcher. */
    FetchPageTool(WebFetcher webFetcher) {
        this.webFetcher = webFetcher;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("url", "The http(s) URL to fetch.", true)
                .addString("format",
                        "Output format: 'markdown' (default) or 'text'.", false)
                .addInteger("max_chars",
                        "Maximum content characters to return (hard cap " + HARD_MAX_CHARS
                                + ", clamped). Default " + DEFAULT_MAX_CHARS + ".", false)
                .addString("section",
                        "Only return the section whose heading contains this substring.", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Fetch a public http(s) web page and return its readable content. Extracts the "
                        + "main article body, converts it to Markdown (default) or plain text, "
                        + "targets a section by heading, and truncates long pages. Blocked or "
                        + "unsafe URLs are rejected. Only enabled when the Web tools setting is on.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("url") || arguments.get("url").isJsonNull()) {
                return ToolResult.error("Missing required parameter: url");
            }
            String url = arguments.get("url").getAsString().trim();
            if (url.isEmpty()) {
                return ToolResult.error("url must not be empty");
            }

            String format = resolveFormat(arguments);
            int maxChars = resolveMaxChars(arguments);
            String section = resolveSection(arguments);

            return runAsync(url, format, maxChars, section);
        } catch (Exception e) {
            Log.e(TAG, "fetch_page failed", e);
            String msg = e.getMessage();
            return ToolResult.error(msg != null ? msg : "Fetch failed: " + e.getClass().getSimpleName());
        }
    }

    private ToolResult runAsync(String url, String format, int maxChars, String section) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ToolResult> resultRef = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                WebFetcher.FetchResult fetch = webFetcher.fetch(url);
                ExtractResult extracted = htmlExtractor.extract(fetch.getBodyAsString(), format, section);
                resultRef.set(buildResult(fetch, extracted, maxChars, section));
            } catch (FetchException e) {
                Log.d(TAG, "fetch_page blocked or failed: " + e.getMessage());
                resultRef.set(ToolResult.error(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error fetching page", e);
                resultRef.set(ToolResult.error(
                        e.getMessage() != null ? e.getMessage() : "Fetch failed: " + e.getClass().getSimpleName()));
            } finally {
                latch.countDown();
            }
        });
        worker.start();

        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                worker.interrupt();
                return ToolResult.error("fetch_page timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("fetch_page was interrupted");
        }

        ToolResult result = resultRef.get();
        return result != null ? result : ToolResult.error("No response from fetch_page");
    }

    private ToolResult buildResult(WebFetcher.FetchResult fetch, ExtractResult extracted,
                                   int maxChars, String section) {
        String full = extracted.getContent();
        int total = full.length();
        int shown = Math.min(total, maxChars);
        boolean truncated = total > maxChars;

        StringBuilder content = new StringBuilder(full.length() + TRUNCATION_MARKER.length());
        content.append(full, 0, shown);
        if (truncated) {
            String sectionArg = (section == null) ? "" : section;
            content.append(String.format(Locale.ROOT, TRUNCATION_MARKER, shown, total, sectionArg));
        }

        JsonObject result = new JsonObject();
        result.addProperty("url", fetch.getFinalUrl());
        result.addProperty("status", fetch.getStatus());
        result.addProperty("title", extracted.getTitle());
        result.addProperty("content", content.toString());
        result.addProperty("truncated", truncated);
        result.addProperty("content_chars", total);

        Log.d(TAG, "fetch_page ok: " + fetch.getFinalUrl() + " " + shown + "/" + total + " chars");
        return ToolResult.success(result.toString());
    }

    private String resolveFormat(JsonObject arguments) {
        if (arguments.has("format") && !arguments.get("format").isJsonNull()) {
            String f = arguments.get("format").getAsString().trim().toLowerCase(Locale.ROOT);
            if ("text".equals(f)) {
                return "text";
            }
        }
        return "markdown";
    }

    private int resolveMaxChars(JsonObject arguments) {
        int requested = DEFAULT_MAX_CHARS;
        if (arguments.has("max_chars") && !arguments.get("max_chars").isJsonNull()) {
            requested = arguments.get("max_chars").getAsInt();
        }
        return Math.min(Math.max(requested, 1), HARD_MAX_CHARS);
    }

    private String resolveSection(JsonObject arguments) {
        if (arguments.has("section") && !arguments.get("section").isJsonNull()) {
            String section = arguments.get("section").getAsString().trim();
            return section.isEmpty() ? null : section;
        }
        return null;
    }
}
