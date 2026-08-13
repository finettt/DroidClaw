package io.finett.droidclaw.calendar;

import java.util.List;

/**
 * Abstraction over the device calendar store, implemented by
 * {@link CalendarRepository} (Android CalendarContract). Tools depend on this
 * interface so they can be unit-tested with fakes.
 */
public interface CalendarDataSource {

    /** All calendars known to the device, sorted by display name. */
    List<CalendarInfo> getCalendars();

    /**
     * Look up a single event by its id, or {@code null} when not found.
     * For recurring events this returns the series definition row.
     */
    CalendarEvent getEvent(long eventId);

    /**
     * Event occurrences within {@code [beginMillis, endMillis)} (recurring
     * events are expanded). Optionally filtered by calendar and by a text
     * match on title/location/description.
     *
     * @param calendarId nullable; restrict to one calendar
     * @param textQuery  nullable; case-insensitive substring match
     * @param limit      maximum number of occurrences to return
     */
    List<CalendarEvent> queryEvents(long beginMillis, long endMillis,
                                    Long calendarId, String textQuery, int limit);

    /**
     * Insert a new event. Returns the new event id.
     *
     * @throws CalendarException on validation or provider failure
     */
    long createEvent(EventSpec spec);

    /**
     * Update fields of an existing event (non-null spec fields only).
     *
     * @return true if the event was found and updated
     * @throws CalendarException on validation or provider failure
     */
    boolean updateEvent(long eventId, EventSpec spec);

    /**
     * Delete an event (whole series for recurring events).
     *
     * @return true if the event was found and deleted
     */
    boolean deleteEvent(long eventId);

    /** Whether the device has at least one writable calendar. */
    boolean hasWritableCalendar();
}
