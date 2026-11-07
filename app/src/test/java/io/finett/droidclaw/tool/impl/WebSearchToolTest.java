package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.web.SearchUnavailable;
import io.finett.droidclaw.web.SearxngSearchProvider;
import io.finett.droidclaw.web.SearxngSearchProvider.SearchHit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for the {@code web_search} {@link WebSearchTool}: argument validation, result
 * formatting, and end-to-end behaviour against a loopback SearXNG server.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebSearchToolTest {

    // Build a {"results":[...]} body from (url,title,content) rows via Gson, so the test
    // source needs no JSON quote-escaping.
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

    @Mock
    private SearxngSearchProvider provider;

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

    private JsonObject args(String query) {
        JsonObject o = new JsonObject();
        if (query != null) o.addProperty("query", query);
        return o;
    }

    @Test
    public void nameIsWebSearch() {
        assertEquals("web_search", new WebSearchTool(provider).getName());
    }

    @Test
    public void missingQueryIsRejected() {
        WebSearchTool tool = new WebSearchTool(provider);
        ToolResult r = tool.execute(args(null));
        assertFalse(r.isSuccess());
        assertTrue(r.getError().toLowerCase().contains("query"));
    }

    @Test
    public void emptyQueryIsRejected() {
        WebSearchTool tool = new WebSearchTool(provider);
        ToolResult r = tool.execute(args("   "));
        assertFalse(r.isSuccess());
    }

    @Test
    public void emptyQueryDoesNotHitProvider() throws Exception {
        WebSearchTool tool = new WebSearchTool(provider);
        tool.execute(args("  "));
        // provider.search must never be called for an empty query
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.never())
                .search(anyString(), anyInt());
    }

    @Test
    public void searchUnavailableIsReportedAsError() throws Exception {
        when(provider.search(anyString(), anyInt()))
                .thenThrow(new SearchUnavailable("SearXNG returned HTTP 403"));
        ToolResult r = new WebSearchTool(provider).execute(args("cats"));
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("403"));
    }

    @Test
    public void successfulSearchFormatsHits() throws Exception {
        when(provider.search(anyString(), anyInt()))
                .thenReturn(Arrays.asList(
                        new SearchHit("Alpha", "https://a.example/x", "first"),
                        new SearchHit("Beta", "https://b.example/y", "second")));
        JsonObject payload = JsonParser.parseString(
                new WebSearchTool(provider).execute(args("cats")).getContent()).getAsJsonObject();
        assertEquals("cats", payload.get("query").getAsString());
        assertEquals(2, payload.get("result_count").getAsInt());
        JsonArray arr = payload.getAsJsonArray("results");
        assertEquals("Alpha", arr.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("https://b.example/y", arr.get(1).getAsJsonObject().get("url").getAsString());
        assertEquals("second", arr.get(1).getAsJsonObject().get("snippet").getAsString());
    }

    @Test
    public void maxResultsIsClampedToHardCap() throws Exception {
        // Requesting far more than the hard cap must reach the provider clamped to
        // HARD_MAX_RESULTS, so the tool never returns more than 30 hits.
        when(provider.search(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(new SearchHit("t", "https://a.example/1", "")));
        JsonObject args = new JsonObject();
        args.addProperty("query", "q");
        args.addProperty("max_results", 1000);
        new WebSearchTool(provider).execute(args);
        ArgumentCaptor<Integer> requested = ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(provider).search(anyString(), requested.capture());
        assertEquals("Requested max_results clamped to the hard cap",
                WebSearchTool.HARD_MAX_RESULTS, requested.getValue().intValue());
    }

    @Test
    public void endToEndAgainstLoopbackServer() throws Exception {
        SearxngSearchProvider real = new SearxngSearchProvider(server.url("/").toString());
        server.enqueue(new MockResponse().setBody(
                resultsJson(new String[][] {{"https://a.example/z", "Z", "c"}})));
        ToolResult r = new WebSearchTool(real).execute(args("loopback"));
        assertTrue("expected success, was: " + r.getContent(), r.isSuccess());
        JsonObject payload = JsonParser.parseString(r.getContent()).getAsJsonObject();
        assertEquals(1, payload.getAsJsonArray("results").size());
    }

    @Test
    public void definitionMarksQueryRequired() {
        ToolDefinition def = new WebSearchTool(provider).getDefinition();
        JsonObject params = def.getFunction().getParameters();
        assertTrue(params.getAsJsonArray("required").toString().contains("query"));
    }
}
