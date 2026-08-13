package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarEvent;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarInfo;
import io.finett.droidclaw.calendar.CalendarTimeUtil;
import io.finett.droidclaw.calendar.EventSpec;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for the calendar tools against an in-memory fake
 * {@link CalendarDataSource}. Plain JUnit — no Android runtime needed.
 */
public class CalendarToolsTest {

    private static final ZoneId DEVICE = ZoneId.systemDefault();
    private static final long HOUR_MS = 3600 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    private FakeDataSource dataSource;
    private CalendarListCalendarsTool listCalendarsTool;
    private CalendarListEventsTool listEventsTool;
    private CalendarCreateEventTool createEventTool;
    private CalendarUpdateEventTool updateEventTool;
    private CalendarDeleteEventTool deleteEventTool;

    @Before
    public void setUp() {
        dataSource = new FakeDataSource();
        listCalendarsTool = new CalendarListCalendarsTool(dataSource);
        listEventsTool = new CalendarListEventsTool(dataSource);
        createEventTool = new CalendarCreateEventTool(dataSource);
        updateEventTool = new CalendarUpdateEventTool(dataSource);
        deleteEventTool = new CalendarDeleteEventTool(dataSource);
    }

    private static long local(String iso) {
        return java.time.LocalDateTime.parse(iso).atZone(DEVICE).toInstant().toEpochMilli();
    }

    private static CalendarEvent event(long id, String title, long start, long end) {
        return new CalendarEvent(id, 1, "Personal", title, start, end, false,
                "", "", "UTC", false);
    }

    // ==================== Definitions & approval flags ====================

    @Test
    public void toolNames_areStable() {
        assertEquals("calendar_list_calendars", listCalendarsTool.getName());
        assertEquals("calendar_list_events", listEventsTool.getName());
        assertEquals("calendar_create_event", createEventTool.getName());
        assertEquals("calendar_update_event", updateEventTool.getName());
        assertEquals("calendar_delete_event", deleteEventTool.getName());
    }

    @Test
    public void writeTools_requireApproval_readToolsDoNot() {
        assertTrue(createEventTool.requiresApproval());
        assertTrue(updateEventTool.requiresApproval());
        assertTrue(deleteEventTool.requiresApproval());
        assertFalse(listCalendarsTool.requiresApproval());
        assertFalse(listEventsTool.requiresApproval());
    }

    @Test
    public void definitions_haveRequiredParametersAndStrictSchema() {
        JsonObject createParams = createEventTool.getDefinition().getFunction()
                .getParameters();
        assertTrue(createParams.getAsJsonArray("required").contains(
                new com.google.gson.JsonPrimitive("title")));
        assertTrue(createParams.getAsJsonArray("required").contains(
                new com.google.gson.JsonPrimitive("start")));
        assertFalse(createParams.getAsJsonArray("required").contains(
                new com.google.gson.JsonPrimitive("end")));
        assertFalse(createParams.get("additionalProperties").getAsBoolean());

        // reminders parameter is an integer array
        JsonObject reminders = createParams.getAsJsonObject("properties")
                .getAsJsonObject("reminders");
        assertEquals("array", reminders.get("type").getAsString());
        assertEquals("integer", reminders.getAsJsonObject("items").get("type").getAsString());
    }

    // ==================== calendar_list_calendars ====================

    @Test
    public void listCalendars_returnsAllCalendarsAsJson() {
        dataSource.calendars.add(new CalendarInfo(1, "Personal", "me@gmail.com",
                "com.google", 700, true, "UTC"));
        dataSource.calendars.add(new CalendarInfo(2, "Birthdays", "me@gmail.com",
                "com.google", 200, true, "UTC"));

        ToolResult result = listCalendarsTool.execute(new JsonObject());

        assertTrue(result.isSuccess());
        JsonObject json = com.google.gson.JsonParser.parseString(result.getContent())
                .getAsJsonObject();
        assertEquals(2, json.get("count").getAsInt());
        JsonArray arr = json.getAsJsonArray("calendars");
        assertEquals(1, arr.get(0).getAsJsonObject().get("calendar_id").getAsLong());
        assertTrue(arr.get(0).getAsJsonObject().get("writable").getAsBoolean());
        assertFalse(arr.get(1).getAsJsonObject().get("writable").getAsBoolean());
    }

