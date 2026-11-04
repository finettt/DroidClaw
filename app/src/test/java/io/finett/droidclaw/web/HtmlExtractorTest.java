package io.finett.droidclaw.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.finett.droidclaw.web.HtmlExtractor.ExtractResult;
import org.junit.Test;

/** Unit tests for readability-lite HTML extraction. */
public class HtmlExtractorTest {

    private final HtmlExtractor extractor = new HtmlExtractor();

    private ExtractResult md(String html) {
        return extractor.extract(html, "markdown", null);
    }

    private ExtractResult text(String html) {
        return extractor.extract(html, "text", null);
    }

    @Test
    public void extractsTitleFromTitleTag() {
        ExtractResult r = md("<html><head><title>My Page</title></head><body>"
                + "<article><p>hi</p></article></body></html>");
        assertEquals("My Page", r.getTitle());
    }

    @Test
    public void titleFallsBackToFirstH1() {
        ExtractResult r = md("<html><body><article><h1>Heading Only</h1>"
                + "<p>body</p></article></body></html>");
        assertEquals("Heading Only", r.getTitle());
    }

    @Test
    public void titleIsNullWhenAbsent() {
        ExtractResult r = md("<html><body><article><p>just text</p></article></body></html>");
        assertNotNull(r.getContent());
        // No title tag and no h1 -> null title.
        assertTrue(r.getTitle() == null);
    }

    @Test
    public void removesScriptStyleAndNoiseElements() {
        String html = "<article>"
                + "<p>visible text</p>"
                + "<script>var x = 1;</script>"
                + "<style>.a{color:red}</style>"
                + "<nav>nav link</nav>"
                + "<footer>footer text</footer>"
                + "<aside>aside text</aside>"
                + "<form>form input</form>"
                + "<iframe src='x'></iframe>"
                + "<noscript>noscript text</noscript>"
                + "</article>";
        ExtractResult r = md(html);
        String content = r.getContent();
        assertTrue("should keep visible text", content.contains("visible text"));
        assertFalse("script body leaked", content.contains("var x"));
        assertFalse("style body leaked", content.contains("color:red"));
        assertFalse("nav leaked", content.contains("nav link"));
        assertFalse("footer leaked", content.contains("footer text"));
        assertFalse("aside leaked", content.contains("aside text"));
        assertFalse("form leaked", content.contains("form input"));
        assertFalse("noscript leaked", content.contains("noscript text"));
    }

    @Test
    public void markdownHeadingsAndLinks() {
        String html = "<article>"
                + "<h1>Title One</h1>"
                + "<h2>Title Two</h2>"
                + "<h3>Title Three</h3>"
                + "<h4>Title Four</h4>"
                + "<p>See <a href=\"https://example.com\">example</a> here.</p>"
                + "</article>";
        ExtractResult r = md(html);
        String content = r.getContent();
        assertTrue(content.contains("# Title One"));
        assertTrue(content.contains("## Title Two"));
        assertTrue(content.contains("### Title Three"));
        assertTrue(content.contains("#### Title Four"));
        assertTrue(content.contains("[example](https://example.com)"));
    }

    @Test
    public void textFormatStripsMarkdownMarkers() {
        String html = "<article><h1>Heading</h1><p>para text</p></article>";
        ExtractResult r = text(html);
        String content = r.getContent();
        assertTrue(content.contains("Heading"));
        assertFalse("markdown marker should be absent in text mode", content.startsWith("#"));
        assertFalse(content.contains("# "));
    }

    @Test
    public void textFormatNormalizesWhitespace() {
        String html = "<article><p>one</p>\n\n\n\n<p>two</p></article>";
        ExtractResult r = text(html);
        String content = r.getContent();
        // No run of three or more newlines remains.
        assertFalse(content.contains("\n\n\n"));
        assertTrue(content.contains("one"));
        assertTrue(content.contains("two"));
    }

    @Test
    public void sectionHitReturnsUpToNextHeadingOfSameLevel() {
        String html = "<article>"
                + "<h1>Main</h1><p>intro</p>"
                + "<h2>Section A</h2><p>content A</p>"
                + "<h2>Section B</h2><p>content B</p>"
                + "</article>";
        ExtractResult r = md(html);
        // Target by substring match on the heading.
        ExtractResult section = extractor.extract(html, "markdown", "section a");
        String content = section.getContent();
        assertTrue(content.contains("## Section A"));
        assertTrue(content.contains("content A"));
        assertFalse("should stop before next section", content.contains("content B"));
        assertFalse("should not include the h1 section header", content.contains("# Main"));
    }

    @Test
    public void sectionMissListsHeadings() {
        String html = "<article>"
                + "<h1>Alpha</h1>"
                + "<h2>Bravo</h2>"
                + "<h2>Charlie</h2>"
                + "</article>";
        ExtractResult r = extractor.extract(html, "markdown", "DoesNotExist");
        String content = r.getContent();
        assertTrue(content.contains("Section not found: DoesNotExist"));
        assertTrue(content.contains("Alpha"));
        assertTrue(content.contains("Bravo"));
        assertTrue(content.contains("Charlie"));
    }

    @Test
    public void sectionMissListsAtMostTenHeadings() {
        StringBuilder html = new StringBuilder("<article>");
        for (int i = 0; i < 25; i++) {
            html.append("<h2>").append("Heading ").append(i).append("</h2>");
        }
        html.append("</article>");
        ExtractResult r = extractor.extract(html.toString(), "markdown", "Nope");
        String content = r.getContent();
        int headingLines = 0;
        for (String line : content.split("\n")) {
            if (line.startsWith("#") && line.trim().startsWith("#")) {
                headingLines++;
            }
        }
        assertTrue("should list at most 10 headings, was " + headingLines, headingLines <= 10);
        assertTrue(content.contains("Section not found: Nope"));
    }

    @Test
    public void extractsFromDivWithoutArticleOrMain() {
        String html = "<html><head><title>Div Page</title></head><body>"
                + "<div class='main'><p>first paragraph with content</p>"
                + "<p>second paragraph with more content</p></div>"
                + "<div class='sidebar'><p>ad</p></div>"
                + "</body></html>";
        ExtractResult r = md(html);
        String content = r.getContent();
        assertTrue(content.contains("first paragraph with content"));
        assertTrue(content.contains("second paragraph with more content"));
        // The smaller sidebar div should be excluded (lower text density).
        assertFalse(content.contains("ad"));
        assertEquals("Div Page", r.getTitle());
    }

    @Test
    public void neverThrowsOnOddMarkup() {
        // Malformed / unclosed tags should not throw.
        ExtractResult r = extractor.extract("<article><p>unclosed <b>bold<p>text", "markdown", null);
        assertNotNull(r.getContent());
    }
}
