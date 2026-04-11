---
name: automation_manager
description: Conversational task management and automation control. Use when the user wants to create, manage, monitor, or query scheduled tasks and background jobs via natural language.
category: automation
author: DroidClaw
version: 1.0
tags: automation, tasks, scheduling, management, cron, heartbeat
required_tools: create_task, list_tasks, pause_task, resume_task, delete_task, view_task_history, task_stats, setup_heartbeat
---

# Automation Manager

This skill enables conversational task management, allowing users to create, control, and monitor automated background jobs through natural language commands.

## When to Use This Skill

Use this skill when:
- User wants to create a new scheduled task via conversation
- User asks about existing scheduled tasks
- User wants to pause, resume, or delete tasks
- User queries task history or execution statistics
- User wants to set up or configure system monitoring (heartbeat)
- User asks about automation status or results

## Capabilities

- **Task Creation**: Create cron jobs from natural language descriptions
- **Task Management**: Pause, resume, edit, and delete scheduled tasks
- **Task Monitoring**: View task history, success rates, and execution logs
- **Statistics**: Query performance metrics across all tasks
- **Heartbeat Setup**: Configure system monitoring interactively
- **Status Queries**: Check when tasks last ran and their outcomes

## Available Tools

### create_task
Creates a new scheduled background task.

**Parameters:**
- `name` (string): Task name (required)
- `prompt` (string): The prompt/instructions for the LLM to execute (required)
- `schedule` (string): Schedule format "daily", "weekly", "hourly", "daily@HH:MM", "weekly@DAY@HH:MM", "every_N_unit" (e.g., "every_6_hours"), or milliseconds (required)

**Example:**
```
create_task(
  name="Daily News Summary",
  prompt="Search for today's top tech news and create a summary",
  schedule="daily@08:00"
)
```

### list_tasks
Lists all scheduled tasks with their current status.

**Parameters:**
- `filter` (string, optional): Filter by status: "all", "active", "paused", "disabled"

**Returns:** List of tasks with name, schedule, enabled/paused status, success rate

**Example:**
```
list_tasks(filter="active")
```

### pause_task
Pauses a scheduled task without deleting it.

**Parameters:**
- `task_id` (string): ID of the task to pause (required) OR
- `task_name` (string): Name of the task to pause (will find by name)

**Example:**
```
pause_task(task_name="Daily News Summary")
```

### resume_task
Resumes a previously paused task.

**Parameters:**
- `task_id` (string): ID of the task to resume (required) OR
- `task_name` (string): Name of the task to resume

**Example:**
```
resume_task(task_name="Daily News Summary")
```

### delete_task
Permanently deletes a scheduled task and its execution history.

**Parameters:**
- `task_id` (string): ID of the task to delete (required) OR
- `task_name` (string): Name of the task to delete
- `confirm` (boolean): Confirmation flag (required, must be true)

**Example:**
```
delete_task(task_name="Old Task", confirm=true)
```

### view_task_history
Views execution history for a specific task.

**Parameters:**
- `task_id` (string): ID of the task (required) OR
- `task_name` (string): Name of the task
- `limit` (integer, optional): Number of recent executions to show (default: 10)

**Returns:** List of execution records with timestamps, success/failure, duration

**Example:**
```
view_task_history(task_name="Daily News Summary", limit=5)
```

### task_stats
Gets aggregate statistics across all tasks or a specific task.

**Parameters:**
- `task_id` (string, optional): Specific task ID (omit for all tasks)
- `task_name` (string, optional): Specific task name

**Returns:** Total executions, success rate, average duration, success/failure counts

**Example:**
```
task_stats()
```

### setup_heartbeat
Interactive helper to configure system monitoring (heartbeat).

**Parameters:**
- `interval` (string): Check interval: "15min", "30min", "1hour", "2hours" (optional, default: "30min")
- `enabled` (boolean): Whether to enable heartbeat (default: true)
- `monitoring_focus` (string, optional): What to monitor (e.g., "system health", "file changes", "memory usage")

