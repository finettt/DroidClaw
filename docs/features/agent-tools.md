# Agent Tools: App & Screen Control

DroidClaw's agent can operate your phone, not just answer questions. This page
documents the app-control and screen-control tool families and the shell tool
they are often combined with.

> **Requirement:** the screen tools drive the UI through the Android
> Accessibility service. Grant the accessibility permission during onboarding
> (or in Settings) before using them.

## App tools

| Tool | What it does |
|------|--------------|
| `list_apps` | Lists installed apps (name, package name, system/user). Pass `show_system=false` to see only user-installed apps. |
| `open_app` | Launches an app by package name. |

## Screen tools

| Tool | What it does |
|------|--------------|
| `screen_get_ui_tree` | Reads the current screen's accessibility tree. Returns each element's text, resource id, bounds, and `centerX`/`centerY` — the starting point for any interaction. |
| `screen_tap` | Taps an element. Target by `x`+`y` coordinates (use `centerX`/`centerY` from the UI tree), by `resource_id`, or by visible `text`. |
| `screen_swipe` | Swipes between two points (scrolling, paging, pull-to-refresh). |
| `screen_type_text` | Types text into the focused input field. |
| `screen_perform_action` | System navigation: `back`, `home`, `recents`, `notifications`, `quick_settings`, `lock_screen`. |

### Typical flow

1. `screen_get_ui_tree` — see what is on screen.
2. `screen_tap` / `screen_type_text` — interact using the tree's coordinates or ids.
3. Repeat until the task is done; use `screen_perform_action` to navigate.

Sensitive or externally visible actions are gated by the same approval flow as
every other tool — see [Settings](../user/settings.md) for tool approval modes.

## Shell tool

The `execute_shell` tool runs shell commands with two selectable backends:

- **Local** — commands run on the phone itself, inside the app's sandbox.
- **SSH** — commands run on a remote host you configure (host, user, auth).
  Useful when the heavy lifting belongs on a server, while the agent still
  lives on your phone.

Configure the backend in [Settings](../user/settings.md). Commands go through
the exec planner and allowlist policy before they run, so destructive
operations require approval.

## Related docs

- [Settings](../user/settings.md)
- [First steps](../user/first-steps.md)
