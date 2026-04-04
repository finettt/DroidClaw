package io.finett.droidclaw.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskRecord;

/**
 * Adapter for displaying task execution history.
 */
public class TaskRecordAdapter extends RecyclerView.Adapter<TaskRecordAdapter.TaskRecordViewHolder> {

    public interface OnTaskRecordClickListener {
        void onTaskRecordClick(TaskRecord record);
        void onViewFullChatClick(TaskRecord record);
    }

    private final List<TaskRecord> records = new ArrayList<>();
    private final OnTaskRecordClickListener listener;

    public TaskRecordAdapter(OnTaskRecordClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskRecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_record, parent, false);
        return new TaskRecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskRecordViewHolder holder, int position) {
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void submitList(List<TaskRecord> newRecords) {
        records.clear();
        if (newRecords != null) {
            records.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    public TaskRecord getRecord(int position) {
        return records.get(position);
    }

    class TaskRecordViewHolder extends RecyclerView.ViewHolder {
        private final Chip statusChip;
        private final TextView jobNameText;
        private final TextView executedAtText;
        private final TextView responsePreviewText;
        private final TextView durationText;
        private final TextView toolCallsText;
        private final TextView tokensText;
        private final MaterialButton viewFullChatButton;

        TaskRecordViewHolder(@NonNull View itemView) {
            super(itemView);
            statusChip = itemView.findViewById(R.id.statusChip);
            jobNameText = itemView.findViewById(R.id.jobNameText);
            executedAtText = itemView.findViewById(R.id.executedAtText);
            responsePreviewText = itemView.findViewById(R.id.responsePreviewText);
            durationText = itemView.findViewById(R.id.durationText);
            toolCallsText = itemView.findViewById(R.id.toolCallsText);
            tokensText = itemView.findViewById(R.id.tokensText);
            viewFullChatButton = itemView.findViewById(R.id.viewFullChatButton);
        }

        void bind(TaskRecord record) {
            // Job name
            jobNameText.setText(record.getCronJobName());

            // Status chip
            statusChip.setText(record.getStatusDisplayText());
            int statusColor = Color.parseColor(record.getStatusBadgeColor());
            statusChip.setChipBackgroundColorResource(R.color.material_grey_200);
            statusChip.setTextColor(statusColor);

            // Executed at
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            executedAtText.setText(sdf.format(new Date(record.getStartedAt())));

            // Response preview
            if (record.getResponse() != null && !record.getResponse().isEmpty()) {
                String preview = record.getResponse().length() > 150 
                        ? record.getResponse().substring(0, 150) + "..." 
                        : record.getResponse();
                responsePreviewText.setText(preview);
                responsePreviewText.setVisibility(View.VISIBLE);
            } else {
                responsePreviewText.setVisibility(View.GONE);
            }

            // Statistics
            durationText.setText("Duration: " + record.getDurationDisplayString());
            toolCallsText.setText("Tools: " + record.getToolCallsCount());
            
            int tokens = record.getTokensUsed();
            String tokensDisplay = tokens >= 1000 
                    ? String.format(Locale.getDefault(), "%.1fk", tokens / 1000.0) 
                    : String.valueOf(tokens);
            tokensText.setText("Tokens: " + tokensDisplay);

            // Click listeners
            itemView.setOnClickListener(v -> listener.onTaskRecordClick(record));
            viewFullChatButton.setOnClickListener(v -> listener.onViewFullChatClick(record));
        }
    }
}