    @Test
    public void listCalendars_providerError_isSurfaced() {
        dataSource.errorOnRead = "Calendar permission not granted.";
        ToolResult result = listCalendarsTool.execute(new JsonObject());
        assertFalse(result.isSuccess());
        assertEquals("Calendar permission not granted.", result.getError());
    }

    // ==================== calendar_list_events ====================

    @Test
    public void listEvents_defaultWindow_returnsEvents() {
        long start = CalendarTimeUtil.startOfToday();
        dataSource.events.add(event(10, "Lunch", start + 12 * HOUR_MS, start + 13 * HOUR_MS));

        ToolResult result = listEventsTool.execute(new JsonObject());

        assertTrue(result.isSuccess());
        assertEquals(start, dataSource.lastBegin);
        assertEquals(start + 7 * DAY_MS, dataSource.lastEnd);
        JsonObject json = com.google.gson.JsonParser.parseString(result.getContent())
                .getAsJsonObject();
        assertEquals(1, json.get("count").getAsInt());
        assertEquals("Lunch", json.getAsJsonArray("events").get(0)
                .getAsJsonObject().get("title").getAsString());
    }

    @Test
    public void listEvents_customWindowAndFilters_arePassedThrough() {
        JsonObject args = new JsonObject();
        args.addProperty("start", "2026-06-01T09:00");
        args.addProperty("end", "2026-06-02T09:00");
        args.addProperty("query", "standup");
        args.addProperty("calendar_id", 7);

        ToolResult result = listEventsTool.execute(args);

        assertTrue(result.isSuccess());
        assertEquals(local("2026-06-01T09:00:00"), dataSource.lastBegin);
        assertEquals(local("2026-06-02T09:00:00"), dataSource.lastEnd);
        assertEquals("standup", dataSource.lastTextQuery);
        assertEquals(Long.valueOf(7), dataSource.lastCalendarId);
    }

