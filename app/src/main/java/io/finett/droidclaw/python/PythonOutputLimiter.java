package io.finett.droidclaw.python;

import java.nio.charset.StandardCharsets;

/**
 * Enforces {@link PythonConfig#getMaxOutputSize()} on captured Python output.
 *
 * <p>Containment for issue #150: {@code maxOutputSize} was declared and
 * validated but never enforced, so unbounded {@code StringIO} growth could
 * OOM the process. This helper truncates the output <b>on read</b> (after the
 * run finishes) with an explicit marker.
 *
 * <p><b>Documented limitation:</b> memory growth <i>during</i> a run is not
 * bounded by this quick fix -- a run that writes gigabytes to stdout before
 * returning can still exhaust memory. Bounding growth mid-run requires a
 * capped stream object on the Python side and is left to the architectural
 * fix tracked in #150.
 */
final class PythonOutputLimiter {

    private PythonOutputLimiter() { }

    /**
     * Truncates {@code output} to at most {@code maxBytes} UTF-8 bytes,
     * appending a marker when truncation occurred. Never splits a multi-byte
     * character. Returns the input unchanged when it fits.
     */
    static String truncate(String output, int maxBytes) {
        if (output == null || maxBytes <= 0) {
            return output;
        }
        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return output;
        }
        int end = maxBytes;
        // Back up past any UTF-8 continuation bytes (10xxxxxx) so we do not
        // split a multi-byte character.
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        String truncated = new String(bytes, 0, end, StandardCharsets.UTF_8);
        return truncated + "\n[output truncated at " + maxBytes + " bytes]";
    }
}
