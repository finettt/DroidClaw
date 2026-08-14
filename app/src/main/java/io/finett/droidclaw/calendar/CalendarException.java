package io.finett.droidclaw.calendar;

/**
 * Raised by {@link CalendarDataSource} implementations for any calendar
 * operation failure. The message is user/LLM-readable and is surfaced
 * verbatim as the tool error.
 */
public class CalendarException extends RuntimeException {
    public CalendarException(String message) {
        super(message);
    }

    public CalendarException(String message, Throwable cause) {
        super(message, cause);
    }
}
