# DroidClaw Coding Agent Guide

This document explains how to work with DroidClaw as a coding agent, using GitHub tools and Nix for development.

## Table of Contents

- [Using GitHub with `gh`](#using-github-with-gh)
- [Development with `flake.nix`](#development-with-flakenix)
- [Agent Features](#agent-features)
- [Workflow Examples](#workflow-examples)

---

## Using GitHub with `gh`

The `gh` CLI tool is integrated into the development shell and provides access to GitHub runs, issues, and pull requests.

### Prerequisites

1. Install the Nix package manager
2. Enter the development shell: `nix develop`

### Authentication

First, authenticate with GitHub:

```bash
gh auth login
```

Follow the prompts to authenticate with your GitHub account.

### Common Commands

#### Accessing GitHub Runs (CI/CD)

```bash
# List recent workflow runs
gh run list

# View details of a specific run
gh run view <run-id>

# View logs for a specific run
gh run view <run-id> --log

# Retry a failed run
gh run rerun <run-id>

# Watch a run in real-time
gh run watch <run-id>
```

#### Managing Issues

```bash
# List all issues
gh issue list

# View a specific issue
gh issue view <issue-number>

# Create a new issue
gh issue create --title "Title" --body "Description"

# Comment on an issue
gh issue comment <issue-number> --body "Comment text"

# Close an issue
gh issue close <issue-number>

# Reopen an issue
gh issue reopen <issue-number>
```

#### Working with Pull Requests

```bash
# List all pull requests
gh pr list

# View a specific PR
gh pr view <pr-number>

# Create a new pull request
gh pr create --title "Title" --body "Description" --base main

# Add a comment to a PR
gh pr comment <pr-number> --body "Comment text"

# Merge a pull request
gh pr merge <pr-number> --merge

# Close a pull request
gh pr close <pr-number>
```

#### Branch Operations

```bash
# List all branches
gh branch list

# Create a new branch
gh branch create <branch-name>

# Checkout a branch
gh checkout <branch-name>
```

### Using `gh` in Scripts

You can use `gh` in automation scripts to interact with GitHub:

```bash
# Check if an issue exists
if gh issue view 123 > /dev/null 2>&1; then
    echo "Issue 123 exists"
fi

# Get PR status
gh pr view 456 --json state
```

---

## Development with `flake.nix`

The project includes a Nix flake for reproducible development environments.

### Overview

The `flake.nix` defines a development shell with:

- **Java/Android**: JDK 21, Gradle, `gh` CLI
- **Python**: Python 3.11 for Chaquopy build
- **Build tools**: unzip, which

### Getting Started

1. **Install Nix** (if not already installed):

   ```bash
   # Using the official installer
   curl -L https://nixos.org/nix/install | sh

   # Or use multi-user installer for production setups
   curl -L https://nixos.org/nix/install | sh -s -- --multi-user
   ```

2. **Enter the development shell**:

   ```bash
   nix develop
   ```

   This will:
   - Set up `JAVA_HOME` to JDK 21
   - Configure Gradle to use the correct Java home
   - Set up Python 3.11 for Chaquopy
   - Display environment information

### Building the Project

Once in the dev shell:

```bash
# Build the debug APK
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest

# Run linting
./gradlew lintDebug

# Build and install to connected device
./gradlew installDebug
```

### Using Nix Commands

```bash
# Build the dev shell without entering it
nix build

# Show information about the dev shell
nix develop --help

# Export the dev shell to a tarball
nix develop --export-lock-file
```

### Customizing the Development Shell

To add packages to the development shell, edit `flake.nix`:

```nix
packages = with pkgs; [
  # Existing packages...
  jdk21
  gradle
  gh

  # Add your package here
  your-package
];
```

### Troubleshooting

#### Gradle Java Home Issues

If Gradle can't find Java:

```bash
export JAVA_HOME="${pkgs.jdk21}/lib/openjdk"
export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME $GRADLE_OPTS"
```

#### Python Version Mismatch

Ensure Python version matches Chaquopy configuration in `app/build.gradle.kts`:

```kotlin
chaquopy {
    defaultConfig {
        version = "3.11"  # Must match flake.nix
    }
}
```

#### Cache Issues

If builds fail unexpectedly:

```bash
# Clean Gradle cache
./gradlew clean

# Rebuild
./gradlew assembleDebug
```

---

## Agent Features

DroidClaw is an AI-powered coding agent with the following capabilities:

### Tools

- **File Operations**: Read, write, edit, list, search, and delete files
- **Shell Execution**: Run shell commands for system operations
- **Python Execution**: Execute Python scripts as agent skills
- **Pip Management**: Install Python packages at runtime

### Workflows

1. **Code Analysis**: Analyze codebases and provide feedback
2. **Bug Fixes**: Identify and fix bugs in existing code
3. **Feature Development**: Implement new features
4. **Refactoring**: Improve code quality and structure

---

## Workflow Examples

### Typical Development Workflow

```bash
# 1. Enter development environment
nix develop

# 2. Create a new feature branch
gh branch create feature/my-feature

# 3. Make changes and commit
git add .
git commit -m "Add my feature"

# 4. Push to GitHub
git push origin feature/my-feature

# 5. Create a pull request
gh pr create --title "Add my feature" --body "Description of changes" --base main

# 6. Monitor CI runs
gh run watch
```

### Debugging CI Failures

```bash
# List recent runs
gh run list

# View failed run details
gh run view <failed-run-id> --log

# Rerun the workflow
gh run rerun <failed-run-id>
```

### Managing Issues

```bash
# Create issue for a bug
gh issue create \
  --title "Bug: [Description]" \
  --body "Steps to reproduce:\n1. ...\n2. ...\n3. ..." \
  --label bug

# Assign yourself to an issue
gh issue assign <issue-number> --assignee @me

# Add a comment with investigation findings
gh issue comment <issue-number> --body "Investigation findings..."
```

---

## Quick Reference

| Task | Command |
|------|---------|
| Enter dev shell | `nix develop` |
| Build APK | `./gradlew assembleDebug` |
| List runs | `gh run list` |
| View PR | `gh pr view <num>` |
| Create PR | `gh pr create` |
| List issues | `gh issue list` |

---

## Additional Resources

- [Nix Flakes Documentation](https://nixos.org/manual/nix/stable/command-line/nix-flakes.html)
- [GitHub CLI Documentation](https://cli.github.com/manual)
- [Android Gradle Plugin](https://developer.android.com/studio/build/gradle)
- [Chaquopy Documentation](https://chaquopy.com/docs/)
