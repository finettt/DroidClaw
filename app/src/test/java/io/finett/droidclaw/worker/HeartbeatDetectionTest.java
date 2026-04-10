package io.finett.droidclaw.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for HEARTBEAT_OK detection logic.
 * Tests the enhanced detection that checks start, end, and anywhere in content.
 */
public class HeartbeatDetectionTest {

    private static final String HEARTBEAT_OK_MARKER = "HEARTBEAT_OK";

    // ==================== START OF RESPONSE TESTS ====================

    @Test
    public void detectAtStart_onlyMarker() {
        String content = "HEARTBEAT_OK";
        assertTrue("Should detect marker at start", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtStart_withTrailingNewline() {
        String content = "HEARTBEAT_OK\n";
        assertTrue("Should detect marker with trailing newline", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtStart_withAdditionalText() {
        String content = "HEARTBEAT_OK\n\nAll systems are normal.";
        assertTrue("Should detect marker at start with additional text", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtStart_withLeadingWhitespace() {
        String content = "  \n  HEARTBEAT_OK";
        assertTrue("Should detect marker even with leading whitespace", detectHeartbeatOk(content));
    }

    // ==================== END OF RESPONSE TESTS ====================

    @Test
    public void detectAtEnd_onlyMarker() {
        String content = "HEARTBEAT_OK";
        assertTrue("Should detect marker at end", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtEnd_withLeadingText() {
        String content = "System review complete. All healthy.\nHEARTBEAT_OK";
        assertTrue("Should detect marker at end with leading text", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtEnd_withTrailingWhitespace() {
        String content = "HEARTBEAT_OK\n\n  ";
        assertTrue("Should detect marker with trailing whitespace", detectHeartbeatOk(content));
    }

    @Test
    public void detectAtEnd_withNewline() {
        String content = "Check completed\nHEARTBEAT_OK\n";
        assertTrue("Should detect marker at end with newline", detectHeartbeatOk(content));
    }

    // ==================== MIDDLE OF RESPONSE TESTS ====================

    @Test
    public void detectInMiddle_surroundedByText() {
        String content = "System status: HEARTBEAT_OK - all normal";
        assertTrue("Should detect marker in middle", detectHeartbeatOk(content));
    }

    @Test
    public void detectInMarkdownBlock() {
        String content = "## Status\n\n```\nHEARTBEAT_OK\n```\n\nEverything normal.";
        assertTrue("Should detect marker in markdown block", detectHeartbeatOk(content));
    }

    @Test
    public void detectInChecklistResponse() {
        String content = "- [x] Review memories\n- [x] Check workspace\n- [x] Verify backups\n\nHEARTBEAT_OK";
        assertTrue("Should detect marker in checklist response", detectHeartbeatOk(content));
    }

    // ==================== NEGATIVE TESTS ====================

    @Test
    public void notDetected_nullContent() {
        assertFalse("Should not detect in null content", detectHeartbeatOk(null));
    }

    @Test
    public void notDetected_emptyContent() {
        assertFalse("Should not detect in empty content", detectHeartbeatOk(""));
    }

    @Test
    public void notDetected_whitespaceOnly() {
        assertFalse("Should not detect in whitespace-only content", detectHeartbeatOk("   \n\n  "));
    }

    @Test
    public void notDetected_issuesPresent() {
        String content = "Found issues: 3 tasks incomplete, 2 errors in logs";
        assertFalse("Should not detect when issues present", detectHeartbeatOk(content));
    }

    @Test
    public void notDetected_partialMarker() {
        String content = "HEARTBEAT";
        assertFalse("Should not detect partial marker", detectHeartbeatOk(content));
    }

    @Test
    public void notDetected_lowercaseMarker() {
        String content = "heartbeat_ok";
        // The detection is case-sensitive, so lowercase should NOT match
        // But our implementation uses contains() which is case-sensitive
        assertFalse("Should not detect lowercase marker", detectHeartbeatOk(content));
    }

    @Test
    public void notDetected_similarButDifferent() {
        String content = "HEARTBEAT_NOT_OK";
        // This contains HEARTBEAT_OK as substring? Let's check
        // "HEARTBEAT_NOT_OK" does NOT contain "HEARTBEAT_OK" as substring
        assertFalse("Should not detect HEARTBEAT_OK in HEARTBEAT_NOT_OK", detectHeartbeatOk(content));
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void detectWithSpecialCharacters() {
        String content = "Status: HEARTBEAT_OK! ✅";
        assertTrue("Should detect with special characters", detectHeartbeatOk(content));
    }

    @Test
    public void detectInLongResponse() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Line ").append(i).append(": System check normal\n");
        }
        sb.append("\nHEARTBEAT_OK");

        assertTrue("Should detect in long response", detectHeartbeatOk(sb.toString()));
    }

    @Test
    public void detectMultipleMarkers_firstWins() {
        String content = "HEARTBEAT_OK\n\nAdditional info\nHEARTBEAT_OK";
        assertTrue("Should detect with multiple markers", detectHeartbeatOk(content));
    }

    // ==================== HELPER METHOD ====================

    /**
     * Replicates the detection logic from HeartbeatWorker for testing.
     * This allows us to test the logic without instantiating the worker.
     */
    private boolean detectHeartbeatOk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String trimmed = content.trim();

        // Check if content starts with HEARTBEAT_OK
        if (trimmed.startsWith(HEARTBEAT_OK_MARKER)) {
            return true;
        }

        // Check if content ends with HEARTBEAT_OK
        if (trimmed.endsWith(HEARTBEAT_OK_MARKER)) {
            return true;
        }

        // Check anywhere in content
        return content.contains(HEARTBEAT_OK_MARKER);
    }
}
