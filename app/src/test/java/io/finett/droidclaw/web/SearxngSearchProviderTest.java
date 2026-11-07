package io.finett.droidclaw.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.finett.droidclaw.web.SearxngSearchProvider.SearchHit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Behavioural tests for {@link SearxngSearchProvider} against a real (loopback) HTTP server.
 */
public class SearxngSearchProviderTest {

    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private SearxngSearchProvider provider() {
        return new SearxngSearchProvider(server.url("/").toString(), new OkHttpClient());
    }

    // Build a {"results":[...]} body from (url,title,content) rows using Gson, so the
    // test source needs no JSON quote-escaping.
    private static String resultsJson(String[][] rows) {
        JsonArray arr = new JsonArray();
        for (String[] row : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("url", row[0]);
            o.addProperty("title", row[1]);
            o.addProperty("content", row[2]);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("results", arr);
        return root.toString();
    }

    @Test
    public void searchReturnsDedupedHits() throws Exception {
        server.enqueue(new MockResponse().setBody(
                resultsJson(new String[][] {
                    {"https://a.example/x", "Alpha", "First"},
                    {"https://b.example/y", "Beta", "Second"},
                    {"https://a.example/x", "Dup", "ignored"}})));
        List<SearchHit> hits = provider().search("cats", 10);
        assertEquals(2, hits.size());
        assertEquals("Alpha", hits.get(0).getTitle());
        assertEquals("https://b.example/y", hits.get(1).getUrl());
    }

    @Test
    public void searchCapsAtMaxResults() throws Exception {
        server.enqueue(new MockResponse().setBody(
                resultsJson(new String[][] {
                    {"https://a.example/1", "1", ""},
                    {"https://a.example/2", "2", ""},
                    {"https://a.example/3", "3", ""}})));
        assertEquals(2, provider().search("query", 2).size());
    }

    @Test
    public void searchWithNoResultsReturnsEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody(resultsJson(new String[][] {})));
        assertEquals(0, provider().search("query", 5).size());
    }

    @Test
    public void http403SurfacesJsonFormatHint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));
        try {
            provider().search("cats", 5);
            fail("Expected SearchUnavailable on 403");
        } catch (SearchUnavailable expected) {
            assertTrue(expected.getMessage().contains(SearxngSearchProvider.JSON_FORMAT_HINT));
        }
    }

    @Test
    public void nonSuccessStatusIsWrapped() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        try {
            provider().search("cats", 5);
            fail("Expected SearchUnavailable on 500");
        } catch (SearchUnavailable expected) {
            assertTrue(expected.getMessage().contains("500"));
        }
    }

    @Test
    public void malformedJsonIsWrapped() throws Exception {
        server.enqueue(new MockResponse().setBody("not json at all"));
        try {
            provider().search("cats", 5);
            fail("Expected SearchUnavailable on malformed JSON");
        } catch (SearchUnavailable expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("malformed"));
        }
    }

    @Test
    public void emptyQueryThrows() {
        try {
            provider().search("   ", 5);
            fail("Expected SearchUnavailable for empty query");
        } catch (SearchUnavailable expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("empty"));
        }
    }

    @Test
    public void singleRedirectIsFollowed() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/search?format=json").toString()));
        server.enqueue(new MockResponse().setBody(
                resultsJson(new String[][] {{"https://a.example/z", "Z", "c"}})));
        assertEquals(1, provider().search("cats", 5).size());
    }

    @Test
    public void urlIsBuiltWithFormatAndEncodedQuery() throws Exception {
        server.enqueue(new MockResponse().setBody(resultsJson(new String[][] {})));
        provider().search("hello world", 5);
        String path = server.takeRequest().getPath();
        assertTrue(path, path.startsWith("/search?format=json&q=") && path.contains("hello+world"));
    }
}
