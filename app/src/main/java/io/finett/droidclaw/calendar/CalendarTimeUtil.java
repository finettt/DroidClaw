package io.finett.droidclaw.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Parses and formats the ISO-8601-ish timestamps the LLM uses for calendar
 * event boundaries.
 *
 * <p>Accepted input forms:
 * <ul>
 *   <li>{@code 2026-06-01T15:00} or {@code 2026-06-01 15:00} — local time in the
 *       device timezone (seconds optional)</li>
 *   <li>{@code 2026-06-01T15:00:00+02:00} / {@code ...Z} — explicit UTC offset</li>
 *   <li>{@code 2026-06-01} — date only; interpreted as the start of that day.
 *       Callers use {@link ParsedTime#isDateOnly()} to map this to all-day events.</li>
 * </ul>
 */
public final class CalendarTimeUtil {

    private static final DateTimeFormatter LOCAL_TIME_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter OUTPUT_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private CalendarTimeUtil() {
    }

    /** Result of parsing an LLM-supplied timestamp. */
    public static class ParsedTime {
        private final long epochMillis;
        private final boolean dateOnly;

        ParsedTime(long epochMillis, boolean dateOnly) {
            this.epochMillis = epochMillis;
            this.dateOnly = dateOnly;
        }

        /** Epoch milliseconds (UTC). */
        public long getEpochMillis() {
            return epochMillis;
        }

        /** True when the input was a bare date ({@code yyyy-MM-dd}). */
        public boolean isDateOnly() {
            return dateOnly;
        }
    }

    /**
     * Parse a timestamp string into epoch millis.
     *
     * @throws IllegalArgumentException if the string is null, blank, or not a
     *         recognised date/time format.
     */
    public static ParsedTime parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Date/time must not be empty");
        }
        String s = input.trim();

        // Explicit UTC offset (e.g. "2026-06-01T15:00:00+02:00" or "...Z")
        try {
            OffsetDateTime odt = OffsetDateTime.parse(s);
            return new ParsedTime(odt.toInstant().toEpochMilli(), false);
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        // Local date-time with 'T' separator, seconds optional
        try {
            LocalDateTime ldt = LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return new ParsedTime(toDeviceMillis(ldt), false);
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        // Local date-time with space separator ("2026-06-01 15:00")
        try {
            LocalDateTime ldt = LocalDateTime.parse(s, LOCAL_TIME_FORMAT);
            return new ParsedTime(toDeviceMillis(ldt), false);
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        // Bare date -> date only (all-day semantics)
        try {
            LocalDate date = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return new ParsedTime(toDeviceMillis(date.atStartOfDay()), true);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Unrecognised date/time: '" + input + "'. Expected ISO-8601 such as "
                    + "'2026-06-01T15:00', '2026-06-01T15:00:00+02:00' or '2026-06-01'.");
        }
    }

    private static long toDeviceMillis(LocalDateTime ldt) {
        return ldt.atZone(deviceZone()).toInstant().toEpochMilli();
    }

    /** Format epoch millis as ISO-8601 with the device timezone offset. */
    public static String format(long epochMillis) {
        return format(epochMillis, deviceZone());
    }

    /** Format epoch millis as ISO-8601 in the given timezone id. */
    public static String format(long epochMillis, String timeZoneId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZoneId);
        } catch (Exception e) {
            zone = deviceZone();
        }
        return format(epochMillis, zone);
    }

    private static String format(long epochMillis, ZoneId zone) {
        ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
        return zdt.format(OUTPUT_FORMAT);
    }

    /** Start of the next day (device timezone) after the given epoch millis. */
    public static long plusOneDay(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(deviceZone())
                .plusDays(1)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * UTC midnight of the (device-local) calendar day containing the given
     * instant. All-day events in {@code CalendarContract} store UTC midnight
     * boundaries, so use this when building ALL_DAY rows.
     */
    public static long toUtcMidnight(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(deviceZone()).toLocalDate();
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** Epoch millis of the start of today in the device timezone. */
    public static long startOfToday() {
        return LocalDate.now(deviceZone()).atStartOfDay(deviceZone()).toInstant().toEpochMilli();
    }

    /** Epoch millis {@code days} days after the start of today (device timezone). */
    public static long startOfTodayPlusDays(int days) {
        return LocalDate.now(deviceZone()).plusDays(days)
                .atStartOfDay(deviceZone()).toInstant().toEpochMilli();
    }

    private static ZoneId deviceZone() {
        return ZoneId.systemDefault();
    }
}
