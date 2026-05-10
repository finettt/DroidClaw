package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(MockitoJUnitRunner.class)
public class SearxngSearchToolTest {

    @Mock
    private SettingsManager settingsManager;

    @Mock
    private OkHttpClient mockHttpClient;

    @Mock
    private Call mockCall;

    private SearxngSearchTool tool;

    @Before
    public void setUp() {
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        tool = new SearxngSearchTool(settingsManager, mockHttpClient);
    }

    @Test
    public void getName_returnsSearxngWebSearch() {
        assertEquals("searxng_web_search", tool.getName());
    }

    @Test
    public void getDefinition_hasCorrectNameAndParameters() {
        ToolDefinition definition = tool.getDefinition();

        assertNotNull(definition);
        assertEquals("searxng_web_search", definition.getFunction().getName());

        JsonObject params = definition.getFunction().getParameters();
        assertTrue(params.has("properties"));
        assertTrue(params.getAsJsonObject("properties").has("query"));
        assertTrue(params.getAsJsonObject("properties").has("pageno"));
        assertTrue(params.getAsJsonObject("properties").has("time_range"));
        assertTrue(params.getAsJsonObject("properties").has("language"));
    }

    @Test
    public void execute_missingQuery_returnsError() {
        JsonObject args = new JsonObject();
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter: query"));
    }

    @Test
    public void execute_emptyQuery_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("query", "   ");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Query must not be empty"));
    }

    @Test
    public void execute_successfulResponse_returnsParsedResults() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn(null);

        String jsonResponse = "{"
                + "\"query\":\"kotlin\","
                + "\"results\":["
                + "  {\"title\":\"Kotlin Lang\",\"url\":\"https://kotlinlang.org\",\"content\":\"Official site\"}"
                + "]"
                + "}";

        // Capture the callback and invoke it with a mock response
        doAnswer(invocation -> {
            Callback callback = invocation.getArgument(0);
            Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(jsonResponse, okhttp3.MediaType.parse("application/json")))
                    .build();
            callback.onResponse(mockCall, response);
            return null;
        }).when(mockCall).enqueue(any(Callback.class));

        JsonObject args = new JsonObject();
        args.addProperty("query", "kotlin");
        ToolResult result = tool.execute(args);

        assertTrue(result.isSuccess());
        JsonObject parsed = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals("kotlin", parsed.get("query").getAsString());
        assertEquals(1, parsed.get("result_count").getAsInt());
    }

    @Test
    public void execute_httpError_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn(null);

        doAnswer(invocation -> {
            Callback callback = invocation.getArgument(0);
            Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body(ResponseBody.create("error", okhttp3.MediaType.parse("text/plain")))
                    .build();
            callback.onResponse(mockCall, response);
            return null;
        }).when(mockCall).enqueue(any(Callback.class));

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("HTTP error 500"));
    }

    @Test
    public void execute_networkFailure_returnsError() {
        when(settingsManager.getEnvVar("SEARXNG_URL")).thenReturn(null);

        doAnswer(invocation -> {
            Callback callback = invocation.getArgument(0);
            callback.onFailure(mockCall, new IOException("Connection refused"));
            return null;
        }).when(mockCall).enqueue(any(Callback.class));

        JsonObject args = new JsonObject();
        args.addProperty("query", "test");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection refused"));
    }

    @Test
    public void parseResults_extractsResultsCorrectly() {
        String json = "{"
                + "\"query\":\"kotlin\","
                + "\"results\":["
                + "  {\"title\":\"Kotlin Lang\",\"url\":\"https://kotlinlang.org\",\"content\":\"Official site\"},"
                + "  {\"title\":\"Wiki\",\"url\":\"https://wikipedia.org/wiki/Kotlin\",\"snippet\":\"Programming language\"}"
                + "],"
                + "\"suggestions\":[\"kotlin coroutines\"]"
                + "}";

        JsonObject parsed = parseResultsViaReflection(json);

        assertEquals("kotlin", parsed.get("query").getAsString());
        assertEquals(2, parsed.get("result_count").getAsInt());
        assertTrue(parsed.has("results"));
        assertTrue(parsed.has("suggestions"));
    }

    @Test
    public void parseResults_handlesMalformedJson() {
        String badJson = "not json";

        JsonObject parsed = parseResultsViaReflection(badJson);

        assertTrue(parsed.has("error"));
        assertTrue(parsed.has("raw_response"));
    }

    // Helper to access private parseResults method
    private JsonObject parseResultsViaReflection(String json) {
        try {
            java.lang.reflect.Method method = SearxngSearchTool.class.getDeclaredMethod("parseResults", String.class);
            method.setAccessible(true);
            return (JsonObject) method.invoke(tool, json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}