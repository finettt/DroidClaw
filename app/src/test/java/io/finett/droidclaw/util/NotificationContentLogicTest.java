package io.finett.droidclaw.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.finett.droidclaw.model.TaskResult;

/**
 * Unit tests for notification content generation logic.
 * Tests the pure Java logic without requiring Android resources.
 */
public class NotificationContentLogicTest {

    @Test
    public void generateNotificationContent_usesAgentTitleAndSummary_whenAvailable() {
        TaskResult result = createTaskResult(TaskResult.TYPE_HEARTBEAT, true, "Full content here");
        result.putMetadata("notification_title", "Agent Title");
        result.putMetadata("notification_summary", "Agent Summary");

        String title = extractTitle(result);
        String summary = extractSummary(result);

        assertEquals("Agent Title", title);
        assertEquals("Agent Summary", summary);
    }

    @Test
    public void generateNotificationContent_generatesDefaultTitle_whenNoAgentTitle() {
        TaskResult result = createTaskResult(TaskResult.TYPE_HEARTBEAT, false, "Issues found");

        String title = extractTitle(result);

        assertEquals("Heartbeat Alert - Attention Needed", title);
    }

    @Test
    public void generateNotificationContent_usesFullContent_whenNoTruncationNeeded() {
        TaskResult result = createTaskResult(TaskResult.TYPE_MANUAL, true, "Short content");

        String content = result.getContent();
        assertTrue(content.length() <= 1000);
        assertEquals("Short content", content);
    }

    @Test
    public void generateNotificationContent_truncatesLongContent() {
        String longContent = createLongString(2000);
        TaskResult result = createTaskResult(TaskResult.TYPE_MANUAL, true, longContent);

        String truncated = truncateContent(result.getContent(), 1000);

        assertEquals(1003, truncated.length()); // 1000 + "..."
        assertTrue(truncated.endsWith("..."));
    }

    @Test
    public void generateDefaultTitle_heartbeatSuccess() {
        TaskResult result = createTaskResult(TaskResult.TYPE_HEARTBEAT, true, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Heartbeat Check - OK", title);
    }

    @Test
    public void generateDefaultTitle_heartbeatFailure() {
        TaskResult result = createTaskResult(TaskResult.TYPE_HEARTBEAT, false, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Heartbeat Alert - Attention Needed", title);
    }

    @Test
    public void generateDefaultTitle_cronSuccess() {
        TaskResult result = createTaskResult(TaskResult.TYPE_CRON_JOB, true, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Cron Job Completed", title);
    }

    @Test
    public void generateDefaultTitle_cronFailure() {
        TaskResult result = createTaskResult(TaskResult.TYPE_CRON_JOB, false, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Cron Job Failed", title);
    }

    @Test
    public void generateDefaultTitle_manualSuccess() {
        TaskResult result = createTaskResult(TaskResult.TYPE_MANUAL, true, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Task Completed", title);
    }

    @Test
    public void generateDefaultTitle_manualFailure() {
        TaskResult result = createTaskResult(TaskResult.TYPE_MANUAL, false, "content");

        String title = generateDefaultTitle(result);

        assertEquals("Task Failed", title);
    }

    @Test
    public void generateDefaultSummary_extractsFirstSentence() {
        String content = "First sentence. Second sentence.";

        String summary = generateDefaultSummary(content);

        assertEquals("First sentence.", summary);
    }

    @Test
    public void generateDefaultSummary_truncatesWhenNoSentence() {
        String longContent = "This is a long text without any sentence endings " + createLongString(500);

        String summary = generateDefaultSummary(longContent);

        assertEquals(203, summary.length()); // 200 + "..."
        assertTrue(summary.endsWith("..."));
    }

    @Test
    public void generateDefaultSummary_returnsMessage_whenNoContent() {
        String summary = generateDefaultSummary("");

        assertEquals("Task completed with no output", summary);
    }

    @Test
    public void generateDefaultSummary_handlesNullContent() {
        String summary = generateDefaultSummary(null);

        assertEquals("Task completed with no output", summary);
    }

    @Test
    public void extractTitle_returnsNull_whenNullResult() {
        String title = extractTitle(null);

        assertNull(title);
    }

    @Test
    public void extractTitle_prioritizesAgentTitle_overDefault() {
        TaskResult result = createTaskResult(TaskResult.TYPE_HEARTBEAT, false, "Issues found");
        result.putMetadata("notification_title", "Custom Title");

        String title = extractTitle(result);

        assertEquals("Custom Title", title);
    }

    @Test
    public void extractSummary_prioritizesAgentSummary_overDefault() {
        TaskResult result = createTaskResult(TaskResult.TYPE_CRON_JOB, true, "Long content");
        result.putMetadata("notification_summary", "Custom Summary");

        String summary = extractSummary(result);

        assertEquals("Custom Summary", summary);
    }

    /**
     * Replicates the title extraction logic from NotificationManager.
     */
    private String extractTitle(TaskResult result) {
        if (result == null) {
            return null;
        }

        String agentTitle = result.getMetadataValue("notification_title");
        if (agentTitle != null && !agentTitle.isEmpty()) {
            return agentTitle;
        }

        return generateDefaultTitle(result);
    }

    /**
     * Replicates the summary extraction logic from NotificationManager.
     */
    private String extractSummary(TaskResult result) {
        if (result == null) {
            return null;
        }

        String agentSummary = result.getMetadataValue("notification_summary");
        if (agentSummary != null && !agentSummary.isEmpty()) {
            return agentSummary;
        }

        return generateDefaultSummary(result.getContent());
    }

    /**
     * Replicates the default title generation logic from NotificationManager.
     */
    private String generateDefaultTitle(TaskResult result) {
        boolean isSuccess = result.isSuccess();

        switch (result.getType()) {
            case TaskResult.TYPE_HEARTBEAT:
                return isSuccess ? "Heartbeat Check - OK" : "Heartbeat Alert - Attention Needed";
            case TaskResult.TYPE_CRON_JOB:
                return isSuccess ? "Cron Job Completed" : "Cron Job Failed";
            case TaskResult.TYPE_MANUAL:
                return isSuccess ? "Task Completed" : "Task Failed";
            default:
                return isSuccess ? "Task Completed" : "Task Failed";
        }
    }

    /**
     * Replicates the default summary generation logic from NotificationManager.
     */
    private String generateDefaultSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "Task completed with no output";
        }

        String summary = content;
        int sentenceEnd = summary.indexOf('.');
        if (sentenceEnd > 0 && sentenceEnd < 200) {
            summary = summary.substring(0, sentenceEnd + 1);
        } else if (summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }

        return summary;
    }

    /**
     * Replicates the content truncation logic from NotificationManager.
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private TaskResult createTaskResult(int type, boolean success, String content) {
        TaskResult result = new TaskResult("test-id", type, 12345L, content);
        result.putMetadata("status", success ? "success" : "failed");
        return result;
    }

    private String createLongString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }
}
