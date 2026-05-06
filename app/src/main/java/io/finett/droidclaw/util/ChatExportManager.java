package io.finett.droidclaw.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.FileAttachment;

public class ChatExportManager {
    private static final String TAG = "ChatExportManager";
    private static final SimpleDateFormat TIMESTAMP_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private static final int PDF_PAGE_WIDTH  = 595;
    private static final int PDF_PAGE_HEIGHT = 842;
    private static final int PDF_MARGIN      = 48;
    private static final int PDF_LINE_HEIGHT = 18;
    private static final float PDF_FONT_SIZE = 11f;

    private final Context context;

    public ChatExportManager(Context context) {
        this.context = context;
    }


    /**
     * Exports the conversation to Markdown and writes it to {@code out}.
     *
     * @param session  Session metadata (may be null; skips header if so)
     * @param messages The messages to export
     * @param out      Destination stream; caller is responsible for closing it
     */
    public void exportToMarkdown(ChatSession session, List<ChatMessage> messages,
                                 OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();

        // Document header
        sb.append("# DroidClaw Chat Export\n\n");
        if (session != null) {
            sb.append("**Session:** ").append(escapeMarkdown(session.getTitle())).append("\n");
            sb.append("**Exported:** ")
              .append(TIMESTAMP_FMT.format(new Date()))
              .append("\n\n");
            sb.append("---\n\n");
        }

        for (ChatMessage message : messages) {
            appendMessageMarkdown(sb, message);
        }

        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();
        Log.d(TAG, "Exported " + messages.size() + " messages to Markdown");
    }

    private void appendMessageMarkdown(StringBuilder sb, ChatMessage message) {
        String timestamp = TIMESTAMP_FMT.format(new Date(message.getTimestamp()));

        switch (message.getType()) {
            case ChatMessage.TYPE_USER:
                sb.append("### User (").append(timestamp).append(")\n\n");
                if (message.getContent() != null) {
                    sb.append(message.getContent()).append("\n");
                }
                if (message.hasAttachments()) {
                    for (FileAttachment att : message.getAttachments()) {
                        sb.append("\n📎 **File:** ").append(att.getOriginalName())
                          .append(" (`").append(att.getAbsolutePath()).append("`)\n");
                    }
                }
                sb.append("\n");
                break;

            case ChatMessage.TYPE_ASSISTANT:
                sb.append("### Assistant (").append(timestamp).append(")\n\n");
                if (message.getContent() != null) {
                    sb.append(message.getContent()).append("\n");
                }
                sb.append("\n");
                break;

            case ChatMessage.TYPE_TOOL_CALL:
                if (message.getToolCalls() != null) {
                    for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                        sb.append("#### Tool Call: `").append(toolCall.getName())
                          .append("` (").append(timestamp).append(")\n\n");
                        sb.append("```json\n");
                        sb.append(toolCall.getArguments() != null
                                ? toolCall.getArguments().toString() : "{}");
                        sb.append("\n```\n\n");
                    }
                }
                break;

            case ChatMessage.TYPE_TOOL_RESULT:
                sb.append("#### Tool Result: `")
                  .append(message.getToolName() != null ? message.getToolName() : "unknown")
                  .append("`\n\n");
                if (message.getContent() != null) {
                    sb.append("```\n").append(message.getContent()).append("\n```\n\n");
                }
                break;

            case ChatMessage.TYPE_SYSTEM:
                sb.append("> **System:** ");
                if (message.getContent() != null) {
                    sb.append(message.getContent().replace("\n", "\n> "));
                }
                sb.append("\n\n");
                break;

            case ChatMessage.TYPE_CONTEXT_CARD:
                sb.append("> **Context [").append(message.getContextType()).append("]:** ");
                if (message.getContent() != null) {
                    sb.append(message.getContent().replace("\n", "\n> "));
                }
                sb.append("\n\n");
                break;

            case ChatMessage.TYPE_ATTACHMENT:
                sb.append("📎 **File:** ");
                String displayName = message.getDisplayName() != null
                        ? message.getDisplayName() : message.getFilePath();
                sb.append(displayName);
                if (message.getFilePath() != null) {
                    sb.append(" (`").append(message.getFilePath()).append("`)");
                }
                sb.append("\n\n");
                break;

            default:
                break;
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("`", "\\`")
                   .replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("[", "\\[")
                   .replace("]", "\\]");
    }


