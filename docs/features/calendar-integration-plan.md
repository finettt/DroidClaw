# Calendar Integration — Implementation Plan

Status: **implemented** (Phases 1–3; free-time finder deferred as noted in §7)
Target: agent tools backed by the Android CalendarProvider (`CalendarContract`)

> Implementation summary: `READ_CALENDAR`/`WRITE_CALENDAR` manifest permissions,
> `calendarEnabled` flag in `AgentConfig`/Settings (Agent settings → Calendar Access,
> with runtime permission prompt via `CalendarPermissionHelper`), a `calendar` package
> (`CalendarRepository` over `CalendarContract`, models, `CalendarTimeUtil`), and five
> tools (`calendar_list_calendars`, `calendar_list_events`, `calendar_create_event`,
> `calendar_update_event`, `calendar_delete_event`) registered in `ToolRegistry` only
> when the setting is on and permissions are granted. Write tools require approval.
> Unit tests: `CalendarTimeUtilTest`, `CalendarRepositoryTest` (fake provider),
> `CalendarToolsTest`, plus gating tests in `ToolRegistryTest`. Instrumented:
> `CalendarRepositoryInstrumentedTest` (real device provider; throwaway sync-adapter
> calendar, API 28+ via `GrantPermissionRule`, auto-skips on providers that reject
> third-party calendar creation) — run with `./gradlew connectedAndroidTest`.

## 1. Goal

Let the DroidClaw agent read and manage the device calendar through the normal
tool-calling loop, e.g.:

- "What's on my calendar today / this week?"
- "Book a dentist appointment Friday 14:00–15:00 in my personal calendar."
- "Move tomorrow's standup to 10:30." / "Cancel the sync meeting."
- "When am I free Thursday afternoon?"
- Combined with existing cron/heartbeat: "Every morning at 08:00, send me my agenda."

## 2. Integration target: Android CalendarProvider

Use the system `CalendarContract` content provider (`READ_CALENDAR` /
`WRITE_CALENDAR` runtime permissions). One API covers Google Calendar,
Samsung/Etar, DAVx5 (CalDAV sync), Nextcloud, etc. — whatever the user
already syncs to the device. No extra SDK, no OAuth, works fully offline,
min SDK 24 is fine.

Not in first scope (possible later phases):

