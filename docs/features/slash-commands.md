# Slash commands

Type `/` in the chat input to run a command locally instead of sending a message
to the agent. While you type a `/` prefix, matching commands appear as hint rows
above the input; tap one to fill the input.

Commands are handled by the app: they never reach the LLM and their output is
shown as a temporary bubble that is not saved to the session history. Anything
starting with `/` that is not a known command is sent to the agent as a regular
message.

## Available commands

| Command | Description |
|---------|-------------|
| `/effort [level]` | Show or set the reasoning effort level. Levels: `low`, `med`, `high`, `xhigh`, `max`. Effort sets both the reasoning parameter sent to the API (for reasoning-capable models) and the agent's turn budget for subsequent runs. |

Examples:

```
/effort          →  Effort: med (turn budget 20)
/effort high     →  Effort set to high (turn budget 40)
```

The setting is persisted and applies from the next agent run; it is the same
value configured under Settings → Agent settings → Effort.

## Adding a command

Commands live in `app/src/main/java/io/finett/droidclaw/chat/SlashCommand.java`.
Register one in the static block — the name, usage hint, and a handler that
returns markdown reply text:

```java
register("mycommand", "what it does",
        (arg, settings) -> "reply in **markdown**");
```

The hint list and input completion pick new commands up automatically.
