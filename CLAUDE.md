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
| `IdentityManager` | Loads and manages identity documents (soul.md, user.md) |
| `ToolRegistry` | Manages all tools (file, shell, python) |
| `Tool` interface | All tools implement getName(), getDefinition(), execute() |
| `VirtualFileSystem` | Sandboxed filesystem operations |
| `WorkspaceManager` | Workspace directory structure (.agent/skills/, identity files) |
| `PathValidator` | Prevents path traversal attacks |
| `ShellExecutor` | ProcessBuilder wrapper for shell commands |
| `PythonExecutor` | Chaquopy-based Python execution |
| `ChatRepository` | SharedPreferences persistence for messages/sessions |
| `SettingsManager` | JSON-based settings (providers, models, agent config) |

### Data Models

- `ChatMessage` - User/assistant/system/tool_call/tool_result messages
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

### Identity System

DroidClaw maintains two identity documents that provide the agent with persistent identity and user knowledge:

- **`.agent/soul.md`** - Agent's identity, values, capabilities, and self-concept
  - Defines who the agent is and how it operates
  - Includes core principles, working style, and boundaries
  - Instructs agent to update user.md when learning about the user
  - Created from `app/src/main/assets/identity/soul.md` template on first run

- **`.agent/user.md`** - User profile and preferences (auto-updated by agent)
  - Contains user identity, preferences, and working style
  - Tracks goals, projects, and patterns observed by the agent
  - Updated by agent using `write_file` tool when learning new information
  - Provides continuity across sessions where memory is otherwise fragile

**Loading Mechanism:**
- Identity files loaded at session start (ChatFragment initialization)
- Injected as system messages prepended to conversation history
- Hidden from UI (not displayed as chat messages)
- Cached per session to avoid repeated file reads

**Update Flow:**
1. Agent learns something about user during conversation
2. Agent uses `write_file` tool to update `.agent/user.md`
3. Updated profile available in next session
4. Creates continuity despite stateless LLM nature

**Implementation:**
- `IdentityManager` - Loads and formats identity documents
- `WorkspaceManager` - Creates identity files from assets on initialization
- `AgentLoop` - Accepts identity context and passes to API service
- `LlmApiService` - Prepends identity as system messages in API requests

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
- Identity files (soul.md, user.md) are loaded at session start and injected as system context
- Agent can update user.md anytime via write_file tool to maintain user knowledge across sessions
