# First Steps with DroidClaw

Welcome to **DroidClaw**! This guide will help you get started with your mobile AI assistant.

## Installation

1. Download the latest APK from the [GitHub Releases](https://github.com/finettt/DroidClaw/releases) page.
2. Install the APK on your Android device. You may need to allow "Installation from Unknown Sources".
3. Launch the app.

## Onboarding

When you first open DroidClaw, you'll be greeted by an onboarding flow. This process helps set up the basics:

1.  **Welcome**: A brief introduction to what DroidClaw can do.
2.  **Provider Setup**: Choose your LLM provider (e.g., OpenAI, Anthropic, OpenRouter).
3.  **API Key**: Enter your API key for the chosen provider. Your key is stored locally and securely.
4.  **Model Selection**: Select the default model you'd like to use.

## Your First Interaction

Once onboarding is complete, you'll land in the **Chat** screen.

1.  **Type a message**: Try asking the agent something simple, like "Create a hello.txt file in your workspace."
2.  **Observe the loop**: DroidClaw uses an agentic loop. You'll see it "thinking" and using tools (like `FileWriteTool`) to accomplish your request.
3.  **Check the result**: After the agent finishes, you can use the built-in **File Browser** to see the new file.

## Key Concepts

-   **Agentic Loop**: Unlike a standard chatbot, DroidClaw can perform actions. It analyzes your request, decides which tool to use, executes it, and repeats until the task is done.
-   **Workspace**: A sandboxed area on your phone's storage where the agent can safely read and write files without affecting your system data.
-   **Skills**: Pre-defined capabilities (like web search or code analysis) that the agent can utilize.

## Next Steps

-   Configure more providers in [Settings](settings.md).
-   Learn about [Custom Skills](../reference/skills.md).
-   Explore the [File Browser](first-steps.md#file-browser).