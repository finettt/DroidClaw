package io.finett.droidclaw.python;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Tests for maxOutputSize enforcement (#150).
 */
public class PythonOutputLimiterTest {

    @Test
    public void testOutputWithinLimit_isUnchanged() {
        assertEquals("hello", PythonOutputLimiter.truncate("hello", 1024));
    }

    @Test
    public void testOutputExactlyAtLimit_isUnchanged() {
        assertEquals("abcd", PythonOutputLimiter.truncate("abcd", 4));
    }

    @Test
    public void testOversizedOutput_isTruncatedWithMarker() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('x');
        String result = PythonOutputLimiter.truncate(sb.toString(), 10);
        assertTrue("Should contain truncation marker",
                result.contains("[output truncated at 10 bytes]"));
        assertTrue("Content before marker should be capped at 10 bytes",
                result.startsWith("xxxxxxxxxx\n[output truncated"));
    }

    @Test
    public void testTruncation_doesNotSplitMultiByteCharacter() {
        // Each snowman is 3 UTF-8 bytes; a 7-byte cap lands mid-character.
        String input = "\u2603\u2603\u2603\u2603"; // 12 bytes
        String result = PythonOutputLimiter.truncate(input, 7);
        String content = result.substring(0, result.indexOf("\n[output truncated"));
        // Must decode cleanly and contain only whole characters.
        assertEquals("\u2603\u2603", content);
        assertTrue(content.getBytes(StandardCharsets.UTF_8).length <= 7);
    }

    @Test
    public void testNullOutput_isReturnedAsIs() {
        assertNull(PythonOutputLimiter.truncate(null, 10));
    }

    @Test
    public void testTimeoutMessage_statesAbandonmentNotCancellation() {
        String msg = PythonExecutor.timeoutMessage(30);
        assertTrue(msg.contains("timed out after 30 seconds"));
        assertTrue("Must say the code was abandoned", msg.contains("abandoned"));
        assertTrue("Must say it may still be running",
                msg.contains("may still be running"));
        assertTrue("Must say it cannot be stopped", msg.contains("cannot be stopped"));
        assertFalse("Must not claim cancellation", msg.toLowerCase().contains("cancel"));
    }
}
