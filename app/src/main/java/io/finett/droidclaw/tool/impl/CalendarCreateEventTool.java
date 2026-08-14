package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarTimeUtil;
import io.finett.droidclaw.calendar.EventSpec;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Creates a calendar event on the device calendar. Destructive in the sense
 * that it mutates user data visible to other apps, so it always requires
 * approval.
 */
public class CalendarCreateEventTool implements Tool {

    private static final String TOOL_NAME = "calendar_create_event";
    private static final long HOUR_MS = 3600 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    private final CalendarDataSource dataSource;

    public CalendarCreateEventTool(CalendarDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("title", "Event title.", true)
                .addString("start",
                        "Start time, ISO-8601 (e.g. '2026-06-01T15:00' or with explicit "
                        + "offset '2026-06-01T15:00:00+02:00'). Times without an offset "
                        + "are interpreted in the device timezone. A bare date "
                        + "('2026-06-01') creates an all-day event.", true)
                .addString("end",
                        "End time, ISO-8601. Optional: defaults to start + 1 hour for "
                        + "timed events, or the same day for all-day events. For all-day "
                        + "events the end DATE is inclusive (the last day of the event).",
                        false)
                .addBoolean("all_day", "Create an all-day event (default: false).", false)
                .addInteger("calendar_id",
                        "Target calendar id (see calendar_list_calendars). Optional when "
                        + "exactly one writable calendar exists.", false)
                .addString("location", "Event location.", false)
                .addString("description", "Event description/notes.", false)
                .addIntegerArray("reminders",
                        "Reminder lead times in minutes before the event "
                        + "(e.g. [30] or [1440, 60]). Optional; when omitted the "
                        + "calendar's own default applies.", false)
                .addString("timezone",
                        "IANA timezone id for the event (e.g. 'Europe/Berlin'). "
                        + "Defaults to the device timezone.", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Create a new event in the device calendar. Requires user approval. "
                + "When several writable calendars exist you must pass calendar_id — "
                + "call calendar_list_calendars first.",
                parameters
        );
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        try {
            Resolved resolved = resolve(arguments);
            StringBuilder sb = new StringBuilder("Create calendar event: \"")
                    .append(resolved.title).append("\" from ")
                    .append(CalendarTimeUtil.format(resolved.dtStart));
            if (!resolved.allDay) {
                sb.append(" to ").append(CalendarTimeUtil.format(resolved.dtEnd));
            } else {
                sb.append(" (all-day)");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Create calendar event";
        }
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        Resolved resolved;
        try {
            resolved = resolve(arguments);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }

        EventSpec spec = new EventSpec();
        spec.setCalendarId(CalendarArgs.optLong(arguments, "calendar_id"));
        spec.setTitle(resolved.title);
        spec.setDtStart(resolved.dtStart);
        spec.setDtEnd(resolved.dtEnd);
        spec.setAllDay(resolved.allDay);
        spec.setLocation(CalendarArgs.optString(arguments, "location"));
        spec.setDescription(CalendarArgs.optString(arguments, "description"));
        spec.setTimezone(CalendarArgs.optString(arguments, "timezone"));
        List<Integer> reminders = CalendarArgs.optIntegerArray(arguments, "reminders");
        spec.setReminders(reminders);

        try {
            long eventId = dataSource.createEvent(spec);
            JsonObject out = new JsonObject();
            out.addProperty("event_id", eventId);
            out.addProperty("title", resolved.title);
            out.addProperty("start", CalendarTimeUtil.format(resolved.dtStart));
            out.addProperty("end", CalendarTimeUtil.format(resolved.dtEnd));
            if (resolved.allDay) {
                out.addProperty("all_day", true);
            }
            if (reminders != null && !reminders.isEmpty()) {
                out.addProperty("reminders_minutes", reminders.toString());
            }
            return ToolResult.success(out);
        } catch (CalendarException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    /** Resolved event times, shared by execute() and the approval description. */
    private static class Resolved {
        String title;
        long dtStart;
        long dtEnd;
        boolean allDay;
    }

    private static Resolved resolve(JsonObject arguments) {
        Resolved resolved = new Resolved();
        resolved.title = CalendarArgs.reqString(arguments, "title");
        String startStr = CalendarArgs.reqString(arguments, "start");
        String endStr = CalendarArgs.optString(arguments, "end");
        Boolean allDayArg = CalendarArgs.optBoolean(arguments, "all_day");

        CalendarTimeUtil.ParsedTime startParsed = CalendarTimeUtil.parse(startStr);
        resolved.allDay = (allDayArg != null && allDayArg) || startParsed.isDateOnly();

        if (resolved.allDay) {
            resolved.dtStart = CalendarTimeUtil.toUtcMidnight(startParsed.getEpochMillis());
            if (endStr == null) {
                resolved.dtEnd = resolved.dtStart + DAY_MS;
            } else {
                CalendarTimeUtil.ParsedTime endParsed = CalendarTimeUtil.parse(endStr);
                long endMidnight = CalendarTimeUtil.toUtcMidnight(endParsed.getEpochMillis());
                if (endMidnight < resolved.dtStart) {
                    throw new IllegalArgumentException("'end' date is before 'start' date");
                }
                // End date is inclusive for all-day events -> exclusive DTEND is next midnight
                resolved.dtEnd = endMidnight + DAY_MS;
            }
        } else {
            resolved.dtStart = startParsed.getEpochMillis();
            if (endStr == null) {
                resolved.dtEnd = resolved.dtStart + HOUR_MS;
            } else {
                resolved.dtEnd = CalendarTimeUtil.parse(endStr).getEpochMillis();
                if (resolved.dtEnd <= resolved.dtStart) {
                    throw new IllegalArgumentException("'end' must be after 'start'");
                }
            }
        }
        return resolved;
    }
}
