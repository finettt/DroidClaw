# Workflows

Workflows let one chat request fan out into several focused subagents. A workflow
is a JSON file in the workspace at `.agent/workflows/<name>.json`. The agent
discovers workflows with `list_files(".agent/workflows/")` and launches one with
the `run_workflow` tool; you can also ask the agent directly, e.g. *"run the
morning-digest workflow"*.

DroidClaw ships three template workflows. They are seeded into
`.agent/workflows/` on first run and are safe to edit — your edited copy is
never overwritten by app updates.

## Bundled templates

| File | What it does |
|------|--------------|
| `morning-digest.json` | Reads today's events via `calendar_list_events`, summarizes them, and saves a digest to `home/notes/morning-digest.md`. Linear chain: `collect → summarize → write_note`. |
| `workspace-audit.json` | Inventories the sandbox with `list_files`/`file_info`, hunts for stale content with `search_files`, and writes a report to `home/documents/workspace-audit.md`. |
| `two-step-refactor.json` | A read-only agent analyzes a file and proposes exactly one edit; a second agent applies it with `edit_file`; an optional third re-reads and verifies. Demonstrates `retry` (per-node override of `defaults.retry`) and `on_error: continue` (a failed verification does not kill the run). Give it a file to work on as the chat message — the request reaches the first node as `{{workflow.input}}`. |

The write steps in all three templates use `"approval": "inherit"`, so writes
follow your global approval settings instead of being silently rejected by the
`deny_writes` default (see below).

## File format (workflow v1)

The machine-readable contract is `app/src/main/assets/workflow-v1.schema.json`
(JSON Schema draft-07). Top level:

```json
{
  "version": 1,
  "name": "Human-readable name",
  "goal": "One sentence; injected as 'Goal: …' before every node prompt.",
  "entry": "first_node",
  "output": "{{last_node.output}}",
  "defaults": { "max_turns": 8, "timeout_ms": 180000 },
  "agents": { "first_node": { "prompt": { "text": "…" } } }
}
```

- `version` — must be the number `1`.
- `goal` *(required)* — prepended to every node prompt as `Goal: <goal>`.
- `agents` *(required)* — 1–64 nodes. Keys must match `^[a-z][a-z0-9_]{0,63}$`.
- `entry` — a node key or array of keys. Optional; roots are inferred from the
  dependency graph, but naming them silences a validator warning.
- `output` — template for the workflow result, e.g. `{{report.output}}`.
- `defaults` — per-node settings applied when a node does not override them
  (resolution is node → defaults → built-in default).

### Node fields (`agents.<key>`)

| Field | Meaning |
|-------|---------|
| `prompt` *(required)* | Exactly one of `text`, `template`, or `from_agent` (plus optional `system`). `from_agent: "x"` feeds node x's output in and creates a dependency edge. |
| `model` | `providerId/modelId` reference. Omit to use your configured default model. |
| `depends_on` | Explicit dependency edges. Edges are also inferred from `{{other_node.output}}` references and `from_agent`. |
| `inputs` | Named template bindings available to this node as `{{inputs.name}}`. |
| `when` | Guard expression: `{{node.field}} == 'value'`, `!=`, or `contains`, with a single-quoted literal. A false guard skips the node **and every transitive dependent**. |
| `allowed_tools` / `denied_tools` | Tool scope (see security below). `[]` means "no tools", which is different from unset (= all registered tools). |
| `approval` | `inherit`, `auto_approve`, `deny_writes`, `strict`. **Omitted defaults to `deny_writes`.** |
| `on_error` | `fail` (default — abort the run), `skip` (mark failed node and all transitive dependents as skipped, keep going), `continue` (record an error result and let dependents run with it). |
| `retry` | `{ "max_attempts": 1–5, "backoff_ms": 0–30000 }`. Only transport/timeout errors are retried, never content errors. |
| `max_turns`, `timeout_ms` | Turn and time budget. `timeout_ms` is a node-wide budget: it covers all retry attempts **and** backoff delays, not a fresh budget per attempt. |
| `output_schema` | JSON Schema for structured output; enables `{{node.output.field}}` access downstream. |

