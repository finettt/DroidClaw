package io.finett.droidclaw.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Table-driven tests for {@link UpdateVersions}. */
public class UpdateVersionsTest {

    @Test
    public void isCalVer_acceptsThreeSegments() {
        assertTrue(UpdateVersions.isCalVer("2026.10.10"));
        assertTrue(UpdateVersions.isCalVer("2026.08.11"));
    }

    @Test
    public void isCalVer_acceptsFourSegments() {
        assertTrue(UpdateVersions.isCalVer("2026.11.07.1"));
        assertTrue(UpdateVersions.isCalVer("2026.10.10.3"));
    }

    @Test
    public void isCalVer_rejectsTwoSegments() {
        assertFalse(UpdateVersions.isCalVer("1.0"));
        assertFalse(UpdateVersions.isCalVer("2026.10"));
    }

    @Test
    public void isCalVer_rejectsFiveOrMoreSegments() {
        assertFalse(UpdateVersions.isCalVer("2026.10.10.1.1"));
    }

    @Test
    public void isCalVer_rejectsHashDevAndEmpty() {
        assertFalse(UpdateVersions.isCalVer("a1b2c3d"));
        assertFalse(UpdateVersions.isCalVer("dev"));
        assertFalse(UpdateVersions.isCalVer(""));
        assertFalse(UpdateVersions.isCalVer(null));
    }

    @Test
    public void isCalVer_rejectsNonNumericSegment() {
        assertFalse(UpdateVersions.isCalVer("2026.10.x"));
        assertFalse(UpdateVersions.isCalVer("2026..10"));
        assertFalse(UpdateVersions.isCalVer("2026.10.10."));
    }

    @Test
    public void compare_equal_returnsZero() {
        assertEquals(0, UpdateVersions.compare("2026.10.10", "2026.10.10"));
    }

    @Test
    public void compare_olderAndNewer() {
        assertTrue(UpdateVersions.compare("2026.10.9", "2026.10.10") < 0);
        assertTrue(UpdateVersions.compare("2026.10.11", "2026.10.10") > 0);
        assertTrue(UpdateVersions.compare("2025.12.31", "2026.10.10") < 0);
        assertTrue(UpdateVersions.compare("2027.01.01", "2026.10.10") > 0);
    }

    @Test
    public void compare_threeVsFourSegments_shorterIsOlder() {
        // 2026.10.10 (3 segments) must be older than 2026.10.10.1 (4 segments).
        assertTrue(UpdateVersions.compare("2026.10.10", "2026.10.10.1") < 0);
        assertTrue(UpdateVersions.compare("2026.10.10.1", "2026.10.10") > 0);
    }

    @Test
    public void compare_numericNotLexical() {
        // String compare would say "10" < "9"; numerically it is the opposite.
        assertTrue(UpdateVersions.compare("2026.10.9", "2026.10.10") < 0);
        assertTrue(UpdateVersions.compare("2026.10.10", "2026.10.9") > 0);
    }

    @Test
    public void compare_leadingZerosAreNumeric() {
        // "08" parses to 8, so 2026.08.11 equals 2026.8.11 numerically.
        assertEquals(0, UpdateVersions.compare("2026.08.11", "2026.8.11"));
        assertTrue(UpdateVersions.compare("2026.08.11", "2026.09.01") < 0);
    }
}
