package io.finett.droidclaw.util;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.FileAttachment;

public class ChatImportManager {
    private static final String TAG = "ChatImportManager";

    private static final SimpleDateFormat TIMESTAMP_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    // Markdown header patterns
    private static final Pattern USER_HEADER      = Pattern.compile(
            "^###\\s+User\\s*\\(([^)]+)\\)\\s*$");
    private static final Pattern ASSISTANT_HEADER = Pattern.compile(
            "^###\\s+Assistant\\s*\\(([^)]+)\\)\\s*$");
    private static final Pattern TOOL_CALL_HEADER = Pattern.compile(
            "^####\\s+Tool Call:\\s*`([^`]+)`(?:\\s*\\(([^)]+)\\))?\\s*$");
    private static final Pattern TOOL_RESULT_HEADER = Pattern.compile(
            "^####\\s+Tool Result:\\s*`([^`]+)`\\s*$");

    public static class ImportResult {
        public final List<ChatMessage> messages;
        public final String sessionTitle;
        public final List<String> warnings;

        public ImportResult(List<ChatMessage> messages, String sessionTitle,
                            List<String> warnings) {
            this.messages     = messages;
            this.sessionTitle = sessionTitle;
            this.warnings     = warnings;
        }
    }


    /**
     * Parses a full-fidelity JSON backup produced by {@link ChatExportManager#exportToJson}.
     *
     * @param in Input stream (UTF-8). Caller is responsible for closing it.
     * @return   Parsed {@link ImportResult} containing messages and optional session title.
     * @throws IOException if the stream cannot be read or the JSON is invalid.
     */
    public ImportResult importFromJson(InputStream in) throws IOException {
        String json = readStreamToString(in);
        List<String> warnings = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();
        String sessionTitle = null;

        try {
            JSONObject root = new JSONObject(json);

            int version = root.optInt("version", 1);
            if (version != 1) {
                warnings.add("Unknown export version " + version + "; attempting import anyway.");
            }

            if (root.has("session")) {
                JSONObject sessionObj = root.getJSONObject("session");
                sessionTitle = sessionObj.optString("title", null);
            }

            if (!root.has("messages")) {
                throw new IOException("JSON export does not contain a 'messages' field.");
            }

            JSONArray messagesArray = root.getJSONArray("messages");
            for (int i = 0; i < messagesArray.length(); i++) {
                try {
                    ChatMessage message = deserializeMessage(messagesArray.getJSONObject(i));
                    if (message != null) {
                        messages.add(message);
                    }
                } catch (Exception e) {
                    warnings.add("Skipped message at index " + i + ": " + e.getMessage());
                    Log.w(TAG, "Error deserializing message at index " + i, e);
                }
            }

        } catch (JSONException e) {
            throw new IOException("Malformed JSON export: " + e.getMessage(), e);
        }

        Log.d(TAG, "Imported " + messages.size() + " messages from JSON"
                + (warnings.isEmpty() ? "" : " (" + warnings.size() + " warnings)"));
        return new ImportResult(messages, sessionTitle, warnings);
    }

