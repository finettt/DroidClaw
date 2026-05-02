---
name: web_fetch
description: Fetch web pages and convert HTML to clean Markdown. Use when you need to read article content, documentation, or any web page as structured text.
---

# Web Fetch Skill

This skill enables you to fetch web pages and convert their HTML content into clean, readable Markdown using BeautifulSoup4.

## When to Use This Skill

Use this skill when:
- You need to read the content of a web page or article
- Fetching documentation or technical references for analysis
- Extracting readable text from a URL for summarization or processing
- Converting a blog post, wiki page, or news article to Markdown

## Capabilities

- Fetch HTML from any URL via HTTP/HTTPS
- Convert HTML to clean Markdown preserving structure
- Supported elements: headings, paragraphs, links, lists, bold/italic, code blocks, blockquotes, tables
- Removes non-content elements like scripts, styles, navigation, and footers

## Available Tools and Commands

### Python Script: `html_to_markdown.py`

A standalone Python script is bundled with this skill at:

```
.agent/skills/web_fetch/html_to_markdown.py
```

**Usage via `execute_python`:**

```json
{
  "script_path": ".agent/skills/web_fetch/html_to_markdown.py",
  "code": "import sys; sys.argv = ['', 'https://example.com']; exec(open('.agent/skills/web_fetch/html_to_markdown.py').read())"
}
```

Or run directly with `execute_shell`:

```bash
python .agent/skills/web_fetch/html_to_markdown.py "https://example.com"
```

### Inline Python Approach

You can also write inline Python code using `execute_python`:

```python
import urllib.request
from bs4 import BeautifulSoup

url = "https://example.com"
headers = {"User-Agent": "Mozilla/5.0"}
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")

soup = BeautifulSoup(html, "html.parser")
# Extract and format content as needed
print(soup.get_text())
```

## Guidelines

1. Always provide the full URL including `https://`
2. The script automatically strips scripts, styles, nav, footer, and aside elements
3. For JavaScript-heavy sites, only static HTML is fetched
4. Respect rate limits and do not hammer the same server repeatedly
5. If a site blocks requests, try adding a custom User-Agent header
6. For paywalled content, the script will only receive what the server returns

## Example Usage

**User:** "Fetch https://news.ycombinator.com and summarize the top stories"

**Your approach:**
1. Use `execute_python` with `script_path: .agent/skills/web_fetch/html_to_markdown.py`
2. Pass the URL as argument
3. Parse the resulting Markdown
4. Summarize the top stories for the user

**User:** "Convert this documentation page to Markdown: https://docs.python.org/3/tutorial/"

**Your approach:**
1. Run the `html_to_markdown.py` script with the URL
2. Present the clean Markdown output to the user
3. Offer to save it to a file if needed

## Limitations

- No JavaScript execution — only static HTML is fetched
- Some sites may block automated requests
- Paywalled or login-required content cannot be accessed
- Very large pages may be truncated by timeout limits