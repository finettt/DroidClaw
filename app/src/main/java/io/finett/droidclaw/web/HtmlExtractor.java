package io.finett.droidclaw.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Readability-lite HTML extraction, built on Jsoup.
 *
 * <p>Removes noise elements, picks the largest text-density container
 * ({@code article}, {@code main}, or the {@code div} with the most paragraph text) and
 * renders it either as normalized plain text or a lightweight Markdown subset
 * (headings, paragraphs, list items, links, fenced code). It never throws on odd markup.
 */
public class HtmlExtractor {

    /** HTML result carrying the page title and the extracted content. */
    public static class ExtractResult {
        private final String title;
        private final String content;

        ExtractResult(String title, String content) {
            this.title = title;
            this.content = content;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }
    }

    private static class Block {
        final int level;       // 0 unless this block is an h1-h4 heading
        final String headingText; // plain heading text, null when level == 0
        final String content;  // rendered content for the chosen format

        Block(int level, String headingText, String content) {
            this.level = level;
            this.headingText = headingText;
            this.content = content;
        }
    }

    /**
     * Extracts readable content from {@code html}.
     *
     * @param html    the raw HTML document
     * @param format  {@code "markdown"} or {@code "text"} (case-insensitive)
     * @param section optional heading substring; when set, only that section is returned
     */
    public ExtractResult extract(String html, String format, String section) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript,nav,footer,aside,form,iframe").remove();

        String title = extractTitle(doc);
        boolean markdown = "markdown".equalsIgnoreCase(format);

        Element root = pickRoot(doc);

        List<Block> blocks = new ArrayList<>();
        walk(root, markdown, blocks);

