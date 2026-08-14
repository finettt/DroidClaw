package io.finett.droidclaw.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * {@link CalendarDataSource} backed by the Android CalendarProvider
 * ({@link CalendarContract}). Covers every calendar the device syncs
 * (Google, DAVx5/CalDAV, local, ...).
 *
 * <p>Requires {@link Manifest.permission#READ_CALENDAR} and
 * {@link Manifest.permission#WRITE_CALENDAR}. Every operation checks the
 * permissions and throws {@link CalendarException} with an instructive
 * message when they are missing.
 */
public class CalendarRepository implements CalendarDataSource {

    private static final String PERMISSION_ERROR =
            "Calendar permission not granted. Ask the user to enable "
            + "'Calendar access' in Settings -> Agent settings.";

    private static final String[] CALENDAR_PROJECTION = {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_TIME_ZONE,
    };

    private static final String[] INSTANCE_PROJECTION = {
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.RRULE,
    };

    private static final String[] EVENT_PROJECTION = {
            CalendarContract.Events._ID,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
    };

    private static final String[] EVENT_DETAIL_PROJECTION = {
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
    };

    private final Context context;

    public CalendarRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // ==================== Queries ====================

    @Override
    public List<CalendarInfo> getCalendars() {
        requireReadPermission();
        List<CalendarInfo> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION, null, null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
            if (cursor == null) {
                throw new CalendarException("Calendar provider is not available on this device.");
            }
            while (cursor.moveToNext()) {
                result.add(new CalendarInfo(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getInt(4),
                        cursor.getInt(5) != 0,
                        cursor.getString(6)));
            }
        }
        return result;
    }

    @Override
    public List<CalendarEvent> queryEvents(long beginMillis, long endMillis,
                                           Long calendarId, String textQuery, int limit) {
        requireReadPermission();
        if (endMillis <= beginMillis) {
            throw new CalendarException("End time must be after start time.");
        }

        Map<Long, String> calendarNames = new HashMap<>();
        for (CalendarInfo info : getCalendars()) {
            calendarNames.put(info.getId(), info.getDisplayName());
        }

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, beginMillis);
        ContentUris.appendId(builder, endMillis);

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        if (calendarId != null) {
            selection.append(CalendarContract.Instances.CALENDAR_ID).append(" = ?");
            selectionArgs.add(String.valueOf(calendarId));
        }
        if (textQuery != null && !textQuery.trim().isEmpty()) {
            String like = "%" + textQuery.trim().toLowerCase(Locale.ROOT) + "%";
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append("(")
                    .append("LOWER(").append(CalendarContract.Instances.TITLE).append(") LIKE ? OR ")
                    .append("LOWER(").append(CalendarContract.Instances.EVENT_LOCATION).append(") LIKE ? OR ")
                    .append("LOWER(").append(CalendarContract.Instances.DESCRIPTION).append(") LIKE ?")
                    .append(")");
            selectionArgs.add(like);
            selectionArgs.add(like);
            selectionArgs.add(like);
        }

        List<CalendarEvent> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                builder.build(), INSTANCE_PROJECTION,
                selection.length() > 0 ? selection.toString() : null,
                selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) {
                throw new CalendarException("Calendar provider is not available on this device.");
            }
            while (cursor.moveToNext() && result.size() < limit) {
                long eventId = cursor.getLong(0);
                long calId = cursor.getLong(1);
                result.add(new CalendarEvent(
                        eventId,
                        calId,
                        calendarNames.containsKey(calId) ? calendarNames.get(calId) : "",
                        cursor.getString(4),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getInt(7) != 0,
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getString(8),
                        cursor.getString(9) != null && !cursor.getString(9).isEmpty()));
            }
        }
        return result;
    }

    @Override
    public CalendarEvent getEvent(long eventId) {
        requireReadPermission();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                EVENT_DETAIL_PROJECTION, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            long calId = cursor.getLong(1);
            long dtStart = cursor.getLong(3);
            long dtEnd = cursor.isNull(4) ? dtStart : cursor.getLong(4);
            String rrule = cursor.getString(9);

            String calendarName = "";
            try {
                for (CalendarInfo info : getCalendars()) {
                    if (info.getId() == calId) {
                        calendarName = info.getDisplayName();
                        break;
                    }
                }
            } catch (CalendarException e) {
                // Name is cosmetic — continue without it
            }

            return new CalendarEvent(
                    cursor.getLong(0),
                    calId,
                    calendarName,
                    cursor.getString(2),
                    dtStart,
                    dtEnd,
                    cursor.getInt(5) != 0,
                    cursor.getString(6),
                    cursor.getString(7),
                    cursor.getString(8),
                    rrule != null && !rrule.isEmpty());
        }
    }

    // ==================== Mutations ====================

    @Override
    public long createEvent(EventSpec spec) {
        requireWritePermission();
        if (spec == null) {
            throw new CalendarException("Event spec must not be null.");
        }
        if (spec.getTitle() == null || spec.getTitle().trim().isEmpty()) {
            throw new CalendarException("Event title must not be empty.");
        }
        if (spec.getDtStart() == null) {
            throw new CalendarException("Event start time is required.");
        }

        long calendarId = resolveCalendarId(spec.getCalendarId());

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, spec.getTitle().trim());
        values.put(CalendarContract.Events.DTSTART, spec.getDtStart());
        values.put(CalendarContract.Events.EVENT_TIMEZONE,
                resolveTimezone(spec.getTimezone()));

        boolean allDay = spec.getAllDay() != null && spec.getAllDay();
        if (allDay) {
            values.put(CalendarContract.Events.ALL_DAY, 1);
        }

        if (spec.getDtEnd() != null) {
            if (spec.getDtEnd() <= spec.getDtStart()) {
                throw new CalendarException("Event end time must be after the start time.");
            }
            values.put(CalendarContract.Events.DTEND, spec.getDtEnd());
        } else {
            // Default duration: 1 hour for timed events, 1 day for all-day events
            long defaultDurationMs = allDay ? 24 * 3600 * 1000L : 3600 * 1000L;
            values.put(CalendarContract.Events.DTEND, spec.getDtStart() + defaultDurationMs);
        }

        if (spec.getLocation() != null && !spec.getLocation().trim().isEmpty()) {
            values.put(CalendarContract.Events.EVENT_LOCATION, spec.getLocation().trim());
        }
        if (spec.getDescription() != null && !spec.getDescription().trim().isEmpty()) {
            values.put(CalendarContract.Events.DESCRIPTION, spec.getDescription().trim());
        }

        Uri uri;
        try {
            uri = context.getContentResolver().insert(
                    CalendarContract.Events.CONTENT_URI, values);
        } catch (SecurityException e) {
            throw new CalendarException(PERMISSION_ERROR, e);
        }
        if (uri == null) {
            throw new CalendarException(
                    "Failed to create event: the calendar provider rejected the insert.");
        }

        long eventId;
        try {
            eventId = Long.parseLong(uri.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new CalendarException("Failed to parse the new event id.", e);
        }

        insertReminders(eventId, spec.getReminders());
        return eventId;
    }

    @Override
    public boolean updateEvent(long eventId, EventSpec spec) {
        requireWritePermission();
        if (spec == null) {
            throw new CalendarException("Event spec must not be null.");
        }

        // Load the existing event to detect recurring series and validate the id.
        String rrule = null;
        boolean exists = false;
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                EVENT_PROJECTION, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                exists = true;
                rrule = cursor.getString(1);
            }
        }
        if (!exists) {
            return false;
        }

        boolean recurring = rrule != null && !rrule.isEmpty();
        boolean changesTime = spec.getDtStart() != null || spec.getDtEnd() != null;
        if (recurring && changesTime) {
            throw new CalendarException(
                    "Event " + eventId + " is a recurring series. Moving individual "
                    + "occurrences or the series time is not supported. You can update "
                    + "non-time fields (title, location, description) of the whole series, "
                    + "or delete the series with calendar_delete_event and recreate it.");
        }

        ContentValues values = new ContentValues();
        if (spec.getTitle() != null) {
            if (spec.getTitle().trim().isEmpty()) {
                throw new CalendarException("Event title must not be empty.");
            }
            values.put(CalendarContract.Events.TITLE, spec.getTitle().trim());
        }
        if (spec.getDtStart() != null) {
            values.put(CalendarContract.Events.DTSTART, spec.getDtStart());
        }
        if (spec.getDtEnd() != null) {
            values.put(CalendarContract.Events.DTEND, spec.getDtEnd());
        }
        if (spec.getDtStart() != null && spec.getDtEnd() != null
                && spec.getDtEnd() <= spec.getDtStart()) {
            throw new CalendarException("Event end time must be after the start time.");
        }
        if (spec.getAllDay() != null) {
            values.put(CalendarContract.Events.ALL_DAY, spec.getAllDay() ? 1 : 0);
        }
        if (spec.getLocation() != null) {
            values.put(CalendarContract.Events.EVENT_LOCATION, spec.getLocation().trim());
        }
        if (spec.getDescription() != null) {
            values.put(CalendarContract.Events.DESCRIPTION, spec.getDescription().trim());
        }
        if (values.size() == 0) {
            throw new CalendarException("No fields to update.");
        }

        try {
            int rows = resolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    values, null, null);
            if (rows > 0 && spec.getReminders() != null) {
                insertReminders(eventId, spec.getReminders());
            }
            return rows > 0;
        } catch (SecurityException e) {
            throw new CalendarException(PERMISSION_ERROR, e);
        }
    }

    @Override
    public boolean deleteEvent(long eventId) {
        requireWritePermission();
        try {
            int rows = context.getContentResolver().delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    null, null);
            return rows > 0;
        } catch (SecurityException e) {
            throw new CalendarException(PERMISSION_ERROR, e);
        }
    }

    @Override
    public boolean hasWritableCalendar() {
        for (CalendarInfo info : getCalendars()) {
            if (info.isWritable()) {
                return true;
            }
        }
        return false;
    }

    // ==================== Internals ====================

    /**
     * Resolve the target calendar: use the requested id when writable,
     * otherwise fall back to the single writable calendar (if exactly one),
     * otherwise fail with guidance.
     */
    private long resolveCalendarId(Long requestedId) {
        List<CalendarInfo> calendars = getCalendars();
        if (requestedId != null) {
            for (CalendarInfo info : calendars) {
                if (info.getId() == requestedId) {
                    if (!info.isWritable()) {
                        throw new CalendarException(
                                "Calendar " + requestedId + " ('" + info.getDisplayName()
                                + "') is read-only. Use calendar_list_calendars to find "
                                + "a writable calendar.");
                    }
                    return requestedId;
                }
            }
            throw new CalendarException(
                    "Calendar " + requestedId + " not found. Use calendar_list_calendars "
                    + "to list available calendars.");
        }

        List<CalendarInfo> writable = new ArrayList<>();
        for (CalendarInfo info : calendars) {
            if (info.isWritable()) {
                writable.add(info);
            }
        }
        if (writable.isEmpty()) {
            throw new CalendarException(
                    "No writable calendar found on this device. The user needs to add a "
                    + "calendar account (e.g. Google account) first.");
        }
        if (writable.size() == 1) {
            return writable.get(0).getId();
        }
        throw new CalendarException(
                "Multiple writable calendars exist. Call calendar_list_calendars and pass "
                + "an explicit calendar_id.");
    }

    private void insertReminders(long eventId, List<Integer> reminderMinutes) {
        if (reminderMinutes == null || reminderMinutes.isEmpty()) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        for (Integer minutes : reminderMinutes) {
            if (minutes == null || minutes < 0) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Reminders.EVENT_ID, eventId);
            values.put(CalendarContract.Reminders.MINUTES, minutes);
            values.put(CalendarContract.Reminders.METHOD,
                    CalendarContract.Reminders.METHOD_ALERT);
            try {
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, values);
            } catch (Exception e) {
                // Reminder insertion is best-effort — the event itself was created.
            }
        }
    }

    private static String resolveTimezone(String requested) {
        if (requested != null && !requested.trim().isEmpty()) {
            try {
                return TimeZone.getTimeZone(requested.trim()).getID();
            } catch (Exception e) {
                throw new CalendarException("Unknown timezone: '" + requested + "'");
            }
        }
        return TimeZone.getDefault().getID();
    }

    private void requireReadPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            throw new CalendarException(PERMISSION_ERROR);
        }
    }

    private void requireWritePermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            throw new CalendarException(PERMISSION_ERROR);
        }
    }
}
