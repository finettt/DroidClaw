---
name: web_search
description: Search the web for information. Use when you need to find current information, research topics, or fetch web content.
---

# Web Search Skill

This skill enables you to search the web for information using the built-in `searxng_web_search` tool.

## When to Use This Skill

Use this skill when:
- You need current or up-to-date information
- Researching a topic that requires web lookup
- Fetching documentation or technical references
- Finding news or recent events
- Looking up APIs, libraries, or frameworks

## Capabilities

- Search the web via the `searxng_web_search` tool
- Extract and parse search results
- Follow links to gather detailed information
- Summarize findings for the user

## Available Tools and Commands

### `searxng_web_search`

The built-in tool for all web searches.

Parameters:
- `query` (required): the user search query
- `pageno` (optional): page number for pagination (starts at 1)
- `time_range` (optional): filter by time — `day`, `month`, or `year`
- `language` (optional): language code such as `en`, `ru`, or `all` (default: `all`)

### Basic Web Page Fetch

For fetching a specific page after finding its URL via search:

```bash
curl -L "URL"
```

## Configuration

The tool reads the `SEARXNG_URL` environment variable to determine which SearXNG instance to use.

- **Set in:** Settings → Environment Variables
- **Key:** `SEARXNG_URL`
- **Value:** `https://searx.example.com` (your instance URL)
- **Fallback:** if not set, a default public instance is used

## Guidelines

1. Always use the `searxng_web_search` tool for web searches
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
