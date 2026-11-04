package io.finett.droidclaw.tool;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.tool.impl.FetchPageTool;
import io.finett.droidclaw.util.Flaky;
import io.finett.droidclaw.util.FlakyTestRule;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Instrumented tests for {@link FetchPageTool} on real public URLs.
 *
 * <p>The "Web tools" setting is enabled through {@link SettingsManager}, then the tool is
 * exercised against live pages (happy path markdown + title, section targeting, truncation
 * marker). Loopback and private addresses are expected to be rejected by the SSRF guard
 * ({@link io.finett.droidclaw.web.SsrfGuard.SsrfViolation}, surfaced as a tool error).
 */
@RunWith(AndroidJUnit4.class)
public class FetchPageToolInstrumentedTest {

    // example.com (IANA): tiny, always up, single <title> + paragraph, no headings.
    private static final String EXAMPLE_URL = "https://example.com/";
    // Wikipedia HTML article: stable, multi-heading, long enough to truncate.
    private static final String HTML_URL = "https://en.wikipedia.org/wiki/HTML";

    private Context context;
    private SettingsManager settingsManager;

    @Rule
    public FlakyTestRule flakyTestRule = new FlakyTestRule();

    @Before
    public void setUp() {
        context = getApplicationContext();
        settingsManager = new SettingsManager(context);
        settingsManager.clear();
        // Enable the Web tools switch through the normal settings path.
        AgentConfig config = settingsManager.getAgentConfig();
        config.setWebToolsEnabled(true);
        settingsManager.setAgentConfig(config);
        assertTrue("webToolsEnabled should be on after SettingsManager update",
                settingsManager.getAgentConfig().isWebToolsEnabled());
    }

    @Test
    public void enabledFlag_isReflectedAndToolRegistered() {
        assertEquals("Web tools should be enabled", true,
                settingsManager.getAgentConfig().isWebToolsEnabled());
        ToolRegistry registry = new ToolRegistry(context, settingsManager);
        assertTrue("fetch_page must register when Web tools are on",
                registry.hasToolWithName("fetch_page"));
    }

    @Test
    public void disabledFlag_keepsToolUnregistered() {
        settingsManager.clear();
        ToolRegistry registry = new ToolRegistry(context, settingsManager);
        assertFalse("fetch_page must stay unregistered when Web tools are off",
                registry.hasToolWithName("fetch_page"));
    }

    @Flaky(maxAttempts = 5)
    @Test
    public void happyPath_returnsMarkdownAndTitle() {
        FetchPageTool tool = new FetchPageTool();
        JsonObject args = new JsonObject();
        args.addProperty("url", EXAMPLE_URL);
        args.addProperty("format", "markdown");
        ToolResult result = tool.execute(args);
        assertTrue("expected success, got error: " + result.getError(), result.isSuccess());

        JsonObject json = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertEquals("Example Domain", json.get("title").getAsString());
        assertTrue("markdown content should contain the page body",
                json.get("content").getAsString().contains("documentation examples"));
    }

    @Flaky(maxAttempts = 5)
    @Test
    public void sectionTargeting_returnsOnlyRequestedSection() {
        FetchPageTool tool = new FetchPageTool();
        JsonObject args = new JsonObject();
        args.addProperty("url", HTML_URL);
        args.addProperty("format", "markdown");
        args.addProperty("section", "History");
        args.addProperty("max_chars", 8000);
        ToolResult result = tool.execute(args);
        assertTrue("expected success, got error: " + result.getError(), result.isSuccess());

        JsonObject json = JsonParser.parseString(result.getContent()).getAsJsonObject();
        String content = json.get("content").getAsString();
        assertNotNull(content);
        assertTrue("targeted section heading should appear", content.contains("History"));
        assertFalse("unrelated far section should be excluded",
                content.contains("Client-side scripting"));
    }

    @Flaky(maxAttempts = 5)
    @Test
    public void truncation_appendsMarker() {
        FetchPageTool tool = new FetchPageTool();
        JsonObject args = new JsonObject();
        args.addProperty("url", HTML_URL);
        args.addProperty("format", "markdown");
        args.addProperty("max_chars", 100);
        ToolResult result = tool.execute(args);
        assertTrue("expected success, got error: " + result.getError(), result.isSuccess());

        JsonObject json = JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue("should be truncated", json.get("truncated").getAsBoolean());
        assertTrue("total should exceed the cap",
                json.get("content_chars").getAsInt() > 100);
        assertTrue("marker should reference the requested cap",
                json.get("content").getAsString().contains("truncated: showing 100 of "));
    }

    @Test
    public void ssrfBlocksLoopback() {
        FetchPageTool tool = new FetchPageTool();
        JsonObject args = new JsonObject();
        args.addProperty("url", "http://127.0.0.1/");
        ToolResult result = tool.execute(args);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue("loopback must be blocked by SSRF guard, was: " + result.getError(),
                result.getError().toLowerCase().contains("ssrf"));
    }

    @Test
    public void ssrfBlocksPrivateClassA() {
        FetchPageTool tool = new FetchPageTool();
        JsonObject args = new JsonObject();
        args.addProperty("url", "http://10.0.0.1/");
        ToolResult result = tool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue("private 10.0.0.0/8 must be blocked by SSRF guard, was: " + result.getError(),
                result.getError().toLowerCase().contains("ssrf"));
    }
}
