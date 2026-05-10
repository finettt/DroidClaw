package io.finett.droidclaw.tool.impl;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tool that searches the web via a SearXNG instance.
 *
 * <p>The SearXNG URL is resolved from the {@code SEARXNG_URL} environment variable
 * configured in Settings. If unset, a sensible public default is used.
 */
public class SearxngSearchTool implements Tool {

    private static final String TAG = "SearxngSearchTool";
    private static final String TOOL_NAME = "searxng_web_search";
    private static final String DEFAULT_SEARXNG_URL = "https://search.sapti.me";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final SettingsManager settingsManager;
    private final OkHttpClient httpClient;

    public SearxngSearchTool(SettingsManager settingsManager) {
        this(settingsManager, new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build());
    }

    /** Package-private constructor for tests that need to inject a mock OkHttpClient. */
    SearxngSearchTool(SettingsManager settingsManager, OkHttpClient httpClient) {
        this.settingsManager = settingsManager;
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("query", "The search query string", true)
                .addInteger("pageno", "Page number for pagination (starts at 1). Default: 1.", false)
                .addString("time_range", "Time range filter: 'day', 'month', or 'year'. Optional.", false)
                .addString("language", "Language code (e.g., 'en', 'ru', 'all'). Default: 'all'.", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Search the web using a SearXNG instance. Returns search results with titles, URLs, "
                + "and snippets. Configure the SearXNG URL via the SEARXNG_URL environment variable "
                + "in Settings -> Environment Variables.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("query")) {
                return ToolResult.error("Missing required parameter: query");
            }

            String query = arguments.get("query").getAsString();
            if (query.trim().isEmpty()) {
                return ToolResult.error("Query must not be empty");
            }

            // Resolve SearXNG URL from env vars or fall back to default
            String searxngUrl = resolveSearxngUrl();
            if (searxngUrl == null || searxngUrl.isEmpty()) {
                return ToolResult.error(
                        "SearXNG URL is not configured. Set the SEARXNG_URL environment variable "
                        + "in Settings -> Environment Variables."
                );
            }

            // Build request URL
            StringBuilder urlBuilder = new StringBuilder(searxngUrl);
            if (!searxngUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append("search?format=json&q=")
                    .append(URLEncoder.encode(query, StandardCharsets.UTF_8.toString()));

            // Optional: pagination
            int pageNo = 1;
            if (arguments.has("pageno") && !arguments.get("pageno").isJsonNull()) {
                pageNo = arguments.get("pageno").getAsInt();
                if (pageNo < 1) {
                    pageNo = 1;
                }
            }
            urlBuilder.append("&pageno=").append(pageNo);

            // Optional: time range filter
            if (arguments.has("time_range") && !arguments.get("time_range").isJsonNull()) {
                String timeRange = arguments.get("time_range").getAsString();
                if (isValidTimeRange(timeRange)) {
                    urlBuilder.append("&time_range=")
                            .append(URLEncoder.encode(timeRange, StandardCharsets.UTF_8.toString()));
                }
            }

            // Optional: language
            if (arguments.has("language") && !arguments.get("language").isJsonNull()) {
                String language = arguments.get("language").getAsString();
                urlBuilder.append("&language=")
                        .append(URLEncoder.encode(language, StandardCharsets.UTF_8.toString()));
            } else {
                urlBuilder.append("&language=all");
            }

            String requestUrl = urlBuilder.toString();
            Log.d(TAG, "Searching: " + requestUrl);

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "DroidClaw/1.0")
                    .build();

            // Execute asynchronously to avoid NetworkOnMainThreadException,
            // but block the calling thread with a CountDownLatch so the Tool
            // interface remains synchronous.
            return executeAsync(request);

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error", e);
            String msg = e.getMessage();
            return ToolResult.error(msg != null ? msg : "Search failed: " + e.getClass().getSimpleName());
        }
    }

    private ToolResult executeAsync(Request request) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ToolResult> resultRef = new AtomicReference<>();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Search request failed", e);
                String msg = e.getMessage();
                resultRef.set(ToolResult.error(
                        msg != null ? msg : "Network request failed: " + e.getClass().getSimpleName()
                ));
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        resultRef.set(ToolResult.error(
                                "HTTP error " + response.code() + ": " + response.message()
                        ));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    resultRef.set(ToolResult.success(parseResults(responseBody)));
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            boolean completed = latch.await(DEFAULT_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
            if (!completed) {
                return ToolResult.error("Search request timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Search was interrupted");
        }

        ToolResult result = resultRef.get();
        return result != null ? result : ToolResult.error("No response from search");
    }

    /** Resolve SearXNG base URL from env vars, or fall back to the built-in default. */
    private String resolveSearxngUrl() {
        if (settingsManager != null) {
            String envUrl = settingsManager.getEnvVar("SEARXNG_URL");
            if (envUrl != null && !envUrl.trim().isEmpty()) {
                return envUrl.trim();
            }
        }
        return DEFAULT_SEARXNG_URL;
    }

    private boolean isValidTimeRange(String timeRange) {
        return "day".equals(timeRange) || "month".equals(timeRange) || "year".equals(timeRange);
    }

    /** Parse SearXNG JSON response into a simplified result object. */
    private JsonObject parseResults(String jsonResponse) {
        JsonObject result = new JsonObject();
        JsonArray resultsArray = new JsonArray();

        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (root.has("results") && root.get("results").isJsonArray()) {
                JsonArray rawResults = root.getAsJsonArray("results");
                for (int i = 0; i < rawResults.size(); i++) {
                    JsonObject rawResult = rawResults.get(i).getAsJsonObject();
                    JsonObject simplified = new JsonObject();

                    if (rawResult.has("title")) {
                        simplified.addProperty("title", rawResult.get("title").getAsString());
                    }
                    if (rawResult.has("url")) {
                        simplified.addProperty("url", rawResult.get("url").getAsString());
                    }
                    if (rawResult.has("content")) {
                        simplified.addProperty("snippet", rawResult.get("content").getAsString());
                    } else if (rawResult.has("snippet")) {
                        simplified.addProperty("snippet", rawResult.get("snippet").getAsString());
                    }
                    if (rawResult.has("engine")) {
                        simplified.addProperty("engine", rawResult.get("engine").getAsString());
                    }

                    resultsArray.add(simplified);
                }
            }

            result.addProperty("query", root.has("query")
                    ? root.get("query").getAsString() : "");
            result.addProperty("result_count", resultsArray.size());
            result.add("results", resultsArray);

            if (root.has("suggestions") && root.get("suggestions").isJsonArray()) {
                result.add("suggestions", root.getAsJsonArray("suggestions"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse search results", e);
            result.addProperty("error", "Failed to parse response: " + e.getMessage());
            result.addProperty("raw_response", jsonResponse);
        }

        return result;
    }
}