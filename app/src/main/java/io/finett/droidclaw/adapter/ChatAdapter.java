package io.finett.droidclaw.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.util.MarkdownRenderer;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_ASSISTANT = 1;
    private static final int VIEW_TYPE_TOOL_CALL = 2;
    private static final int VIEW_TYPE_TOOL_RESULT = 3;
    private static final int VIEW_TYPE_CONTEXT_CARD = 5;

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
        private final Context context;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
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
        private final TextView toolCallIcon;
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
                String icon = getToolIcon(toolName);
                toolCallIcon.setText(icon);
                
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
        
        private String getToolIcon(String toolName) {
            if (toolName.contains("shell") || toolName.contains("execute")) {
                return "💻";
            } else if (toolName.contains("file") || toolName.contains("read") || toolName.contains("write")) {
                return "📁";
            } else if (toolName.contains("python") || toolName.contains("pip")) {
                return "🐍";
            } else if (toolName.contains("search")) {
                return "🔍";
            } else if (toolName.contains("list")) {
                return "📋";
            } else {
                return "🔧";
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
        private final TextView toolResultIcon;
        private final TextView toolResultLabel;
        private final TextView toolResultToggle;
        private final TextView toolResultContent;
        private boolean isExpanded = false;

        ToolResultMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            toolResultHeader = itemView.findViewById(R.id.toolResultHeader);
            toolResultIcon = itemView.findViewById(R.id.toolResultIcon);
            toolResultLabel = itemView.findViewById(R.id.toolResultLabel);
            toolResultToggle = itemView.findViewById(R.id.toolResultToggle);
            toolResultContent = itemView.findViewById(R.id.toolResultContent);
            
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
            toolResultIcon.setText(isError ? "❌" : "✅");
            
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
    }

    static class ContextCardMessageViewHolder extends MessageViewHolder {
        private final View contextCardHeader;
        private final TextView contextCardIcon;
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
            String icon = getContextIcon(contextType);
            contextCardIcon.setText(icon);

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

        private String getContextIcon(String contextType) {
            if (contextType == null) return "📋";
            
            switch (contextType.toLowerCase()) {
                case "heartbeat":
                    return "💓";
                case "cron_job":
                    return "⏰";
                case "manual":
                    return "🔧";
                default:
                    return "📋";
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
}
