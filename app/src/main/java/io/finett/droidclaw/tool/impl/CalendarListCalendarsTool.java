package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.calendar.CalendarDataSource;
import io.finett.droidclaw.calendar.CalendarException;
import io.finett.droidclaw.calendar.CalendarInfo;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Lists the calendars available on the device (all synced accounts).
 * Read-only. The output tells the agent which calendars are writable,
 * which is required before creating events when several exist.
 */
public class CalendarListCalendarsTool implements Tool {

    private static final String TOOL_NAME = "calendar_list_calendars";

    private final CalendarDataSource dataSource;

    public CalendarListCalendarsTool(CalendarDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder().build();
        return new ToolDefinition(
                TOOL_NAME,
                "List all calendars on the device (from all synced accounts, e.g. Google). "
                + "Returns each calendar's calendar_id, name, account, and whether it is "
                + "writable. Call this before creating events when you need to choose a "
                + "calendar.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            List<CalendarInfo> calendars = dataSource.getCalendars();
            JsonArray arr = new JsonArray();
            for (CalendarInfo calendar : calendars) {
                arr.add(calendar.toJson());
            }
            JsonObject out = new JsonObject();
            out.addProperty("count", calendars.size());
            out.add("calendars", arr);
            return ToolResult.success(out);
        } catch (CalendarException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
