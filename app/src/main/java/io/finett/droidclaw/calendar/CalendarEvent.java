package io.finett.droidclaw.calendar;

import com.google.gson.JsonObject;

/**
 * One calendar event occurrence (from {@code CalendarContract.Instances}).
 * Recurring events appear once per occurrence within the queried window.
 */
public class CalendarEvent {
    /** Cap applied to long text fields so tool output stays bounded. */
    private static final int MAX_FIELD_LENGTH = 500;

    private final long eventId;
    private final long calendarId;
    private final String calendarName;
    private final String title;
    private final long startMillis;
    private final long endMillis;
    private final boolean allDay;
    private final String location;
    private final String description;
    private final String timezone;
    private final boolean recurring;

    public CalendarEvent(long eventId, long calendarId, String calendarName, String title,
                         long startMillis, long endMillis, boolean allDay,
                         String location, String description, String timezone,
                         boolean recurring) {
        this.eventId = eventId;
        this.calendarId = calendarId;
        this.calendarName = calendarName != null ? calendarName : "";
        this.title = title != null ? title : "";
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.allDay = allDay;
        this.location = location != null ? location : "";
        this.description = description != null ? description : "";
        this.timezone = timezone != null ? timezone : "";
        this.recurring = recurring;
    }

    public long getEventId() {
        return eventId;
    }

    public long getCalendarId() {
        return calendarId;
    }

    public String getCalendarName() {
        return calendarName;
    }

    public String getTitle() {
        return title;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("event_id", eventId);
        json.addProperty("calendar_id", calendarId);
        if (!calendarName.isEmpty()) {
            json.addProperty("calendar", calendarName);
        }
        json.addProperty("title", truncate(title));
        if (allDay) {
            json.addProperty("all_day", true);
            json.addProperty("date", CalendarTimeUtil.format(startMillis));
        } else {
            json.addProperty("start", CalendarTimeUtil.format(startMillis));
            json.addProperty("end", CalendarTimeUtil.format(endMillis));
        }
        if (!location.isEmpty()) {
            json.addProperty("location", truncate(location));
        }
        if (!description.isEmpty()) {
            json.addProperty("description", truncate(description));
        }
        if (recurring) {
            json.addProperty("recurring", true);
        }
        return json;
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_FIELD_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_FIELD_LENGTH) + "...";
    }
}