    /**
     * Exports the full conversation to JSON (full-fidelity backup).
     *
     * @param session  Session metadata (may be null)
     * @param messages The messages to export
     * @param out      Destination stream; caller is responsible for closing it
     */
    public void exportToJson(ChatSession session, List<ChatMessage> messages,
                             OutputStream out) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("exportedAt", TIMESTAMP_FMT.format(new Date()));
            root.put("version", 1);

            if (session != null) {
                JSONObject sessionObj = new JSONObject();
                sessionObj.put("id", session.getId());
                sessionObj.put("title", session.getTitle());
                sessionObj.put("updatedAt", session.getUpdatedAt());
                root.put("session", sessionObj);
            }

            JSONArray messagesArray = new JSONArray();
            for (ChatMessage message : messages) {
                messagesArray.put(serializeMessage(message));
            }
            root.put("messages", messagesArray);

            out.write(root.toString(2).getBytes("UTF-8"));
            out.flush();
            Log.d(TAG, "Exported " + messages.size() + " messages to JSON");

        } catch (JSONException e) {
            throw new IOException("Failed to serialize chat to JSON", e);
        }
    }

    private JSONObject serializeMessage(ChatMessage message) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", message.getType());
        obj.put("timestamp", message.getTimestamp());

        if (message.getContent() != null) {
            obj.put("content", message.getContent());
        }

        if (message.getType() == ChatMessage.TYPE_TOOL_CALL
                && message.getToolCalls() != null) {
            JSONArray toolCallsArr = new JSONArray();
            for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                JSONObject tc = new JSONObject();
                tc.put("id", toolCall.getId());
                tc.put("name", toolCall.getName());
                tc.put("arguments",
                        toolCall.getArguments() != null ? toolCall.getArguments().toString() : "{}");
                toolCallsArr.put(tc);
            }
            obj.put("toolCalls", toolCallsArr);
        }

        if (message.getType() == ChatMessage.TYPE_TOOL_RESULT) {
            if (message.getToolCallId() != null) obj.put("toolCallId", message.getToolCallId());
            if (message.getToolName()   != null) obj.put("toolName",   message.getToolName());
        }

        if (message.getType() == ChatMessage.TYPE_CONTEXT_CARD) {
            obj.put("isContextCard", message.isContextCard());
            if (message.getContextType()    != null) obj.put("contextType",    message.getContextType());
            if (message.getOriginalTaskId() != null) obj.put("originalTaskId", message.getOriginalTaskId());
        }

        if (message.hasAttachments()) {
            JSONArray attArr = new JSONArray();
            for (FileAttachment att : message.getAttachments()) {
                JSONObject attObj = new JSONObject();
                attObj.put("filename",     att.getFilename());
                attObj.put("originalName", att.getOriginalName());
                attObj.put("absolutePath", att.getAbsolutePath());
                attObj.put("mimeType",     att.getMimeType());
                attArr.put(attObj);
            }
            obj.put("attachments", attArr);
        }

        if (message.getType() == ChatMessage.TYPE_ATTACHMENT) {
            if (message.getFilePath()    != null) obj.put("filePath",    message.getFilePath());
            if (message.getFileMimeType() != null) obj.put("fileMimeType", message.getFileMimeType());
            if (message.getDisplayName() != null) obj.put("displayName", message.getDisplayName());
        }

        return obj;
    }


    /**
     * Exports the conversation to PDF using the Android {@link PdfDocument} API.
     *
     * @param session  Session metadata (may be null)
     * @param messages The messages to export
     * @param out      Destination stream; caller is responsible for closing it
     */
    public void exportToPdf(ChatSession session, List<ChatMessage> messages,
                            OutputStream out) throws IOException {
        PdfDocument pdfDoc = new PdfDocument();

        Paint titlePaint = new Paint();
        titlePaint.setTextSize(16f);
        titlePaint.setFakeBoldText(true);

        Paint bodyPaint = new Paint();
        bodyPaint.setTextSize(PDF_FONT_SIZE);

        Paint labelPaint = new Paint();
        labelPaint.setTextSize(PDF_FONT_SIZE);
        labelPaint.setFakeBoldText(true);

        // Flatten all messages to text lines first
        List<String> lines = buildPdfLines(session, messages);

        int pageNumber = 1;
        int lineIndex  = 0;

        while (lineIndex < lines.size()) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNumber).create();
            PdfDocument.Page page = pdfDoc.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            float y = PDF_MARGIN;
            float maxY = PDF_PAGE_HEIGHT - PDF_MARGIN;

            while (lineIndex < lines.size() && y < maxY) {
                String line = lines.get(lineIndex);
                canvas.drawText(line, PDF_MARGIN, y, bodyPaint);
                y += PDF_LINE_HEIGHT;
                lineIndex++;
            }

            pdfDoc.finishPage(page);
            pageNumber++;
        }

        pdfDoc.writeTo(out);
        pdfDoc.close();
        out.flush();
        Log.d(TAG, "Exported " + messages.size() + " messages to PDF (" + (pageNumber - 1) + " pages)");
    }

    private List<String> buildPdfLines(ChatSession session, List<ChatMessage> messages) {
        List<String> lines = new java.util.ArrayList<>();

        lines.add("DroidClaw Chat Export");
        lines.add("");
        if (session != null) {
            lines.add("Session: " + session.getTitle());
            lines.add("Exported: " + TIMESTAMP_FMT.format(new Date()));
            lines.add("");
            lines.add("----------------------------------------------");
            lines.add("");
        }

        for (ChatMessage message : messages) {
            String timestamp = TIMESTAMP_FMT.format(new Date(message.getTimestamp()));
            switch (message.getType()) {
                case ChatMessage.TYPE_USER:
                    lines.add("[User - " + timestamp + "]");
                    appendWrappedLines(lines, message.getContent());
                    break;

                case ChatMessage.TYPE_ASSISTANT:
                    lines.add("[Assistant - " + timestamp + "]");
                    appendWrappedLines(lines, message.getContent());
                    break;

                case ChatMessage.TYPE_TOOL_CALL:
                    if (message.getToolCalls() != null) {
                        for (LlmApiService.ToolCall tc : message.getToolCalls()) {
                            lines.add("[Tool Call: " + tc.getName() + " - " + timestamp + "]");
                            appendWrappedLines(lines,
                                    tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                        }
                    }
                    break;

                case ChatMessage.TYPE_TOOL_RESULT:
                    lines.add("[Tool Result: " + message.getToolName() + "]");
                    appendWrappedLines(lines, message.getContent());
                    break;

                case ChatMessage.TYPE_SYSTEM:
                    lines.add("[System]");
                    appendWrappedLines(lines, message.getContent());
                    break;

                case ChatMessage.TYPE_CONTEXT_CARD:
                    lines.add("[Context: " + message.getContextType() + "]");
                    appendWrappedLines(lines, message.getContent());
                    break;

                case ChatMessage.TYPE_ATTACHMENT:
                    String name = message.getDisplayName() != null
                            ? message.getDisplayName() : message.getFilePath();
                    lines.add("[Attachment: " + name + "]");
                    break;

                default:
                    break;
            }
            lines.add("");
        }

        return lines;
    }

    /**
     * Splits {@code text} into lines no longer than ~80 characters and appends them.
     */
    private void appendWrappedLines(List<String> lines, String text) {
        if (text == null || text.isEmpty()) {
            lines.add("");
            return;
        }
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.length() <= 80) {
                lines.add(rawLine);
            } else {
                int start = 0;
                while (start < rawLine.length()) {
                    int end = Math.min(start + 80, rawLine.length());
                    lines.add(rawLine.substring(start, end));
                    start = end;
                }
            }
        }
    }


    /**
     * Returns a suggested export filename for the given session and format extension
     * (e.g. "md", "json", "pdf").
     */
    public String buildExportFileName(ChatSession session, String extension) {
        String base = "droidclaw-chat";
        if (session != null && session.getTitle() != null && !session.getTitle().isEmpty()) {
            String sanitized = session.getTitle()
                    .replaceAll("[^a-zA-Z0-9\\s\\-]", "")
                    .trim()
                    .replaceAll("\\s+", "-")
                    .toLowerCase(Locale.US);
            if (sanitized.length() > 40) sanitized = sanitized.substring(0, 40);
            if (!sanitized.isEmpty()) base = sanitized;
        }
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        return base + "-" + datePart + "." + extension;
    }
}