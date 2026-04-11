# System Heartbeat Checklist

Perform a comprehensive system health check. Review the following areas:

## Checklist

- [ ] Review recent memories for important patterns or unresolved issues
- [ ] Check workspace for incomplete tasks or stale work
- [ ] Verify critical files are backed up and accessible
- [ ] Scan for urgent messages, errors, or failed operations
- [ ] Assess if any pending actions need immediate attention

## Response Guidelines

At the end of your check, **always call the `submit_notification` tool** with:
- **title**: Short status (e.g., "Heartbeat - All Clear", "Heartbeat - Issues Detected")
- **summary**: Brief description of findings
- **status**: "success" if all normal, "warning" if minor concerns, "error" if critical issues

Respond with a JSON object containing:
- `"healthy"`: `true` if all items are normal, `false` if anything needs attention
- `"summary"`: A brief summary of the system health status
- `"issues"`: An array of issues found (empty if all normal), each with:
  - `"category"`: The area affected (e.g., "workspace", "memory", "tasks")
  - `"description"`: What needs attention
  - `"severity"`: "low", "medium", or "high"

If all items are normal, set `healthy` to `true` with an empty issues array and a brief summary.

If anything needs attention, set `healthy` to `false` and describe each issue:
- What specific area needs attention
- Why it matters
- Suggested next steps

Keep your response concise and actionable.
