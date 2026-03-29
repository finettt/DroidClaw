# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

DroidClaw is an Android application using Gradle with Nix for reproducible builds.

### Nix Development Shell (Recommended)

```bash
nix develop          # Enter development shell with JDK 21, Gradle, Python 3.11
./gradlew assembleDebug   # Build debug APK (alias: build)
./gradlew testDebugUnitTest # Run unit tests (alias: test)
./gradlew lintDebug    # Run linting (alias: lint)
./gradlew installDebug # Install to connected device (alias: install)
./gradlew clean        # Clean build artifacts (alias: clean)
```

### Manual Build

```bash
./gradlew assembleDebug
```

Requires JDK 21 and Python 3.11 for Chaquopy.

### Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests
./gradlew connectedAndroidTest
```

## High-Level Architecture

DroidClaw is an AI-powered coding agent for Android with the following core components:

### Core Architecture

```
MainActivity (Navigation hub with drawer)
├── ChatFragment (Main chat interface with AgentLoop integration)
├── SettingsFragment (Settings hub - ListView style)
│   ├── ProvidersListFragment (API provider management)
│   ├── ProviderDetailFragment (Provider + models)
│   ├── ModelDetailFragment (Model configuration)
│   └── AgentSettingsFragment (Agent behavior settings)
├── SkillsBrowserFragment (Browse SKILL.md files)
└── SkillContentFragment (Display skill details)
```

### Agent Architecture

The agent uses an iterative tool-calling loop (`AgentLoop.java`):

1. User sends message → LLM API
2. LLM responds with either text or tool_calls
3. If tool_calls: execute tools (with approval if enabled)
4. Send tool results back to LLM
5. Repeat until final text response

### Key Components

| Component | Purpose |
|-----------|---------|
| `LlmApiService` | OpenAI-compatible API client (OkHttp + Gson) |
| `AgentLoop` | Iterative tool-calling workflow |
| `ToolRegistry` | Manages all tools (file, shell, python) |
| `Tool` interface | All tools implement getName(), getDefinition(), execute() |
| `VirtualFileSystem` | Sandboxed filesystem operations |
| `WorkspaceManager` | Workspace directory structure (.agent/skills/) |
| `PathValidator` | Prevents path traversal attacks |
| `ShellExecutor` | ProcessBuilder wrapper for shell commands |
| `PythonExecutor` | Chaquopy-based Python execution |
| `ChatRepository` | SharedPreferences persistence for messages/sessions |
| `SettingsManager` | JSON-based settings (providers, models, agent config) |

### Data Models

- `ChatMessage` - User/assistant/tool_call/tool_result messages
- `ChatSession` - Session metadata (id, title, updatedAt)
- `Provider` - API provider (id, name, baseUrl, apiKey, models)
- `Model` - Model config (id, name, contextWindow, maxTokens, input types)
- `AgentConfig` - Agent settings (shellAccess, sandboxMode, maxIterations, requireApproval)

### Tools

- `read_file`, `write_file`, `edit_file`, `list_files`, `delete_file`, `search_files`, `file_info` - Virtual filesystem
- `execute_shell` - Shell commands (requires shell access)
- `execute_python` / `pip_install` - Python execution (requires shell access)

### Skills System

Skills are directory-based with `SKILL.md` files in `.agent/skills/`:

- Built-in: `skill_creator`, `web_search`, `code_analysis`, `data_processing`, `task_automation`
- Agent discovers skills via `list_files(".agent/skills/")`
- Agent loads skills on-demand via `read_file(".agent/skills/[name]/SKILL.md")`

### Configuration

Settings stored as JSON in SharedPreferences (`droidclaw_settings`):

```json
{
  "providers": { "provider-id": { "name", "baseUrl", "apiKey", "api", "models": [...] } },
  "agents": { "defaults": { "model", "shellAccess", "sandboxMode", "maxIterations", "requireApproval", "shellTimeout" } },
  "onboarding": { "completed", "userName" }
}
```

## Technology Stack

- **Language**: Java
- **UI**: Material Design 3, RecyclerView, Navigation Component
- **Markdown**: Markwon (core, strikethrough, tables, tasklist)
- **HTTP**: OkHttp 4.12
- **JSON**: Gson 2.10.1
- **Python**: Chaquopy 15.0.1 (Python 3.11)
- **Min SDK**: 22 (Android 5.1)
- **Target SDK**: 35

## Development Notes

- All file paths in tools are relative to workspace root
- Path traversal is prevented via `PathValidator`
- Shell commands require `shellAccess` enabled in agent settings
- Tool execution requires approval by default (configurable)
- Max iterations limit prevents infinite loops (default: 20)
- Skill files limited to 100KB

## Releases and Versioning

DroidClaw uses [release-please](https://github.com/googleapis/release-please) for automated releases.

### How It Works

1. **Conventional Commits**: Use [Conventional Commits](https://www.conventionalcommits.org/) format:
   - `feat:` - New features (bumps minor version)
   - `fix:` - Bug fixes (bumps patch version)
   - `feat!:` or `BREAKING CHANGE:` - Breaking changes (bumps major version)

2. **Automated Release PRs**: When commits are pushed to `main`/`master`, release-please:
   - Analyzes commit messages
   - Creates/updates a Release PR with CHANGELOG updates
   - Updates version in [`app/build.gradle.kts`](app/build.gradle.kts:24)

3. **Creating a Release**: Merge the Release PR to:
   - Create a GitHub release with tag
   - Trigger APK build and upload to release assets

### Configuration Files

| File | Purpose |
|------|---------|
| [`.github/release-please-config.json`](.github/release-please-config.json) | Release-please settings |
| [`.release-please-manifest.json`](.release-please-manifest.json) | Current version tracking |
| [`.github/workflows/release-please.yml`](.github/workflows/release-please.yml) | GitHub Actions workflow |

### Version Markers

The version in [`app/build.gradle.kts`](app/build.gradle.kts:24) uses the `x-release-please-version` comment marker for automatic updates.