- Direct CalDAV client (would mirror the `SearxngSearchTool` "self-hosted
  endpoint" pattern).
- Google Calendar REST API + OAuth (heavy, network-only, redundant while
  Google accounts already sync into the provider).

Reminders are inserted as `CalendarContract.Reminders` rows — the calendar
app fires the alarms, so DroidClaw needs no alarm permissions.

## 3. New components

### 3.1 `calendar` package (data layer)

| Class | Purpose |
|---|---|
| `calendar/CalendarRepository.java` | Thin wrapper over `CalendarContract`: list calendars, query events (via `Instances` for recurrence expansion), insert/update/delete events, insert reminders. All cursor/URI handling lives here. |
| `calendar/CalendarEvent.java` | Plain model: id, calendarId, calendarName/account, title, start/end (epoch millis + ISO-8601 with offset), allDay, location, description, availability. Has `toJson()` for tool output. |
| `calendar/CalendarInfo.java` | Calendar metadata: id, displayName, accountName, color, access level (`CAL_ACCESS_*` → writable?), `visible`. |
| `calendar/CalendarTimeUtil.java` | Parse/validate LLM-supplied ISO-8601 (`yyyy-MM-dd'T'HH:mm(:ss)` with optional offset; bare date ⇒ all-day), format output, default timezone = device TZ. |
| `util/CalendarPermissionHelper.java` | Mirrors `NotificationPermissionHelper`: check/request `READ_CALENDAR`+`WRITE_CALENDAR` (API 23+ runtime permissions). |

Design for testability: tools receive a small `CalendarDataSource` interface
implemented by `CalendarRepository`; unit tests inject fakes, instrumented
tests hit the real provider.

### 3.2 Tools (`tool/impl/`)

Follow the `OpenAppTool`/`CreateTaskTool` pattern (Context-injecting ctor,
`ParametersBuilder` definitions, `getApprovalDescription`).

| Tool | Approval | Notes |
|---|---|---|
| `calendar_list_calendars` | DEFAULT (read) | Returns writable calendars so the LLM can pick a `calendar_id` before creating. |
| `calendar_list_events` | DEFAULT (read) | Args: `start`/`end` (ISO-8601, default = today / next 7 days), optional `query` (text match on title/location/description), optional `calendar_id`. Uses `Instances` so recurring events expand correctly. Returns JSON array of `CalendarEvent`. |
| `calendar_create_event` | **requiresApproval = true** | Args: `title` (req), `start` (req), `end` (optional ⇒ 1 h default), `calendar_id` (optional ⇒ single writable calendar, else error telling the LLM to call `calendar_list_calendars`), `all_day`, `location`, `description`, `reminders` (int minutes array, default from calendar setting or none). |
| `calendar_update_event` | **requiresApproval = true** | Args: `event_id` (req) + any subset of create fields. For recurring instances: optional `scope: "this_event"\|"all_events"` — first implementation: only non-recurring + full-series updates; instance exceptions are a phase-3 enhancement. |
| `calendar_delete_event` | **requiresApproval = true** | Args: `event_id` (req). Recurring: whole series only in phase 1. |
| `calendar_find_free_time` (stretch) | DEFAULT | Query busy intervals in window, return free slots. Pure computation over `Instances` results — no extra permission. |

Reads follow the global `requireApproval` toggle and are individually
overridable in the existing Tool Approval settings screen
(`ToolApprovalMode.DEFAULT / ALWAYS_APPROVE / ALWAYS_REJECT`) — no new
machinery needed since overrides are keyed by tool name.

Error contract: permission missing ⇒ `ToolResult.error("Calendar permission
not granted. Ask the user to enable it in Settings → Agent → Calendar
access.")`; no writable calendar / not found ⇒ instructive errors so the LLM
self-corrects.

### 3.3 Settings plumbing

- `model/AgentConfig.java`: add `calendarEnabled` (default `false`) +
  getter/setter, mirroring `screenControlEnabled`.
- `util/SettingsManager.java`: add to `parseAgentConfig` /
  `serializeAgentConfig` (`json.optBoolean("calendarEnabled", false)`).
- `tool/ToolRegistry.registerTools()`: gated registration, mirroring the
  screen-control block:

```java
// Calendar tools — only when enabled in settings AND permission granted
if (settingsManager != null
        && settingsManager.getAgentConfig().isCalendarEnabled()
        && CalendarPermissionHelper.hasCalendarPermission(context)) {
    CalendarRepository repo = new CalendarRepository(context);
    registerTool(new CalendarListCalendarsTool(repo));
    registerTool(new CalendarListEventsTool(repo));
    registerTool(new CalendarCreateEventTool(repo));
    registerTool(new CalendarUpdateEventTool(repo));
    registerTool(new CalendarDeleteEventTool(repo));
}
```

Because `ToolRegistry` is rebuilt per agent run (ChatFragment,
`AgentExecutionService`, `BaseTaskWorker`), toggling the setting or granting
the permission takes effect on the next run with no extra invalidation work.
Background cron/heartbeat agents automatically get the same tools.

- `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

- UI: `AgentSettingsFragment` — new `switch_calendar_access` block in
  `fragment_agent_settings.xml` (copy the screen-control section, ~line 507).
  On enabling: request the runtime permission first
  (`CalendarPermissionHelper.requestCalendarPermissions(activity)`); if
  denied, keep the switch off and show a Snackbar explaining why.

### 3.4 System prompt / discoverability

`LlmApiService` already injects the current date/time — the agent can reason
about "today/tomorrow" and translate to ISO timestamps. No prompt change
strictly required; optionally mention calendar tools in the built-in skills
docs (see §5).

## 4. Key implementation details

### 4.1 CalendarContract specifics

- **Calendars**: query `CalendarContract.Calendars` for `_ID`,
  `CALENDAR_DISPLAY_NAME`, `ACCOUNT_NAME`, `CALENDAR_ACCESS_LEVEL`
  (writable ⇔ ≥ `CAL_ACCESS_CONTRIBUTOR`), `VISIBLE`, `TIME_ZONE`.
- **Reading events**: query `CalendarContract.Instances` (not `Events`) with
  `BEGIN/END` window so recurring events expand. Join back to calendars for
  display name. Handle `Events.DTSTART == null` exception rows.
- **All-day events**: set `ALL_DAY=1`, start/end are UTC midnight; convert
  user's local date ↔ UTC midnight at the edges.
- **Creating**: `ContentValues` with `CALENDAR_ID`, `TITLE`, `DTSTART`,
  either `DTEND` (non-recurring) or `RRULE`+`DURATION` (recurring, phase 3),
  `EVENT_TIMEZONE` = device timezone id.
- **Updating**: `ContentResolver.update(Event.CONTENT_URI, id)`; moving an
  event = update `DTSTART`/`DTEND`.
- **Deleting**: `delete(ContentUris.withAppendedId(Events.CONTENT_URI, id))`.
- **Reminders**: insert into `Reminders` with `MINUTES` + `METHOD_ALERT`.

### 4.2 Privacy & safety

- Calendar content is personal data that enters the LLM context — the
  settings toggle wording should say so ("Agent can read your calendar
  events").
- Writes always default to approval; power users can set
  `calendar_create_event` to `ALWAYS_APPROVE` via the existing per-tool
  approval overrides.
- Tools never expose events when disabled/unpermitted: they are simply not
  registered (and `executeTool` errors defensively).

## 5. Documentation & skills

- `docs/features/calendar.md`: user-facing doc in the style of
  `docs/features/agent-tools.md` (tool table, permission flow, example
  prompts, privacy note). Link from `docs/user/settings.md`.
- Optional: built-in skill asset `.agent/skills/calendar_assistant/SKILL.md`
  with prompting guidance (time-window defaults, list calendars before
  create, ISO-8601 format) so the agent uses the tools idiomatically.

## 6. Testing plan

Existing infra: JUnit4 + Robolectric + Mockito (`testImplementation`),
Espresso instrumented tests.

Unit (Robolectric):
- `CalendarTimeUtilTest` — parse/format edge cases: naive datetime (device
  TZ), explicit offsets, bare dates ⇒ all-day, invalid strings rejected.
- `CalendarRepositoryTest` — seed Robolectric's content resolver with
  `CalendarContract` rows; verify query windows, recurrence expansion,
  insert/update/delete round-trips, all-day handling.
- `CalendarToolsTest` — definition validity, required-arg validation,
  `requiresApproval()` semantics, approval descriptions, permission-missing
  error text, JSON output shape (mirror `ListAppsToolTest`/`OpenAppToolTest`).
- Extend `AgentConfigTest` + `SettingsManagerTest` for `calendarEnabled`
  round-trip.

Instrumented:
- `CalendarRepositoryInstrumentedTest` — create a dedicated throwaway
  calendar account (`ACCOUNT_TYPE` local), CRUD events, clean up in
  `@After`; guard with `@SdkSuppress` where provider behavior differs.

## 7. Phased delivery

| Phase | Scope | Effort est. |
|---|---|---|
| **1. Plumbing** | Manifest permissions, `CalendarPermissionHelper`, `AgentConfig.calendarEnabled`, SettingsManager parse/serialize, settings switch + permission flow, gated (empty) registration block. | ~0.5 day |
| **2. Read path** | `CalendarRepository` read APIs, `CalendarEvent`/`CalendarInfo` models, `CalendarTimeUtil`, `calendar_list_calendars`, `calendar_list_events`, unit tests. | 1–1.5 days |
| **3. Write path** | `calendar_create_event`, `calendar_update_event`, `calendar_delete_event`, reminders support, approval descriptions, tests. | 1–1.5 days |
| **4. Polish** | `calendar_find_free_time`, recurring-instance updates (EXDATE/exceptions), docs, optional skill asset. | 1 day |

## 8. Risks & open questions

1. **Recurring-event edits** ("move just this occurrence") need
   `ORIGINAL_ID`/exception handling — defer to phase 4; phase 1 tools should
   detect `RRULE != null` and tell the LLM it can only modify the whole
   series.
2. **No local calendar on some ROMs** — creation may require an existing
   writable calendar; `calendar_list_calendars` output must make this
   explicit, and create errors should guide the user.
3. **Permission UX on enabling**: Android shows its system dialog; if the
   user chose "don't ask again", deep-link to app settings (pattern already
   used elsewhere? — if not, simple Snackbar suffices for v1).
4. **Read tool privacy**: consider truncating long descriptions / attendee
   lists in tool output to bound context size and leakage (v1: cap each
   field, e.g. description at 500 chars).
5. **Timezones for travelling users**: v1 uses device timezone everywhere;
   explicit `timezone` param on create can be a later addition.
