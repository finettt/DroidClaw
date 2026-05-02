#!/usr/bin/env python3
"""
Fetch a web page and convert its HTML to Markdown.

Usage:
    python html_to_markdown.py <url>
    echo "<url>" | python html_to_markdown.py
"""

import re
import sys
import textwrap
import urllib.error
import urllib.request

from bs4 import BeautifulSoup, NavigableString


class HTMLToMarkdownConverter:
    """Convert HTML to Markdown text."""

    def convert(self, html):
        soup = BeautifulSoup(html, "html.parser")

        # Remove non-content elements
        for tag in soup.find_all(["script", "style", "nav", "footer", "aside"]):
            tag.decompose()

        # Prefer main content areas
        root = (
            soup.find("main")
            or soup.find("article")
            or soup.find("div", class_=re.compile("content|main|article"))
            or soup.find("body")
            or soup
        )

        self.lines = []
        self.list_stack = []
        self._process_element(root)
        return "\n".join(self.lines).strip()

    def _process_element(self, element):
        if isinstance(element, NavigableString):
            text = str(element)
            if not getattr(self, "_in_pre", False):
                text = re.sub(r"\s+", " ", text)
            if text and text != " ":
                self._add_text(text)
            return

        tag = element.name
        if tag is None:
            return

        if tag in ["script", "style", "nav", "footer", "aside"]:
            return

        if tag in ["h1", "h2", "h3", "h4", "h5", "h6"]:
            self._heading(element, int(tag[1]))
        elif tag == "p":
            self._paragraph(element)
        elif tag == "br":
            self._add_text("  \n")
        elif tag == "hr":
            self._ensure_blank()
            self.lines.append("---")
            self.lines.append("")
        elif tag == "a":
            self._link(element)
        elif tag == "img":
            self._image(element)
        elif tag in ["strong", "b"]:
            self._wrap_children(element, "**")
        elif tag in ["em", "i"]:
            self._wrap_children(element, "*")
        elif tag == "code":
            self._inline_code(element)
        elif tag == "pre":
            self._preformatted(element)
        elif tag == "blockquote":
            self._blockquote(element)
        elif tag == "ul":
            self._list(element, "- ")
        elif tag == "ol":
            self._list(element, "1. ")
        elif tag == "li":
            self._list_item(element)
        elif tag == "table":
            self._table(element)
        elif tag in ["thead", "tbody"]:
            for child in element.children:
                self._process_element(child)
        elif tag == "tr":
            self._table_row(element)
        elif tag in ["th", "td"]:
            self._table_cell(element)
        else:
            for child in element.children:
                self._process_element(child)

    def _add_text(self, text):
        if not self.lines:
            self.lines.append(text)
        else:
            self.lines[-1] += text

    def _ensure_blank(self):
        if self.lines and self.lines[-1]:
            self.lines.append("")

    def _heading(self, element, level):
        self._ensure_blank()
        text = self._extract_text(element).strip()
        if text:
            self.lines.append("#" * level + " " + text)
            self.lines.append("")

    def _paragraph(self, element):
        self._ensure_blank()
        text = self._extract_text(element).strip()
        if text:
            self.lines.append(text)
            self.lines.append("")

    def _link(self, element):
        href = element.get("href", "")
        text = self._extract_text(element).strip() or href
        self._add_text(f"[{text}]({href})")

    def _image(self, element):
        src = element.get("src", "")
        alt = element.get("alt", "")
        self._add_text(f"![{alt}]({src})")

    def _wrap_children(self, element, marker):
        self._add_text(marker)
        for child in element.children:
            self._process_element(child)
        self._add_text(marker)

    def _inline_code(self, element):
        text = element.get_text()
        if "\n" in text:
            self._add_text(f"```\n{text}\n```")
        else:
            self._add_text(f"`{text}`")

    def _preformatted(self, element):
        self._ensure_blank()
        code = element.find("code")
        lang = ""
        if code:
            cls = code.get("class", [])
            if isinstance(cls, list) and cls:
                lang = cls[0].replace("language-", "")
            text = code.get_text()
        else:
            text = element.get_text()
        self.lines.append(f"```{lang}")
        self.lines.append(text.rstrip())
        self.lines.append("```")
        self.lines.append("")

    def _blockquote(self, element):
        self._ensure_blank()
        text = self._extract_text(element).strip()
        for line in text.split("\n"):
            self.lines.append("> " + line)
        self.lines.append("")

    def _list(self, element, prefix):
        self.list_stack.append(prefix)
        for child in element.children:
            self._process_element(child)
        self.list_stack.pop()
        self.lines.append("")

    def _list_item(self, element):
        depth = len(self.list_stack)
        prefix = self.list_stack[-1] if self.list_stack else "- "
        text = self._extract_text(element).strip()
        if text:
            indent = "    " * (depth - 1)
            lines = textwrap.indent(text, indent + "    ").split("\n")
            lines[0] = indent + prefix + lines[0].lstrip()
            self.lines.extend(lines)
            self.lines.append("")

    def _table(self, element):
        self._ensure_blank()
        rows = []
        for tr in element.find_all("tr"):
            cells = []
            for cell in tr.find_all(["th", "td"]):
                cells.append(cell.get_text(strip=True))
            if cells:
                rows.append(cells)
        if not rows:
            return
        max_cols = max(len(r) for r in rows)
        for r in rows:
            while len(r) < max_cols:
                r.append("")
        for i, r in enumerate(rows):
            self.lines.append("| " + " | ".join(r) + " |")
            if i == 0:
                self.lines.append("| " + " | ".join(["---"] * max_cols) + " |")
        self.lines.append("")

    def _table_row(self, element):
        pass  # handled in _table

    def _table_cell(self, element):
        pass  # handled in _table

    def _extract_text(self, element):
        parts = []
        for child in element.children:
            if isinstance(child, NavigableString):
                parts.append(str(child))
            elif child.name == "br":
                parts.append("  \n")
            elif child.name in ["strong", "b"]:
                parts.append(f"**{self._extract_text(child)}**")
            elif child.name in ["em", "i"]:
                parts.append(f"*{self._extract_text(child)}*")
            elif child.name == "code":
                parts.append(f"`{self._extract_text(child)}`")
            elif child.name == "a":
                href = child.get("href", "")
                text = self._extract_text(child)
                parts.append(f"[{text}]({href})")
            else:
                parts.append(self._extract_text(child))
        return "".join(parts)


def fetch_url(url, timeout=30):
    headers = {
        "User-Agent": "Mozilla/5.0 (compatible; DroidClaw/1.0)"
    }
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def main():
    if len(sys.argv) > 1:
        url = sys.argv[1]
    else:
        url = sys.stdin.read().strip()

    if not url:
        print("Usage: python html_to_markdown.py <url>", file=sys.stderr)
        sys.exit(1)

    try:
        html = fetch_url(url)
    except urllib.error.URLError as e:
        print(f"Error fetching URL: {e}", file=sys.stderr)
        sys.exit(1)

    converter = HTMLToMarkdownConverter()
    print(converter.convert(html))


if __name__ == "__main__":
    main()