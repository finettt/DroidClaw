---
name: web_search
description: Search the web for information. Use when you need to find current information, research topics, or fetch web content.
---

# Web Search Skill

This skill enables you to search the web for information. Prefer the built-in `searxng_search` tool when a SearXNG instance is configured, and fall back to command-line tools like curl and wget only when needed.

## When to Use This Skill

Use this skill when:
- You need current or up-to-date information
- Researching a topic that requires web lookup
- Fetching documentation or technical references
- Finding news or recent events
- Looking up APIs, libraries, or frameworks

## Capabilities

- Search using the `searxng_search` tool for structured web results
- Search using curl/wget to query search engines when SearXNG is not configured
- Extract and parse search results
- Follow links to gather detailed information
- Summarize findings for the user

## Available Tools and Commands

### Preferred: SearXNG tool
If the environment variable `SEARXNG_URL` is configured in Settings → Environment Variables, use the built-in `searxng_search` tool first.

Example parameters:
- `query`: the user search query
- `categories`: optional, such as `general` or `news`
- `language`: optional, such as `en` or `all`
- `max_results`: optional, default 5

### DuckDuckGo Instant Answer API
```bash
curl "https://api.duckduckgo.com/?q=QUERY&format=json"
```

### Basic Web Page Fetch
```bash
curl -L "URL"
```

### Google Search via command line
```bash
curl -L "https://www.google.com/search?q=QUERY"
```

### Wikipedia Search
```bash
curl -L "https://en.wikipedia.org/w/api.php?action=search&search=QUERY&format=json"
```

## Guidelines

1. If `SEARXNG_URL` is configured, prefer the `searxng_search` tool over raw shell commands
2. Use DuckDuckGo API or other shell-based fallbacks only when SearXNG is unavailable or unsuitable
3. Cite sources by including URLs in your responses
4. For complex research, break down the query into multiple searches
5. Be mindful of rate limiting and respectful of web resources
6. For JavaScript-heavy sites, note that curl only fetches static content
7. Always verify information from multiple sources when possible

## Example Usage

**User:** "What is the current weather in Tokyo?"

**Your approach:**
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
