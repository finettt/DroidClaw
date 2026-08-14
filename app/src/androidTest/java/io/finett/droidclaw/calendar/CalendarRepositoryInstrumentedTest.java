package io.finett.droidclaw.calendar;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

/**
 * Instrumented tests for {@link CalendarRepository} against the real device
 * CalendarProvider.
 *
 * <p>Each run creates a dedicated throwaway calendar (inserted as a sync
 * adapter under a private test account), performs CRUD through the
 * repository, and removes the calendar in {@link #tearDown()} — deleting a
 * calendar also deletes its events. If the device's provider rejects
 * third-party calendar creation (some OEM builds do), the tests are skipped.
 *
 * <p>Requires API 28+ because runtime permissions are granted via
 * UiAutomation ({@code GrantPermissionRule}).
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 28)
public class CalendarRepositoryInstrumentedTest {

    private static final String TEST_ACCOUNT_NAME = "droidclaw.instrumented.test";
    private static final String TEST_ACCOUNT_TYPE = "io.finett.droidclaw.test";
    private static final String TEST_CALENDAR_NAME = "DroidClaw Instrumented Test Calendar";

    private static final long HOUR_MS = 3600 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    @Rule
    public androidx.test.rule.GrantPermissionRule permissionRule =
            androidx.test.rule.GrantPermissionRule.grant(
                    Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);

    private CalendarRepository repository;
    private long testCalendarId = -1;
    private long baseMillis;

    @Before
    public void setUp() {
        repository = new CalendarRepository(getApplicationContext());
        // Anchor event times in the near future; some providers behave oddly
        // with events far in the past.
        baseMillis = (System.currentTimeMillis() / HOUR_MS) * HOUR_MS + DAY_MS;

        testCalendarId = createTestCalendar();
        Assume.assumeTrue(
                "Device CalendarProvider rejected test calendar creation — skipping",
                testCalendarId != -1);
    }

    @After
    public void tearDown() {
        if (testCalendarId != -1) {
            deleteCalendar(testCalendarId);
        }
    }

    // ==================== Calendars ====================

    @Test
    public void getCalendars_containsTestCalendar_andItIsWritable() {
        List<CalendarInfo> calendars = repository.getCalendars();

        CalendarInfo found = null;
        for (CalendarInfo calendar : calendars) {
            if (calendar.getId() == testCalendarId) {
                found = calendar;
                break;
            }
        }

        assertNotNull("Test calendar should be listed", found);
        assertEquals(TEST_CALENDAR_NAME, found.getDisplayName());
        assertEquals(TEST_ACCOUNT_NAME, found.getAccountName());
        assertTrue("Test calendar should be writable", found.isWritable());
        assertTrue(repository.hasWritableCalendar());
    }

    // ==================== Create / read round-trip ====================

    @Test
    public void createEvent_thenGetEvent_roundTripsAllFields() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Instrumented Meeting");
        spec.setLocation("Conference Room 3");
        spec.setDescription("Created by CalendarRepositoryInstrumentedTest");
        spec.setDtStart(baseMillis);
        spec.setDtEnd(baseMillis + HOUR_MS);
        spec.setTimezone(TimeZone.getDefault().getID());

        long eventId = repository.createEvent(spec);
        assertTrue(eventId > 0);

        CalendarEvent event = repository.getEvent(eventId);
        assertNotNull(event);
        assertEquals("Instrumented Meeting", event.getTitle());
        assertEquals("Conference Room 3", event.getLocation());
        assertEquals("Created by CalendarRepositoryInstrumentedTest", event.getDescription());
        assertEquals(baseMillis, event.getStartMillis());
        assertEquals(baseMillis + HOUR_MS, event.getEndMillis());
        assertEquals(testCalendarId, event.getCalendarId());
        assertEquals(TEST_CALENDAR_NAME, event.getCalendarName());
        assertFalse(event.isAllDay());
        assertFalse(event.isRecurring());
    }

    @Test
    public void createEvent_allDay_setsFlag() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Instrumented Holiday");
        spec.setAllDay(true);
        spec.setDtStart(baseMillis);

        long eventId = repository.createEvent(spec);

        CalendarEvent event = repository.getEvent(eventId);
        assertNotNull(event);
        assertTrue(event.isAllDay());
    }

    @Test
    public void createEvent_withReminders_insertsReminderRows() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Instrumented Reminder Test");
        spec.setDtStart(baseMillis);
        spec.setDtEnd(baseMillis + HOUR_MS);
        spec.setReminders(Arrays.asList(30, 1440));

        long eventId = repository.createEvent(spec);

        assertEquals(2, countReminders(eventId));
    }

    // ==================== Query ====================

    @Test
    public void queryEvents_returnsCreatedEventInWindow() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Window Probe");
        spec.setDtStart(baseMillis);
        spec.setDtEnd(baseMillis + HOUR_MS);
        repository.createEvent(spec);

        List<CalendarEvent> events = repository.queryEvents(
                baseMillis - DAY_MS, baseMillis + DAY_MS, testCalendarId, null, 100);

        assertEquals(1, events.size());
        assertEquals("Window Probe", events.get(0).getTitle());
        assertEquals(TEST_CALENDAR_NAME, events.get(0).getCalendarName());
    }

    @Test
    public void queryEvents_textQuery_matchesTitle() {
        EventSpec match = new EventSpec();
        match.setCalendarId(testCalendarId);
        match.setTitle("Unique Dentist Probe");
        match.setDtStart(baseMillis);
        match.setDtEnd(baseMillis + HOUR_MS);
        repository.createEvent(match);

        EventSpec other = new EventSpec();
        other.setCalendarId(testCalendarId);
        other.setTitle("Something else");
        other.setDtStart(baseMillis + 2 * HOUR_MS);
        other.setDtEnd(baseMillis + 3 * HOUR_MS);
        repository.createEvent(other);

        List<CalendarEvent> events = repository.queryEvents(
                baseMillis - DAY_MS, baseMillis + DAY_MS, testCalendarId, "dentist", 100);

        assertEquals(1, events.size());
        assertEquals("Unique Dentist Probe", events.get(0).getTitle());
    }

    @Test
    public void queryEvents_windowExcludesDistantEvent() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Far Future Event");
        spec.setDtStart(baseMillis + 30 * DAY_MS);
        spec.setDtEnd(baseMillis + 30 * DAY_MS + HOUR_MS);
        repository.createEvent(spec);

        List<CalendarEvent> events = repository.queryEvents(
                baseMillis, baseMillis + DAY_MS, testCalendarId, null, 100);

        assertEquals(0, events.size());
    }

    // ==================== Update / delete ====================

    @Test
    public void updateEvent_changesTitleAndMovesTime() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Before Move");
        spec.setDtStart(baseMillis);
        spec.setDtEnd(baseMillis + HOUR_MS);
        long eventId = repository.createEvent(spec);

        EventSpec update = new EventSpec();
        update.setTitle("After Move");
        update.setDtStart(baseMillis + 2 * DAY_MS);
        update.setDtEnd(baseMillis + 2 * DAY_MS + HOUR_MS);

        assertTrue(repository.updateEvent(eventId, update));

        CalendarEvent event = repository.getEvent(eventId);
        assertNotNull(event);
        assertEquals("After Move", event.getTitle());
        assertEquals(baseMillis + 2 * DAY_MS, event.getStartMillis());
        assertEquals(baseMillis + 2 * DAY_MS + HOUR_MS, event.getEndMillis());
    }

    @Test
    public void deleteEvent_removesEvent() {
        EventSpec spec = new EventSpec();
        spec.setCalendarId(testCalendarId);
        spec.setTitle("Doomed Event");
        spec.setDtStart(baseMillis);
        spec.setDtEnd(baseMillis + HOUR_MS);
        long eventId = repository.createEvent(spec);

        assertTrue(repository.deleteEvent(eventId));
        assertNull(repository.getEvent(eventId));
    }

    @Test
    public void deleteEvent_unknownId_returnsFalse() {
        assertFalse(repository.deleteEvent(999_999_999L));
    }

    // ==================== Test calendar lifecycle ====================

    /**
     * Creates the throwaway calendar as a sync adapter. Returns the calendar
     * id, or -1 if the provider rejected the insert (test is skipped then).
     */
    private long createTestCalendar() {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE);
        values.put(CalendarContract.Calendars.NAME, TEST_CALENDAR_NAME);
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, TEST_CALENDAR_NAME);
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, TEST_ACCOUNT_NAME);
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF33B5E5);
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER);
        values.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                TimeZone.getDefault().getID());
        values.put(CalendarContract.Calendars.VISIBLE, 1);
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);

        Uri uri = getApplicationContext().getContentResolver()
                .insert(syncAdapterCalendarsUri(), values);
        if (uri == null) {
            return -1;
        }
        return ContentUris.parseId(uri);
    }

    private void deleteCalendar(long calendarId) {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        // Deleting via the item URI; fall back to a selection-based delete
        // scoped to the test account for providers that ignore item deletes.
        int deleted = resolver.delete(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
                null, null);
        if (deleted == 0) {
            resolver.delete(syncAdapterCalendarsUri(),
                    CalendarContract.Calendars._ID + " = ?",
                    new String[]{String.valueOf(calendarId)});
        }
    }

    private static Uri syncAdapterCalendarsUri() {
        return CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, TEST_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE)
                .build();
    }

    private static int countReminders(long eventId) {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        try (Cursor cursor = resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                new String[]{CalendarContract.Reminders._ID},
                CalendarContract.Reminders.EVENT_ID + " = ?",
                new String[]{String.valueOf(eventId)},
                null)) {
            return cursor == null ? 0 : cursor.getCount();
        }
    }
}
