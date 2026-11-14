package io.finett.droidclaw.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/** Behavioural tests for {@link LatestReleaseParser}. */
public class LatestReleaseParserTest {

    private final LatestReleaseParser parser = new LatestReleaseParser();

    private static String fullJson() {
        return "{"
                + "\"tag_name\": \"2026.10.10\","
                + "\"name\": \"DroidClaw 2026.10.10\","
                + "\"html_url\": \"https://github.com/finettt/DroidClaw/releases/tag/2026.10.10\","
                + "\"body\": \"Bug fixes and improvements.\","
                + "\"prerelease\": false,"
                + "\"draft\": false"
                + "}";
    }

    @Test
    public void parse_fullJson_populatesAllFields() {
        UpdateInfo info = parser.parse(fullJson()).getInfo();
        assertNotNull(info);
        assertEquals("2026.10.10", info.getTag());
        assertEquals("DroidClaw 2026.10.10", info.getTitle());
        assertEquals("https://github.com/finettt/DroidClaw/releases/tag/2026.10.10", info.getHtmlUrl());
        assertEquals("Bug fixes and improvements.", info.getBody());
        assertFalse(info.isPrerelease());
        assertFalse(parser.parse(fullJson()).isSkip());
    }

    @Test
    public void parse_nullBody_returnsNullInfoNoSkip() {
        LatestReleaseParser.ParsedRelease parsed = parser.parse(null);
        assertNull(parsed.getInfo());
        assertFalse(parsed.isSkip());
    }

    @Test
    public void parse_emptyBody_returnsNullInfoNoSkip() {
        LatestReleaseParser.ParsedRelease parsed = parser.parse("   ");
        assertNull(parsed.getInfo());
        assertFalse(parsed.isSkip());
    }

    @Test
    public void parse_malformedJson_returnsNullInfoNoSkip() {
        LatestReleaseParser.ParsedRelease parsed = parser.parse("{not json");
        assertNull(parsed.getInfo());
        assertFalse(parsed.isSkip());
    }

    @Test
    public void parse_missingBodyAndUrl_fieldsNullButTagKept() {
        String json = "{\"tag_name\": \"2026.10.10\", \"prerelease\": false}";
        UpdateInfo info = parser.parse(json).getInfo();
        assertNotNull(info);
        assertEquals("2026.10.10", info.getTag());
        assertNull(info.getTitle());
        assertNull(info.getHtmlUrl());
        assertNull(info.getBody());
    }

    @Test
    public void parse_noTag_returnsNullInfo() {
        String json = "{\"name\": \"No tag here\", \"body\": \"x\"}";
        assertNull(parser.parse(json).getInfo());
    }

    @Test
    public void parse_prereleaseFlagCarried() {
        String json = "{\"tag_name\": \"2026.08.11\", \"prerelease\": true}";
        UpdateInfo info = parser.parse(json).getInfo();
        assertNotNull(info);
        assertTrue(info.isPrerelease());
        assertFalse(parser.parse(json).isSkip());
    }

    @Test
    public void parse_draftAndPrereleaseBothTrue_signalsSkip() {
        String json = "{\"tag_name\": \"2026.11.07.1\", \"prerelease\": true, \"draft\": true}";
        LatestReleaseParser.ParsedRelease parsed = parser.parse(json);
        assertTrue(parsed.isSkip());
        assertTrue(parsed.getInfo().isPrerelease());
    }

    @Test
    public void parse_draftOnlyDoesNotSkip() {
        String json = "{\"tag_name\": \"2026.11.07.1\", \"draft\": true}";
        assertFalse(parser.parse(json).isSkip());
    }
}