**Example:**
```
setup_heartbeat(interval="1hour", monitoring_focus="system health")
```

### submit_notification
Submit a structured notification at the end of a background task execution.

**Parameters:**
- `title` (string): Short notification title (required)
- `summary` (string): 1-2 sentence summary (required)
- `status` (string): Task status: "success", "warning", or "error" (optional, default: "success")

**Example:**
```
submit_notification(
  title="Daily Summary Complete",
  summary="Processed 15 files, created 3 reports",
  status="success"
)
```

## Guidelines

1. **Confirm Before Creating**: Always confirm task details with user before creating
2. **Use Natural Language**: Parse user intent into appropriate schedule format
3. **Validate Schedules**: Ensure schedule format is valid before creating task
4. **Show Results**: After operations, show confirmation with relevant details
5. **Handle Ambiguity**: If task name is ambiguous, show options and ask user to clarify
6. **Provide Context**: When showing history/stats, explain what the numbers mean
7. **Suggest Improvements**: If tasks have low success rates, suggest investigating

## Schedule Format Reference

| Format | Example | Description |
|--------|---------|-------------|
| hourly | `hourly` | Every hour |
| daily | `daily` | Every 24 hours |
| weekly | `weekly` | Every 7 days |
| daily@HH:MM | `daily@08:00` | Daily at specific time (24h) |
| weekly@DAY@HH:MM | `weekly@MON@09:00` | Weekly on day at time |
| every_N_unit | `every_6_hours` | Every N units (hours/minutes/days) |
| milliseconds | `3600000` | Raw interval in ms |

**Valid DAY values:** MON, TUE, WED, THU, FRI, SAT, SUN

## Common Conversational Patterns

### Pattern 1: Task Creation
**User:** "Create a task to check my email every morning"
**Agent:**
1. Parse intent: daily task at morning time
2. Clarify: "I'll create a daily task at 8:00 AM. What should it do when checking email?"
3. User: "Summarize new emails"
4. Create prompt: "Check for new emails and create a summary of important messages"
5. Confirm: "Creating 'Email Summary' task - daily at 8:00 AM. OK?"
6. Call `create_task()` with parsed parameters

### Pattern 2: Task Listing
**User:** "What scheduled tasks do I have?"
**Agent:**
1. Call `list_tasks(filter="all")`
2. Format results in readable table
3. Include status indicators (✓ active, ⏸ paused, ✗ disabled)

### Pattern 3: Task Control
**User:** "Pause the email checker"
**Agent:**
1. Find task by name using `list_tasks()`
2. Call `pause_task(task_id=<found_id>)`
3. Confirm: "Email Summary task paused"

### Pattern 4: History Query
**User:** "When did my backup task last run?"
**Agent:**
1. Find task by name
2. Call `view_task_history(task_id=<id>, limit=1)`
3. Report: "Backup task last ran yesterday at 3:00 AM - succeeded"

### Pattern 5: Heartbeat Setup
**User:** "Set up system monitoring"
**Agent:**
1. Ask: "What would you like to monitor? (e.g., system health, file changes)"
2. Ask: "How often should I check? (15min, 30min, 1hour, 2hours)"
3. Call `setup_heartbeat()` with parameters
4. Confirm: "System monitoring enabled - checking every 30 minutes"

## Error Handling

- **Task Not Found**: "I couldn't find a task named 'X'. Here are your current tasks: [list]"
- **Name Ambiguity**: "I found multiple tasks with similar names. Which one?" [show options]
- **Invalid Schedule**: "The schedule format 'X' isn't valid. Use formats like: daily@08:00, every_6_hours"
- **Permission Denied**: If heartbeat requires notifications and permission denied, warn user

## Limitations

- Minimum interval is 15 minutes (WorkManager limitation)
- Background tasks run with auto-approved tools (no user interaction)
- Tasks execute in isolated sessions (can't interact with ongoing chats)
- Maximum 10 iterations per task execution
- Maximum 5-minute execution time per task
