# Updates

DroidClaw checks GitHub for new releases and tells you when one is available.
When a newer stable release exists, the app shows a dialog with the release
notes and a link to the release page, where you can download the APK. The app
never downloads or installs anything on its own.

## What it does

- On startup, the app reads `https://api.github.com/repos/finettt/DroidClaw/releases/latest`.
- If the latest stable tag is newer than the version you are running, a dialog
  appears with the release title, notes (truncated to about 1500 characters),
  and two buttons:
  - **Open release** — opens the release page in your browser, where the APK can
    be downloaded.
  - **Later** — dismisses the dialog.
- On the Info screen (drawer → Info), a banner shows when a newer release is
  known but the startup dialog was not shown, and a **Check for updates** button
  runs a manual check.

## How often it checks

- The startup check runs at most once every 24 hours, tracked by a local
  preference (`last_update_check_at`).
- The **Check for updates** button on the Info screen always runs, regardless of
  the 24h throttle.
- If GitHub rate-limits the request (HTTP 403/429), the app shows "Update check
  failed" and you can try again later. If the repo has no full releases yet, the
  app treats that as "up to date".

## Turning it off

The check is enabled by default. To stop the automatic startup check, turn off
**Check for updates automatically** on the Info screen. When off, the startup
path makes no network requests. The manual **Check for updates** button still
works.

## Local builds

In local/debug builds the app version is a git hash or `dev` rather than a
CalVer tag, so the startup check is skipped entirely — the app never phones home
from a local build. The manual **Check for updates** button still shows the
latest release.

## Privacy

The app contacts `https://api.github.com/repos/finettt/DroidClaw/releases/latest`
only, to compare the latest release against the version you are running. Nothing
is sent back to DroidClaw.

## Versions

DroidClaw tags releases with CalVer (`YYYY.MM.DD`, with a `.N` suffix for
same-day re-releases). The app compares release tags numerically, segment by
segment, so `2026.10.10.1` is newer than `2026.10.10`.
