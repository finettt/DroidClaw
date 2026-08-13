package io.finett.droidclaw.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Tests {@link CalendarRepository} against an in-memory fake CalendarContract
 * provider registered with Robolectric's content resolver.
 */
@RunWith(RobolectricTestRunner.class)
public class CalendarRepositoryTest {

    private static final long HOUR_MS = 3600 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    private Context context;
    private FakeCalendarProvider provider;
    private CalendarRepository repository;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Robolectric denies dangerous permissions by default — grant them as the
        // production flow (Settings toggle -> runtime dialog) would.
        Shadows.shadowOf((Application) context).grantPermissions(
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
        provider = new FakeCalendarProvider();
        provider.attachInfo(context, null);
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider);
        repository = new CalendarRepository(context);
    }

    // ==================== Helpers ====================

    private long addCalendar(long id, String name, String account, int accessLevel) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars._ID, id);
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name);
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, "com.google");
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, accessLevel);
        values.put(CalendarContract.Calendars.VISIBLE, 1);
        values.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC");
        provider.calendars.add(values);
        return id;
    }

    private long addEvent(long id, long calendarId, String title, long dtstart, Long dtend,
                          String rrule) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events._ID, id);
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DTSTART, dtstart);
        if (dtend != null) {
            values.put(CalendarContract.Events.DTEND, dtend);
        }
        if (rrule != null) {
            values.put(CalendarContract.Events.RRULE, rrule);
        }
        provider.events.put(id, values);
        return id;
    }

    private static void expectCalendarException(Runnable action, String messagePart) {
        try {
            action.run();
            fail("Expected CalendarException containing: " + messagePart);
        } catch (CalendarException e) {
            assertTrue("Expected message containing '" + messagePart + "' but got: "
                    + e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    // ==================== Permission gating ====================

    @Test
    public void readOperations_withoutPermission_throwGuidingError() {
        Shadows.shadowOf((Application) context).denyPermissions(
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
        expectCalendarException(() -> repository.getCalendars(), "Calendar permission not granted");
        expectCalendarException(() -> repository.queryEvents(0, 10_000, null, null, 10),
                "Calendar permission not granted");
        expectCalendarException(() -> repository.getEvent(1), "Calendar permission not granted");
    }

    @Test
    public void writeOperations_withoutPermission_throwGuidingError() {
        Shadows.shadowOf((Application) context).denyPermissions(
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
        EventSpec spec = new EventSpec();
        spec.setTitle("X");
        spec.setDtStart(1_000L);
        expectCalendarException(() -> repository.createEvent(spec), "Calendar permission not granted");
        expectCalendarException(() -> repository.updateEvent(1, spec), "Calendar permission not granted");
        expectCalendarException(() -> repository.deleteEvent(1), "Calendar permission not granted");
    }

    // ==================== getCalendars ====================

    @Test
    public void getCalendars_mapsRowsAndWritableFlag() {
        addCalendar(1, "Personal", "me@gmail.com", 700);   // owner
        addCalendar(2, "Birthdays", "me@gmail.com", 200);  // read-only

        List<CalendarInfo> calendars = repository.getCalendars();

        assertEquals(2, calendars.size());
        CalendarInfo personal = calendars.get(0);
        assertEquals(1, personal.getId());
        assertEquals("Personal", personal.getDisplayName());
        assertEquals("me@gmail.com", personal.getAccountName());
        assertTrue(personal.isWritable());
        assertFalse(calendars.get(1).isWritable());
    }

    @Test
    public void hasWritableCalendar_reflectsCalendars() {
        assertFalse(repository.hasWritableCalendar());
        addCalendar(1, "Birthdays", "me@gmail.com", 200);
        assertFalse(repository.hasWritableCalendar());
        addCalendar(2, "Personal", "me@gmail.com", 500);
        assertTrue(repository.hasWritableCalendar());
    }

    // ==================== queryEvents ====================

    @Test
    public void queryEvents_returnsEventsInWindow_withCalendarName() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Lunch", 1_000_000_000_000L, 1_000_000_000_000L + HOUR_MS, null);

        List<CalendarEvent> events = repository.queryEvents(
                999_999_000_000L, 1_000_001_000_000L, null, null, 100);

        assertEquals(1, events.size());
        CalendarEvent event = events.get(0);
        assertEquals(10, event.getEventId());
        assertEquals("Lunch", event.getTitle());
        assertEquals("Personal", event.getCalendarName());
        assertEquals(1_000_000_000_000L, event.getStartMillis());
        assertFalse(event.isRecurring());
    }

    @Test
    public void queryEvents_excludesEventsOutsideWindow() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Inside", 1_000L, 2_000L, null);
        addEvent(11, 1, "Outside", 50_000L, 51_000L, null);

        List<CalendarEvent> events = repository.queryEvents(0, 10_000, null, null, 100);

        assertEquals(1, events.size());
        assertEquals("Inside", events.get(0).getTitle());
    }

    @Test
    public void queryEvents_calendarFilter_restrictsResults() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addCalendar(2, "Work", "me@work.com", 700);
        addEvent(10, 1, "Home thing", 1_000L, 2_000L, null);
        addEvent(11, 2, "Work thing", 1_500L, 2_500L, null);

        List<CalendarEvent> events = repository.queryEvents(0, 10_000, 2L, null, 100);

        assertEquals(1, events.size());
        assertEquals("Work thing", events.get(0).getTitle());
    }

    @Test
    public void queryEvents_textQuery_matchesTitleLocationDescriptionCaseInsensitive() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Dentist Appointment", 1_000L, 2_000L, null);
        addEvent(11, 1, "Gym", 3_000L, 4_000L, null);
        provider.events.get(11L).put(CalendarContract.Events.EVENT_LOCATION, "fitness CENTER");

        List<CalendarEvent> byTitle = repository.queryEvents(0, 10_000, null, "dentist", 100);
        assertEquals(1, byTitle.size());
        assertEquals("Dentist Appointment", byTitle.get(0).getTitle());

        List<CalendarEvent> byLocation = repository.queryEvents(0, 10_000, null, "center", 100);
        assertEquals(1, byLocation.size());
        assertEquals("Gym", byLocation.get(0).getTitle());
    }

    @Test
    public void queryEvents_recurringEvent_expandsToOccurrencesWithFlag() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Standup", 1_000L, null, "FREQ=DAILY");

        List<CalendarEvent> events = repository.queryEvents(0, 10 * DAY_MS, null, null, 100);

        assertEquals(3, events.size()); // fake expands recurring events to 3 occurrences
        for (CalendarEvent event : events) {
            assertTrue(event.isRecurring());
            assertEquals("Standup", event.getTitle());
        }
        assertTrue(events.get(0).getStartMillis() < events.get(1).getStartMillis());
    }

    @Test
    public void queryEvents_invalidWindow_throws() {
        expectCalendarException(
                () -> repository.queryEvents(5_000, 1_000, null, null, 100),
                "End time must be after start time");
    }

    @Test
    public void queryEvents_respectsLimit() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        for (int i = 0; i < 5; i++) {
            addEvent(10 + i, 1, "Event " + i, 1_000L + i * 100, 2_000L + i * 100, null);
        }
        List<CalendarEvent> events = repository.queryEvents(0, 10_000, null, null, 2);
        assertEquals(2, events.size());
    }

    // ==================== createEvent ====================

    @Test
    public void createEvent_minimalSpec_insertsRowWithDefaults() {
        addCalendar(1, "Personal", "me@gmail.com", 700);

        EventSpec spec = new EventSpec();
        spec.setTitle("Dentist");
        spec.setDtStart(1_000_000L);

        long eventId = repository.createEvent(spec);

        assertTrue(eventId > 0);
        ContentValues stored = provider.events.get(eventId);
        assertNotNull(stored);
        assertEquals("Dentist", stored.getAsString(CalendarContract.Events.TITLE));
        assertEquals(Long.valueOf(1_000_000L), stored.getAsLong(CalendarContract.Events.DTSTART));
        // Default duration: 1 hour
        assertEquals(Long.valueOf(1_000_000L + HOUR_MS),
                stored.getAsLong(CalendarContract.Events.DTEND));
        assertEquals(Long.valueOf(1L), stored.getAsLong(CalendarContract.Events.CALENDAR_ID));
        assertEquals(TimeZone.getDefault().getID(),
                stored.getAsString(CalendarContract.Events.EVENT_TIMEZONE));
    }

    @Test
    public void createEvent_allDay_setsFlagAndOneDayDuration() {
        addCalendar(1, "Personal", "me@gmail.com", 700);

        EventSpec spec = new EventSpec();
        spec.setTitle("Holiday");
        spec.setDtStart(0L);
        spec.setAllDay(true);

        long eventId = repository.createEvent(spec);

        ContentValues stored = provider.events.get(eventId);
        assertEquals(Integer.valueOf(1), stored.getAsInteger(CalendarContract.Events.ALL_DAY));
        assertEquals(Long.valueOf(DAY_MS), stored.getAsLong(CalendarContract.Events.DTEND));
    }

    @Test
    public void createEvent_endNotAfterStart_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        EventSpec spec = new EventSpec();
        spec.setTitle("Broken");
        spec.setDtStart(2_000L);
        spec.setDtEnd(1_000L);
        expectCalendarException(() -> repository.createEvent(spec), "after the start");
    }

    @Test
    public void createEvent_missingTitle_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        EventSpec spec = new EventSpec();
        spec.setDtStart(1_000L);
        expectCalendarException(() -> repository.createEvent(spec), "title");
    }

    @Test
    public void createEvent_noWritableCalendar_throws() {
        addCalendar(1, "Birthdays", "me@gmail.com", 200);
        EventSpec spec = new EventSpec();
        spec.setTitle("X");
        spec.setDtStart(1_000L);
        expectCalendarException(() -> repository.createEvent(spec), "No writable calendar");
    }

    @Test
    public void createEvent_multipleWritable_withoutExplicitId_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addCalendar(2, "Work", "me@work.com", 700);
        EventSpec spec = new EventSpec();
        spec.setTitle("X");
        spec.setDtStart(1_000L);
        expectCalendarException(() -> repository.createEvent(spec), "calendar_id");
    }

    @Test
    public void createEvent_unknownCalendarId_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        EventSpec spec = new EventSpec();
        spec.setTitle("X");
        spec.setDtStart(1_000L);
        spec.setCalendarId(999L);
        expectCalendarException(() -> repository.createEvent(spec), "not found");
    }

    @Test
    public void createEvent_readOnlyCalendar_throws() {
        addCalendar(1, "Birthdays", "me@gmail.com", 200);
        addCalendar(2, "Personal", "me@gmail.com", 700);
        EventSpec spec = new EventSpec();
        spec.setTitle("X");
        spec.setDtStart(1_000L);
        spec.setCalendarId(1L);
        expectCalendarException(() -> repository.createEvent(spec), "read-only");
    }

    @Test
    public void createEvent_reminders_areInserted() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        EventSpec spec = new EventSpec();
        spec.setTitle("Call");
        spec.setDtStart(1_000L);
        spec.setReminders(Arrays.asList(30, 1440));

        long eventId = repository.createEvent(spec);

        assertEquals(2, provider.reminders.size());
        assertEquals(Integer.valueOf(30),
                provider.reminders.get(0).getAsInteger(CalendarContract.Reminders.MINUTES));
        assertEquals(Long.valueOf(eventId),
                provider.reminders.get(0).getAsLong(CalendarContract.Reminders.EVENT_ID));
        assertEquals(Integer.valueOf(CalendarContract.Reminders.METHOD_ALERT),
                provider.reminders.get(0).getAsInteger(CalendarContract.Reminders.METHOD));
    }

    // ==================== getEvent ====================

    @Test
    public void getEvent_returnsEventWithDetails() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.EVENT_LOCATION, "Office");
        values.put(CalendarContract.Events.DESCRIPTION, "Bring notes");
        addEvent(10, 1, "Meeting", 1_000L, 2_000L, null);
        provider.events.get(10L).putAll(values);

        CalendarEvent event = repository.getEvent(10);

        assertNotNull(event);
        assertEquals("Meeting", event.getTitle());
        assertEquals("Office", event.getLocation());
        assertEquals("Bring notes", event.getDescription());
        assertEquals("Personal", event.getCalendarName());
        assertFalse(event.isRecurring());
    }

    @Test
    public void getEvent_unknownId_returnsNull() {
        assertNull(repository.getEvent(999));
    }

    @Test
    public void getEvent_recurringSeries_hasRecurringFlag() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Standup", 1_000L, null, "FREQ=DAILY");
        CalendarEvent event = repository.getEvent(10);
        assertNotNull(event);
        assertTrue(event.isRecurring());
    }

    // ==================== updateEvent ====================

    @Test
    public void updateEvent_changesTitle() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Old", 1_000L, 2_000L, null);

        EventSpec spec = new EventSpec();
        spec.setTitle("New");

        assertTrue(repository.updateEvent(10, spec));
        assertEquals("New", provider.events.get(10L).getAsString(CalendarContract.Events.TITLE));
    }

    @Test
    public void updateEvent_movesTimedEvent() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Meeting", 1_000L, 2_000L, null);

        EventSpec spec = new EventSpec();
        spec.setDtStart(5_000L);
        spec.setDtEnd(6_000L);

        assertTrue(repository.updateEvent(10, spec));
        assertEquals(Long.valueOf(5_000L),
                provider.events.get(10L).getAsLong(CalendarContract.Events.DTSTART));
        assertEquals(Long.valueOf(6_000L),
                provider.events.get(10L).getAsLong(CalendarContract.Events.DTEND));
    }

    @Test
    public void updateEvent_unknownId_returnsFalse() {
        EventSpec spec = new EventSpec();
        spec.setTitle("New");
        assertFalse(repository.updateEvent(999, spec));
    }

    @Test
    public void updateEvent_recurringTimeChange_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Standup", 1_000L, null, "FREQ=DAILY");

        EventSpec spec = new EventSpec();
        spec.setDtStart(5_000L);

        expectCalendarException(() -> repository.updateEvent(10, spec), "recurring");
    }

    @Test
    public void updateEvent_recurringNonTimeChange_allowed() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Standup", 1_000L, null, "FREQ=DAILY");

        EventSpec spec = new EventSpec();
        spec.setLocation("Room 2");

        assertTrue(repository.updateEvent(10, spec));
        assertEquals("Room 2",
                provider.events.get(10L).getAsString(CalendarContract.Events.EVENT_LOCATION));
    }

    @Test
    public void updateEvent_emptySpec_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Meeting", 1_000L, 2_000L, null);
        expectCalendarException(() -> repository.updateEvent(10, new EventSpec()),
                "No fields to update");
    }

    @Test
    public void updateEvent_endNotAfterStart_throws() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Meeting", 1_000L, 2_000L, null);

        EventSpec spec = new EventSpec();
        spec.setDtStart(9_000L);
        spec.setDtEnd(8_000L);

        expectCalendarException(() -> repository.updateEvent(10, spec), "after the start");
    }

    // ==================== deleteEvent ====================

    @Test
    public void deleteEvent_removesExistingEvent() {
        addCalendar(1, "Personal", "me@gmail.com", 700);
        addEvent(10, 1, "Old", 1_000L, 2_000L, null);

        assertTrue(repository.deleteEvent(10));
        assertFalse(provider.events.containsKey(10L));
    }

    @Test
    public void deleteEvent_unknownId_returnsFalse() {
        assertFalse(repository.deleteEvent(999));
    }

    // ==================== Fake CalendarContract provider ====================

    /**
     * Minimal in-memory CalendarContract fake. Supports calendars, events
     * (with item URIs), reminders, and instance-range queries with simple
     * expansion of recurring events (3 daily occurrences).
     */
    private static class FakeCalendarProvider extends ContentProvider {

        private static final int CALENDARS = 1;
        private static final int EVENTS = 2;
        private static final int EVENT_ITEM = 3;
        private static final int INSTANCES_RANGE = 4;
        private static final int REMINDERS = 5;

        private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

        static {
            MATCHER.addURI(CalendarContract.AUTHORITY, "calendars", CALENDARS);
            MATCHER.addURI(CalendarContract.AUTHORITY, "events", EVENTS);
            MATCHER.addURI(CalendarContract.AUTHORITY, "events/#", EVENT_ITEM);
            MATCHER.addURI(CalendarContract.AUTHORITY, "instances/when/*/*", INSTANCES_RANGE);
            MATCHER.addURI(CalendarContract.AUTHORITY, "reminders", REMINDERS);
        }

        final List<ContentValues> calendars = new ArrayList<>();
        final Map<Long, ContentValues> events = new LinkedHashMap<>();
        final List<ContentValues> reminders = new ArrayList<>();
        private long nextEventId = 1_000;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                            String[] selectionArgs, String sortOrder) {
            switch (MATCHER.match(uri)) {
                case CALENDARS:
                    return toCursor(projection, calendars);

                case EVENT_ITEM: {
                    long id = ContentUris.parseId(uri);
                    ContentValues values = events.get(id);
                    List<ContentValues> rows = values != null
                            ? java.util.Collections.singletonList(values)
                            : java.util.Collections.emptyList();
                    return toCursor(projection, rows);
                }

                case INSTANCES_RANGE: {
                    // Path: instances/when/<begin>/<end>
                    long begin = Long.parseLong(uri.getPathSegments().get(2));
                    long end = Long.parseLong(uri.getPathSegments().get(3));
                    List<ContentValues> occurrences = expandOccurrences(begin, end);
                    occurrences = applyInstanceFilters(occurrences, selection, selectionArgs);
                    return toCursor(projection, occurrences);
                }

                default:
                    return toCursor(projection, java.util.Collections.emptyList());
            }
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            switch (MATCHER.match(uri)) {
                case EVENTS: {
                    long id = nextEventId++;
                    ContentValues copy = new ContentValues(values);
                    copy.put(CalendarContract.Events._ID, id);
                    events.put(id, copy);
                    return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                }
                case REMINDERS: {
                    reminders.add(new ContentValues(values));
                    return Uri.withAppendedPath(CalendarContract.Reminders.CONTENT_URI,
                            String.valueOf(reminders.size()));
                }
                default:
                    return null;
            }
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            if (MATCHER.match(uri) == EVENT_ITEM) {
                long id = ContentUris.parseId(uri);
                ContentValues existing = events.get(id);
                if (existing == null) {
                    return 0;
                }
                existing.putAll(values);
                return 1;
            }
            return 0;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            if (MATCHER.match(uri) == EVENT_ITEM) {
                long id = ContentUris.parseId(uri);
                if (events.remove(id) != null) {
                    reminders.removeIf(r -> {
                        Long eventId = r.getAsLong(CalendarContract.Reminders.EVENT_ID);
                        return eventId != null && eventId == id;
                    });
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        /** Expand stored events into instance rows within [begin, end). */
        private List<ContentValues> expandOccurrences(long begin, long end) {
            List<ContentValues> occurrences = new ArrayList<>();
            for (ContentValues event : events.values()) {
                long dtstart = event.getAsLong(CalendarContract.Events.DTSTART);
                Long dtendObj = event.getAsLong(CalendarContract.Events.DTEND);
                long duration = (dtendObj != null ? dtendObj : dtstart + HOUR_MS) - dtstart;
                String rrule = event.getAsString(CalendarContract.Events.RRULE);
                boolean recurring = rrule != null && !rrule.isEmpty();

                int occurrencesCount = recurring ? 3 : 1;
                for (int i = 0; i < occurrencesCount; i++) {
                    long occBegin = dtstart + i * DAY_MS;
                    long occEnd = occBegin + duration;
                    if (occBegin < end && occEnd > begin) {
                        ContentValues row = new ContentValues(event);
                        row.put(CalendarContract.Instances.EVENT_ID,
                                event.getAsLong(CalendarContract.Events._ID));
                        row.put(CalendarContract.Instances.BEGIN, occBegin);
                        row.put(CalendarContract.Instances.END, occEnd);
                        occurrences.add(row);
                    }
                }
            }
            occurrences.sort((a, b) -> Long.compare(
                    a.getAsLong(CalendarContract.Instances.BEGIN),
                    b.getAsLong(CalendarContract.Instances.BEGIN)));
            return occurrences;
        }

        /**
         * Apply the repository's fixed selection shapes: a calendar_id equality
         * filter and/or a LOWER(...) LIKE text filter.
         */
        private List<ContentValues> applyInstanceFilters(List<ContentValues> rows,
                                                         String selection, String[] selectionArgs) {
            if (selection == null || selectionArgs == null) {
                return rows;
            }
            List<ContentValues> result = new ArrayList<>(rows);

            if (selection.contains(CalendarContract.Instances.CALENDAR_ID + " = ?")) {
                long calendarFilter = Long.parseLong(selectionArgs[0]);
                result.removeIf(row -> {
                    Long calendarId = row.getAsLong(CalendarContract.Instances.CALENDAR_ID);
                    return calendarId == null || calendarId != calendarFilter;
                });
            }

            String likeArg = null;
            for (String arg : selectionArgs) {
                if (arg != null && arg.startsWith("%") && arg.endsWith("%")) {
                    likeArg = arg;
                    break;
                }
            }
            if (likeArg != null) {
                String needle = likeArg.substring(1, likeArg.length() - 1)
                        .toLowerCase(Locale.ROOT);
                result.removeIf(row -> !containsText(row, CalendarContract.Instances.TITLE, needle)
                        && !containsText(row, CalendarContract.Instances.EVENT_LOCATION, needle)
                        && !containsText(row, CalendarContract.Instances.DESCRIPTION, needle));
            }
            return result;
        }

        private static boolean containsText(ContentValues row, String column, String needle) {
            String value = row.getAsString(column);
            return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
        }

        private static Cursor toCursor(String[] projection, List<ContentValues> rows) {
            MatrixCursor cursor = new MatrixCursor(projection);
            for (ContentValues row : rows) {
                Object[] values = new Object[projection.length];
                for (int i = 0; i < projection.length; i++) {
                    values[i] = row.get(projection[i]);
                }
                cursor.addRow(values);
            }
            return cursor;
        }
    }
}
