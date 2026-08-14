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
- **Require Approval**: Ask for confirmation before executing tools that change state.
- **Stream Responses**: When enabled (default), LLM text is streamed live over SSE as it is generated, instead of waiting for the full answer. Disable it if a provider does not support streaming.
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

## Shell

The agent can run shell commands through the `execute_shell` tool. See [Agent Tools](../features/agent-tools.md) for details.

- **Shell Access**: Master toggle for the shell tool. Off by default.
- **Backend**:
    - `local` — commands run on your phone, inside the app's sandbox.
    - `ssh` — commands run on a remote host you configure.
- **Timeout**: How long a command may run before it is killed (default: 30 seconds).

### SSH Backend

When the backend is set to `ssh`, configure the remote connection:

- **Host** and **Port** (default 22), **User**
- **Authentication**: password or a private key file
- **Host key verification**: enabled by default. Disable it only if you know exactly why.

## Tool Approvals

- **Require Approval**: When enabled (default), the agent asks for your confirmation before running tools that can change state.
- **Per-tool overrides**: Each tool can be set to:
    - `Default` — follows the global toggle
    - `Always Approve` — runs without prompting
    - `Always Reject` — the tool is blocked entirely

## Screen Control

- **Screen Control**: Enables the screen tools (tap, swipe, type text, read the UI tree). Requires the Android accessibility service to be enabled. Off by default.
- **Trust Mode**: Skip per-action approval prompts for screen interactions.

## Calendar Access

- **Calendar access**: Enables the calendar tools (`calendar_list_calendars`, `calendar_list_events`, `calendar_create_event`, `calendar_update_event`, `calendar_delete_event`). Requires the Android `READ_CALENDAR` / `WRITE_CALENDAR` permissions, requested when you flip the switch. Off by default. See [Calendar Tools](../features/calendar.md).

## Cron Tasks & Heartbeat

- **Cron jobs**: Schedule prompts to run on a schedule (e.g. `every_2_hours`, `daily`). Failed runs are retried with exponential backoff (1, 2, 4, … minutes, capped at one day); the job is paused after repeated failures.
- **Heartbeat**: Periodic proactive check-ins driven by your `HEARTBEAT.md` instructions.

## Privacy & Security

- **Local Storage**: All API keys and conversation histories are stored locally on your device.
- **Network Access**: DroidClaw communicates with the LLM providers you configure — and with your SSH host, if you enable the remote shell backend.