    private ChatMessage deserializeMessage(JSONObject obj) throws JSONException {
        int type = obj.getInt("type");
        if (type < ChatMessage.TYPE_USER || type > ChatMessage.TYPE_ATTACHMENT) {
            throw new JSONException("Invalid message type: " + type);
        }

        long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
        String content = obj.has("content") && !obj.isNull("content")
                ? obj.getString("content") : null;

        ChatMessage message;

        if (type == ChatMessage.TYPE_TOOL_CALL && obj.has("toolCalls")) {
            JSONArray toolCallsArr = obj.getJSONArray("toolCalls");
            List<LlmApiService.ToolCall> toolCalls = new ArrayList<>();
            for (int j = 0; j < toolCallsArr.length(); j++) {
                JSONObject tc = toolCallsArr.getJSONObject(j);
                String id   = tc.optString("id", "");
                String name = tc.getString("name");
                String argsStr = tc.optString("arguments", "{}");
                JsonObject args = JsonParser.parseString(argsStr).getAsJsonObject();
                toolCalls.add(new LlmApiService.ToolCall(id, name, args));
            }
            message = ChatMessage.createToolCallMessage(toolCalls);
        } else if (type == ChatMessage.TYPE_TOOL_RESULT) {
            String toolCallId = obj.optString("toolCallId", null);
            String toolName   = obj.optString("toolName",   null);
            message = ChatMessage.createToolResultMessage(toolCallId, toolName, content);
        } else if (type == ChatMessage.TYPE_CONTEXT_CARD) {
            message = new ChatMessage(content, type);
            message.setIsContextCard(obj.optBoolean("isContextCard", true));
            if (obj.has("contextType") && !obj.isNull("contextType")) {
                message.setContextType(obj.getString("contextType"));
            }
            if (obj.has("originalTaskId") && !obj.isNull("originalTaskId")) {
                message.setOriginalTaskId(obj.getString("originalTaskId"));
            }
        } else if (type == ChatMessage.TYPE_ATTACHMENT) {
            String filePath    = obj.optString("filePath",    null);
            String mimeType    = obj.optString("fileMimeType", null);
            String displayName = obj.optString("displayName", null);
            message = ChatMessage.createAttachmentMessage(filePath, displayName, mimeType);
        } else {
            message = new ChatMessage(content, type);
        }

        // Restore attachments for user messages
        if (obj.has("attachments")) {
            JSONArray attArr = obj.getJSONArray("attachments");
            List<FileAttachment> attachments = new ArrayList<>();
            for (int j = 0; j < attArr.length(); j++) {
                JSONObject attObj = attArr.getJSONObject(j);
                attachments.add(new FileAttachment(
                        attObj.optString("filename", ""),
                        attObj.optString("originalName", ""),
                        attObj.optString("absolutePath", ""),
                        attObj.optString("mimeType", "")
                ));
            }
            message.setAttachments(attachments);
        }

        message.setTimestamp(timestamp);
        return message;
    }


    /**
     * Parses Markdown produced by {@link ChatExportManager#exportToMarkdown}.
     *
     * Note: Markdown import is inherently lossy – tool call arguments and
     * multi-attachment state may not be fully recoverable. JSON import is preferred
     * for round-trip fidelity.
     *
     * @param in Input stream (UTF-8). Caller is responsible for closing it.
     * @return   Parsed {@link ImportResult}.
     * @throws IOException if the stream cannot be read.
     */
    public ImportResult importFromMarkdown(InputStream in) throws IOException {
        String text = readStreamToString(in);
        List<String> warnings = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();

        String sessionTitle = null;
        String[] lines = text.split("\n", -1);

        // State machine
        int currentType = -1;
        String currentToolName = null;
        long currentTimestamp = System.currentTimeMillis();
        StringBuilder currentContent = new StringBuilder();
        boolean insideCodeBlock = false;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            // Extract session title from the first `**Session:**` line
            if (line.startsWith("**Session:**")) {
                sessionTitle = line.substring("**Session:**".length()).trim();
                continue;
            }

            // Track code blocks (don't parse header patterns inside them)
            if (line.startsWith("```") && !insideCodeBlock) {
                insideCodeBlock = true;
                currentContent.append(rawLine).append("\n");
                continue;
            }
            if (insideCodeBlock) {
                currentContent.append(rawLine).append("\n");
                if (line.startsWith("```")) {
                    insideCodeBlock = false;
                }
                continue;
            }

            Matcher userMatcher      = USER_HEADER.matcher(line);
            Matcher assistantMatcher = ASSISTANT_HEADER.matcher(line);
            Matcher toolCallMatcher  = TOOL_CALL_HEADER.matcher(line);
            Matcher toolResultMatcher = TOOL_RESULT_HEADER.matcher(line);

            if (userMatcher.matches()) {
                flushMessage(messages, warnings, currentType, currentToolName,
                        currentTimestamp, currentContent.toString());
                currentType      = ChatMessage.TYPE_USER;
                currentToolName  = null;
                currentTimestamp = parseTimestamp(userMatcher.group(1), warnings);
                currentContent   = new StringBuilder();
            } else if (assistantMatcher.matches()) {
                flushMessage(messages, warnings, currentType, currentToolName,
                        currentTimestamp, currentContent.toString());
                currentType      = ChatMessage.TYPE_ASSISTANT;
                currentToolName  = null;
                currentTimestamp = parseTimestamp(assistantMatcher.group(1), warnings);
                currentContent   = new StringBuilder();
            } else if (toolCallMatcher.matches()) {
                flushMessage(messages, warnings, currentType, currentToolName,
                        currentTimestamp, currentContent.toString());
                currentType      = ChatMessage.TYPE_TOOL_CALL;
                currentToolName  = toolCallMatcher.group(1);
                String tsStr     = toolCallMatcher.group(2);
                currentTimestamp = tsStr != null ? parseTimestamp(tsStr, warnings)
                                                 : System.currentTimeMillis();
                currentContent   = new StringBuilder();
            } else if (toolResultMatcher.matches()) {
                flushMessage(messages, warnings, currentType, currentToolName,
                        currentTimestamp, currentContent.toString());
                currentType      = ChatMessage.TYPE_TOOL_RESULT;
                currentToolName  = toolResultMatcher.group(1);
                currentTimestamp = System.currentTimeMillis();
                currentContent   = new StringBuilder();
            } else if (currentType != -1) {
                // Skip document-level headers and horizontal rules
                if (!line.equals("---") && !line.startsWith("# ") && !line.startsWith("**Exported:**")) {
                    currentContent.append(rawLine).append("\n");
                }
            }
        }

