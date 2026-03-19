# DroidClaw - LLM Chat Application

An Android application for chatting with Large Language Models (LLMs) with a clean, modern interface.

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

## Technical Details

- **Language**: Java
- **Minimum SDK**: 22 (Android 5.1)
- **Target SDK**: 36
- **Architecture**: Fragment-based with Navigation Component
- **Dependencies**:
  - AndroidX Navigation
  - Material Design Components
  - RecyclerView
  - OkHttp for network requests
  - Gson for JSON parsing

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
├── util/
│   └── SettingsManager.java        # Settings persistence
└── MainActivity.java               # Main activity with navigation
```

## License

This project is open source and available for educational purposes.