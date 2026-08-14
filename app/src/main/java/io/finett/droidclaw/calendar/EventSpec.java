package io.finett.droidclaw.calendar;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification for creating or updating a calendar event. All fields except
 * {@code title}/{@code dtStart} on creation are nullable, meaning "leave
 * unchanged" for updates.
 */
public class EventSpec {
    private Long calendarId;
    private String title;
    private Long dtStart;
    private Long dtEnd;
    private Boolean allDay;
    private String location;
    private String description;
    private List<Integer> reminders;
    private String timezone;

    public Long getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(Long calendarId) {
        this.calendarId = calendarId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getDtStart() {
        return dtStart;
    }

    public void setDtStart(Long dtStart) {
        this.dtStart = dtStart;
    }

    public Long getDtEnd() {
        return dtEnd;
    }

    public void setDtEnd(Long dtEnd) {
        this.dtEnd = dtEnd;
    }

    public Boolean getAllDay() {
        return allDay;
    }

    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Integer> getReminders() {
        return reminders;
    }

    public void setReminders(List<Integer> reminders) {
        this.reminders = reminders != null ? new ArrayList<>(reminders) : null;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