    @Test
    public void listEvents_invalidDate_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("start", "next tuesday");
        ToolResult result = listEventsTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Unrecognised date/time"));
    }

    @Test
    public void listEvents_endBeforeStart_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("start", "2026-06-02T09:00");
        args.addProperty("end", "2026-06-01T09:00");
        ToolResult result = listEventsTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("after"));
    }

    // ==================== calendar_create_event ====================

    @Test
    public void createEvent_minimalArgs_defaultsToOneHour() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Dentist");
        args.addProperty("start", "2026-06-01T15:00");

        ToolResult result = createEventTool.execute(args);

        assertTrue(result.isSuccess());
        EventSpec spec = dataSource.lastSpec;
        assertEquals("Dentist", spec.getTitle());
        assertEquals(Long.valueOf(local("2026-06-01T15:00:00")), spec.getDtStart());
        assertEquals(Long.valueOf(local("2026-06-01T15:00:00") + HOUR_MS), spec.getDtEnd());
        assertEquals(Boolean.FALSE, spec.getAllDay());

        JsonObject json = com.google.gson.JsonParser.parseString(result.getContent())
                .getAsJsonObject();
        assertEquals(42, json.get("event_id").getAsLong());
    }

    @Test
    public void createEvent_dateOnlyStart_becomesAllDay() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Conference");
        args.addProperty("start", "2026-06-01");

        ToolResult result = createEventTool.execute(args);

        assertTrue(result.isSuccess());
        EventSpec spec = dataSource.lastSpec;
        assertEquals(Boolean.TRUE, spec.getAllDay());
        long expectedStart = LocalDate.of(2026, 6, 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(Long.valueOf(expectedStart), spec.getDtStart());
        assertEquals(Long.valueOf(expectedStart + DAY_MS), spec.getDtEnd());
    }

    @Test
    public void createEvent_allDayWithInclusiveEndDate() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Vacation");
        args.addProperty("start", "2026-06-01");
        args.addProperty("end", "2026-06-03");
        args.addProperty("all_day", true);

        ToolResult result = createEventTool.execute(args);

        assertTrue(result.isSuccess());
        EventSpec spec = dataSource.lastSpec;
        long june1 = LocalDate.of(2026, 6, 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        // End date inclusive -> DTEND exclusive = June 4 UTC midnight
        assertEquals(Long.valueOf(june1), spec.getDtStart());
        assertEquals(Long.valueOf(june1 + 3 * DAY_MS), spec.getDtEnd());
    }

    @Test
    public void createEvent_remindersAndMetadata_passedThrough() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Call");
        args.addProperty("start", "2026-06-01T10:00");
        args.addProperty("end", "2026-06-01T10:30");
        args.addProperty("location", "Office");
        args.addProperty("description", "With Sam");
        args.addProperty("calendar_id", 3);
        args.addProperty("timezone", "Europe/Berlin");
        JsonArray reminders = new JsonArray();
        reminders.add(30);
        reminders.add(1440);
        args.add("reminders", reminders);

        ToolResult result = createEventTool.execute(args);

        assertTrue(result.isSuccess());
        EventSpec spec = dataSource.lastSpec;
        assertEquals(Long.valueOf(3), spec.getCalendarId());
        assertEquals("Office", spec.getLocation());
        assertEquals("With Sam", spec.getDescription());
        assertEquals("Europe/Berlin", spec.getTimezone());
        assertEquals(Arrays.asList(30, 1440), spec.getReminders());
    }

    @Test
    public void createEvent_missingTitle_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("start", "2026-06-01T15:00");
        ToolResult result = createEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("title"));
    }

    @Test
    public void createEvent_invalidStart_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "X");
        args.addProperty("start", "garbage");
        ToolResult result = createEventTool.execute(args);
        assertFalse(result.isSuccess());
    }

    @Test
    public void createEvent_endBeforeStart_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "X");
        args.addProperty("start", "2026-06-01T15:00");
        args.addProperty("end", "2026-06-01T14:00");
        ToolResult result = createEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("after"));
    }

    @Test
    public void createEvent_providerError_isSurfaced() {
        dataSource.errorOnWrite = "Multiple writable calendars exist. Call calendar_list_calendars and pass an explicit calendar_id.";
        JsonObject args = new JsonObject();
        args.addProperty("title", "X");
        args.addProperty("start", "2026-06-01T15:00");
        ToolResult result = createEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("calendar_list_calendars"));
    }

    @Test
    public void createEvent_approvalDescription_containsTitleAndTime() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Dentist");
        args.addProperty("start", "2026-06-01T15:00");
        String description = createEventTool.getApprovalDescription(args);
        assertTrue(description.contains("Dentist"));
        assertTrue(description.contains("2026-06-01T15:00:00"));
    }

    @Test
    public void createEvent_allDayApprovalDescription_marksAllDay() {
        JsonObject args = new JsonObject();
        args.addProperty("title", "Holiday");
        args.addProperty("start", "2026-06-01");
        String description = createEventTool.getApprovalDescription(args);
        assertTrue(description.contains("(all-day)"));
    }

    @Test
    public void createEvent_invalidArgs_approvalDescriptionFallsBack() {
        String description = createEventTool.getApprovalDescription(new JsonObject());
        assertEquals("Create calendar event", description);
    }

    // ==================== calendar_update_event ====================

    @Test
    public void updateEvent_renamesEvent() {
        dataSource.events.add(event(5, "Old title", 0, HOUR_MS));

        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        args.addProperty("title", "New title");

        ToolResult result = updateEventTool.execute(args);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(5), dataSource.lastEventId);
        assertEquals("New title", dataSource.lastSpec.getTitle());
        assertNull(dataSource.lastSpec.getDtStart());
    }

    @Test
    public void updateEvent_movesTimedEvent() {
        dataSource.events.add(event(5, "Meeting", 0, HOUR_MS));

        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        args.addProperty("start", "2026-06-01T10:30");
        args.addProperty("end", "2026-06-01T11:00");

        ToolResult result = updateEventTool.execute(args);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(local("2026-06-01T10:30:00")),
                dataSource.lastSpec.getDtStart());
        assertEquals(Long.valueOf(local("2026-06-01T11:00:00")),
                dataSource.lastSpec.getDtEnd());
    }

    @Test
    public void updateEvent_missingEventId_returnsError() {
        ToolResult result = updateEventTool.execute(new JsonObject());
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("event_id"));
    }

    @Test
    public void updateEvent_unknownEvent_returnsNotFoundError() {
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 999);
        args.addProperty("title", "New title");
        ToolResult result = updateEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    public void updateEvent_nothingToUpdate_returnsError() {
        dataSource.events.add(event(5, "Meeting", 0, HOUR_MS));
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        ToolResult result = updateEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("at least one field"));
    }

    @Test
    public void updateEvent_endBeforeStart_returnsError() {
        dataSource.events.add(event(5, "Meeting", 0, HOUR_MS));
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        args.addProperty("start", "2026-06-01T12:00");
        args.addProperty("end", "2026-06-01T11:00");
        ToolResult result = updateEventTool.execute(args);
        assertFalse(result.isSuccess());
    }

    @Test
    public void updateEvent_providerError_isSurfaced() {
        dataSource.events.add(new CalendarEvent(5, 1, "Personal", "Series",
                0, HOUR_MS, false, "", "", "UTC", true));
        dataSource.errorOnWrite = "Event 5 is a recurring series.";

        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        args.addProperty("start", "2026-06-01T12:00");
        ToolResult result = updateEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("recurring"));
    }

    @Test
    public void updateEvent_approvalDescription_containsTitle() {
        dataSource.events.add(event(5, "Team sync", 0, HOUR_MS));
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        String description = updateEventTool.getApprovalDescription(args);
        assertTrue(description.contains("Team sync"));
        assertTrue(description.contains("5"));
    }

    // ==================== calendar_delete_event ====================

    @Test
    public void deleteEvent_existingEvent_isDeleted() {
        dataSource.events.add(event(5, "Old meeting", 0, HOUR_MS));

        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);

        ToolResult result = deleteEventTool.execute(args);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(5), dataSource.lastEventId);
        assertTrue(dataSource.deletedIds.contains(5L));
        JsonObject json = com.google.gson.JsonParser.parseString(result.getContent())
                .getAsJsonObject();
        assertTrue(json.get("deleted").getAsBoolean());
        assertEquals("Old meeting", json.get("title").getAsString());
    }

    @Test
    public void deleteEvent_unknownEvent_returnsNotFoundError() {
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 999);
        ToolResult result = deleteEventTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    public void deleteEvent_missingEventId_returnsError() {
        ToolResult result = deleteEventTool.execute(new JsonObject());
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("event_id"));
    }

    @Test
    public void deleteEvent_approvalDescription_marksRecurringSeries() {
        dataSource.events.add(new CalendarEvent(5, 1, "Personal", "Weekly sync",
                0, HOUR_MS, false, "", "", "UTC", true));
        JsonObject args = new JsonObject();
        args.addProperty("event_id", 5);
        String description = deleteEventTool.getApprovalDescription(args);
        assertTrue(description.contains("Weekly sync"));
        assertTrue(description.contains("ENTIRE recurring series"));
    }

    // ==================== Fake data source ====================

    private static class FakeDataSource implements CalendarDataSource {
        final List<CalendarInfo> calendars = new ArrayList<>();
        final List<CalendarEvent> events = new ArrayList<>();
        final List<Long> deletedIds = new ArrayList<>();

        long lastBegin;
        long lastEnd;
        Long lastCalendarId;
        String lastTextQuery;
        EventSpec lastSpec;
        Long lastEventId;
        String errorOnRead;
        String errorOnWrite;

        @Override
        public List<CalendarInfo> getCalendars() {
            if (errorOnRead != null) throw new CalendarException(errorOnRead);
            return calendars;
        }

        @Override
        public CalendarEvent getEvent(long eventId) {
            if (errorOnRead != null) throw new CalendarException(errorOnRead);
            for (CalendarEvent event : events) {
                if (event.getEventId() == eventId) return event;
            }
            return null;
        }

        @Override
        public List<CalendarEvent> queryEvents(long beginMillis, long endMillis,
                                               Long calendarId, String textQuery, int limit) {
            if (errorOnRead != null) throw new CalendarException(errorOnRead);
            lastBegin = beginMillis;
            lastEnd = endMillis;
            lastCalendarId = calendarId;
            lastTextQuery = textQuery;
            return events;
        }

        @Override
        public long createEvent(EventSpec spec) {
            if (errorOnWrite != null) throw new CalendarException(errorOnWrite);
            lastSpec = spec;
            return 42;
        }

        @Override
        public boolean updateEvent(long eventId, EventSpec spec) {
            if (errorOnWrite != null) throw new CalendarException(errorOnWrite);
            lastEventId = eventId;
            lastSpec = spec;
            return getEvent(eventId) != null;
        }

        @Override
        public boolean deleteEvent(long eventId) {
            if (errorOnWrite != null) throw new CalendarException(errorOnWrite);
            lastEventId = eventId;
            deletedIds.add(eventId);
            return true;
        }

        @Override
        public boolean hasWritableCalendar() {
            return true;
        }
    }
}
