package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarEvent;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarTimeUtil;
import io.finett.droidclaw.calendar.EventSpec;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Updates an existing calendar event. Only the fields passed by the LLM are
 * changed. Requires approval.
 *
 * <p>Phase-1 limitation: recurring series can have non-time fields updated;
 * moving a recurring event (changing start/end) is rejected with guidance.
 */
public class CalendarUpdateEventTool implements Tool {

    private static final String TOOL_NAME = "calendar_update_event";
    private static final long DAY_MS = 24 * 3600 * 1000L;

    private final CalendarDataSource dataSource;

    public CalendarUpdateEventTool(CalendarDataSource dataSource) {
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
                .addInteger("event_id",
                        "The id of the event to update (from calendar_list_events).", true)
                .addString("title", "New title.", false)
                .addString("start",
                        "New start time, ISO-8601. When moving a timed event, pass both "
                        + "'start' and 'end' to keep its duration.", false)
                .addString("end", "New end time, ISO-8601.", false)
                .addBoolean("all_day", "Change whether the event is all-day.", false)
                .addString("location", "New location (empty string clears it).", false)
                .addString("description", "New description (empty string clears it).", false)
                .addIntegerArray("reminders",
                        "Replace reminders with these lead times in minutes.", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Update an existing calendar event (move it, rename it, change location or "
                + "description). Only the provided fields are changed. Recurring events can "
                + "only have non-time fields updated. Requires user approval.",
                parameters
        );
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        try {
            Long eventId = CalendarArgs.optLong(arguments, "event_id");
            if (eventId == null) {
                return "Update calendar event";
            }
            String label = "event " + eventId;
            try {
                CalendarEvent existing = dataSource.getEvent(eventId);
                if (existing != null && !existing.getTitle().isEmpty()) {
                    label = "'" + existing.getTitle() + "' (event " + eventId + ")";
                }
            } catch (Exception e) {
                // Title is cosmetic for the dialog — ignore lookup failures
            }
            return "Update calendar " + label;
        } catch (Exception e) {
            return "Update calendar event";
        }
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        Long eventId;
        try {
            eventId = CalendarArgs.optLong(arguments, "event_id");
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
        if (eventId == null) {
            return ToolResult.error("Missing required parameter: event_id");
        }

        EventSpec spec = new EventSpec();
        try {
            String startStr = CalendarArgs.optString(arguments, "start");
            String endStr = CalendarArgs.optString(arguments, "end");
            Boolean allDay = CalendarArgs.optBoolean(arguments, "all_day");

            if (startStr != null) {
                CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse(startStr);
                boolean eventBecomesAllDay = allDay != null && allDay;
                long dtStart = eventBecomesAllDay || parsed.isDateOnly()
                        ? CalendarTimeUtil.toUtcMidnight(parsed.getEpochMillis())
                        : parsed.getEpochMillis();
                spec.setDtStart(dtStart);
            }
            if (endStr != null) {
                CalendarTimeUtil.ParsedTime parsed = CalendarTimeUtil.parse(endStr);
                boolean eventBecomesAllDay = allDay != null && allDay;
                if (eventBecomesAllDay || parsed.isDateOnly()) {
                    // End date is inclusive for all-day events
                    spec.setDtEnd(CalendarTimeUtil.toUtcMidnight(parsed.getEpochMillis()) + DAY_MS);
                } else {
                    spec.setDtEnd(parsed.getEpochMillis());
                }
            }
            if (spec.getDtStart() != null && spec.getDtEnd() != null
                    && spec.getDtEnd() <= spec.getDtStart()) {
                return ToolResult.error("'end' must be after 'start'");
            }

            spec.setTitle(CalendarArgs.optString(arguments, "title"));
            spec.setAllDay(allDay);
            spec.setLocation(CalendarArgs.optString(arguments, "location"));
            spec.setDescription(CalendarArgs.optString(arguments, "description"));
            spec.setReminders(CalendarArgs.optIntegerArray(arguments, "reminders"));

            if (spec.getTitle() == null && spec.getDtStart() == null && spec.getDtEnd() == null
                    && spec.getAllDay() == null && spec.getLocation() == null
                    && spec.getDescription() == null && spec.getReminders() == null) {
                return ToolResult.error("Nothing to update: provide at least one field to change");
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }

        try {
            boolean updated = dataSource.updateEvent(eventId, spec);
            if (!updated) {
                return ToolResult.error(
                        "Event " + eventId + " not found. Use calendar_list_events to find "
                        + "current event ids.");
            }
            JsonObject out = new JsonObject();
            out.addProperty("event_id", eventId);
            out.addProperty("updated", true);
            return ToolResult.success(out);
        } catch (CalendarException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
