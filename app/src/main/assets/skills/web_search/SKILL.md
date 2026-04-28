---
name: web_search
description: Search the web for information. Use when you need to find current information, research topics, or fetch web content.
---

# Web Search Skill

This skill enables you to search the web for information using the MCP SearXNG server.

## When to Use This Skill

Use this skill when:
- You need current or up-to-date information
- Researching a topic that requires web lookup
- Fetching documentation or technical references
- Finding news or recent events
- Looking up APIs, libraries, or frameworks

## Capabilities

- Search the web via the MCP `searxng_web_search` tool
- Extract and parse search results
- Follow links to gather detailed information
- Summarize findings for the user

## Available Tools and Commands

### MCP SearXNG Tool
Use the MCP `searxng_web_search` tool for all web searches.

Example parameters:
- `query`: the user search query
- `pageno`: optional, page number (starts at 1)
- `time_range`: optional, `day`, `month`, or `year`
- `language`: optional, such as `en` or `all`

### Basic Web Page Fetch
```bash
curl -L "URL"
```

## Guidelines

1. Always use the MCP `searxng_web_search` tool for web searches
2. Cite sources by including URLs in your responses
3. For complex research, break down the query into multiple searches
4. Be mindful of rate limiting and respectful of web resources
5. For JavaScript-heavy sites, note that curl only fetches static content
6. Always verify information from multiple sources when possible

## Example Usage

**User:** "What is the current weather in Tokyo?"

**Your approach:**
1. Use `searxng_web_search` with query "current weather Tokyo"
2. Parse results for weather data
3. Present formatted result to user

**User:** "Find recent news about AI"

**Your approach:**
1. Use `searxng_web_search` with query "recent news AI" and `time_range: month`
2. Extract relevant headlines and summaries
3. Present findings to user

## Limitations

- No JavaScript execution (static content only)
- Rate limits on API calls
- Some sites may block automated access
- Complex pages may require parsing
1. Use DuckDuckGo API to get instant answer
2. Parse JSON response for weather data
3. Present formatted result to user

**User:** "Find recent news about AI"

**Your approach:**
1. Search with curl to a news site or search engine
2. Extract relevant headlines and summaries
3. Present findings to user

## Limitations

- No JavaScript execution (static content only)
- Rate limits on API calls
- Some sites may block automated access
- Complex pages may require parsing
