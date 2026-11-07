package io.finett.droidclaw.web;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * SearXNG search provider.
 *
 * <p>Makes a single {@code GET {searxngUrl}/search?q={query}&format=json} request, tolerates
 * missing fields, dedupes results by URL, and caps the number of hits.
 *
 * <p><b>{@link SsrfGuard} is intentionally not applied.</b> The SearXNG URL is explicit,
 * user-configured input — the same trust class as a user-configured LLM provider URL — and
 * these instances frequently live on a LAN. Applying the SSRF gate here would reject exactly
 * the deployments this tool is meant for.
 *
 * <p>Pure Java + OkHttp + Gson. Network work is synchronous in {@link #search}; callers that
 * must stay off the main thread (e.g. {@code WebSearchTool}) wrap it in a background thread.
 */
public class SearxngSearchProvider {

    private static final String TAG = "SearxngSearchProvider";

    /** Message handed to the agent when the instance is not serving JSON. */
    public static final String JSON_FORMAT_HINT =
            "enable JSON format on your SearXNG instance "
            + "(add 'formats: [json]' to its settings).";

    private static final int TIMEOUT_MS = 15_000;
    /** Hard cap on the response body that will be buffered and parsed. */
    static final int MAX_RESPONSE_BYTES = 1 * 1024 * 1024; // 1 MB
    private static final int MAX_REDIRECTS = 1;

    private final String searxngUrl;
    private final OkHttpClient httpClient;

    public SearxngSearchProvider(String searxngUrl) {
        this(searxngUrl, new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build());
    }

    /** Constructor for tests that inject a controllable client. */
    SearxngSearchProvider(String searxngUrl, OkHttpClient httpClient) {
        this.searxngUrl = searxngUrl;
        this.httpClient = httpClient;
    }

    /**
     * Run a search.
     *
     * @param query      the search text (trimmed, must not be empty)
     * @param maxResults maximum number of hits to return after dedupe (must be &gt; 0)
     * @return the deduped, capped hits; never {@code null}
     * @throws SearchUnavailable if the instance is unreachable, returns a non-2xx status, a
     *                           malformed payload, or JSON output is not enabled
     */
    public List<SearchHit> search(String query, int maxResults) throws SearchUnavailable {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) {
            throw new SearchUnavailable("Query must not be empty");
        }
        if (maxResults < 1) {
            maxResults = 1;
        }
        String encoded = URLEncoder.encode(q); // no-arg overload is UTF-8 and throws no checked exception

        Response response = null;
        try {
            response = fetchWithSingleRedirect(buildUrl(encoded));
            int code = response.code();
            if (code == 403) {
                throw new SearchUnavailable(
                        "SearXNG returned HTTP 403 \u2014 " + JSON_FORMAT_HINT);
            }
            if (!response.isSuccessful()) {
                throw new SearchUnavailable(
                        "SearXNG returned HTTP " + code + ": " + response.message());
            }
            ResponseBody body = response.body();
            String text = (body == null) ? "{}" : body.string();
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new SearchUnavailable("SearXNG response exceeds the "
                        + MAX_RESPONSE_BYTES + "-byte cap");
            }
            return parse(text, maxResults);
        } catch (IOException e) {
            throw new SearchUnavailable("Network error: " + e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private String buildUrl(String encodedQuery) throws SearchUnavailable {
        String base = (searxngUrl == null) ? "" : searxngUrl.trim();
        if (base.isEmpty()) {
            throw new SearchUnavailable("SearXNG URL is not configured");
        }
        String sep = base.endsWith("/") ? "" : "/";
        return base + sep + "search?format=json&q=" + encodedQuery;
    }

    /**
     * Follows at most one http(s) redirect. With {@code followRedirects(false)} on the client,
     * a single manual hop gives a strict "max 1 redirect" budget.
     */
    private Response fetchWithSingleRedirect(String url) throws IOException {
        Request request = newRequest(url);
        Response response = httpClient.newCall(request).execute();
        int code = response.code();
        if (code >= 300 && code < 400) {
            String location = response.header("Location");
            response.close();
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("Redirect without a Location header");
            }
            return httpClient.newCall(newRequest(location.trim())).execute();
        }
        return response;
    }

    private static Request newRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", WebFetcher.USER_AGENT)
                .build();
    }

    /**
     * Parse a SearXNG JSON response into hits. Missing fields become empty strings; a payload
     * that is not valid JSON (or not a JSON object) is a typed {@link SearchUnavailable}, never
     * a raw throw. The list is deduped by URL and capped at {@code maxResults}.
     */
    private List<SearchHit> parse(String text, int maxResults) throws SearchUnavailable {
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (Exception e) {
            throw new SearchUnavailable("Malformed SearXNG response: " + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new SearchUnavailable("SearXNG response is not a JSON object");
        }

        JsonArray arr = root.getAsJsonObject().getAsJsonArray("results");
        Set<String> seen = new HashSet<>();
        List<SearchHit> hits = new ArrayList<>();
        if (arr == null) {
            return hits;
        }
        for (int i = 0; i < arr.size() && hits.size() < maxResults; i++) {
            JsonElement el = arr.get(i);
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject r = el.getAsJsonObject();
            String url = jsonText(r, "url");
            if (url.isEmpty() || !seen.add(url.toLowerCase(Locale.ROOT))) {
                continue; // skip missing-URL and duplicate rows
            }
            hits.add(new SearchHit(jsonText(r, "title"), url, jsonText(r, "content")));
        }
        return hits;
    }

    private static String jsonText(JsonObject r, String key) {
        JsonElement el = r.get(key);
        if (el == null || !el.isJsonPrimitive() || el.isJsonNull()) {
            return "";
        }
        return el.getAsString();
    }

    /** Immutable search hit. All fields are non-null but may be empty strings. */
    public static class SearchHit {
        private final String title;
        private final String url;
        private final String snippet;

        public SearchHit(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getSnippet() {
            return snippet;
        }
    }
}
