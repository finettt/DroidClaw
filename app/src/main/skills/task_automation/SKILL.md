---
name: task_automation
version: 1.0.0
description: Common automation workflows and repetitive tasks
author: system
category: automation
enabled: true
required_tools:
  - execute_shell
  - read_file
  - write_file
tags:
  - automation
  - workflow
  - batch-operations
---

# Task Automation Skill

This skill enables you to automate common workflows, perform batch operations, and execute repetitive tasks efficiently.

## Capabilities

- Batch file operations (copy, move, rename, delete)
- Scheduled or one-time task execution
- Data migration and synchronization
- Report generation
- Log file rotation and cleanup
- System maintenance tasks

## Available Tools

### File Operations
```bash
list_files(path=".")
read_file(path="config.txt")
write_file(path="output.txt", content="...")
delete_file(path="temp/file.txt")
```

### Shell Commands for Automation
```bash
# Copy files
cp -r source/ destination/

# Move files
mv *.txt archive/

# Delete old files
find . -name "*.tmp" -mtime +7 -delete

# Create backup
tar -czf backup.tar.gz data/
```

## Guidelines

1. Always confirm destructive operations before executing
2. Use dry-run or preview mode when possible
3. Log automation actions for audit trails
4. Handle errors gracefully and continue with remaining tasks
5. Provide progress updates for long-running tasks
6. Clean up temporary files after completion

## Common Automation Patterns

### File Management
- Batch rename files
- Organize files by type/date
- Create backups
- Clean up temporary files

### Data Operations
- Merge multiple files
- Split large files
- Convert file formats
- Synchronize directories

### System Tasks
- Rotate log files
- Archive old data
- Generate reports
- Send notifications

## Example Usage

**User:** "Automate backup of my documents"

**Your approach:**
1. List documents directory
2. Create backup directory
3. Copy files with timestamp
4. Verify backup integrity
5. Report completion

**User:** "Clean up temporary files older than 7 days"

**Your approach:**
1. List files in temp directory
2. Identify old files
3. Delete old files
4. Report cleanup results

## Limitations

- No access to system-level scheduling (cron, etc.)
- File operations limited to workspace
- No email or notification system integration
- No GUI automation capabilities