Templates support `{{workflow.input}}` (the chat message that launched the run),
`{{workflow.goal}}`, `{{workflow.name}}`, `{{node.output}}`,
`{{node.output.field}}` (with `output_schema`), and `{{inputs.name}}`.

### Validation

Files are parsed leniently (common misspellings like `propmt` are repaired with
a warning) and then strictly validated: unknown tools and model references,
unbalanced `{{ }}`, references to undeclared agents, and dependency cycles are
errors; a workflow with errors will not run. Warnings flag likely mistakes such
as a root node missing from `entry` or a node whose output nobody consumes.
Workflow files are capped at 64 KB.

## Security model

- **Content-bound approval.** `run_workflow` always requires user approval,
  regardless of global auto-approve settings. The approval dialog shows a parsed
  review — goal, per-node models, effective allowed/denied tools, effective
  approval policy — plus the SHA-256 of the exact file bytes. The file is
  re-read and re-hashed at launch: if it was modified between review and launch,
  the run is rejected. A hash supplied by the model is never accepted as
  evidence of approval.
- **Scoped tool registry.** Each node sees only its resolved tool scope:
  `allowed_tools` (or all registered tools when unset) minus `denied_tools`,
  with `run_workflow` always excluded — workflows cannot start workflows, so
  there is no recursion.
- **Approval floor.** The default node policy is `deny_writes`: read-only tools
  run freely and approval-requiring tools (writes, deletes, calendar mutations)
  are auto-rejected. `inherit` follows your global approval config and is meant
  for foreground runs; `auto_approve` is an explicit opt-in that overrides
  global per-tool modes; `strict` rejects all tool calls. Globally
  `ALWAYS_REJECT`-ed tools stay rejected under every policy except an explicit
  `auto_approve`. Shell-dependent tools stay disabled when shell access is off.
- **Safe file loading.** Workflow names must match `[a-zA-Z0-9_-]{1,64}` (no
  path separators); the resolved path may not contain symbolic links; files
  over 64 KB are rejected.

## Limitations (honest list)

- **The runner is sequential.** `max_parallel` is accepted by the schema but
  **not implemented**: nodes execute one at a time in deterministic topological
  order, as if `max_parallel` were 1. Declaring it changes nothing today.
- `on_error: skip` cascades: every transitive dependent of the skipped node is
  also skipped, and a skipped node contributes an empty string to templates and
  the workflow `output`.
- A false `when` guard behaves like `skip` for the node and its dependents.
- Run-scoped model and approval overrides are cleared on every terminal
  callback path, including errors ([#147](https://github.com/finettt/DroidClaw/issues/147)).
- Synchronous waits run off the main callback looper, so a workflow cannot ANR
  the app through its own callbacks ([#149](https://github.com/finettt/DroidClaw/issues/149)).
- `timeout_ms` expires the node, but in-flight tool/LLM cancellation and
  request isolation are still being hardened
  ([#144](https://github.com/finettt/DroidClaw/issues/144)); per-node `model`
  application has open work ([#145](https://github.com/finettt/DroidClaw/issues/145)).
- Nested workflows are rejected by design, not a bug.

## Writing your own

1. Copy a template in `.agent/workflows/` to a new name matching
   `[a-zA-Z0-9_-]{1,64}.json` — the agent itself can do this with `write_file`.
2. Keep write-capable tools confined to as few nodes as possible and give those
   nodes `"approval": "inherit"` (foreground) — or leave the `deny_writes`
   default and treat the workflow as read-only.
3. Ask the agent to run it; review the approval summary before accepting.

*Русская версия: [workflows.ru.md](workflows.ru.md)*
