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
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.scheduler.CronJobScheduler;

public class CronJobAdapter extends RecyclerView.Adapter<CronJobAdapter.CronJobViewHolder> {

    public interface OnCronJobClickListener {
        void onCronJobClick(CronJob job);
        void onCronJobLongClick(CronJob job, View anchorView);
    }

    private final List<CronJob> jobs = new ArrayList<>();
    private final OnCronJobClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public CronJobAdapter(OnCronJobClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CronJobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cron_job, parent, false);
        return new CronJobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CronJobViewHolder holder, int position) {
        holder.bind(jobs.get(position));
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    public void submitList(List<CronJob> newJobs) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return jobs.size();
            }

            @Override
            public int getNewListSize() {
                return newJobs.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return jobs.get(oldItemPosition).getId().equals(newJobs.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CronJob oldItem = jobs.get(oldItemPosition);
                CronJob newItem = newJobs.get(newItemPosition);
                return oldItem.getName().equals(newItem.getName()) &&
                        oldItem.isEnabled() == newItem.isEnabled() &&
                        oldItem.isPaused() == newItem.isPaused() &&
                        oldItem.getLastRunTimestamp() == newItem.getLastRunTimestamp() &&
                        oldItem.getSuccessRate() == newItem.getSuccessRate();
            }
        });

        jobs.clear();
        jobs.addAll(newJobs);
        result.dispatchUpdatesTo(this);
    }

    public CronJob getJobAt(int position) {
        return jobs.get(position);
    }

    class CronJobViewHolder extends RecyclerView.ViewHolder {
        private final TextView textJobName;
        private final TextView textSchedule;
        private final TextView textLastRun;
        private final TextView textSuccessRate;
        private final TextView textErrorMessage;
        final Chip chipStatus;

        CronJobViewHolder(@NonNull View itemView) {
            super(itemView);
            textJobName = itemView.findViewById(R.id.text_job_name);
            textSchedule = itemView.findViewById(R.id.text_schedule);
            textLastRun = itemView.findViewById(R.id.text_last_run);
            textSuccessRate = itemView.findViewById(R.id.text_success_rate);
            textErrorMessage = itemView.findViewById(R.id.text_error_message);
            chipStatus = itemView.findViewById(R.id.chip_status);
        }

        void bind(CronJob job) {
            textJobName.setText(job.getName());
            textSchedule.setText(CronJobScheduler.formatScheduleForDisplay(job.getSchedule()));

            // Status chip
            if (job.isPaused()) {
                chipStatus.setText(R.string.cron_status_paused);
            } else if (job.isEnabled()) {
                chipStatus.setText(R.string.cron_status_active);
            } else {
                chipStatus.setText(R.string.cron_status_disabled);
            }

            // Last run time
            if (job.getLastRunTimestamp() > 0) {
                String lastRunText = formatRelativeTime(job.getLastRunTimestamp());
                textLastRun.setText(itemView.getContext().getString(R.string.cron_last_run, lastRunText));
                textLastRun.setVisibility(View.VISIBLE);
            } else {
                textLastRun.setText(R.string.cron_never_run);
                textLastRun.setVisibility(View.VISIBLE);
            }

            // Success rate
            textSuccessRate.setText(itemView.getContext().getString(R.string.cron_success_rate, job.getSuccessRate()));

            // Error message
            if (job.getLastError() != null && !job.getLastError().isEmpty() && job.getRetryCount() > 0) {
                textErrorMessage.setText(itemView.getContext().getString(R.string.cron_error_prefix, job.getLastError()));
                textErrorMessage.setVisibility(View.VISIBLE);
            } else {
                textErrorMessage.setVisibility(View.GONE);
            }

            // Click listeners
            itemView.setOnClickListener(v -> listener.onCronJobClick(job));
            itemView.setOnLongClickListener(v -> {
                listener.onCronJobLongClick(job, v);
                return true;
            });
        }

        private String formatRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + " day" + (days > 1 ? "s" : "") + " ago";
            } else if (hours > 0) {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else if (minutes > 0) {
                return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
            } else {
                return "Just now";
            }
        }
    }
}
