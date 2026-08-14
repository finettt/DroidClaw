package io.finett.droidclaw.calendar;

import com.google.gson.JsonObject;

/**
 * Metadata about one device calendar (from {@code CalendarContract.Calendars}),
 * e.g. a Google account calendar or a locally synced calendar.
 */
public class CalendarInfo {
    private final long id;
    private final String displayName;
    private final String accountName;
    private final String accountType;
    private final int accessLevel;
    private final boolean visible;
    private final String timeZone;

    public CalendarInfo(long id, String displayName, String accountName, String accountType,
                        int accessLevel, boolean visible, String timeZone) {
        this.id = id;
        this.displayName = displayName != null ? displayName : "";
        this.accountName = accountName != null ? accountName : "";
        this.accountType = accountType != null ? accountType : "";
        this.accessLevel = accessLevel;
        this.visible = visible;
        this.timeZone = timeZone != null ? timeZone : "";
    }

    public long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getTimeZone() {
        return timeZone;
    }

    /**
     * Whether the agent may create/update/delete events in this calendar.
     * Requires at least contributor access (CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR = 500).
     */
    public boolean isWritable() {
        return accessLevel >= 500;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("calendar_id", id);
        json.addProperty("name", displayName);
        json.addProperty("account_name", accountName);
        json.addProperty("account_type", accountType);
        json.addProperty("writable", isWritable());
        json.addProperty("visible", visible);
        if (!timeZone.isEmpty()) {
            json.addProperty("timezone", timeZone);
        }
        return json;
    }
}
