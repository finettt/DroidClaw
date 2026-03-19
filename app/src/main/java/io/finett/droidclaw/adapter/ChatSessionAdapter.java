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
import io.finett.droidclaw.model.ChatSession;

public class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.ChatSessionViewHolder> {

    public interface OnChatSessionClickListener {
        void onChatSessionClick(ChatSession session);
        void onChatSessionLongClick(ChatSession session);
    }

    private final List<ChatSession> sessions = new ArrayList<>();
    private final OnChatSessionClickListener listener;

    public ChatSessionAdapter(OnChatSessionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatSessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_session, parent, false);
        return new ChatSessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatSessionViewHolder holder, int position) {
        holder.bind(sessions.get(position));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public void submitList(List<ChatSession> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    class ChatSessionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;

        ChatSessionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.chatSessionTitle);
        }

        void bind(ChatSession session) {
            titleText.setText(session.getTitle());
            itemView.setOnClickListener(v -> listener.onChatSessionClick(session));
            itemView.setOnLongClickListener(v -> {
                listener.onChatSessionLongClick(session);
                return true;
            });
        }
    }
}