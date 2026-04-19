# Settings and Configuration

DroidClaw offers extensive configuration options to tailor the AI assistant to your needs. You can access these from the side menu or the settings icon in the top bar.

## LLM Providers

You can manage multiple providers to switch between different AI models.

### Adding a Provider
1. Go to **Settings > Providers**.
2. Tap the **+** button.
3. Choose a provider type:
    - **OpenAI**: For GPT-5.4, GPT-5.4 Mini, etc.
    - **Anthropic**: For Claude Opus 4.7, Claude Sonnet 4.6, etc.
    - **OpenRouter**: Access hundreds of models through a single API.
    - **Custom (OpenAI Compatible)**: For local servers like Llama.cpp or vLLM.
4. Enter the required **API Key** and **Base URL** (if applicable).

### Managing Models
Once a provider is added, you can select which specific models are available for use in the **Models** tab within that provider's configuration.

## Agent Behavior

Adjust how the agent interacts and processes tasks:

- **Max Iterations**: Limits how many steps the agent can take in a single loop to prevent infinite loops or excessive API usage.
- **Context Window**: Configure how much conversation history is sent to the model.
- **System Prompt**: Customize the "Soul" of your agent. This defines its personality and core instructions.

## Filesystem & Workspace

DroidClaw operates in a sandboxed directory.

- **Workspace Path**: View where your agent's files are stored on your device.
- **Clear Workspace**: Delete all files created by the agent.
- **Import/Export**: Move files between the DroidClaw sandbox and your phone's public storage.

## Python Environment

The app includes a bundled Python 3.11 interpreter.

- **Pip Packages**: View or install additional Python libraries for the agent to use in scripts.
- **Scripts Directory**: Manage the Python scripts the agent has access to.

## Privacy & Security

- **Local Storage**: All API keys and conversation histories are stored locally on your device.
- **Network Access**: DroidClaw only communicates with the LLM providers you configure.