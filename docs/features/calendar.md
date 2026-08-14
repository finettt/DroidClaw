# Calendar Tools

DroidClaw's agent can read and manage events on your device calendar —
any calendar synced to the phone through Android's normal calendar system
(Google, CalDAV apps, local calendars, …). No extra accounts or services
are involved: the tools talk to the built-in `CalendarContract` provider.

> **Privacy note:** calendar content is personal data. When these tools are
> enabled, event titles, times, locations and descriptions can enter the
> LLM context. The feature is off by default.

## Enabling

1. Open **Settings → Agent settings**.
2. Turn on **Calendar access**. Android will ask for the `READ_CALENDAR` /
   `WRITE_CALENDAR` runtime permissions — grant them.
3. If you deny the permission, the switch stays off and the tools are not
   offered to the agent.

Disabling the switch (or revoking the permission in Android settings) hides
the tools completely; the agent no longer sees or can call them.

## Tools

| Tool | What it does | Approval |
|------|--------------|----------|
| `calendar_list_calendars` | Lists the calendars on the device (id, name, account, writable). | default |
| `calendar_list_events` | Lists events in a time window, with recurring events expanded to occurrences. | default |
| `calendar_create_event` | Creates an event (optionally all-day, with location, description, reminders). | required |
| `calendar_update_event` | Changes fields of an existing event (title, time, location, …). | required |
| `calendar_delete_event` | Deletes an event. | required |

"required" means the write tools always go through the approval prompt
(unless you override them per-tool under **Tool Approvals**); the read tools
follow your normal approval mode.

### Arguments

- **`start` / `end`** — ISO-8601: `2025-06-20T14:00:00`, optionally with an
  offset (`…+02:00`, `…Z`). Times without an offset use the device timezone.
  A bare date (`2025-06-20`) implies an all-day event.
- **`query`** (`calendar_list_events`) — free-text filter matched against
  title, location and description (case-insensitive).
- **`calendar_id`** — target calendar. If omitted on create and exactly one
  writable calendar exists, that one is used; otherwise the agent must list
  calendars first and pick one.
- **`all_day`** — all-day events store UTC-midnight boundaries; the tools
  handle the conversion for you.
- **`reminders`** — list of lead times in minutes before the event, e.g.
  `[30, 1440]` = 30 minutes and 1 day ahead.
- **`event_id`** (update/delete) — the id returned by `calendar_list_events`.

### Recurring events

`calendar_list_events` expands recurring series into individual occurrences
inside the requested window. Update and delete act on the **whole series**;
editing a single occurrence of a recurring series is not supported (and time
changes on recurring events are rejected with an explanatory error).

## Typical flows

- *"What's on my calendar today?"* → `calendar_list_events` with today's window.
- *"Book a dentist appointment Friday 14:00–15:00"* → the agent lists
  calendars (if needed), then `calendar_create_event`; you approve the write.
- *"Move tomorrow's standup to 10:30"* → `calendar_list_events` to find the
  event id, then `calendar_update_event` with the new start/end.
- *"Cancel the sync meeting"* → find it, then `calendar_delete_event`.
- Combined with cron/heartbeat: *"Every morning at 08:00, send me my agenda
  for today."*

## See also

- [Agent Tools: App & Screen Control](agent-tools.md)
- [Settings and Configuration](../user/settings.md)
