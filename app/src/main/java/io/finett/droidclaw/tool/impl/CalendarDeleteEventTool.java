package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarEvent;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarTimeUtil;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Deletes a calendar event. For recurring events the whole series is removed.
 * Requires approval.
 */
public class CalendarDeleteEventTool implements Tool {

    private static final String TOOL_NAME = "calendar_delete_event";

    private final CalendarDataSource dataSource;

    public CalendarDeleteEventTool(CalendarDataSource dataSource) {
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
                        "The id of the event to delete (from calendar_list_events).", true)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Delete a calendar event. For recurring events the entire series is "
                + "deleted. Requires user approval.",
                parameters
        );
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        try {
            Long eventId = CalendarArgs.optLong(arguments, "event_id");
            if (eventId == null) {
                return "Delete calendar event";
            }
            try {
                CalendarEvent existing = dataSource.getEvent(eventId);
                if (existing != null) {
                    StringBuilder sb = new StringBuilder("Delete calendar event: '")
                            .append(existing.getTitle()).append("' at ")
                            .append(CalendarTimeUtil.format(existing.getStartMillis()));
                    if (existing.isRecurring()) {
                        sb.append(" (ENTIRE recurring series)");
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                // Fall through to the generic description
            }
            return "Delete calendar event " + eventId;
        } catch (Exception e) {
            return "Delete calendar event";
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

        try {
            CalendarEvent existing = dataSource.getEvent(eventId);
            if (existing == null) {
                return ToolResult.error(
                        "Event " + eventId + " not found. Use calendar_list_events to find "
                        + "current event ids.");
            }
            boolean deleted = dataSource.deleteEvent(eventId);
            if (!deleted) {
                return ToolResult.error("Failed to delete event " + eventId);
            }
            JsonObject out = new JsonObject();
            out.addProperty("event_id", eventId);
            out.addProperty("deleted", true);
            out.addProperty("title", existing.getTitle());
            return ToolResult.success(out);
        } catch (CalendarException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
