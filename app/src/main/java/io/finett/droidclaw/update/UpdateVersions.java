package io.finett.droidclaw.update;

import java.util.regex.Pattern;

/**
 * Pure-JVM comparison helpers for DroidClaw's CalVer release tags.
 *
 * <p>Release tags follow CalVer ({@code YYYY.MM.DD}), optionally with a 4th
 * segment {@code N} for same-day re-releases ({@code YYYY.MM.DD.N}). These
 * helpers cover exactly those shapes. Anything else — a git hash, {@code "dev"},
 * or a short dotted version such as {@code "1.0"} — is <em>not</em> CalVer, so
 * callers treat a non-CalVer <em>current</em> version as "unknown" rather than
 * "older than everything".
 */
public final class UpdateVersions {

    /** 3 or 4 dot-separated segments, each a run of decimal digits. */
    private static final Pattern CAL_VER = Pattern.compile("\\d+(?:\\.\\d+){2,3}");

    private UpdateVersions() {
    }

    /**
     * @param v the string to test
     * @return {@code true} only if {@code v} is exactly 3 or 4 dot-separated
     *         numeric segments, e.g. {@code "2026.10.10"} or {@code "2026.11.07.1"}
     */
    public static boolean isCalVer(String v) {
        return v != null && CAL_VER.matcher(v).matches();
    }

    /**
     * Compares two CalVer strings segment by segment, numerically. String
     * comparison is avoided because {@code "9"} sorts after {@code "10"}
     * lexically. A shorter tag is padded with trailing zero segments, so
     * {@code "2026.10.10"} compares as older than {@code "2026.10.10.1"}.
     *
     * @param a the first tag
     * @param b the second tag
     * @return a negative integer, zero, or a positive integer as {@code a} is
     *         older than, equal to, or newer than {@code b}
     */
    public static int compare(String a, String b) {
        String[] x = split(a);
        String[] y = split(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int px = i < x.length ? toInt(x[i]) : 0;
            int py = i < y.length ? toInt(y[i]) : 0;
            int c = Integer.compare(px, py);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static String[] split(String v) {
        return v == null ? new String[0] : v.split("\\.", -1);
    }

    private static int toInt(String seg) {
        try {
            return Integer.parseInt(seg);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
