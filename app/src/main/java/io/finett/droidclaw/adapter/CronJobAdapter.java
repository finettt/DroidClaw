package io.finett.droidclaw.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;

/**
 * Adapter for displaying a list of cron jobs.
 */
public class CronJobAdapter extends RecyclerView.Adapter<CronJobAdapter.CronJobViewHolder> {

    public interface OnCronJobClickListener {
        void onCronJobClick(CronJob job);
        void onCronJobLongClick(CronJob job);
    }

    private final List<CronJob> jobs = new ArrayList<>();
    private final OnCronJobClickListener listener;

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
        jobs.clear();
        jobs.addAll(newJobs);
        notifyDataSetChanged();
    }

    public CronJob getJob(int position) {
        return jobs.get(position);
    }

    class CronJobViewHolder extends RecyclerView.ViewHolder {
        private final Chip statusChip;
        private final TextView jobNameText;
        private final TextView intervalText;
        private final TextView lastRunText;
        private final TextView nextRunText;
        private final TextView runCountText;
        private final TextView successRateText;

        CronJobViewHolder(@NonNull View itemView) {
            super(itemView);
            statusChip = itemView.findViewById(R.id.statusChip);
            jobNameText = itemView.findViewById(R.id.jobNameText);
            intervalText = itemView.findViewById(R.id.intervalText);
            lastRunText = itemView.findViewById(R.id.lastRunText);
            nextRunText = itemView.findViewById(R.id.nextRunText);
            runCountText = itemView.findViewById(R.id.runCountText);
            successRateText = itemView.findViewById(R.id.successRateText);
        }

        void bind(CronJob job) {
            // Job name
            jobNameText.setText(job.getName());

            // Status chip
            if (job.isEnabled()) {
                statusChip.setText("Active");
                statusChip.setChipBackgroundColorResource(R.color.material_grey_300);
            } else {
                statusChip.setText("Paused");
                statusChip.setChipBackgroundColorResource(R.color.material_grey_100);
            }

            // Interval
            intervalText.setText(job.getIntervalDisplayString());

            // Last run info
            if (job.getLastRunAt() > 0) {
                long timeSinceLastRun = System.currentTimeMillis() - job.getLastRunAt();
                String timeAgo = getTimeAgoString(timeSinceLastRun);
                int successRate = job.getSuccessRate();
                
                String statusIcon = successRate >= 80 ? "✓" : "⚠";
                lastRunText.setText(String.format("Last: %s (%s %d%%)", 
                        timeAgo, statusIcon, successRate));
            } else {
                lastRunText.setText("Never run");
            }

            // Next run
            if (job.isEnabled() && job.getNextRunAt() > 0) {
                long timeUntilNext = job.getNextRunAt() - System.currentTimeMillis();
                if (timeUntilNext > 0) {
                    nextRunText.setText("Next: " + getTimeFromNowString(timeUntilNext));
                    nextRunText.setVisibility(View.VISIBLE);
                } else {
                    nextRunText.setVisibility(View.GONE);
                }
            } else {
                nextRunText.setVisibility(View.GONE);
            }

            // Statistics
            runCountText.setText("Runs: " + job.getRunCount());
            successRateText.setText(job.getSuccessRate() + "%");

            // Click listeners
            itemView.setOnClickListener(v -> listener.onCronJobClick(job));
            itemView.setOnLongClickListener(v -> {
                listener.onCronJobLongClick(job);
                return true;
            });
        }

        private String getTimeAgoString(long millis) {
            long seconds = millis / 1000;
            if (seconds < 60) return seconds + "s ago";
            
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            
            long days = hours / 24;
            return days + "d ago";
        }

        private String getTimeFromNowString(long millis) {
            long seconds = millis / 1000;
            if (seconds < 60) return "in " + seconds + "s";
            
            long minutes = seconds / 60;
            if (minutes < 60) return "in " + minutes + "m";
            
            long hours = minutes / 60;
            if (hours < 24) return "in " + hours + "h " + (minutes % 60) + "m";
            
            long days = hours / 24;
            return "in " + days + "d";
        }
    }
}
