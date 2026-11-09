# Настройки и конфигурация

DroidClaw предлагает обширные настройки, чтобы настроить ИИ-ассистента под вас. Открыть их можно из бокового меню или из иконки настроек в верхней панели.

## Провайдеры LLM

Вы можете управлять несколькими провайдерами для переключения между разными ИИ-моделями.

### Добавление провайдера
1. Перейдите в **Настройки > Провайдеры**.
2. Нажмите кнопку **+**.
3. Выберите тип провайдера:
    - **OpenAI**: For GPT-5.4, GPT-5.4 Mini, etc.
    - **Anthropic**: For Claude Opus 4.7, Claude Sonnet 4.6, etc.
    - **OpenRouter**: Access hundreds of models through a single API.
    - **Custom (OpenAI Compatible)**: For local servers like Llama.cpp or vLLM.
4. Введите необходимые **API Key** и **Base URL** (если требуется).

### Управление моделями
Once a provider is added, you can select which specific models are available for use in the **Models** tab within that provider's configuration.

## Поведение агента

Настройте, как агент взаимодействует и обрабатывает задачи:

- **Effort**: Заменяет старое число "Max Iterations". Задаёт две вещи сразу — уровень рассуждений, отправляемый в API, и лимит ходов агента (сколько итераций проходит цикл перед остановкой). Уровни:

| Уровень | Лимит ходов (итерации) |
| --- | --- |
| Low | 8 |
| Medium | 20 |
| High | 40 |
| Very High | 80 |
| Max | Без лимита |

- **Лимит ходов**: цикл останавливается после этого числа итераций и выводит сообщение "Maximum iterations". **Max** никогда не устанавливает лимит и работает без ограничений.
- **Параметр API**: для моделей, поддерживающих рассуждения, усилие отправляется провайдеру (OpenAI `reasoning_effort`, Anthropic `output_config.effort`). Оно отправляется только если выбранная модель действительно поддерживает рассуждения; лимит ходов применяется всегда, независимо от модели.
- **Быстрое переключение**: ряд чипов усилием над полем ввода переключает усилие для текущего чата. Изменение применяется к следующему запуску сессии и никогда не записывается в глобальные настройки — это разрешение только для текущей сессии. Измените его там, не открывая Настройки.

Выберите более высокий уровень для задач, требующих больше рассуждений или планирования, и более низкий, чтобы сэкономить токены на простых вопросах.

- **Context Window**: Configure how much conversation history is sent to the model.
- **Require Approval**: Ask for confirmation before executing tools that can change state.
- **Stream Responses**: When enabled (default), LLM text is streamed live over SSE as it is generated, instead of waiting for the full answer. Disable it if a provider does not support streaming.
- **System Prompt**: Customize the "Soul" of your agent. This defines its personality and core instructions.

## Файловая система и рабочая область

DroidClaw работает в изолированной директории.

- **Workspace Path**: View where your agent's files are stored on your device.
- **Clear Workspace**: Delete all files created by the agent.
- **Import/Export**: Move files between the DroidClaw sandbox and your phone's public storage.

## Среда Python

В приложение встроен интерпретатор Python 3.11.

- **Pip Packages**: View or install additional Python libraries for the agent to use in scripts.
- **Scripts Directory**: Manage the Python scripts the agent has access to.

## Shell

The agent can run shell commands through the `execute_shell` tool. See [Agent Tools](../features/agent-tools.md) for details.

- **Shell Access**: Master toggle for the shell tool. Off by default.
- **Backend**:
    - `local` — commands run on your phone, inside the app's sandbox.
    - `ssh` — commands run on a remote host you configure.
- **Timeout**: How long a command may run before it is killed (default: 30 seconds).

### Backend SSH

When the backend is set to `ssh`, configure the remote connection:

- **Host** and **Port** (default 22), **User**
- **Authentication**: password or a private key file
- **Host key verification**: enabled by default. Disable it only if you know exactly why.

## Одобрение инструментов

- **Require Approval**: When enabled (default), the agent asks for your confirmation before running tools that can change state.
- **Per-tool overrides**: Each tool can be set to:
    - `Default` — follows the global toggle
    - `Always Approve` — runs without prompting
    - `Always Reject` — the tool is blocked entirely

## Управление экраном

- **Screen Control**: Enables the screen tools (tap, swipe, type text, read the UI tree). Requires the Android accessibility service to be enabled. Off by default.
- **Trust Mode**: Skip per-action approval prompts for screen interactions.

## Доступ к календарю

- **Calendar access**: Enables the calendar tools (`calendar_list_calendars`, `calendar_list_events`, `calendar_create_event`, `calendar_update_event`, `calendar_delete_event`). Requires the Android `READ_CALENDAR` / `WRITE_CALENDAR` permissions, requested when you flip the switch. Off by default. See [Calendar Tools](../features/calendar.md).

## Веб-инструменты

- **Web tools**: Enables the `fetch_page` tool, which fetches a public web page and returns its readable content as Markdown or plain text (section targeting, truncation). Enabled by default (opt-out). SSRF protection blocks private/loopback addresses; only http(s) URLs with allowlisted content types are fetched. Note: page fetches run from your device's own network. See [Agent Tools](../features/agent-tools.md).

## Самоулучшение (обучение GUIDELINES.md)

- **Self-Improvement (GUIDELINES.md)**: When enabled (default), the agent runs one extra structured LLM call after each finished conversation. It distills durable workflow lessons — your corrections, preferred formats, recurring workflows — into `.agent/GUIDELINES.md`, which is then injected into every new conversation.

Примечания:

- The analysis call goes to your configured LLM provider, so the recent transcript leaves the device — disable the toggle if you don't want that.
- The file is capped at 8 KB, written atomically with a `.bak` backup, and identical output is never re-written.
- You can inspect and hand-edit the learned guidelines in the Memory Browser.
- Design details: [Self-Improvement plan](../features/self-improvement-plan.md).

## Задачи по расписанию (Cron) и Heartbeat

- **Cron jobs**: Schedule prompts to run on a schedule (e.g. `every_2_hours`, `daily`). Failed runs are retried with exponential backoff (1, 2, 4, … minutes, capped at one day); the job is paused after repeated failures.
- **Heartbeat**: Periodic proactive check-ins driven by your `HEARTBEAT.md` instructions.

## Конфиденциальность и безопасность

- **Local Storage**: All API keys and conversation histories are stored locally on your device.
- **Network Access**: DroidClaw communicates with the LLM providers you configure — and with your SSH host, if you enable the remote shell backend.
