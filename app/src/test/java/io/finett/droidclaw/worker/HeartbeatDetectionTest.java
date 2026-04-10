package io.finett.droidclaw.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.finett.droidclaw.model.HeartbeatResponse;

/**
 * Unit tests for heartbeat detection logic.
 * Tests both Structured Outputs parsing and legacy {"HEARTBEAT_OK": bool} fallback.
 */
public class HeartbeatDetectionTest {

    // ==================== STRUCTURED OUTPUT TESTS ====================

    @Test
    public void parseStructuredResponse_healthyTrue() {
        String json = "{\"healthy\":true,\"summary\":\"All systems normal\",\"issues\":[]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertTrue("Should be healthy", response.isHealthy());
        assertEquals("All systems normal", response.getSummary());
        assertTrue("Should have no issues", response.getIssues().isEmpty());
    }

    @Test
    public void parseStructuredResponse_healthyFalse() {
        String json = "{\"healthy\":false,\"summary\":\"Issues found\",\"issues\":[{\"category\":\"workspace\",\"description\":\"3 incomplete tasks\",\"severity\":\"low\"}]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertFalse("Should not be healthy", response.isHealthy());
        assertEquals("Issues found", response.getSummary());
        assertEquals("Should have 1 issue", 1, response.getIssues().size());
        assertEquals("workspace", response.getIssues().get(0).getCategory());
        assertEquals("3 incomplete tasks", response.getIssues().get(0).getDescription());
        assertEquals("low", response.getIssues().get(0).getSeverity());
    }

    @Test
    public void parseStructuredResponse_multipleIssues() {
        String json = "{\"healthy\":false,\"summary\":\"Multiple issues\",\"issues\":[{\"category\":\"memory\",\"description\":\"Stale memories found\",\"severity\":\"medium\"},{\"category\":\"tasks\",\"description\":\"Failed task execution\",\"severity\":\"high\"}]}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertFalse("Should not be healthy", response.isHealthy());
        assertEquals("Should have 2 issues", 2, response.getIssues().size());
        assertEquals("medium", response.getIssues().get(0).getSeverity());
        assertEquals("high", response.getIssues().get(1).getSeverity());
    }

    @Test
    public void parseStructuredResponse_withExtraFields() {
        // Model might add extra fields - parser should still work
        String json = "{\"healthy\":true,\"summary\":\"All good\",\"issues\":[],\"extra_field\":\"ignored\"}";
        HeartbeatResponse response = HeartbeatResponse.fromJson(json);

        assertTrue("Should be healthy", response.isHealthy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStructuredResponse_missingIssues() {
        // Missing issues array - should throw (required field)
        String json = "{\"healthy\":true,\"summary\":\"All good\"}";
        HeartbeatResponse.fromJson(json);
    }

    @Test(expected = com.google.gson.JsonSyntaxException.class)
    public void parseStructuredResponse_invalidJson() {
        HeartbeatResponse.fromJson("not valid json");
    }

    @Test
    public void structuredResponse_schemaIsValid() {
        var schema = HeartbeatResponse.getJsonSchema();
        assertNotNull("Schema should not be null", schema);
        assertEquals("object", schema.get("type").getAsString());
        assertTrue("Should have properties", schema.has("properties"));
        assertTrue("Should have required", schema.has("required"));

        var properties = schema.getAsJsonObject("properties");
        assertTrue("Should have healthy property", properties.has("healthy"));
        assertTrue("Should have summary property", properties.has("summary"));
        assertTrue("Should have issues property", properties.has("issues"));

        var required = schema.getAsJsonArray("required");
        assertEquals("Should require 3 fields", 3, required.size());
    }

    @Test
    public void structuredResponse_issueToString() {
        HeartbeatResponse.Issue issue = new HeartbeatResponse.Issue(
                "workspace", "Incomplete tasks found", "medium");
        assertEquals("[MEDIUM] workspace: Incomplete tasks found", issue.toString());
    }

    // ==================== LEGACY FALLBACK TESTS ====================

    @Test
    public void detectLegacyTrue_atEndOfResponse() {
        String content = "System review complete. All healthy.\n{\"HEARTBEAT_OK\": true}";
        assertTrue("Should detect true at end", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacyTrue_withSpaces() {
        String content = "{ \"HEARTBEAT_OK\" : true }";
        assertTrue("Should detect true with spaces", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void detectLegacyFalse_issuesPresent() {
        String content = "Found issues: 3 tasks incomplete\n{\"HEARTBEAT_OK\": false}";
        assertFalse("Should return false when HEARTBEAT_OK is false", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void notDetected_nullContent() {
        assertFalse("Should not detect in null content", detectLegacyHeartbeatOk(null));
    }

    @Test
    public void notDetected_emptyContent() {
        assertFalse("Should not detect in empty content", detectLegacyHeartbeatOk(""));
    }

    @Test
    public void notDetected_refusal() {
        String content = "[REFUSAL] I'm sorry, I cannot assist with that request.";
        assertFalse("Should return false for refusal", detectLegacyHeartbeatOk(content));
    }

    @Test
    public void notDetected_noJson() {
        String content = "Found issues: 3 tasks incomplete, 2 errors in logs";
        assertFalse("Should not detect when no JSON present", detectLegacyHeartbeatOk(content));
    }

    // ==================== HELPER METHOD ====================

    /**
     * Replicates the legacy detection logic from HeartbeatWorker for testing.
     * This tests the fallback regex-based detection.
     */
    private java.util.regex.Pattern LEGACY_PATTERN = java.util.regex.Pattern.compile(
            "\\{\\s*\"HEARTBEAT_OK\"\\s*:\\s*(true|false)\\s*\\}",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private boolean detectLegacyHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        // Handle refusal
        if (content.startsWith("[REFUSAL]")) {
            return false;
        }

        // Try structured output first - only if it looks like structured JSON
        if (content.contains("\"healthy\"") && content.contains("\"summary\"")) {
            try {
                HeartbeatResponse response = HeartbeatResponse.fromJson(content);
                return response.isHealthy();
            } catch (Exception e) {
                // Fall through to legacy
            }
        }

        java.util.regex.Matcher matcher = LEGACY_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonStr = matcher.group(0);
            if (jsonStr.contains("\"HEARTBEAT_OK\"") && jsonStr.contains("true")) {
                return true;
            } else if (jsonStr.contains("\"HEARTBEAT_OK\"") && jsonStr.contains("false")) {
                return false;
            }
        }

        return false;
    }
}
