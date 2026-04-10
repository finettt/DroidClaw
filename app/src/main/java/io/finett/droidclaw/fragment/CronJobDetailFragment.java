package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.scheduler.CronJobScheduler;

public class CronJobDetailFragment extends Fragment {

    private TextView textJobName;
    private TextView textJobPrompt;
    private TextView textSchedule;
    private TextView textStatus;
    private TextView textCreated;
    private TextView textSuccessRate;
    private TextView textAvgDuration;
    private TextView textTotalRuns;
    private TextView textLastRun;
    private MaterialButton buttonViewHistory;
    private MaterialButton buttonRunNow;

    private CronJob job;
    private TaskRepository taskRepository;
    private CronJobScheduler scheduler;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cron_job_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskRepository = new TaskRepository(requireContext());
        scheduler = new CronJobScheduler(requireContext());

        initializeViews(view);

        // Get job ID from arguments
        if (getArguments() != null) {
            String jobId = getArguments().getString("job_id");
            if (jobId != null) {
                job = taskRepository.getCronJob(jobId);
                if (job != null) {
                    displayJobDetails();
                }
            }
        }

        setupButtons();
    }

    private void initializeViews(View view) {
        textJobName = view.findViewById(R.id.text_job_name);
        textJobPrompt = view.findViewById(R.id.text_job_prompt);
        textSchedule = view.findViewById(R.id.text_schedule);
        textStatus = view.findViewById(R.id.text_status);
        textCreated = view.findViewById(R.id.text_created);
        textSuccessRate = view.findViewById(R.id.text_success_rate);
        textAvgDuration = view.findViewById(R.id.text_avg_duration);
        textTotalRuns = view.findViewById(R.id.text_total_runs);
        textLastRun = view.findViewById(R.id.text_last_run);
        buttonViewHistory = view.findViewById(R.id.button_view_history);
        buttonRunNow = view.findViewById(R.id.button_run_now);
    }

    private void displayJobDetails() {
        textJobName.setText(job.getName());
        textJobPrompt.setText(job.getPrompt());
        textSchedule.setText(CronJobScheduler.formatScheduleForDisplay(job.getSchedule()));

        // Status
        String status;
        if (job.isPaused()) {
            status = requireContext().getString(R.string.cron_status_paused_fmt);
        } else if (job.isEnabled()) {
            status = requireContext().getString(R.string.cron_status_active_fmt);
        } else {
            status = requireContext().getString(R.string.cron_status_disabled_fmt);
        }
        textStatus.setText(status);

        // Created date
        textCreated.setText(requireContext().getString(R.string.cron_created_fmt, dateFormat.format(job.getCreatedAt())));

        // Metrics
        textSuccessRate.setText(requireContext().getString(R.string.cron_success_rate_fmt, job.getSuccessRate()));

        long avgMs = job.getAverageExecutionTime();
        String avgText;
        if (avgMs > 0) {
            long avgSec = avgMs / 1000;
            if (avgSec < 60) {
                avgText = String.format(Locale.US, "%.1fs", avgMs / 1000.0);
            } else {
                long minutes = avgSec / 60;
                long seconds = avgSec % 60;
                avgText = minutes + "m " + seconds + "s";
            }
        } else {
            avgText = requireContext().getString(R.string.cron_avg_duration_na);
        }
        textAvgDuration.setText(requireContext().getString(R.string.cron_avg_duration_fmt, avgText));

        int totalRuns = job.getSuccessCount() + job.getFailureCount();
        textTotalRuns.setText(requireContext().getString(R.string.cron_total_runs_fmt, totalRuns));

        if (job.getLastRunTimestamp() > 0) {
            textLastRun.setText(requireContext().getString(R.string.cron_last_run_fmt, formatRelativeTime(job.getLastRunTimestamp())));
        } else {
            textLastRun.setText(R.string.cron_never_run);
        }
    }

    private void setupButtons() {
        buttonViewHistory.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("job_id", job.getId());
            NavController navController = Navigation.findNavController(requireView());
            navController.navigate(R.id.taskHistoryFragment, args);
        });

        buttonRunNow.setOnClickListener(v -> {
            scheduler.executeJobNow(job.getId());
            Toast.makeText(requireContext(), "Job queued for execution", Toast.LENGTH_SHORT).show();
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