        String content;
        if (section != null && !section.trim().isEmpty()) {
            String sliced = sliceSection(blocks, section.trim(), markdown);
            content = (sliced != null) ? normalizeOut(sliced, markdown)
                    : sectionNotFound(section.trim(), blocks, markdown);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < blocks.size(); i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(blocks.get(i).content);
            }
            content = normalizeOut(sb.toString(), markdown);
        }

        return new ExtractResult(title, content);
    }

    private String extractTitle(Document doc) {
        Element titleEl = doc.head().selectFirst("title");
        String title = titleEl != null ? titleEl.text().trim() : "";
        if (title.isEmpty()) {
            Element h1 = doc.selectFirst("h1");
            title = h1 != null ? h1.text().trim() : "";
        }
        return title.isEmpty() ? null : title;
    }

    private Element pickRoot(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null && article.text().trim().length() > 0) {
            return article;
        }
        Element main = doc.selectFirst("main");
        if (main != null && main.text().trim().length() > 0) {
            return main;
        }

        // Div with the most paragraph text.
        long best = 0;
        Element bestDiv = null;
        for (Element div : doc.select("div")) {
            long score = 0;
            for (Element p : div.select("p")) {
                score += p.text().length();
            }
            if (score > best) {
                best = score;
                bestDiv = div;
            }
        }
        if (bestDiv != null && best > 0) {
            return bestDiv;
        }

        Element body = doc.body();
        return body != null ? body : doc;
    }

    /** Recursively turns block-level elements into a flat list of {@link Block}s. */
    private void walk(Element el, boolean markdown, List<Block> blocks) {
        StringBuilder textAccum = new StringBuilder();
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode) {
                String t = ((TextNode) node).text();
                if (t.trim().length() > 0) {
                    textAccum.append(t).append('\n');
                }
            } else if (node instanceof Element) {
                if (textAccum.length() > 0) {
                    String t = textAccum.toString().trim();
                    if (!t.isEmpty()) {
                        blocks.add(new Block(0, null, t));
                    }
                    textAccum.setLength(0);
                }
                addElement((Element) node, markdown, blocks);
            }
        }
        if (textAccum.length() > 0) {
            String t = textAccum.toString().trim();
            if (!t.isEmpty()) {
                blocks.add(new Block(0, null, t));
            }
        }
    }

    private void addElement(Element c, boolean markdown, List<Block> blocks) {
        String tag = c.tagName();
        switch (tag) {
            case "h1":
            case "h2":
            case "h3":
            case "h4": {
                int level = tag.charAt(1) - '0';
                String txt = c.text().trim();
                blocks.add(new Block(level, txt, markdown ? "#".repeat(level) + " " + txt : txt));
                break;
            }
            case "p":
                blocks.add(new Block(0, null, inline(c, markdown)));
                break;
            case "ul":
                for (Element li : c.select(" > li")) {
                    blocks.add(new Block(0, null, "- " + inline(li, markdown)));
                }
                break;
            case "ol": {
                int n = 1;
                for (Element li : c.select(" > li")) {
                    blocks.add(new Block(0, null, (markdown ? n + ". " : "") + inline(li, markdown)));
                    n++;
                }
                break;
            }
            case "li":
                blocks.add(new Block(0, null, "- " + inline(c, markdown)));
                break;
            case "pre":
                String code = codeText(c);
                blocks.add(new Block(0, null, markdown ? "```\n" + code + "\n```" : code));
                break;
            case "code":
                blocks.add(new Block(0, null, markdown ? "`" + inline(c, markdown) + "`" : inline(c, markdown)));
                break;
            case "blockquote":
                String q = c.text().trim();
                if (!q.isEmpty()) {
                    blocks.add(new Block(0, null,
                        markdown ? "> " + q.replace("\n", "\n> ") : q));
                }
                break;
            case "img": {
                String alt = c.attr("alt").trim();
                if (!alt.isEmpty()) {
                    blocks.add(new Block(0, null, alt));
                }
                break;
            }
            default:
                // Container (div, section, span, ...): recurse.
                walk(c, markdown, blocks);
        }
    }

    private String codeText(Element pre) {
        List<Node> kids = pre.childNodes();
        if (kids.size() == 1 && kids.get(0) instanceof Element
                && ((Element) kids.get(0)).tagName().equals("code")) {
            return ((Element) kids.get(0)).text();
        }
        return pre.text();
    }

    /** Renders inline content, turning links into {@code [text](url)} for Markdown. */
    private String inline(Element el, boolean markdown) {
        StringBuilder sb = new StringBuilder();
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode) {
                sb.append(((TextNode) node).text());
            } else if (node instanceof Element) {
                Element c = (Element) node;
                String tag = c.tagName();
                if (tag.equals("a")) {
                    String href = c.attr("href").trim();
                    String text = c.text().trim();
                    if (markdown && !href.isEmpty()) {
                        sb.append("[").append(text).append("](").append(href).append(")");
                    } else {
                        sb.append(text);
                    }
                } else if (tag.equals("code")) {
                    sb.append(markdown ? "`" : "").append(c.text()).append(markdown ? "`" : "");
                } else if (tag.equals("br")) {
                    sb.append(markdown ? "  \n" : "\n");
                } else {
                    // span, em, strong, etc.
                    sb.append(inline(c, markdown));
                }
            }
        }
        return sb.toString();
    }

    private String sliceSection(List<Block> blocks, String section, boolean markdown) {
        int start = -1;
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.level > 0 && b.headingText != null
                    && b.headingText.toLowerCase(Locale.ROOT).contains(section.toLowerCase(Locale.ROOT))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }

        int startLevel = blocks.get(start).level;
        int end = blocks.size();
        for (int i = start + 1; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.level > 0 && b.level <= startLevel) {
                end = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(blocks.get(i).content);
        }
        return sb.toString();
    }

    private String sectionNotFound(String section, List<Block> blocks, boolean markdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("Section not found: ").append(section).append("\n\n");
        sb.append("Headings found on this page:");
        int shown = 0;
        for (Block b : blocks) {
            if (b.level > 0 && shown < 10) {
                String marker = markdown ? "#".repeat(b.level) + " " : "";
                sb.append('\n').append(marker).append(b.headingText);
                shown++;
            }
        }
        return sb.toString();
    }

    private String normalizeOut(String s, boolean markdown) {
        String r = s.replaceAll("\n{3,}", "\n\n").trim();
        if (!markdown) {
            r = r.replaceAll("[ \t\r]+", " ");
        }
        return r;
    }
}
