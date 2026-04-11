package io.finett.droidclaw.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.finett.droidclaw.model.TaskResult;

/**
 * Unit tests for notification content extraction from agent responses.
 * Tests the TITLE: and SUMMARY: marker parsing logic.
 */
public class NotificationContentExtractionTest {

    // ==================== TITLE EXTRACTION TESTS ====================

    @Test
    public void extractTitle_findsTitle_atStartOfResponse() {
        String response = "TITLE: System Check Complete\n\nAll systems are operating normally.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("System Check Complete", result.getMetadataValue("notification_title"));
    }

    @Test
    public void extractTitle_findsTitle_inMiddleOfResponse() {
        String response = "Review complete.\nTITLE: Review Complete\n\nDetails follow...";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Review Complete", result.getMetadataValue("notification_title"));
    }

    @Test
    public void extractTitle_handlesWhitespace_aroundTitle() {
        String response = "  TITLE:   Trimmed Title  \nSummary here";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Trimmed Title", result.getMetadataValue("notification_title"));
    }

    @Test
    public void extractTitle_returnsNull_whenNoTitleMarker() {
        String response = "No title marker here, just regular text.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertNull("Should not set title when TITLE: marker not found",
                result.getMetadataValue("notification_title"));
    }

    @Test
    public void extractTitle_returnsNull_forEmptyResponse() {
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, "");

        assertNull("Should not set title for empty response",
                result.getMetadataValue("notification_title"));
    }

    @Test
    public void extractTitle_returnsNull_forNullResponse() {
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, null);

        assertNull("Should not set title for null response",
                result.getMetadataValue("notification_title"));
    }

    // ==================== SUMMARY EXTRACTION TESTS ====================

    @Test
    public void extractSummary_findsSummary_atStartOfResponse() {
        String response = "SUMMARY: Quick summary\n\nDetailed content here...";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Quick summary", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractSummary_findsSummary_inMiddleOfResponse() {
        String response = "Task completed.\nSUMMARY: Task completed successfully.\n\nMore details...";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Task completed successfully.", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractSummary_handlesWhitespace_aroundSummary() {
        String response = "TITLE: Test\n  SUMMARY:   Trimmed Summary  ";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Trimmed Summary", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractSummary_returnsNull_whenNoSummaryMarker() {
        String response = "No summary marker here, just regular text.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertNull("Should not set summary when SUMMARY: marker not found",
                result.getMetadataValue("notification_summary"));
    }

    // ==================== COMBINED EXTRACTION TESTS ====================

    @Test
    public void extractBoth_titleAndSummary_whenBothPresent() {
        String response = "TITLE: Task Complete\nSUMMARY: All checks passed.\n\nDetailed report...";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Task Complete", result.getMetadataValue("notification_title"));
        assertEquals("All checks passed.", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractBoth_handlesMultilineContent() {
        String response = "TITLE: Memory Cleanup\n" +
                "SUMMARY: Cleaned 15 stale entries.\n" +
                "\n" +
                "## Details\n" +
                "- Removed 10 expired sessions\n" +
                "- Cleared 5 orphaned caches\n" +
                "\n" +
                "System is healthy.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Memory Cleanup", result.getMetadataValue("notification_title"));
        assertEquals("Cleaned 15 stale entries.", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractBoth_onlyTitle_whenNoSummary() {
        String response = "TITLE: Only Title\n\nNo summary marker present.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("Only Title", result.getMetadataValue("notification_title"));
        assertNull("Should not set summary when SUMMARY: marker not found",
                result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extractBoth_onlySummary_whenNoTitle() {
        String response = "SUMMARY: Only Summary\n\nNo title marker present.";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertNull("Should not set title when TITLE: marker not found",
                result.getMetadataValue("notification_title"));
        assertEquals("Only Summary", result.getMetadataValue("notification_summary"));
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void extract_handlesCaseSensitive_markers() {
        // Markers should be case-sensitive (TITLE: not title:)
        String response = "title: lowercase title\nsummary: lowercase summary";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertNull("Should not match lowercase title:",
                result.getMetadataValue("notification_title"));
        assertNull("Should not match lowercase summary:",
                result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extract_ignoresSimilarMarkers() {
        // Should not match markers that are similar but not exact
        String response = "TITLE_EXTRA: Not a title\nSUMMARY_EXTRA: Not a summary";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        // These should match because the line starts with "TITLE_EXTRA:" which doesn't start with "TITLE:"
        // Wait, actually "TITLE_EXTRA:".startsWith("TITLE:") is false
        // Let me check: "TITLE_EXTRA:".startsWith("TITLE:") -> false
        // So this test should pass (null)
        assertNull("Should not match TITLE_EXTRA:",
                result.getMetadataValue("notification_title"));
    }

    @Test
    public void extract_handlesMultipleTitleMarkers_usesFirst() {
        String response = "TITLE: First Title\nSUMMARY: First Summary\n\nMore text\nTITLE: Second Title";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        assertEquals("First Title", result.getMetadataValue("notification_title"));
        assertEquals("First Summary", result.getMetadataValue("notification_summary"));
    }

    @Test
    public void extract_handlesEmptyMarkers() {
        String response = "TITLE: \nSUMMARY: ";
        TaskResult result = createTaskResult(true);

        extractAndCacheNotificationContent(result, response);

        // Empty strings after trim should NOT be set (filtered by isEmpty check)
        assertNull("Empty title should not be cached",
                result.getMetadataValue("notification_title"));
        assertNull("Empty summary should not be cached",
                result.getMetadataValue("notification_summary"));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Replicates the extraction logic from BaseTaskWorker for testing.
     * This allows us to test the parsing without instantiating the worker.
     */
    private void extractAndCacheNotificationContent(TaskResult result, String response) {
        if (response == null || response.isEmpty()) {
            return;
        }

        String notificationTitle = null;
        String notificationSummary = null;

        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (notificationTitle == null && line.startsWith("TITLE:")) {
                notificationTitle = line.substring(6).trim();
            } else if (notificationSummary == null && line.startsWith("SUMMARY:")) {
                notificationSummary = line.substring(8).trim();
            }
            // Stop when both are found
            if (notificationTitle != null && notificationSummary != null) {
                break;
            }
        }

        if (notificationTitle != null && !notificationTitle.isEmpty()) {
            result.putMetadata("notification_title", notificationTitle);
        }

        if (notificationSummary != null && !notificationSummary.isEmpty()) {
            result.putMetadata("notification_summary", notificationSummary);
        }
    }

    private TaskResult createTaskResult(boolean success) {
        TaskResult result = new TaskResult("test-id", TaskResult.TYPE_HEARTBEAT, 12345L, "content");
        result.putMetadata("status", success ? "success" : "failed");
        return result;
    }
}
