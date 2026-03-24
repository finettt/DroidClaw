package io.finett.droidclaw.adapter;

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

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_ASSISTANT = 1;
    private static final int VIEW_TYPE_TOOL_CALL = 2;
    private static final int VIEW_TYPE_TOOL_RESULT = 3;

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

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        @Override
        void bind(ChatMessage message) {
            messageText.setText(message.getContent());
        }
    }

    static class AssistantMessageViewHolder extends MessageViewHolder {
        private final TextView messageText;

        AssistantMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        @Override
        void bind(ChatMessage message) {
            messageText.setText(message.getContent());
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
                toolCallText.setText(firstToolCall.getName());

                // Show all tool call arguments
                StringBuilder argsBuilder = new StringBuilder();
                for (LlmApiService.ToolCall toolCall : message.getToolCalls()) {
                    argsBuilder.append("Tool: ").append(toolCall.getName())
                            .append("\nArgs: ").append(toolCall.getArguments().toString())
                            .append("\n\n");
                }
                toolCallArgs.setText(argsBuilder.toString().trim());
            }
        }
    }

    static class ToolResultMessageViewHolder extends MessageViewHolder {
        private final TextView toolResultLabel;
        private final TextView toolResultContent;

        ToolResultMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            toolResultLabel = itemView.findViewById(R.id.toolResultLabel);
            toolResultContent = itemView.findViewById(R.id.toolResultContent);
        }

        @Override
        void bind(ChatMessage message) {
            toolResultContent.setText(message.getContent());
        }
    }
}