        // Flush the last message
        flushMessage(messages, warnings, currentType, currentToolName,
                currentTimestamp, currentContent.toString());

        Log.d(TAG, "Imported " + messages.size() + " messages from Markdown"
                + (warnings.isEmpty() ? "" : " (" + warnings.size() + " warnings)"));
        return new ImportResult(messages, sessionTitle, warnings);
    }

    private void flushMessage(List<ChatMessage> messages, List<String> warnings,
                              int type, String toolName, long timestamp, String rawContent) {
        if (type == -1) return;

        String content = rawContent.stripLeading();
        // Remove a trailing newline if present
        if (content.endsWith("\n")) {
            content = content.substring(0, content.length() - 1);
        }

        ChatMessage message;
        switch (type) {
            case ChatMessage.TYPE_USER:
                message = new ChatMessage(content.isEmpty() ? null : content, ChatMessage.TYPE_USER);
                break;

            case ChatMessage.TYPE_ASSISTANT:
                message = new ChatMessage(content.isEmpty() ? null : content, ChatMessage.TYPE_ASSISTANT);
                break;

            case ChatMessage.TYPE_TOOL_CALL: {
                // Best-effort: reconstruct a single tool call from the code block content
                String argsJson = extractCodeBlockContent(content);
                JsonObject args;
                try {
                    args = JsonParser.parseString(argsJson.isEmpty() ? "{}" : argsJson)
                                     .getAsJsonObject();
                } catch (Exception e) {
                    args = new JsonObject();
                    warnings.add("Could not parse tool call arguments for '" + toolName + "'");
                }
                List<LlmApiService.ToolCall> toolCalls = new ArrayList<>();
                toolCalls.add(new LlmApiService.ToolCall(
                        "imported-" + System.nanoTime(), toolName != null ? toolName : "unknown", args));
                message = ChatMessage.createToolCallMessage(toolCalls);
                break;
            }

            case ChatMessage.TYPE_TOOL_RESULT: {
                String resultContent = extractCodeBlockContent(content);
                message = ChatMessage.createToolResultMessage(null, toolName, resultContent);
                break;
            }

            default:
                message = new ChatMessage(content.isEmpty() ? null : content, type);
                break;
        }

        message.setTimestamp(timestamp);
        messages.add(message);
    }

    /**
     * Extracts the text inside the first ``` ... ``` code block in {@code content}.
     * Returns the raw content unchanged if no code block is found.
     */
    private String extractCodeBlockContent(String content) {
        if (content == null) return "";
        int start = content.indexOf("```");
        if (start < 0) return content.trim();
        int bodyStart = content.indexOf('\n', start);
        if (bodyStart < 0) return content.trim();
        int end = content.indexOf("```", bodyStart);
        if (end < 0) return content.substring(bodyStart).trim();
        return content.substring(bodyStart, end).trim();
    }

    private long parseTimestamp(String ts, List<String> warnings) {
        if (ts == null) return System.currentTimeMillis();
        try {
            Date date = TIMESTAMP_FMT.parse(ts.trim());
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (ParseException e) {
            warnings.add("Could not parse timestamp: " + ts);
            return System.currentTimeMillis();
        }
    }


    private String readStreamToString(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int bytesRead;
        while ((bytesRead = in.read(buf)) != -1) {
            sb.append(new String(buf, 0, bytesRead, "UTF-8"));
        }
        return sb.toString();
    }
}