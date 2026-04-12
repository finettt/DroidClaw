package io.finett.droidclaw.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;

import static org.junit.Assert.*;

/**
 * Unit tests for ChatMessage attachment handling.
 */
public class ChatMessageAttachmentTest {

    private FileAttachment imageAttachment;
    private FileAttachment pdfAttachment;
    private FileAttachment textAttachment;

    @Before
    public void setUp() {
        imageAttachment = new FileAttachment(
            "abc12345_test.png",
            "test.png",
            "/data/data/io.finett.droidclaw/files/workspace/uploads/abc12345_test.png",
            "image/png"
        );

        pdfAttachment = new FileAttachment(
            "def67890_report.pdf",
            "report.pdf",
            "/data/data/io.finett.droidclaw/files/workspace/uploads/def67890_report.pdf",
            "application/pdf"
        );

        textAttachment = new FileAttachment(
            "ghi11111_notes.txt",
            "notes.txt",
            "/data/data/io.finett.droidclaw/files/workspace/uploads/ghi11111_notes.txt",
            "text/plain"
        );
    }

    @Test
    public void testCreateUserMessageWithAttachments() {
        List<FileAttachment> attachments = new ArrayList<>();
        attachments.add(imageAttachment);
        attachments.add(pdfAttachment);

        ChatMessage message = ChatMessage.createUserMessageWithAttachments(
            "Check these files", attachments);

        assertEquals(ChatMessage.TYPE_USER, message.getType());
        assertEquals("Check these files", message.getContent());
        assertTrue(message.hasAttachments());
        assertEquals(2, message.getAttachments().size());
    }

    @Test
    public void testCreateUserMessageWithNullAttachments() {
        ChatMessage message = ChatMessage.createUserMessageWithAttachments("Hello", null);

        assertEquals(ChatMessage.TYPE_USER, message.getType());
        assertEquals("Hello", message.getContent());
        assertFalse(message.hasAttachments());
    }

    @Test
    public void testCreateAttachmentMessage() {
        ChatMessage message = ChatMessage.createAttachmentMessage(
            "/workspace/uploads/file.txt",
            "My File",
            "text/plain"
        );

        assertEquals(ChatMessage.TYPE_ATTACHMENT, message.getType());
        assertEquals("/workspace/uploads/file.txt", message.getFilePath());
        assertEquals("My File", message.getDisplayName());
        assertEquals("text/plain", message.getFileMimeType());
        assertTrue(message.isAttachment());
    }

    @Test
    public void testToApiMessage_UserMessageWithoutAttachments() {
        ChatMessage message = new ChatMessage("Hello world", ChatMessage.TYPE_USER);
        JsonObject result = message.toApiMessage();

        assertEquals("user", result.get("role").getAsString());
        assertEquals("Hello world", result.get("content").getAsString());
    }

    @Test
    public void testToApiMessage_UserMessageWithNonImageAttachments() {
        List<FileAttachment> attachments = new ArrayList<>();
        attachments.add(textAttachment);

        ChatMessage message = ChatMessage.createUserMessageWithAttachments("Read this", attachments);
        JsonObject result = message.toApiMessage();

        assertEquals("user", result.get("role").getAsString());
        assertTrue("Content should be an array for attachments",
                result.get("content").isJsonArray());

        JsonArray contentArray = result.getAsJsonArray("content");
        assertEquals("Should have text + file reference parts", 2, contentArray.size());

        // First part: text content
        JsonObject textPart = contentArray.get(0).getAsJsonObject();
        assertEquals("text", textPart.get("type").getAsString());
        assertEquals("Read this", textPart.get("text").getAsString());

        // Second part: file reference
        JsonObject filePart = contentArray.get(1).getAsJsonObject();
        assertEquals("text", filePart.get("type").getAsString());
        String fileText = filePart.get("text").getAsString();
        assertTrue("Should mention filename", fileText.contains("notes.txt"));
        assertTrue("Should say attached", fileText.contains("file attached by user"));
    }

    @Test
    public void testToApiMessage_OnlyAttachmentNoText() {
        List<FileAttachment> attachments = new ArrayList<>();
        attachments.add(imageAttachment);

        ChatMessage message = ChatMessage.createUserMessageWithAttachments(null, attachments);
        JsonObject result = message.toApiMessage();

        assertEquals("user", result.get("role").getAsString());
        JsonArray contentArray = result.getAsJsonArray("content");

        // Should have at least a text reference for the file
        // (image_url part may be missing since the file doesn't exist in unit tests)
        boolean hasFileReference = false;
        for (int i = 0; i < contentArray.size(); i++) {
            JsonObject part = contentArray.get(i).getAsJsonObject();
            if (part.has("text") && part.get("text").getAsString().contains("test.png")) {
                hasFileReference = true;
                break;
            }
        }
        assertTrue("Should contain file reference text", hasFileReference);
    }

    @Test
    public void testToApiMessage_ToolCallUnchanged() {
        ChatMessage message = ChatMessage.createToolCallMessage(new ArrayList<>());
        JsonObject result = message.toApiMessage();

        assertEquals("assistant", result.get("role").getAsString());
        assertTrue(result.has("tool_calls"));
    }

    @Test
    public void testToApiMessage_ToolResultUnchanged() {
        ChatMessage message = ChatMessage.createToolResultMessage("call_1", "read_file", "file content");
        JsonObject result = message.toApiMessage();

        assertEquals("tool", result.get("role").getAsString());
        assertEquals("call_1", result.get("tool_call_id").getAsString());
        assertEquals("file content", result.get("content").getAsString());
    }

    @Test
    public void testToApiMessage_AttachmentType() {
        ChatMessage message = ChatMessage.createAttachmentMessage(
            "/workspace/uploads/report.pdf", "Report", "application/pdf");
        JsonObject result = message.toApiMessage();

        assertEquals("user", result.get("role").getAsString());
        String content = result.get("content").getAsString();
        assertTrue(content.contains("Report"));
        assertTrue(content.contains("/workspace/uploads/report.pdf"));
    }

    @Test
    public void testFileAttachment_IsImage() {
        assertTrue(imageAttachment.isImage());
        assertFalse(pdfAttachment.isImage());
        assertFalse(textAttachment.isImage());
    }

    @Test
    public void testFileAttachment_DisplayIcons() {
        assertEquals(R.drawable.ic_file_image, imageAttachment.getDisplayIconResId());
        assertEquals(R.drawable.ic_file_text, pdfAttachment.getDisplayIconResId());
        assertEquals(R.drawable.ic_file_text, textAttachment.getDisplayIconResId());
    }
}
