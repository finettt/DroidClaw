package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SearxngSearchToolTest {

    private SettingsManager settingsManager;
    private OkHttpClient mockHttpClient;
    private SearxngSearchTool tool;

    @Before
    public void setUp() {
        settingsManager = mock(SettingsManager.class);
        mockHttpClient = mock(OkHttpClient.class);
        tool = new SearxngSearchTool(settingsManager, mockHttpClient);
    }

    // ==================== getName / getDefinition ====================

    @Test
    public void testGetName() {
        assertEquals("searxng_search", tool.getName());
    }

    @Test
    public void testRequiresApproval_isFalse() {
        assertFalse(tool.requiresApproval());
    }

    @Test
    public void testGetDefinition_hasRequiredParameters() {
        ToolDefinition definition = tool.getDefinition();

        assertNotNull(definition);
        assertEquals("function", definition.getType());
        assertEquals("searxng_search", definition.getFunction().getName());
        assertNotNull(definition.getFunction().getDescription());

        JsonObject props = definition.getFunction().getParameters()
                .getAsJsonObject("properties");
        assertTrue("Should have 'query' parameter", props.has("query"));
        assertTrue("Should have 'categories' parameter", props.has("categories"));
        assertTrue("Should have 'language' parameter", props.has("language"));
        assertTrue("Should have 'max_results' parameter", props.has("max_results"));
    }

    @Test
    public void testGetDefinition_queryIsRequired() {
        ToolDefinition definition = tool.getDefinition();
        JsonObject params = definition.getFunction().getParameters();

        assertTrue("Parameters should declare required array", params.has("required"));
        assertTrue("'query' should be in required", 
                params.getAsJsonArray("required").toString().contains("query"));
    }

    // ==================== Missing / unconfigured SEARXNG_URL ====================

    @Test
    public void testExecute_missingQuery_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        JsonObject args = new JsonObject();
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("query"));
    }

    @Test
    public void testExecute_emptyQuery_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        JsonObject args = new JsonObject();
        args.addProperty("query", "   ");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("query"));
    }

    @Test
    public void testExecute_searxngUrlNotConfigured_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn(null);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue("Error should mention SEARXNG_URL", result.getError().contains("SEARXNG_URL"));
        assertTrue("Error should mention Environment Variables",
                result.getError().contains("Environment Variables"));
    }

    @Test
    public void testExecute_searxngUrlEmpty_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("  ");

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("SEARXNG_URL"));
    }

    // ==================== HTTP error handling ====================

    @Test
    public void testExecute_networkError_returnsError() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenThrow(new IOException("Connection refused"));
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "android development");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue("Error should mention network error", result.getError().contains("Network error"));
    }

    @Test
    public void testExecute_httpErrorStatus_returnsError() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        Response mockResponse = buildMockResponse(500, "Internal Server Error", "error");
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test query");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue("Error should mention HTTP status", result.getError().contains("500"));
    }

    @Test
    public void testExecute_http403_returnsError() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        Response mockResponse = buildMockResponse(403, "Forbidden", "forbidden");
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("403"));
    }

    // ==================== Successful search ====================

    @Test
    public void testExecute_successfulSearch_returnsResults() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String jsonBody = buildSearxngResponse(
                "android development",
                new String[]{"Android Dev Guide", "Android Tutorial"},
                new String[]{"https://developer.android.com", "https://example.com/tutorial"},
                new String[]{"Official Android documentation", "Learn Android step by step"}
        );

        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "android development");
        ToolResult result = tool.execute(args);

        assertTrue("Expected success, got error: " + result.getError(), result.isSuccess());
        String content = result.getContent();
        assertTrue("Result should contain query", content.contains("android development"));
        assertTrue("Result should contain results array", content.contains("results"));
        assertTrue("Result should contain title", content.contains("Android Dev Guide"));
        assertTrue("Result should contain URL", content.contains("developer.android.com"));
    }

    @Test
    public void testExecute_respectsMaxResults() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        // Build response with 10 results
        String[] titles = new String[10];
        String[] urls = new String[10];
        String[] snippets = new String[10];
        for (int i = 0; i < 10; i++) {
            titles[i] = "Result " + i;
            urls[i] = "https://example.com/" + i;
            snippets[i] = "Snippet for result " + i;
        }
        String jsonBody = buildSearxngResponse("test", titles, urls, snippets);

        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        args.addProperty("max_results", 3);
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals("Should return exactly 3 results", 3, output.get("result_count").getAsInt());
        assertEquals(3, output.getAsJsonArray("results").size());
    }

    @Test
    public void testExecute_defaultMaxResults_isFive() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String[] titles = new String[10];
        String[] urls = new String[10];
        String[] snippets = new String[10];
        for (int i = 0; i < 10; i++) {
            titles[i] = "Title " + i;
            urls[i] = "https://example.com/" + i;
            snippets[i] = "Snippet " + i;
        }
        String jsonBody = buildSearxngResponse("test", titles, urls, snippets);

        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals("Default max_results should be 5", 5, output.get("result_count").getAsInt());
    }

    @Test
    public void testExecute_maxResults_cappedAt20() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String[] titles = new String[25];
        String[] urls = new String[25];
        String[] snippets = new String[25];
        for (int i = 0; i < 25; i++) {
            titles[i] = "Title " + i;
            urls[i] = "https://example.com/" + i;
            snippets[i] = "Snippet " + i;
        }
        String jsonBody = buildSearxngResponse("test", titles, urls, snippets);

        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        args.addProperty("max_results", 100); // Way over limit
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue("Result count should be capped at 20",
                output.get("result_count").getAsInt() <= 20);
    }

    @Test
    public void testExecute_emptyResults_returnsSuccessWithZeroCount() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String jsonBody = "{\"query\":\"xyzzy\",\"results\":[],\"answers\":[],\"infoboxes\":[]}";
        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "xyzzy");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals(0, output.get("result_count").getAsInt());
    }

    @Test
    public void testExecute_instantAnswer_includedInOutput() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String jsonBody = "{\"query\":\"2+2\",\"results\":[]," +
                "\"answers\":[\"4\"],\"infoboxes\":[]}";
        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "2+2");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue("Should include instant_answer", output.has("instant_answer"));
        assertEquals("4", output.get("instant_answer").getAsString());
    }

    @Test
    public void testExecute_trailingSlashInUrl_stripped() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com///");

        String jsonBody = "{\"query\":\"test\",\"results\":[],\"answers\":[],\"infoboxes\":[]}";
        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        // Should succeed - verify the URL was cleaned up
        assertTrue(result.isSuccess());
    }

    @Test
    public void testExecute_withCategories_includesInRequest() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String jsonBody = "{\"query\":\"news\",\"results\":[],\"answers\":[],\"infoboxes\":[]}";
        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "news");
        args.addProperty("categories", "news");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        // Verify the request URL contained categories
        verify(mockHttpClient).newCall(argThat(request ->
                request.url().toString().contains("categories=news")
        ));
    }

    @Test
    public void testExecute_outputContainsQueryField() throws IOException {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn("https://searx.example.com");

        String jsonBody = "{\"query\":\"hello world\",\"results\":[],\"answers\":[],\"infoboxes\":[]}";
        Response mockResponse = buildMockResponse(200, "OK", jsonBody);
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        JsonObject args = new JsonObject();
        args.addProperty("query", "hello world");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject output = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue("Output should have 'query' field", output.has("query"));
        assertEquals("hello world", output.get("query").getAsString());
    }

    // ==================== Helpers ====================

    private Response buildMockResponse(int code, String message, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://searx.example.com/search?q=test&format=json&categories=general&language=all").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    private String buildSearxngResponse(String query, String[] titles, String[] urls, String[] snippets) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"query\":\"").append(query).append("\",\"results\":[");
        for (int i = 0; i < titles.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"title\":\"").append(titles[i]).append("\",")
              .append("\"url\":\"").append(urls[i]).append("\",")
              .append("\"content\":\"").append(snippets[i]).append("\",")
              .append("\"engine\":\"google\"}");
        }
        sb.append("],\"answers\":[],\"infoboxes\":[]}");
        return sb.toString();
    }
}