package io.finett.droidclaw.util;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * JVM unit tests for {@link ChatExportManager} (Markdown only) and
 * {@link ChatImportManager} (Markdown + JSON parsing that uses only java.* APIs).
 *
 * JSON export is excluded here because it relies on org.json.JSONObject which is
 * Android-only and not available without Robolectric. Full JSON round-trip coverage
 * lives in the instrumented test suite.
 */
public class ChatExportImportTest {

    // ChatExportManager requires a Context for PDF only; Markdown paths don't use it.
    private ChatExportManager exportManager;
    private ChatImportManager importManager;

    private List<ChatMessage> sampleMessages;
    private ChatSession sampleSession;

    @Before
    public void setUp() {
        // Pass null context — only Markdown code paths are tested here (no PDF, no Context needed).
        exportManager = new ChatExportManager(null);
        importManager = new ChatImportManager();

        sampleMessages = new ArrayList<>();
        sampleMessages.add(new ChatMessage("Hello from user", ChatMessage.TYPE_USER));
        sampleMessages.add(new ChatMessage("Hello from assistant!", ChatMessage.TYPE_ASSISTANT));
        sampleMessages.add(ChatMessage.createToolResultMessage("tc1", "read_file", "file contents here"));

        sampleSession = new ChatSession("session-1", "Test Session", System.currentTimeMillis());
    }

    // ==================== Markdown Export ====================

    @Test
    public void markdownExport_containsDocumentHeader() throws IOException {
        String md = exportToMarkdown(null, sampleMessages);
        assertTrue("Should start with DroidClaw header", md.contains("# DroidClaw Chat Export"));
    }

    @Test
    public void markdownExport_containsSessionMetadata() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain session title", md.contains("Test Session"));
    }

    @Test
    public void markdownExport_containsUserHeader() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain ### User header", md.contains("### User"));
    }

    @Test
    public void markdownExport_containsUserContent() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain user message content", md.contains("Hello from user"));
    }

    @Test
    public void markdownExport_containsAssistantHeader() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain ### Assistant header", md.contains("### Assistant"));
    }

    @Test
    public void markdownExport_containsAssistantContent() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain assistant message content", md.contains("Hello from assistant!"));
    }

    @Test
    public void markdownExport_containsToolResultHeader() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain tool result header",
                md.contains("#### Tool Result: `read_file`"));
    }

    @Test
    public void markdownExport_containsToolResultContent() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        assertTrue("Should contain tool result content", md.contains("file contents here"));
    }

    @Test
    public void markdownExport_withNullSession_stillProducesOutput() throws IOException {
        String md = exportToMarkdown(null, sampleMessages);
        assertNotNull(md);
        assertTrue(md.contains("Hello from user"));
    }

    @Test
    public void markdownExport_systemMessageIsBlockquoted() throws IOException {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatMessage("System notice here", ChatMessage.TYPE_SYSTEM));
        String md = exportToMarkdown(null, msgs);
        assertTrue("System messages should start with >", md.contains("> **System:**"));
        assertTrue(md.contains("System notice here"));
    }

    // ==================== Markdown Round-trip ====================

    @Test
    public void markdownRoundTrip_preservesMessageCount() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertEquals(sampleMessages.size(), result.messages.size());
    }

    @Test
    public void markdownRoundTrip_firstMessageIsUser() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertEquals(ChatMessage.TYPE_USER, result.messages.get(0).getType());
    }

    @Test
    public void markdownRoundTrip_userContentPreserved() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertNotNull(result.messages.get(0).getContent());
        assertTrue(result.messages.get(0).getContent().contains("Hello from user"));
    }

    @Test
    public void markdownRoundTrip_secondMessageIsAssistant() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertEquals(ChatMessage.TYPE_ASSISTANT, result.messages.get(1).getType());
    }

    @Test
    public void markdownRoundTrip_assistantContentPreserved() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertNotNull(result.messages.get(1).getContent());
        assertTrue(result.messages.get(1).getContent().contains("Hello from assistant!"));
    }

    @Test
    public void markdownRoundTrip_toolResultPreserved() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertEquals(ChatMessage.TYPE_TOOL_RESULT, result.messages.get(2).getType());
        assertEquals("read_file", result.messages.get(2).getToolName());
    }

    @Test
    public void markdownRoundTrip_toolResultContentPreserved() throws IOException {
        String md = exportToMarkdown(sampleSession, sampleMessages);
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertNotNull(result.messages.get(2).getContent());
        assertTrue(result.messages.get(2).getContent().contains("file contents here"));
    }

    @Test
    public void markdownImport_emptyInput_returnsEmptyList() throws IOException {
        ChatImportManager.ImportResult result = importFromMarkdown("");
        assertTrue("Empty input should produce no messages", result.messages.isEmpty());
    }

    @Test
    public void markdownImport_onlyHeader_returnsEmptyList() throws IOException {
        String md = "# DroidClaw Chat Export\n\n**Session:** My Session\n";
        ChatImportManager.ImportResult result = importFromMarkdown(md);
        assertTrue("Header-only Markdown should produce no messages", result.messages.isEmpty());
    }

    // ==================== Filename helper ====================

    @Test
    public void buildExportFileName_includesMdExtension() {
        String name = exportManager.buildExportFileName(sampleSession, "md");
        assertTrue("Should end with .md", name.endsWith(".md"));
    }

    @Test
    public void buildExportFileName_includesJsonExtension() {
        String name = exportManager.buildExportFileName(sampleSession, "json");
        assertTrue("Should end with .json", name.endsWith(".json"));
    }

    @Test
    public void buildExportFileName_includesPdfExtension() {
        String name = exportManager.buildExportFileName(sampleSession, "pdf");
        assertTrue("Should end with .pdf", name.endsWith(".pdf"));
    }

    @Test
    public void buildExportFileName_fallsBackForNullSession() {
        String name = exportManager.buildExportFileName(null, "json");
        assertTrue("Should end with .json", name.endsWith(".json"));
        assertTrue("Should use droidclaw-chat prefix", name.startsWith("droidclaw-chat"));
    }

    @Test
    public void buildExportFileName_sanitizesTitle() {
        ChatSession special = new ChatSession("s", "Hello: World! (Test)", 0);
        String name = exportManager.buildExportFileName(special, "md");
        assertTrue("Should not contain colon", !name.contains(":"));
        assertTrue("Should not contain exclamation", !name.contains("!"));
        assertTrue("Should end with .md", name.endsWith(".md"));
    }

    // ==================== Helpers ====================

    private String exportToMarkdown(ChatSession session, List<ChatMessage> messages)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportManager.exportToMarkdown(session, messages, out);
        return out.toString("UTF-8");
    }

    private ChatImportManager.ImportResult importFromMarkdown(String md) throws IOException {
        return importManager.importFromMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));
    }
}