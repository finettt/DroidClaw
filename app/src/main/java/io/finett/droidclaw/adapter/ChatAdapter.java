package io.finett.droidclaw.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.droidclaw.R;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.FileAttachment;
import io.finett.droidclaw.util.MarkdownRenderer;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_ASSISTANT = 1;
    private static final int VIEW_TYPE_TOOL_CALL = 2;
    private static final int VIEW_TYPE_TOOL_RESULT = 3;
    private static final int VIEW_TYPE_CONTEXT_CARD = 5;
    private static final int VIEW_TYPE_ATTACHMENT = 6;

    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_USER:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_user, parent, false);
                return new UserMessageViewHolder(view);

            case VIEW_TYPE_ASSISTANT:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_assistant, parent, false);
                return new AssistantMessageViewHolder(view);

            case VIEW_TYPE_TOOL_CALL:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_tool_call, parent, false);
                return new ToolCallMessageViewHolder(view);

            case VIEW_TYPE_TOOL_RESULT:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_tool_result, parent, false);
                return new ToolResultMessageViewHolder(view);

            case VIEW_TYPE_CONTEXT_CARD:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_context_card, parent, false);
                return new ContextCardMessageViewHolder(view);

            case VIEW_TYPE_ATTACHMENT:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_attachment_chip, parent, false);
                return new AttachmentMessageViewHolder(view);

            default:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_assistant, parent, false);
                return new AssistantMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(String content) {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            messages.get(lastIndex).setContent(content);
            notifyItemChanged(lastIndex);
        }
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(ChatMessage message);
    }

    static class UserMessageViewHolder extends MessageViewHolder {
        private final TextView messageText;
        private final LinearLayout attachmentsContainer;
        private final Context context;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            attachmentsContainer = itemView.findViewById(R.id.attachmentsContainer);
            context = itemView.getContext();
        }

        @Override
        void bind(ChatMessage message) {
            String content = message.getContent();
            if (content != null && MarkdownRenderer.containsMarkdown(content)) {
                MarkdownRenderer.render(context, messageText, content);
            } else {
                messageText.setText(content);
            }

            // Display attachments if present
            renderAttachments(message);
        }

        /**
         * Renders file attachment chips for the message.
         */
        private void renderAttachments(ChatMessage message) {
            attachmentsContainer.removeAllViews();

            if (!message.hasAttachments()) {
                attachmentsContainer.setVisibility(View.GONE);
                return;
            }

            attachmentsContainer.setVisibility(View.VISIBLE);

            for (FileAttachment attachment : message.getAttachments()) {
                Chip chip = (Chip) LayoutInflater.from(context)
                        .inflate(R.layout.item_attachment_chip, attachmentsContainer, false);

                chip.setText(attachment.getOriginalName());
                int iconRes = attachment.getDisplayIconResId();
                chip.setChipIconResource(iconRes);

                // Click to open file
                chip.setOnClickListener(v -> openFile(attachment));

                attachmentsContainer.addView(chip);
            }
        }

        /**
         * Opens the attached file with an external app.
         */
        private void openFile(FileAttachment attachment) {
            File file = new File(attachment.getAbsolutePath());
            if (!file.exists()) {
                Toast.makeText(context, "File not found: " + attachment.getOriginalName(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri fileUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", file);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, attachment.getMimeType());
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Check if there's an app to handle this intent
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    // No app found - show chooser with "Open with" prompt
                    Intent chooser = Intent.createChooser(intent,
                            context.getString(R.string.file_viewer_open_with));
                    if (chooser.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(chooser);
                    } else {
                        Toast.makeText(context,
                                context.getString(R.string.file_viewer_no_app_found,
                                        attachment.getOriginalName()),
                                Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context,
                        context.getString(R.string.file_viewer_open_error,
                                attachment.getOriginalName()),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    static class AssistantMessageViewHolder extends MessageViewHolder {
        private final TextView messageText;
        private final Context context;

        AssistantMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            context = itemView.getContext();
        }

        @Override
        void bind(ChatMessage message) {
            String content = message.getContent();
            // Always render assistant messages as markdown since they often contain formatting
            if (content != null) {
                MarkdownRenderer.render(context, messageText, content);
            } else {
                messageText.setText("");
            }
        }
    }

    static class ToolCallMessageViewHolder extends MessageViewHolder {
        private final ImageView toolCallIcon;
        private final TextView toolCallText;
        private final TextView toolCallArgs;

        ToolCallMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            toolCallIcon = itemView.findViewById(R.id.toolCallIcon);
            toolCallText = itemView.findViewById(R.id.toolCallText);
            toolCallArgs = itemView.findViewById(R.id.toolCallArgs);
        }

        @Override
        void bind(ChatMessage message) {
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                // Show the first tool call's name as the main text
                LlmApiService.ToolCall firstToolCall = message.getToolCalls().get(0);
                String toolName = firstToolCall.getName();

                // Set icon based on tool type
                int iconRes = getToolIcon(toolName);
                toolCallIcon.setImageResource(iconRes);

                // Format tool name for display
                toolCallText.setText(formatToolName(toolName));

                // Show all tool call arguments
                StringBuilder argsBuilder = new StringBuilder();
                for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                    if (message.getToolCalls().size() > 1) {
                        argsBuilder.append("• ").append(toolCall.getName()).append("\n");
                    }
                    argsBuilder.append(formatArguments(toolCall.getArguments().toString()))
                            .append("\n");
                }
                toolCallArgs.setText(argsBuilder.toString().trim());
            }
        }

        private int getToolIcon(String toolName) {
            if (toolName.contains("shell") || toolName.contains("execute")) {
                return R.drawable.ic_tool_shell;
            } else if (toolName.contains("file") || toolName.contains("read") || toolName.contains("write")) {
                return R.drawable.ic_folder;
            } else if (toolName.contains("python") || toolName.contains("pip")) {
                return R.drawable.ic_tool_python;
            } else if (toolName.contains("search")) {
                return R.drawable.ic_tool_search;
            } else if (toolName.contains("list")) {
                return R.drawable.ic_tool_list;
            } else {
                return R.drawable.ic_tool_generic;
            }
        }
        
        private String formatToolName(String toolName) {
            // Convert snake_case to Title Case
            String[] parts = toolName.split("_");
            StringBuilder formatted = new StringBuilder();
            for (String part : parts) {
                if (formatted.length() > 0) {
                    formatted.append(" ");
                }
                formatted.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1).toLowerCase());
            }
            return formatted.toString();
        }
        
        private String formatArguments(String args) {
            // Pretty-print JSON arguments with limited length
            if (args.length() > 200) {
                return args.substring(0, 197) + "...";
            }
            return args;
        }
    }

    static class ToolResultMessageViewHolder extends MessageViewHolder {
        private final View toolResultHeader;
        private final ImageView toolResultIcon;
        private final TextView toolResultLabel;
        private final TextView toolResultToggle;
        private final TextView toolResultContent;
        private final LinearLayout filesContainer;
        private boolean isExpanded = false;

        // Pattern to detect file paths in tool results
        private static final Pattern FILE_REF_PATTERN = Pattern.compile(
            "`([^`]+)`|(/data/data/[^\\s]+|uploads/[^\\s]+)"
        );

        ToolResultMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            toolResultHeader = itemView.findViewById(R.id.toolResultHeader);
            toolResultIcon = itemView.findViewById(R.id.toolResultIcon);
            toolResultLabel = itemView.findViewById(R.id.toolResultLabel);
            toolResultToggle = itemView.findViewById(R.id.toolResultToggle);
            toolResultContent = itemView.findViewById(R.id.toolResultContent);
            filesContainer = itemView.findViewById(R.id.toolResultFilesContainer);

            // Set up click listener for expand/collapse
            toolResultHeader.setOnClickListener(v -> toggleExpanded());
        }

        @Override
        void bind(ChatMessage message) {
            String content = message.getContent();
            String toolName = message.getToolName();
            Context context = itemView.getContext();

            // Set label with tool name
            if (toolName != null) {
                toolResultLabel.setText(formatToolName(toolName) + " result");
            } else {
                toolResultLabel.setText("Tool result");
            }

            // Determine if result indicates success or error
            boolean isError = content != null && (content.toLowerCase().contains("error:")
                    || content.toLowerCase().startsWith("error"));

            // Set icon based on success/error
            toolResultIcon.setImageResource(isError ? R.drawable.ic_status_error : R.drawable.ic_status_success);
            
            // Set content with markdown rendering if applicable
            if (content != null) {
                if (MarkdownRenderer.containsMarkdown(content)) {
                    MarkdownRenderer.render(context, toolResultContent, content);
                } else {
                    toolResultContent.setText(content);
                }
            } else {
                toolResultContent.setText("No output");
            }
            
            // Start collapsed if content is long
            if (content != null && content.length() > 100) {
                isExpanded = false;
                updateExpandState();
            } else {
                isExpanded = true;
                toolResultToggle.setVisibility(View.GONE);
                toolResultContent.setMaxLines(Integer.MAX_VALUE);
            }

            // Detect and render file references
            renderFileReferences(content);
        }
        
        private void toggleExpanded() {
            isExpanded = !isExpanded;
            updateExpandState();
        }
        
        private void updateExpandState() {
            if (isExpanded) {
                toolResultToggle.setText("▲");
                toolResultContent.setMaxLines(Integer.MAX_VALUE);
            } else {
                toolResultToggle.setText("▼");
                toolResultContent.setMaxLines(3);
            }
        }
        
        private String formatToolName(String toolName) {
            // Convert snake_case to Title Case
            String[] parts = toolName.split("_");
            StringBuilder formatted = new StringBuilder();
            for (String part : parts) {
                if (formatted.length() > 0) {
                    formatted.append(" ");
                }
                formatted.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1).toLowerCase());
            }
            return formatted.toString();
        }

        /**
         * Detects file references in tool result content and displays them as clickable chips.
         */
        private void renderFileReferences(String content) {
            filesContainer.removeAllViews();

            if (content == null) {
                filesContainer.setVisibility(View.GONE);
                return;
            }

            java.util.Set<String> foundFiles = new java.util.HashSet<>();
            Matcher matcher = FILE_REF_PATTERN.matcher(content);
            while (matcher.find()) {
                String rawPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (rawPath == null || rawPath.isEmpty()) continue;

                // Skip common non-file patterns
                if (rawPath.contains("```") || rawPath.contains("\n") ||
                    rawPath.startsWith("[") || rawPath.startsWith("(")) continue;

                foundFiles.add(rawPath);
            }

            if (foundFiles.isEmpty()) {
                filesContainer.setVisibility(View.GONE);
                return;
            }

            filesContainer.setVisibility(View.VISIBLE);

            for (String rawPath : foundFiles) {
                File file = resolveFile(rawPath);
                if (file == null || !file.exists()) continue;

                String displayName = file.getName();
                String mimeType = io.finett.droidclaw.filesystem.FileUploadManager.resolveMimeType(displayName);

                Chip chip = (Chip) LayoutInflater.from(itemView.getContext())
                        .inflate(R.layout.item_attachment_chip, filesContainer, false);

                int iconRes = R.drawable.ic_attachment;
                if (mimeType != null) {
                    if (mimeType.startsWith("image/")) iconRes = R.drawable.ic_file_image;
                    else if (mimeType.startsWith("text/")) iconRes = R.drawable.ic_file_text;
                    else if (mimeType.contains("pdf")) iconRes = R.drawable.ic_file_text;
                    else if (mimeType.contains("spreadsheet")) iconRes = R.drawable.ic_file_spreadsheet;
                }

                chip.setText(displayName);
                chip.setChipIconResource(iconRes);
                chip.setOnClickListener(v -> openFile(file, displayName, mimeType));

                filesContainer.addView(chip);
            }
        }

        /**
         * Resolves a file reference path to an actual File object.
         */
        private File resolveFile(String rawPath) {
            if (rawPath.startsWith("/")) {
                return new File(rawPath);
            }

            // Try workspace directories
            String[] searchDirs = {"uploads", "home", "home/documents", "home/scripts", "home/notes", "tmp"};
            File filesDir = itemView.getContext().getFilesDir();
            File workspaceRoot = new File(filesDir, "workspace");

            // Direct path under workspace
            File directFile = new File(workspaceRoot, rawPath);
            if (directFile.exists()) return directFile;

            // Search in subdirectories
            for (String dir : searchDirs) {
                File file = new File(workspaceRoot, dir + "/" + rawPath);
                if (file.exists()) return file;
            }

            // Just the filename - search uploads first
            File uploadFile = new File(new File(workspaceRoot, "uploads"), rawPath);
            if (uploadFile.exists()) return uploadFile;

            return null;
        }

        /**
         * Opens a file with an external app.
         */
        private void openFile(File file, String displayName, String mimeType) {
            try {
                Uri fileUri = FileProvider.getUriForFile(itemView.getContext(),
                        itemView.getContext().getPackageName() + ".fileprovider", file);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mimeType != null ? mimeType : "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (intent.resolveActivity(itemView.getContext().getPackageManager()) != null) {
                    itemView.getContext().startActivity(intent);
                } else {
                    Intent chooser = Intent.createChooser(intent,
                            itemView.getContext().getString(R.string.file_viewer_open_with));
                    if (chooser.resolveActivity(itemView.getContext().getPackageManager()) != null) {
                        itemView.getContext().startActivity(chooser);
                    } else {
                        Toast.makeText(itemView.getContext(),
                                itemView.getContext().getString(R.string.file_viewer_no_app_found, displayName),
                                Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(itemView.getContext(),
                        itemView.getContext().getString(R.string.file_viewer_open_error, displayName),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    static class ContextCardMessageViewHolder extends MessageViewHolder {
        private final View contextCardHeader;
        private final ImageView contextCardIcon;
        private final TextView contextCardTitle;
        private final TextView contextCardTimestamp;
        private final TextView contextCardToggle;
        private final TextView contextCardContent;
        private boolean isExpanded = false;

        ContextCardMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            contextCardHeader = itemView.findViewById(R.id.contextCardHeader);
            contextCardIcon = itemView.findViewById(R.id.contextCardIcon);
            contextCardTitle = itemView.findViewById(R.id.contextCardTitle);
            contextCardTimestamp = itemView.findViewById(R.id.contextCardTimestamp);
            contextCardToggle = itemView.findViewById(R.id.contextCardToggle);
            contextCardContent = itemView.findViewById(R.id.contextCardContent);

            // Set up click listener for expand/collapse
            contextCardHeader.setOnClickListener(v -> toggleExpanded());
        }

        @Override
        void bind(ChatMessage message) {
            Context context = itemView.getContext();

            // Set icon based on context type
            String contextType = message.getContextType();
            int iconRes = getContextIcon(contextType);
            contextCardIcon.setImageResource(iconRes);

            // Set title
            String title = getContextTitle(contextType, context);
            contextCardTitle.setText(title);

            // Set timestamp
            long timestamp = message.getTimestamp();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", 
                    java.util.Locale.getDefault());
            contextCardTimestamp.setText(sdf.format(new java.util.Date(timestamp)));

            // Set content with markdown rendering if applicable
            String content = message.getContent();
            if (content != null) {
                if (MarkdownRenderer.containsMarkdown(content)) {
                    MarkdownRenderer.render(context, contextCardContent, content);
                } else {
                    contextCardContent.setText(content);
                }
            } else {
                contextCardContent.setText("No content available");
            }

            // Start collapsed if content is long
            if (content != null && content.length() > 150) {
                isExpanded = false;
                updateExpandState();
            } else {
                isExpanded = true;
                contextCardToggle.setVisibility(View.GONE);
                contextCardContent.setMaxLines(Integer.MAX_VALUE);
                contextCardContent.setVisibility(View.VISIBLE);
            }
        }

        private void toggleExpanded() {
            isExpanded = !isExpanded;
            updateExpandState();
        }

        private void updateExpandState() {
            if (isExpanded) {
                contextCardToggle.setText("▲");
                contextCardContent.setVisibility(View.VISIBLE);
                contextCardContent.setMaxLines(Integer.MAX_VALUE);
            } else {
                contextCardToggle.setText("▼");
                contextCardContent.setVisibility(View.VISIBLE);
                contextCardContent.setMaxLines(3);
            }
        }

        private int getContextIcon(String contextType) {
            if (contextType == null) return R.drawable.ic_tool_list;

            switch (contextType.toLowerCase()) {
                case "heartbeat":
                    return R.drawable.ic_settings_heartbeat;
                case "cron_job":
                    return R.drawable.ic_settings_cron;
                case "manual":
                    return R.drawable.ic_tool_generic;
                default:
                    return R.drawable.ic_tool_list;
            }
        }

        private String getContextTitle(String contextType, Context context) {
            if (contextType == null) return "Task Result";
            
            switch (contextType.toLowerCase()) {
                case "heartbeat":
                    return "Heartbeat Check";
                case "cron_job":
                    return "Scheduled Task";
                case "manual":
                    return "Manual Task";
                default:
                    return "Task Result";
            }
        }
    }

    /**
     * ViewHolder for agent-referenced file attachment messages (TYPE_ATTACHMENT).
     * Displays a clickable chip that opens the file in an external app.
     */
    static class AttachmentMessageViewHolder extends MessageViewHolder {
        private final Chip attachmentChip;
        private final Context context;

        AttachmentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            attachmentChip = (Chip) itemView;
            context = itemView.getContext();
        }

        @Override
        void bind(ChatMessage message) {
            String rawDisplayName = message.getDisplayName();
            final String mimeType = message.getFileMimeType();
            final String filePath = message.getFilePath();

            final String displayName;
            if (rawDisplayName == null || rawDisplayName.isEmpty()) {
                if (filePath != null) {
                    displayName = new File(filePath).getName();
                } else {
                    displayName = "Unknown file";
                }
            } else {
                displayName = rawDisplayName;
            }

            int iconRes = R.drawable.ic_attachment;
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) iconRes = R.drawable.ic_file_image;
                else if (mimeType.startsWith("text/")) iconRes = R.drawable.ic_file_text;
                else if (mimeType.contains("pdf")) iconRes = R.drawable.ic_file_text;
                else if (mimeType.contains("spreadsheet") || mimeType.contains("excel")) iconRes = R.drawable.ic_file_spreadsheet;
                else if (mimeType.contains("document")) iconRes = R.drawable.ic_file_text;
            }

            attachmentChip.setText(displayName);
            attachmentChip.setChipIconResource(iconRes);

            attachmentChip.setOnClickListener(v -> {
                if (filePath == null) {
                    Toast.makeText(context, "File path not available",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                File file = new File(filePath);
                if (!file.exists()) {
                    Toast.makeText(context, "File not found: " + displayName,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    Uri fileUri = FileProvider.getUriForFile(context,
                            context.getPackageName() + ".fileprovider", file);

                    String actualMimeType = mimeType != null ? mimeType : "*/*";
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri, actualMimeType);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    if (intent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(intent);
                    } else {
                        Intent chooser = Intent.createChooser(intent,
                                context.getString(R.string.file_viewer_open_with));
                        if (chooser.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(chooser);
                        } else {
                            Toast.makeText(context,
                                    context.getString(R.string.file_viewer_no_app_found, displayName),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(context,
                            context.getString(R.string.file_viewer_open_error, displayName),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
