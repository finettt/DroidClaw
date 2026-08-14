package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarEvent;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarTimeUtil;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Queries calendar events in a time window via the CalendarContract
 * Instances table, so recurring events are expanded to occurrences.
 * Read-only.
 */
public class CalendarListEventsTool implements Tool {

    private static final String TOOL_NAME = "calendar_list_events";
    private static final int DEFAULT_WINDOW_DAYS = 7;
    private static final int MAX_EVENTS = 200;

    private final CalendarDataSource dataSource;

    public CalendarListEventsTool(CalendarDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("start",
                        "Start of the time window, ISO-8601 (e.g. '2026-06-01T09:00'). "
                        + "Defaults to the start of today.", false)
                .addString("end",
                        "End of the time window, ISO-8601. Defaults to " + DEFAULT_WINDOW_DAYS
                        + " days after the start.", false)
                .addString("query",
                        "Optional text to filter events by title, location or description "
                        + "(case-insensitive substring match).", false)
                .addInteger("calendar_id",
                        "Optional calendar id to restrict the search to one calendar "
                        + "(see calendar_list_calendars).", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "List calendar events from the device calendar within a time window. "
                + "Recurring events are expanded to individual occurrences. Returns each "
                + "event's event_id, title, start/end times (ISO-8601 with timezone offset), "
                + "location, description and calendar. Use 'start'/'end' to bound the window "
                + "(default: today through the next " + DEFAULT_WINDOW_DAYS + " days).",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            String startStr = CalendarArgs.optString(arguments, "start");
            String endStr = CalendarArgs.optString(arguments, "end");
            String query = CalendarArgs.optString(arguments, "query");
            Long calendarId = CalendarArgs.optLong(arguments, "calendar_id");

            long startMillis = startStr != null
                    ? CalendarTimeUtil.parse(startStr).getEpochMillis()
                    : CalendarTimeUtil.startOfToday();
            long endMillis = endStr != null
                    ? CalendarTimeUtil.parse(endStr).getEpochMillis()
                    : startMillis + (long) DEFAULT_WINDOW_DAYS * 24 * 3600 * 1000;

            if (endMillis <= startMillis) {
                return ToolResult.error("'end' must be after 'start'");
            }

            List<CalendarEvent> events = dataSource.queryEvents(
                    startMillis, endMillis, calendarId, query, MAX_EVENTS);

            JsonArray arr = new JsonArray();
            for (CalendarEvent event : events) {
                arr.add(event.toJson());
            }
            JsonObject out = new JsonObject();
            out.addProperty("count", events.size());
            out.addProperty("window_start", CalendarTimeUtil.format(startMillis));
            out.addProperty("window_end", CalendarTimeUtil.format(endMillis));
            out.add("events", arr);
            return ToolResult.success(out);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (CalendarException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
