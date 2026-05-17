# File Browser

The File Browser lets you explore, read, and open files stored in DroidClaw's sandboxed workspace — the same filesystem the agent reads and writes during its tasks.

## Opening the File Browser

1. Tap the **hamburger menu** (☰) in the top-left corner.
2. Select **Files** from the navigation drawer.

The browser opens at the workspace root (`/`).

---

## Understanding the Workspace Layout

DroidClaw maintains a private, sandboxed directory on your device. No other app can access it, and the agent cannot reach outside of it. The workspace is structured as follows:

| Path | Purpose |
|---|---|
| `home/` | General-purpose home directory |
| `home/documents/` | User documents |
| `home/scripts/` | Python and shell scripts |
| `home/notes/` | Text notes |
| `tmp/` | Temporary files (cleared between sessions) |
| `uploads/` | Files you attach to the chat (max 50 MB each) |
| `.agent/` | Agent identity and configuration (read-only for the agent) |
| `.agent/memory/` | Long-term memory entries |
| `.agent/skills/` | Built-in and custom agent skills |
| `.agent/config/` | Agent configuration files |

> **Protected files:** `.agent/soul.md`, `.agent/user.md`, and `.agent/HEARTBEAT.md` are **read-only** — the agent cannot overwrite or delete them. Only you can edit them through the [Settings](settings.md).

---

## Navigating Directories

- **Tap a folder** to open it. The title bar updates to show the folder name and the full path is shown below the title.
- **Tap `..`** (the first row in any subdirectory) to go back to the parent directory.
- Directories are always listed **before files**, and both groups are sorted alphabetically (case-insensitive).

---

## Viewing Files

Tapping any file opens a dialog with two choices:

| Option | Behavior |
|---|---|
| **View in app** | Opens an internal viewer dialog that renders the raw text content directly inside DroidClaw. Large files are automatically truncated to the first 10 000 lines; a notice is shown at the end if truncation occurred. |
| **Open with external app** | Shares the file via Android's standard intent chooser so you can open it in any compatible app (text editor, PDF reader, image viewer, etc.). |

### Supported file types for in-app viewing

The internal viewer displays the plain-text content of any file. It works best for:

- Markdown (`.md`)
- Plain text (`.txt`, `.log`, `.csv`)
- Source code (`.py`, `.js`, `.java`, `.kt`, `.sh`, `.yaml`, `.yml`)
- Data files (`.json`, `.xml`)

Binary files (images, PDFs, etc.) are better opened with an external app.

### Supported file types for external opening

DroidClaw maps file extensions to MIME types when launching external apps:

| Extensions | Type |
|---|---|
| `.txt`, `.md`, `.csv`, `.log` | Plain text |
| `.html`, `.htm` | HTML |
| `.json` | JSON |
| `.xml` | XML |
| `.py` | Python source |
| `.js` | JavaScript |
| `.java` | Java source |
| `.kt` | Kotlin source |
| `.sh`, `.bash` | Shell script |
| `.yaml`, `.yml` | YAML |
| `.pdf` | PDF document |
| `.jpg`, `.jpeg` | JPEG image |
| `.png` | PNG image |
| `.gif` | GIF image |
| anything else | Generic binary (`*/*`) |

If no app on your device handles the chosen type, a toast notification will inform you.

---

## File Metadata

Each file row shows:

- **Icon** — folder icon for directories, document or code icon for files.
- **Name** — the file or folder name.
- **Details** — for directories: the label *Directory*; for files: the file size and last-modified date (e.g. `3.2 KB • May 12, 2026`).
- **Chevron** (`›`) — shown only for directories, indicating they can be navigated into.

---

## File Size Limits

| Limit | Value |
|---|---|
| Maximum file size readable in-app | 10 MB |
| Maximum file upload via chat attachment | 50 MB |
| Maximum lines displayed per in-app view | 10 000 lines |

---

## How Files Get There

Files appear in the workspace through several routes:

1. **Agent actions** — The agent writes files as part of completing tasks (e.g., generated scripts, reports, downloaded content).
2. **Chat attachments** — Files you attach in the chat are copied to `uploads/` automatically.
3. **Skill outputs** — Built-in skills like *Document Editor* or *Data Processing* save their results to `home/documents/` or `tmp/`.

---

## Tips

- Use the **internal viewer** for a quick peek at a script or note without leaving DroidClaw.
- Use **Open with external app** to edit a file in your preferred text editor, then ask the agent to re-read it.
- The `tmp/` directory is a good place for scratch files — the agent may clear it between tasks.
- If you cannot find a file the agent mentioned, check `tmp/` and `home/documents/` first.
- The `.agent/skills/` directory contains the Markdown skill definitions you can browse to understand what the agent can do. See the [Skills Browser](settings.md) for a dedicated UI.