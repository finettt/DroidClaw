package io.finett.droidclaw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskExecutionRecord;

public class TaskExecutionAdapter extends RecyclerView.Adapter<TaskExecutionAdapter.TaskExecutionViewHolder> {

    public interface OnTaskExecutionClickListener {
        void onTaskExecutionClick(TaskExecutionRecord record);
    }

    private final List<TaskExecutionRecord> records = new ArrayList<>();
    private final OnTaskExecutionClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public TaskExecutionAdapter(OnTaskExecutionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskExecutionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_execution, parent, false);
        return new TaskExecutionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskExecutionViewHolder holder, int position) {
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void submitList(List<TaskExecutionRecord> newRecords) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return records.size();
            }

            @Override
            public int getNewListSize() {
                return newRecords.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return records.get(oldItemPosition).getSessionId()
                        .equals(newRecords.get(newItemPosition).getSessionId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                TaskExecutionRecord oldItem = records.get(oldItemPosition);
                TaskExecutionRecord newItem = newRecords.get(newItemPosition);
                return oldItem.isSuccess() == newItem.isSuccess() &&
                        oldItem.getStartTime() == newItem.getStartTime() &&
                        oldItem.getDurationMillis() == newItem.getDurationMillis();
            }
        });

        records.clear();
        records.addAll(newRecords);
        result.dispatchUpdatesTo(this);
    }

    class TaskExecutionViewHolder extends RecyclerView.ViewHolder {
        private final TextView textExecutionTime;
        private final TextView textDuration;
        private final TextView textTokens;
        private final TextView textIterations;
        private final TextView textErrorMessage;
        private final TextView textPreviewContent;
        final Chip chipExecutionStatus;

        TaskExecutionViewHolder(@NonNull View itemView) {
            super(itemView);
            textExecutionTime = itemView.findViewById(R.id.text_execution_time);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTokens = itemView.findViewById(R.id.text_tokens);
            textIterations = itemView.findViewById(R.id.text_iterations);
            textErrorMessage = itemView.findViewById(R.id.text_error_message);
            textPreviewContent = itemView.findViewById(R.id.text_preview_content);
            chipExecutionStatus = itemView.findViewById(R.id.chip_execution_status);
        }

        void bind(TaskExecutionRecord record) {
            // Format date and time
            String date = dateFormat.format(record.getStartTime());
            String time = timeFormat.format(record.getStartTime());
            textExecutionTime.setText(itemView.getContext().getString(R.string.execution_datetime_fmt, date, time));

            // Status chip
            if (record.isSuccess()) {
                chipExecutionStatus.setText(R.string.status_success);
            } else {
                chipExecutionStatus.setText(R.string.status_failed);
            }

            // Duration
            long durationSec = record.getDurationMillis() / 1000;
            String durationText;
            if (durationSec < 60) {
                durationText = durationSec + "s";
            } else {
                long minutes = durationSec / 60;
                long seconds = durationSec % 60;
                durationText = minutes + "m " + seconds + "s";
            }
            textDuration.setText(itemView.getContext().getString(R.string.execution_duration, durationText));

            // Tokens
            int tokens = record.getTokensUsed();
            String tokenText;
            if (tokens >= 1000) {
                tokenText = String.format(Locale.US, "%.1fk", tokens / 1000.0);
            } else {
                tokenText = String.valueOf(tokens);
            }
            textTokens.setText(itemView.getContext().getString(R.string.execution_tokens, tokenText));

            // Iterations
            textIterations.setText(itemView.getContext().getString(R.string.execution_iterations, record.getIterations()));

            // Error message
            if (!record.isSuccess() && record.getErrorMessage() != null && !record.getErrorMessage().isEmpty()) {
                textErrorMessage.setText(itemView.getContext().getString(R.string.execution_error_prefix, record.getErrorMessage()));
                textErrorMessage.setVisibility(View.VISIBLE);
                textPreviewContent.setVisibility(View.GONE);
            } else {
                textErrorMessage.setVisibility(View.GONE);
                textPreviewContent.setVisibility(View.VISIBLE);
                textPreviewContent.setText(R.string.execution_detail);
            }

            // Click listener
            itemView.setOnClickListener(v -> listener.onTaskExecutionClick(record));
        }
    }
}
