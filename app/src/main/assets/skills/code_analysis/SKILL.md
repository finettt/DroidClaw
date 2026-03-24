---
name: code_analysis
description: Analyze code structure and quality. Use when reviewing code for bugs, anti-patterns, security issues, or best practices.
---

# Code Analysis Skill

This skill enables you to analyze code structure, detect patterns, and identify potential issues in source code.

## When to Use This Skill

Use this skill when:
- Reviewing code for bugs or potential issues
- Checking code for security vulnerabilities
- Evaluating code quality and best practices
- Identifying code smells or anti-patterns
- Analyzing code complexity or structure

## Capabilities

- Read and parse source code files
- Search for patterns and anti-patterns
- Analyze code complexity and structure
- Detect potential bugs and issues
- Suggest improvements and best practices
- Review code for common problems

## Guidelines

1. Always read the full file content before making conclusions
2. Look for common anti-patterns:
   - Code duplication
   - Long methods/classes
   - Missing error handling
   - Hardcoded values
   - Inconsistent naming
3. Check for security issues:
   - Hardcoded credentials
   - SQL injection risks
   - XSS vulnerabilities
4. Suggest improvements based on language-specific best practices
5. Consider the code's purpose and context before critiquing

## Common Patterns to Detect

### Code Smells
- Long methods (>20 lines)
- Large classes (>200 lines)
- Excessive parameters (>3)
- Magic numbers/strings

### Security Issues
- Hardcoded passwords/API keys
- SQL queries with string concatenation
- Missing input validation
- Insecure data storage

### Performance Issues
- Inefficient algorithms
- Missing early returns
- Unnecessary object creation
- Poor database query patterns

## Example Usage

**User:** "Analyze this code for bugs"

**Your approach:**
1. Read the source file
2. Search for common issue patterns
3. Analyze logic flow and edge cases
4. Present findings with line numbers and suggestions

**User:** "Review this Java code for best practices"

**Your approach:**
1. Read the Java file
2. Check for common Java anti-patterns
3. Evaluate naming conventions and structure
4. Provide specific improvement suggestions

## Limitations

- Cannot execute code to verify runtime behavior
- May miss logic errors that don't follow patterns
- Context understanding limited to code content
- No access to external dependencies or libraries
