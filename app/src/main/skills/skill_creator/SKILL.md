---
name: skill_creator
description: Help users create custom skills through conversation. Use when users ask to create, modify, or build new skills.
---

# Skill Creator Skill

This skill enables you to help users create custom skills through an interactive conversational process.

## Capabilities

- Interactive skill creation through chat dialogue
- Guided questions to extract skill requirements
- Auto-generate SKILL.md with proper YAML frontmatter
- Validate and save to `.agent/skills/custom/`
- Preview and iterate on skill content
- Suggest appropriate categories, tags, and required tools

## How It Works

When a user asks to create a skill:

1. Ask clarifying questions about:
   - The skill's purpose and capabilities
   - Required tools (execute_shell, read_file, write_file, etc.)
   - Guidelines for using the skill
   - Example use cases

2. Generate a properly formatted SKILL.md file with YAML frontmatter

3. Create the skill directory and save the file

4. Offer to enable the new skill immediately

5. Allow user to iterate and refine through continued conversation

## Guidelines

1. Always ask clarifying questions before creating a skill
2. Suggest appropriate tool names based on user requirements
3. Generate properly formatted YAML frontmatter
4. Validate skill content before saving
5. Save skills to `.agent/skills/[name]/SKILL.md`
6. Offer to show the generated skill for review before saving
7. Allow users to request revisions through continued conversation

## Example Usage

**User:** "Help me create a skill for analyzing Android logcat output"

**Your approach:**
1. Ask: "What specific capabilities should this skill have?"
   - Parse logcat format?
   - Filter by priority levels?
   - Search for crash patterns?
   - Generate summary reports?

2. Ask: "What tools will it need?"
   - Suggest: execute_shell, read_file, write_file

3. Ask: "Any specific guidelines for using this skill?"

4. Generate SKILL.md content and show it to the user

5. Ask: "Would you like me to save this as skill_logcat_analyzer.md?"

## Limitations

- Cannot create skills that require new tool types
- Skills must fit within the 100KB size limit
- Skill names must be lowercase with underscores only