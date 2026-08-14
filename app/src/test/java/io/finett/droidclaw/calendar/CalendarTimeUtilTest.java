package io.finett.droidclaw.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class CalendarTimeUtilTest {

    private static final ZoneId DEVICE = ZoneId.systemDefault();

    private static long expectedLocal(String iso) {
        return LocalDateTime.parse(iso).atZone(DEVICE).toInstant().toEpochMilli();
    }

    @Test
    public void parse_localDateTimeWithoutSeconds() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01T15:00");
        assertFalse(parsed.isDateOnly());
        assertEquals(expectedLocal("2026-06-01T15:00:00"), parsed.getEpochMillis());
    }

    @Test
    public void parse_localDateTimeWithSeconds() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01T15:30:45");
        assertEquals(expectedLocal("2026-06-01T15:30:45"), parsed.getEpochMillis());
    }

    @Test
    public void parse_spaceSeparatedDateTime() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01 15:30");
        assertFalse(parsed.isDateOnly());
        assertEquals(expectedLocal("2026-06-01T15:30:00"), parsed.getEpochMillis());
    }

    @Test
    public void parse_explicitPositiveOffset() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01T15:00:00+02:00");
        long expected = LocalDateTime.parse("2026-06-01T13:00:00")
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expected, parsed.getEpochMillis());
    }

    @Test
    public void parse_utcZulu() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01T15:00:00Z");
        long expected = LocalDateTime.parse("2026-06-01T15:00:00")
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expected, parsed.getEpochMillis());
    }

    @Test
    public void parse_bareDate_isDateOnlyAtLocalMidnight() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("2026-06-01");
        assertTrue(parsed.isDateOnly());
        long expected = LocalDate.of(2026, 6, 1).atStartOfDay(DEVICE)
                .toInstant().toEpochMilli();
        assertEquals(expected, parsed.getEpochMillis());
    }

    @Test
    public void parse_trimsWhitespace() {
        CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse("  2026-06-01T15:00  ");
        assertEquals(expectedLocal("2026-06-01T15:00:00"), parsed.getEpochMillis());
    }

    @Test
    public void parse_invalidString_throws() {
        String[] invalid = {null, "", "   ", "tomorrow", "2026/06/01", "15:00", "2026-13-45"};
        for (String s : invalid) {
            try {
                CalendarTimeUtil.parse(s);
                fail("Expected IllegalArgumentException for input: " + s);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void format_roundTripsLocalTime() {
        long millis = expectedLocal("2026-06-01T15:00:00");
        String formatted = CalendarTimeUtil.format(millis);
        assertTrue("Formatted value should start with the local date-time, got: " + formatted,
                formatted.startsWith("2026-06-01T15:00:00"));
    }

    @Test
    public void format_invalidTimezoneFallsBackToDevice() {
        long millis = expectedLocal("2026-06-01T15:00:00");
        String formatted = CalendarTimeUtil.format(millis, "Not/AZone");
        assertTrue(formatted.startsWith("2026-06-01T15:00:00"));
    }

    @Test
    public void toUtcMidnight_returnsUtcMidnightOfLocalDay() {
        // Noon local time on 2026-06-01
        long noon = LocalDateTime.parse("2026-06-01T12:00:00")
                .atZone(DEVICE).toInstant().toEpochMilli();
        long utcMidnight = CalendarTimeUtil.toUtcMidnight(noon);

        Instant instant = Instant.ofEpochMilli(utcMidnight);
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        assertEquals(0, utc.getHour());
        assertEquals(0, utc.getMinute());
        // The UTC calendar day must match the device-local calendar day
        LocalDate localDay = Instant.ofEpochMilli(noon).atZone(DEVICE).toLocalDate();
        assertEquals(localDay, utc.toLocalDate());
    }

    @Test
    public void plusOneDay_adds24Hours() {
        long base = expectedLocal("2026-06-01T15:00:00");
        assertEquals(base + 24 * 3600 * 1000L, CalendarTimeUtil.plusOneDay(base));
    }

    @Test
    public void startOfToday_isAtMostNow_andPlusDaysOrders() {
        long now = System.currentTimeMillis();
        long today = CalendarTimeUtil.startOfToday();
        assertTrue(today <= now);
        assertTrue(CalendarTimeUtil.startOfTodayPlusDays(1) > today);
        assertTrue(CalendarTimeUtil.startOfTodayPlusDays(7) > CalendarTimeUtil.startOfTodayPlusDays(1));
    }
}
