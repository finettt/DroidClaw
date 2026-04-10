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

If **all items are normal**, also include `HEARTBEAT_OK` in your text response.

If **anything needs attention**, describe what requires review:
- What specific area needs attention
- Why it matters
- Suggested next steps

Keep your response concise and actionable.
