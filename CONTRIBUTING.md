# Contributing to DroidClaw

Thank you for your interest in contributing to DroidClaw! This guide covers everything you need to get started.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Setting Up the Development Environment](#setting-up-the-development-environment)
- [Building the Project](#building-the-project)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Adding Skills](#adding-skills)

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Nix | Latest | Recommended — provides reproducible dev shell |
| JDK | 21 | Required if not using Nix |
| Python | 3.11 | Required for Chaquopy builds |
| Gradle | Managed by wrapper | Do not install separately |
| Android SDK | API 35 | Installed automatically by Gradle |
| `gh` CLI | Latest | Included in Nix dev shell, for GitHub operations |

## Setting Up the Development Environment

### Option A: Nix (Recommended)

```bash
# Install Nix if you don't have it
curl -L https://nixos.org/nix/install | sh

# Enter the development shell
nix develop
```

This automatically configures `JAVA_HOME`, `PYTHON`, and provides convenient shell aliases (`build-app`, `test-unit-app`, `lint-app`, etc.).

### Option B: Manual Setup

1. Install JDK 21 and set `JAVA_HOME`
2. Install Python 3.11
3. Set environment variables:

```bash
export JAVA_HOME=/path/to/jdk21
export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME"
export PYTHON=/path/to/python3.11
```

### Clone and Build

```bash
git clone https://github.com/finettt/DroidClaw.git
cd DroidClaw
./gradlew assembleDebug
```

## Building the Project

```bash
# Build debug APK
./gradlew assembleDebug

# Clean build artifacts
./gradlew clean

# Run Android Lint
./gradlew lintDebug

# Install to connected device
./gradlew installDebug
```

## Running Tests

DroidClaw has two test suites: unit tests and instrumented (on-device) tests.

### Unit Tests

```bash
./gradlew testDebugUnitTest
```

### Instrumented Tests

Requires an emulator or physical device:

```bash
./gradlew connectedAndroidTest
```

### CI Pipeline

CI runs automatically on every push and PR to `main`:

1. **Unit Tests** — `./gradlew testDebugUnitTest` + `./gradlew lintDebug`
2. **Emulator Tests** — Sharded across API levels 34, 35, 36 (3 shards each) using `android-emulator-runner`

Your PR must pass all CI checks before it can be merged.

## Project Structure

```
app/src/main/java/io/finett/droidclaw/
├── MainActivity.java          # Navigation hub with drawer
├── adapter/                   # RecyclerView adapters
├── agent/                     # Agent loop, identity, memory, summarization
├── api/                       # LLM API client (OkHttp + Gson)
├── filesystem/                # Virtual filesystem, path validation
├── fragment/                  # UI fragments (chat, settings, providers, etc.)
├── model/                     # Data models (ChatMessage, Provider, Model, etc.)
├── python/                    # Chaquopy Python execution, pip management
├── repository/                # SharedPreferences-based persistence
├── scheduler/                 # Cron job scheduling
├── service/                   # Background services (task scheduling, chat continuation)
├── shell/                     # Shell command execution
├── tool/                      # Tool interface and registry
│   └── impl/                  # Concrete tool implementations
└── util/                      # Utility classes

app/src/main/assets/
├── heartbeat/                 # Heartbeat skill definition
├── identity/                  # soul.md and user.md templates
└── skills/                    # Built-in skills (each with SKILL.md)

app/src/androidTest/           # Instrumented tests
├── adapter/                   # Adapter tests
├── agent/                     # Agent tests
├── fragment/                  # Fragment UI tests
├── integration/               # End-to-end integration tests
├── python/                    # Python execution tests
├── repository/                # Repository tests
├── scheduler/                 # Scheduler tests
├── service/                   # Service tests
├── ui/                        # Critical user journey UI tests
├── util/                      # Test helpers
└── worker/                    # WorkManager worker tests
```

## Coding Standards

### Java

- Source/target compatibility: Java 11
- Follow standard Java naming conventions (camelCase for methods/variables, PascalCase for classes)
- Use descriptive names — avoid abbreviations unless widely understood (e.g., `api`, `url`, `id`)

### Comments

Comments should explain **why**, not **what**. The code itself should describe what it does.

**Do:**

```java
// Retry with exponential backoff to handle transient network failures
int delay = baseDelay * (1 << attempt);
```

**Don't:**

```java
// Get the name
public String getName() { ... }

// Check if this is a CRON JOB result
public boolean isCronJob() { ... }

//text without space after //
```

- Do not write Javadoc or inline comments that simply restate the method/class name
- Do not write comments that state the obvious (e.g., getters, setters, boolean checks)
- Always use a space after `//` — write `// text` not `//text`
- Preserve comments that explain business logic, non-obvious behavior, or design decisions
- Collapse consecutive blank lines to a single blank line

### Code Style

- Opening brace on same line
- No trailing whitespace
- Files should end with a single newline

### Security

- All file paths in tools are **relative** to the workspace root
- Path traversal is prevented via [`PathValidator`](app/src/main/java/io/finett/droidclaw/filesystem/PathValidator.java) — never bypass it
- Shell commands require `shellAccess` to be explicitly enabled
- Tool execution requires approval by default

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]
```

**Types:**

| Type | Purpose |
|------|---------|
| `feat` | New feature |
| `fix` | Bug fix |
| `chore` | Maintenance, cleanup, refactoring |
| `docs` | Documentation changes |
| `test` | Adding or updating tests |
| `refactor` | Code restructuring without behavior change |
| `style` | Formatting, whitespace (no logic change) |

**Examples:**

```
feat(tools): add file search tool with regex support
fix(chat): resolve message ordering issue on session restore
chore: remove junk comments across Java source files
docs: update CONTRIBUTING.md with coding standards
test(agent): add integration tests for tool approval flow
```

## Pull Request Process

1. **Create a branch** from `main`:

   ```bash
   git switch main
   git pull
   git switch -c feat/your-feature
   ```

2. **Make your changes** following the coding standards above.

3. **Run tests locally** before pushing:

   ```bash
   ./gradlew testDebugUnitTes
   ./gradlew lintDebug
   ```

4. **Commit** using conventional commit format (see above).

5. **Push and create a PR**:

   ```bash
   git push origin feat/your-feature
   gh pr create --title "feat(scope): description" --body "Description of changes"
   ```

6. **Wait for CI** — all checks must pass (unit tests, lint, emulator tests across API 34/35/36).

7. **Address review feedback** if requested.

### PR Checklist

- [ ] Code follows the project's coding standards
- [ ] Comments explain *why*, not *what*
- [ ] No `//text` comments (always `// text`)
- [ ] No redundant Javadoc on getters/setters/obvious methods
- [ ] No consecutive blank lines
- [ ] Unit tests pass (`./gradlew testDebugUnitTest`)
- [ ] Lint passes (`./gradlew lintDebug`)
- [ ] Commit messages follow Conventional Commits
- [ ] No new security issues (path traversal bypasses, unchecked shell access, etc.)

## Reporting Bugs

If you find a bug:

1. Check [existing issues](https://github.com/finettt/DroidClaw/issues) first
2. If not reported, [create a new issue](https://github.com/finettt/DroidClaw/issues/new) with:
   - **Title**: Clear, concise description of the bug
   - **Steps to reproduce**: Numbered steps
   - **Expected behavior**: What should happen
   - **Actual behavior**: What happens instead
   - **Device/Android version**: e.g., "Pixel 7, Android 14"
   - **App version**: From Settings → Info
   - **Logs**: If available (logcat output)

## Adding Skills

Skills are directory-based modules located in `app/src/main/assets/skills/`. Each skill has a `SKILL.md` file that the agent loads on-demand.

To add a new skill:

1. Create a directory under `app/src/main/assets/skills/<skill_name>/`
2. Create a `SKILL.md` file describing the skill's capabilities, instructions, and any constraints
3. The agent will automatically discover it via `list_files(".agent/skills/")` and load it when needed

Existing built-in skills for reference:
- `skill_creator` — Creates new skills
- `web_search` — Web search capabilities
- `code_analysis` — Code analysis and feedback
- `data_processing` — Data processing tasks
- `task_automation` — Task automation workflows
- `document_editor` — Document editing
- `automation_manager` — Automation management

## Additional Resources

- [CLAUDE.md](CLAUDE.md) — Detailed architecture and development notes for AI coding agents
- [README.md](README.md) — Project overview and installation
- [docs/](docs/) — User and reference documentation
- [Nix Flakes Documentation](https://nixos.org/manual/nix/stable/command-line/nix-flakes.html)
- [Chaquopy Documentation](https://chaquopy.com/docs/)
- [Android Gradle Plugin](https://developer.android.com/studio/build/gradle)