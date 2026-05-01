package io.finett.droidclaw.worker;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.HeartbeatResponse;

public class HeartbeatWorkerTest {

    private static final Pattern HEARTBEAT_JSON_PATTERN = Pattern.compile(
            "\\{\\s*\"HEARTBEAT_OK\"\\s*:\\s*(true|false)\\s*\\}",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    public void parseStructured_healthyTrue_noIssues() {
        String json = "{\"healthy\":true,\"summary\":\"All systems normal\",\"issues\":[]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertTrue("Should be healthy", response.isHealthy());
        assertEquals("All systems normal", response.getSummary());
        assertTrue("Should have no issues", response.getIssues().isEmpty());
        assertFalse("Should not have issues", response.hasIssues());
    }

    @Test
    public void parseStructured_healthyFalse_withIssues() {
        String json = "{\"healthy\":false,\"summary\":\"Issues found\",\"issues\":[{\"category\":\"workspace\",\"description\":\"3 incomplete tasks\",\"severity\":\"low\"}]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertFalse("Should not be healthy", response.isHealthy());
        assertEquals("Issues found", response.getSummary());
        assertTrue("Should have issues", response.hasIssues());
        assertEquals("Should have 1 issue", 1, response.getIssues().size());

        HeartbeatResponse.Issue issue = response.getIssues().get(0);
        assertEquals("workspace", issue.getCategory());
        assertEquals("3 incomplete tasks", issue.getDescription());
        assertEquals("low", issue.getSeverity());
    }

    @Test
    public void parseStructured_multipleIssues_variousSeverities() {
        String json = "{\"healthy\":false,\"summary\":\"Multiple issues\",\"issues\":[" +
                "{\"category\":\"memory\",\"description\":\"Stale memories\",\"severity\":\"medium\"}," +
                "{\"category\":\"tasks\",\"description\":\"Failed execution\",\"severity\":\"high\"}" +
                "]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertFalse("Should not be healthy", response.isHealthy());
        assertEquals("Should have 2 issues", 2, response.getIssues().size());
        assertEquals("medium", response.getIssues().get(0).getSeverity());
        assertEquals("high", response.getIssues().get(1).getSeverity());
    }

    @Test
    public void parseStructured_withExtraFields_ignoresThem() {
        String json = "{\"healthy\":true,\"summary\":\"All good\",\"issues\":[],\"extra\":\"ignored\",\"model\":\"gpt-4\"}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertTrue("Should be healthy despite extra fields", response.isHealthy());
        assertEquals("All good", response.getSummary());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStructured_missingHealthyField_throws() {
        String json = "{\"summary\":\"Missing healthy\",\"issues\":[]}";
        HeartbeatResponse.fromJson(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStructured_missingSummaryField_throws() {
        String json = "{\"healthy\":true,\"issues\":[]}";
        HeartbeatResponse.fromJson(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStructured_missingIssuesField_throws() {
        String json = "{\"healthy\":true,\"summary\":\"Missing issues\"}";
        HeartbeatResponse.fromJson(json);
    }

    @Test(expected = com.google.gson.JsonSyntaxException.class)
    public void parseStructured_invalidJson_throws() {
        HeartbeatResponse.fromJson("not valid json at all");
    }

    @Test
    public void parseStructured_emptyIssuesArray() {
        String json = "{\"healthy\":true,\"summary\":\"No issues\",\"issues\":[]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertTrue("Should be healthy", response.isHealthy());
        assertNotNull("Issues list should not be null", response.getIssues());
        assertTrue("Issues should be empty", response.getIssues().isEmpty());
    }

    @Test
    public void getJsonSchema_returnsValidSchema() {
        JsonObject schema = HeartbeatResponse.getJsonSchema();

        assertNotNull("Schema should not be null", schema);
        assertEquals("Type should be object", "object", schema.get("type").getAsString());
        assertTrue("Should have properties", schema.has("properties"));
        assertTrue("Should have required fields", schema.has("required"));

        JsonObject properties = schema.getAsJsonObject("properties");
        assertTrue("Should have healthy property", properties.has("healthy"));
        assertTrue("Should have summary property", properties.has("summary"));
        assertTrue("Should have issues property", properties.has("issues"));

        JsonObject healthyProp = properties.getAsJsonObject("healthy");
        assertEquals("boolean", healthyProp.get("type").getAsString());

        JsonObject issuesProp = properties.getAsJsonObject("issues");
        assertEquals("array", issuesProp.get("type").getAsString());
        assertTrue("Issues should have items", issuesProp.has("items"));

        JsonArray required = schema.getAsJsonArray("required");
        assertEquals("Should require 3 fields", 3, required.size());
    }

    @Test
    public void getJsonSchema_issueSchemaHasSeverityEnum() {
        JsonObject schema = HeartbeatResponse.getJsonSchema();
        JsonObject properties = schema.getAsJsonObject("properties");
        JsonObject issuesProp = properties.getAsJsonObject("issues");
        JsonObject items = issuesProp.getAsJsonObject("items");
        JsonObject issueProps = items.getAsJsonObject("properties");
        JsonObject severityProp = issueProps.getAsJsonObject("severity");

        assertTrue("Severity should have enum", severityProp.has("enum"));
        JsonArray enumValues = severityProp.getAsJsonArray("enum");
        assertEquals("Should have 3 severity levels", 3, enumValues.size());
        assertEquals("low", enumValues.get(0).getAsString());
        assertEquals("medium", enumValues.get(1).getAsString());
        assertEquals("high", enumValues.get(2).getAsString());
    }

    @Test
    public void issue_toString_formatsCorrectly() {
        HeartbeatResponse.Issue issue = new HeartbeatResponse.Issue(
                "workspace", "Incomplete tasks found", "medium");

        assertEquals("[MEDIUM] workspace: Incomplete tasks found", issue.toString());
    }

    @Test
    public void issue_gettersReturnCorrectValues() {
        HeartbeatResponse.Issue issue = new HeartbeatResponse.Issue(
                "category1", "description1", "high");

        assertEquals("category1", issue.getCategory());
        assertEquals("description1", issue.getDescription());
        assertEquals("high", issue.getSeverity());
    }

    @Test
    public void issue_withEmptyStrings() {
        HeartbeatResponse.Issue issue = new HeartbeatResponse.Issue("", "", "low");

        assertEquals("", issue.getCategory());
        assertEquals("", issue.getDescription());
        assertEquals("low", issue.getSeverity());
        assertEquals("[LOW] : ", issue.toString());
    }

    @Test
    public void detectLegacy_trueAtEndOfResponse() {
        String content = "System review complete. All healthy.\n\n{\"HEARTBEAT_OK\": true}";
        assertTrue("Should detect HEARTBEAT_OK true", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_trueWithSpaces() {
        String content = "{ \"HEARTBEAT_OK\" : true }";
        assertTrue("Should detect with spaces", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_false_issuesPresent() {
        String content = "Found issues: 3 tasks incomplete\n{\"HEARTBEAT_OK\": false}";
        assertFalse("Should return false when HEARTBEAT_OK is false", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_noMarker() {
        String content = "System has issues. Some checks failed.";
        assertFalse("Should not detect when no JSON present", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_nullContent() {
        assertFalse("Should not detect in null content", detectLegacyHeartbeatOk(null));
    }

    @Test
    public void detectLegacy_emptyContent() {
        assertFalse("Should not detect in empty content", detectLegacyHeartbeatOk(""));
    }

    @Test
    public void detectLegacy_refusalPrefix() {
        String content = "[REFUSAL] I'm sorry, I cannot assist with that request.";
        assertFalse("Should return false for refusal", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_mixedCase_regexMatchesButKeyLookupFails() {
        String content = "{\"heartbeat_ok\": true}";
        // The regex is case-insensitive and matches, but Gson key lookup is case-sensitive
        // so "HEARTBEAT_OK" won't find "heartbeat_ok" - result is false
        assertFalse("Mixed case key should not be found by case-sensitive Gson lookup",
                detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacy_multipleMarkers_usesFirst() {
        String content = "{\"HEARTBEAT_OK\": false} some text {\"HEARTBEAT_OK\": true}";
        // First match is false
        assertFalse("Should use first match", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectStructured_healthyTrue() {
        String content = "{\"healthy\":true,\"summary\":\"All good\",\"issues\":[]}";
        assertTrue("Should detect structured healthy", detectStructuredHeartbeatOk(content));
    }

    @Test
    public void detectStructured_healthyFalse() {
        String content = "{\"healthy\":false,\"summary\":\"Issues\",\"issues\":[{\"category\":\"test\",\"description\":\"test\",\"severity\":\"low\"}]}";
        assertFalse("Should detect structured unhealthy", detectStructuredHeartbeatOk(content));
    }

    @Test
    public void detectStructured_invalidJson_fallsBackToLegacy() {
        String content = "{\"healthy\":true,\"summary\":\"bad\"} {\"HEARTBEAT_OK\": true}";
        // First tries structured, fails (missing issues), falls back to legacy
        assertTrue("Should fall back to legacy", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void refusal_startsWithRefusalMarker() {
        String content = "[REFUSAL] Content policy violation";
        assertTrue("Should detect refusal prefix", isRefusal(content));
    }

    @Test
    public void refusal_doesNotStartWithRefusal() {
        String content = "Some normal response [REFUSAL] in middle";
        assertFalse("Should not detect refusal in middle", isRefusal(content));
    }

    @Test
    public void refusal_emptyString() {
        assertFalse("Empty string is not refusal", isRefusal(""));
    }

    @Test
    public void refusal_nullString() {
        assertFalse("Null is not refusal", isRefusal(null));
    }

    @Test
    public void stalenessLevel_fresh_neverRun() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        long now = System.currentTimeMillis();

        assertEquals("Never run should be FRESH",
                HeartbeatConfig.StalenessLevel.FRESH, config.getStalenessLevel(now));
        assertEquals("Ratio should be 0", 0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void stalenessLevel_fresh_withinInterval() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 15 * 60 * 1000L);

        assertEquals("Within interval should be FRESH",
                HeartbeatConfig.StalenessLevel.FRESH, config.getStalenessLevel(now));
        assertTrue("Ratio should be < 1", config.getStalenessRatio(now) < 1.0);
    }

    @Test
    public void stalenessLevel_slightlyLate_oneInterval() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 30 * 60 * 1000L);

        assertEquals("Exactly one interval should be SLIGHTLY_LATE",
                HeartbeatConfig.StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(now));
        assertEquals("Ratio should be 1.0", 1.0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void stalenessLevel_slightlyLate_twoIntervals() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 60 * 60 * 1000L);

        assertEquals("Two intervals should be SLIGHTLY_LATE",
                HeartbeatConfig.StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(now));
        assertEquals("Ratio should be 2.0", 2.0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void stalenessLevel_slightlyLate_threeIntervals() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 90 * 60 * 1000L);

        assertEquals("Three intervals should be SLIGHTLY_LATE",
                HeartbeatConfig.StalenessLevel.SLIGHTLY_LATE, config.getStalenessLevel(now));
        assertEquals("Ratio should be 3.0", 3.0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void stalenessLevel_dead_fourIntervals() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 120 * 60 * 1000L);

        assertEquals("Four intervals should be DEAD",
                HeartbeatConfig.StalenessLevel.DEAD, config.getStalenessLevel(now));
        assertTrue("Ratio should be > 3", config.getStalenessRatio(now) > 3.0);
    }

    @Test
    public void stalenessRatio_zeroInterval_returnsZero() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 0L, now - 1000L);

        assertEquals("Zero interval should return 0 ratio", 0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void stalenessRatio_negativeInterval_returnsZero() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, -1L, now - 1000L);

        assertEquals("Negative interval should return 0 ratio", 0, config.getStalenessRatio(now), 0.001);
    }

    @Test
    public void shouldRun_disabled_returnsFalse() {
        HeartbeatConfig config = new HeartbeatConfig(false, 30 * 60 * 1000L, 0L);
        assertFalse("Disabled should not run", config.shouldRun(System.currentTimeMillis()));
    }

    @Test
    public void shouldRun_enabled_firstRun_returnsTrue() {
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, 0L);
        assertTrue("First run should execute", config.shouldRun(System.currentTimeMillis()));
    }

    @Test
    public void shouldRun_intervalNotElapsed_returnsFalse() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 60 * 60 * 1000L, now - 5 * 60 * 1000L);
        assertFalse("Should not run when interval not elapsed", config.shouldRun(now));
    }

    @Test
    public void shouldRun_intervalElapsed_returnsTrue() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 60 * 60 * 1000L);
        assertTrue("Should run when interval elapsed", config.shouldRun(now));
    }

    @Test
    public void shouldRun_exactlyAtInterval_returnsTrue() {
        long now = System.currentTimeMillis();
        HeartbeatConfig config = new HeartbeatConfig(true, 30 * 60 * 1000L, now - 30 * 60 * 1000L);
        assertTrue("Should run exactly at interval", config.shouldRun(now));
    }

    @Test
    public void defaultConfig_values() {
        HeartbeatConfig config = HeartbeatConfig.getDefaults();

        assertFalse("Default should be disabled", config.isEnabled());
        assertEquals("Default interval should be 30 min", 30 * 60 * 1000L, config.getIntervalMillis());
        assertEquals("Default last run should be 0", 0L, config.getLastRunTimestamp());
    }

    @Test
    public void config_settersAndGetters() {
        HeartbeatConfig config = new HeartbeatConfig();

        config.setEnabled(true);
        assertTrue("Should be enabled", config.isEnabled());

        config.setIntervalMillis(60000L);
        assertEquals("Interval should be 60s", 60000L, config.getIntervalMillis());

        config.setLastRunTimestamp(12345L);
        assertEquals("Last run should be 12345", 12345L, config.getLastRunTimestamp());
    }

    private boolean detectLegacyHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        if (content.startsWith("[REFUSAL]")) {
            return false;
        }

        // Try structured output first
        if (content.contains("\"healthy\"") && content.contains("\"summary\"")) {
            try {
                HeartbeatResponse response = HeartbeatResponse.fromJson(content);
                return response.isHealthy();
            } catch (Exception e) {
                // Fall through to legacy
            }
        }

        Matcher matcher = HEARTBEAT_JSON_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonStr = matcher.group(0);
            try {
                JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                if (json.has("HEARTBEAT_OK")) {
                    return json.get("HEARTBEAT_OK").getAsBoolean();
                }
            } catch (Exception e) {
                // Parse failed
            }
        }

        return false;
    }

    private boolean detectStructuredHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        try {
            HeartbeatResponse response = HeartbeatResponse.fromJson(content);
            return response.isHealthy();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRefusal(String content) {
        return content != null && content.startsWith("[REFUSAL]");
    }
}
