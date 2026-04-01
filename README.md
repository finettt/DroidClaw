# DroidClaw - LLM Chat Application

[![Android CI](https://github.com/finettt/DroidClaw/actions/workflows/android.yml/badge.svg)](https://github.com/finettt/DroidClaw/actions/workflows/android.yml)

An Android application for chatting with Large Language Models (LLMs) with a clean, modern interface. DroidClaw includes Python 3.11 runtime support via Chaquopy, enabling the AI agent to execute Python scripts as part of its skills system.

## Features

- **Chat Interface**: Clean chat UI with message bubbles for user and assistant messages
- **Settings Screen**: Configure your LLM API settings including:
  - API Key
  - API URL (supports OpenAI-compatible endpoints)
  - Model selection
  - System prompt customization
  - Max tokens configuration
  - Temperature control (0.0 - 2.0)
- **Navigation**: Seamless navigation between Chat and Settings screens
- **Persistent Settings**: All settings are saved locally using SharedPreferences
- **Python Execution**: Run Python scripts and code as agent skills with runtime pip support

## Screenshots

### Chat Screen
- Send messages to the LLM
- View conversation history
- Clear chat option in menu
- Settings access from menu

### Settings Screen
- Configure API endpoint
- Set model parameters
- Customize system prompt
- Adjust temperature with slider

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run the app on an emulator or physical device

## Configuration

Before using the app, you need to configure the API settings:

1. Open the app
2. Tap the menu (three dots) and select "Settings"
3. Enter your API Key (required)
4. Configure the API URL (defaults to OpenAI's endpoint)
5. Set your preferred model name (e.g., "gpt-3.5-turbo", "gpt-4")
6. Customize the system prompt if desired
7. Tap "Save Settings"

## API Compatibility

The app is designed to work with OpenAI-compatible APIs, including:
- OpenAI ChatGPT API
- Azure OpenAI Service
- Other compatible endpoints (LocalAI, Ollama with OpenAI compatibility, etc.)

## Python Capabilities

DroidClaw includes Python 3.11 runtime support via Chaquopy, enabling:

- Execute Python scripts as agent skills
- Install packages dynamically via pip
- Access Python's rich ecosystem for data processing, web scraping, etc.
- Create custom Python-based skills

### Using Python

The agent can execute Python code through the `execute_python` tool:

1. **Inline Code**: Execute Python code directly
2. **Script Files**: Run Python scripts from the workspace
3. **Package Management**: Install packages at runtime

### Python Configuration

Python is configured via the app's `build.gradle.kts`:

```kotlin
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("requests")
            install("beautifulsoup4")
            install("lxml")
        }
    }
}
```

## Technical Details

- **Language**: Java
- **Minimum SDK**: 22 (Android 5.1)
- **Target SDK**: 35
- **Architecture**: Fragment-based with Navigation Component
- **Python Runtime**: Python 3.11 via Chaquopy 15.0.1
- **Dependencies**:
  - AndroidX Navigation
  - Material Design Components
  - RecyclerView
  - OkHttp for network requests
  - Gson for JSON parsing
  - Chaquopy for Python execution

## Project Structure

```
app/src/main/java/io/finett/droidclaw/
├── api/
│   └── LlmApiService.java          # Handles API communication
├── adapter/
│   └── ChatAdapter.java            # RecyclerView adapter for messages
├── fragment/
│   ├── ChatFragment.java           # Main chat interface
│   └── SettingsFragment.java      # Settings configuration
├── model/
│   └── ChatMessage.java            # Message data model
├── python/
│   ├── PythonConfig.java           # Python execution configuration
│   ├── PythonExecutor.java         # Main Python execution wrapper
│   ├── PythonResult.java           # Execution result container
│   └── PipManager.java             # Runtime pip package management
├── tool/
│   ├── Tool.java                   # Tool interface
│   ├── ToolDefinition.java         # Tool schema definition
│   ├── ToolResult.java             # Tool execution result
│   └── impl/
│       ├── FileReadTool.java       # File reading tool
│       ├── FileWriteTool.java      # File writing tool
│       ├── FileEditTool.java       # File editing tool
│       ├── FileListTool.java       # Directory listing tool
│       ├── FileSearchTool.java     # File search tool
│       ├── FileDeleteTool.java     # File deletion tool
│       ├── FileInfoTool.java       # File info tool
│       ├── ShellTool.java          # Shell command execution
│       └── PythonTool.java         # Python execution tool
├── shell/
│   ├── ShellConfig.java            # Shell execution configuration
│   ├── ShellExecutor.java          # Shell command executor
│   └── ShellResult.java            # Shell result container
├── filesystem/
│   ├── PathValidator.java          # Path validation utilities
│   ├── VirtualFileSystem.java      # Virtual file system
│   └── WorkspaceManager.java       # Workspace management
├── util/
│   └── SettingsManager.java        # Settings persistence
└── MainActivity.java               # Main activity with navigation
```

## Building

### Using Nix (Recommended)

The project includes a Nix flake for reproducible builds:

```bash
# Enter development shell
nix develop

# Build the project
./gradlew assembleDebug
```

### Manual Setup

1. Ensure JDK 21 is installed
2. Ensure Python 3.11 is available (for Chaquopy build)
3. Run `./gradlew assembleDebug`

## License

This project is open source and available for educational purposes.
