package io.finett.droidclaw.tool.impl;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SearxngSearchTool implements Tool {

    private static final String TAG = "SearxngSearchTool";
    private static final String TOOL_NAME = "searxng_search";
    private static final String ENV_KEY_URL = "SEARXNG_URL";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int ABSOLUTE_MAX_RESULTS = 20;
    private static final int TIMEOUT_SECONDS = 15;

    private final SettingsManager settingsManager;
    private final OkHttpClient httpClient;

    public SearxngSearchTool(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /** Package-private constructor for testing with a custom OkHttpClient. */
    SearxngSearchTool(SettingsManager settingsManager, OkHttpClient httpClient) {
        this.settingsManager = settingsManager;
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("query", "The search query to look up", true)
                .addString("categories",
                        "Search categories: general, news, images, science, files, social_media, videos, music, it. " +
                        "Default: general", false)
                .addString("language",
                        "Language code for results, e.g. 'en', 'de', 'fr', or 'all'. Default: all", false)
                .addInteger("max_results",
                        "Maximum number of results to return (1-20). Default: 5", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Search the web using a SearXNG instance. Returns structured search results with titles, " +
                "URLs, and content snippets. Requires SEARXNG_URL to be set in Settings → Environment Variables.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        // Validate required parameter
        if (!arguments.has("query") || arguments.get("query").getAsString().trim().isEmpty()) {
            return ToolResult.error("Missing required parameter: query");
        }

        // Resolve SearXNG base URL from env vars
        String baseUrl = settingsManager.getEnvVar(ENV_KEY_URL);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ToolResult.error(
                    "SEARXNG_URL is not configured. " +
                    "Go to Settings → Environment Variables and add SEARXNG_URL = https://your-searxng-instance.example.com"
            );
        }
        baseUrl = baseUrl.trim().replaceAll("/+$", ""); // strip trailing slashes

        String query = arguments.get("query").getAsString().trim();

        String categories = "general";
        if (arguments.has("categories") && !arguments.get("categories").isJsonNull()) {
            String cat = arguments.get("categories").getAsString().trim();
            if (!cat.isEmpty()) {
                categories = cat;
            }
        }

        String language = "all";
        if (arguments.has("language") && !arguments.get("language").isJsonNull()) {
            String lang = arguments.get("language").getAsString().trim();
            if (!lang.isEmpty()) {
                language = lang;
            }
        }

        int maxResults = DEFAULT_MAX_RESULTS;
        if (arguments.has("max_results") && !arguments.get("max_results").isJsonNull()) {
            maxResults = arguments.get("max_results").getAsInt();
            if (maxResults < 1) maxResults = 1;
            if (maxResults > ABSOLUTE_MAX_RESULTS) maxResults = ABSOLUTE_MAX_RESULTS;
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = baseUrl + "/search?q=" + encodedQuery
                    + "&format=json"
                    + "&categories=" + URLEncoder.encode(categories, StandardCharsets.UTF_8.name())
                    + "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8.name());

            Log.d(TAG, "Querying SearXNG: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "DroidClaw/1.0")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.error(
                            "SearXNG returned HTTP " + response.code() +
                            ". Check that your SEARXNG_URL is correct and the instance is reachable."
                    );
                }

                String body = response.body() != null ? response.body().string() : "";
                if (body.isEmpty()) {
                    return ToolResult.error("SearXNG returned an empty response.");
                }

                return parseResults(body, maxResults, query);
            }

        } catch (IOException e) {
            Log.e(TAG, "Network error querying SearXNG", e);
            return ToolResult.error(
                    "Network error reaching SearXNG: " + e.getMessage() +
                    ". Check that SEARXNG_URL is reachable from this device."
            );
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in SearxngSearchTool", e);
            return ToolResult.error("Search error: " + e.getMessage());
        }
    }

    private ToolResult parseResults(String jsonBody, int maxResults, String query) {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

            JsonArray results = new JsonArray();
            if (root.has("results") && root.get("results").isJsonArray()) {
                JsonArray rawResults = root.getAsJsonArray("results");
                int count = Math.min(rawResults.size(), maxResults);
                for (int i = 0; i < count; i++) {
                    JsonObject raw = rawResults.get(i).getAsJsonObject();
                    JsonObject result = new JsonObject();

                    if (raw.has("title")) {
                        result.addProperty("title", raw.get("title").getAsString());
                    }
                    if (raw.has("url")) {
                        result.addProperty("url", raw.get("url").getAsString());
                    }
                    if (raw.has("content")) {
                        result.addProperty("snippet", raw.get("content").getAsString());
                    }
                    if (raw.has("engine")) {
                        result.addProperty("engine", raw.get("engine").getAsString());
                    }

                    results.add(result);
                }
            }

            JsonObject output = new JsonObject();
            output.addProperty("query", query);
            output.addProperty("result_count", results.size());
            output.add("results", results);

            // Include infoboxes/answers if available
            if (root.has("answers") && root.get("answers").isJsonArray()) {
                JsonArray answers = root.getAsJsonArray("answers");
                if (answers.size() > 0) {
                    output.addProperty("instant_answer", answers.get(0).getAsString());
                }
            }
            if (root.has("infoboxes") && root.get("infoboxes").isJsonArray()) {
                JsonArray infoboxes = root.getAsJsonArray("infoboxes");
                if (infoboxes.size() > 0) {
                    JsonObject box = infoboxes.get(0).getAsJsonObject();
                    if (box.has("content")) {
                        output.addProperty("infobox", box.get("content").getAsString());
                    }
                }
            }

            return ToolResult.success(output);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse SearXNG response", e);
            return ToolResult.error("Failed to parse SearXNG response: " + e.getMessage());
        }
    }
}