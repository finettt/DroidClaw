package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.web.FetchException;
import io.finett.droidclaw.web.WebFetcher.FetchResult;
import io.finett.droidclaw.web.WebFetcher;
import org.junit.Test;

/**
 * Tool-level behavior for {@code fetch_page}: argument parsing, max_chars clamping,
 * section targeting, and error shape. Network is faked by a controllable WebFetcher.
 */
public class FetchPageToolTest {

    /** Fetcher whose single response (or exception) is fixed for the test. */
    static class StubFetcher extends WebFetcher {
        final byte[] body;
        final String contentType;
        final String finalUrl;
        final RuntimeException throwables;

        StubFetcher(String html, String contentType, String finalUrl) {
            this.body = html.getBytes(StandardCharsets.UTF_8);
            this.contentType = contentType;
            this.finalUrl = finalUrl;
            this.throwables = null;
        }

        StubFetcher(RuntimeException error) {
            this.body = new byte[0];
            this.contentType = null;
            this.finalUrl = null;
            this.throwables = error;
        }

        @Override
        public FetchResult fetch(String url) throws FetchException {
            if (throwables != null) {
                throw new FetchException(throwables.getMessage());
            }
            return WebFetcher.of(finalUrl, 200, contentType, body);
        }
    }

    private JsonObject execute(String url, String... kv) {
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html><body></body></html>", "text/html", url));
        JsonObject args = new JsonObject();
        args.addProperty("url", url);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = kv[i];
            String value = kv[i + 1];
            if ("max_chars".equals(key)) {
                args.addProperty(key, Integer.parseInt(value));
            } else {
                args.addProperty(key, value);
            }
        }
        ToolResult result = tool.execute(args);
        if (!result.isSuccess()) {
            return null;
        }
        return JsonParser.parseString(result.getContent()).getAsJsonObject();
    }

    @Test
    public void missingUrlReturnsError() {
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html></html>", "text/html", "http://x/"));
        ToolResult result = tool.execute(new JsonObject());
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("url"));
    }

    @Test
    public void defaultMaxCharsIsClampedToHardCap() {
        // 60000-char body, request 999999 -> clamps to hard cap 50000.
        String big = "x".repeat(60000);
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html><body><p>" + big + "</p></body></html>", "text/html", "http://x/"));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://x/");
        args.addProperty("max_chars", 999999);
        JsonObject result = JsonParser.parseString(tool.execute(args).getContent()).getAsJsonObject();

        assertTrue("should be truncated", result.get("truncated").getAsBoolean());
        assertEquals("total chars reported", 60000, result.get("content_chars").getAsInt());
        // 50000 chars of real content, then the truncation marker.
        assertEquals("clamped to hard cap", 50000,
                result.get("content").getAsString().indexOf("[\u2026truncated"));
        assertTrue(result.get("content").getAsString()
                .contains("truncated: showing 50000 of 60000 chars"));
    }

    @Test
    public void truncationMarkerTextIsExact() {
        String body = "y".repeat(30);
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html><body><h1>Intro</h1><p>" + body + "</p></body></html>",
                "text/html", "http://x/"));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://x/");
        args.addProperty("max_chars", 10);
        args.addProperty("section", "Intro");
        JsonObject result = JsonParser.parseString(tool.execute(args).getContent()).getAsJsonObject();
        int total = result.get("content_chars").getAsInt();
        assertTrue("should be truncated", result.get("truncated").getAsBoolean());
        String expected = "truncated: showing 10 of " + total + " chars. Use section=\"Intro\" to target a part.";
        assertTrue("marker text should be exact, was: " + result.get("content").getAsString(),
                result.get("content").getAsString().contains(expected));
    }

    @Test
    public void notTruncatedWhenWithinLimit() {
        String body = "short content";
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html><body><p>" + body + "</p></body></html>", "text/html", "http://x/"));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://x/");
        args.addProperty("max_chars", 1000);
        JsonObject result = JsonParser.parseString(tool.execute(args).getContent()).getAsJsonObject();

        assertFalse(result.get("truncated").getAsBoolean());
        assertEquals(13, result.get("content_chars").getAsInt());
    }

    @Test
    public void markdownFormatIsDefault() {
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                "<html><head><title>Doc</title></head><body><h1>Doc Title</h1><p>body para</p></body></html>",
                "text/html", "http://x/"));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://x/");
        JsonObject result = JsonParser.parseString(tool.execute(args).getContent()).getAsJsonObject();

        assertTrue(result.get("content").getAsString().contains("# Doc Title"));
        assertTrue(result.get("content").getAsString().contains("body para"));
        assertEquals("Doc", result.get("title").getAsString());
    }

    @Test
    public void sectionTargetingLimitsOutput() {
        String html = "<html><body>"
                + "<h1>Report</h1>"
                + "<h2>Intro</h2><p>intro text</p>"
                + "<h2>Details</h2><p>detail text</p>"
                + "</body></html>";
        FetchPageTool tool = new FetchPageTool(new StubFetcher(html, "text/html", "http://x/"));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://x/");
        args.addProperty("section", "Intro");
        String content = tool.execute(args).getContent();
        assertTrue(content.contains("intro text"));
        assertFalse("should not include the next section", content.contains("detail text"));
    }

    @Test
    public void errorShapeSurfacesFetchFailure() {
        FetchPageTool tool = new FetchPageTool(new StubFetcher(
                new RuntimeException("Blocked by SSRF guard: 127.0.0.1")));

        JsonObject args = new JsonObject();
        args.addProperty("url", "http://127.0.0.1/");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Blocked by SSRF guard"));
    }
}
