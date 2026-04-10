package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.TaskExecutionAdapter;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.TaskRepository;

public class TaskHistoryFragment extends Fragment implements TaskExecutionAdapter.OnTaskExecutionClickListener {

    private RecyclerView recyclerTaskHistory;
    private TextView textEmptyHistory;
    private TextView textTotalExecutions;
    private TextView textSuccessRate;
    private TextView textAvgDuration;
    private MaterialCardView cardStats;
    private Spinner spinnerJobFilter;
    private FloatingActionButton fabRefreshHistory;

    private TaskExecutionAdapter adapter;
    private TaskRepository taskRepository;
    private String jobIdFilter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskRepository = new TaskRepository(requireContext());

        // Get job ID filter from arguments if provided
        if (getArguments() != null) {
            jobIdFilter = getArguments().getString("job_id");
        }

        recyclerTaskHistory = view.findViewById(R.id.recycler_task_history);
        textEmptyHistory = view.findViewById(R.id.text_empty_history);
        cardStats = view.findViewById(R.id.card_stats);
        textTotalExecutions = view.findViewById(R.id.text_total_executions);
        textSuccessRate = view.findViewById(R.id.text_success_rate);
        textAvgDuration = view.findViewById(R.id.text_avg_duration);
        spinnerJobFilter = view.findViewById(R.id.spinner_job_filter);
        fabRefreshHistory = view.findViewById(R.id.fab_refresh_history);

        setupRecyclerView();
        setupSpinner();
        setupFab();
        loadTaskHistory();
    }

    private void setupRecyclerView() {
        adapter = new TaskExecutionAdapter(this);
        recyclerTaskHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTaskHistory.setAdapter(adapter);
    }

    private void setupSpinner() {
        // Load all cron jobs for filter
        List<CronJob> jobs = taskRepository.getCronJobs();
        List<String> jobNames = new ArrayList<>();
        jobNames.add(getString(R.string.task_history_filter_all));

        for (CronJob job : jobs) {
            jobNames.add(job.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, jobNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerJobFilter.setAdapter(adapter);

        spinnerJobFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // All tasks
                    jobIdFilter = null;
                } else {
                    // Specific job
                    jobIdFilter = jobs.get(position - 1).getId();
                }
                loadTaskHistory();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // If jobIdFilter is set, select that job in spinner
        if (jobIdFilter != null) {
            for (int i = 0; i < jobs.size(); i++) {
                if (jobs.get(i).getId().equals(jobIdFilter)) {
                    spinnerJobFilter.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    private void setupFab() {
        fabRefreshHistory.setOnClickListener(v -> loadTaskHistory());
    }

    private void loadTaskHistory() {
        List<TaskExecutionRecord> records;

        if (jobIdFilter != null) {
            // Filter by specific job
            records = taskRepository.getExecutionHistory(jobIdFilter);
        } else {
            // Show all records
            records = taskRepository.getAllExecutionRecords();
        }

        adapter.submitList(records);

        // Update stats
        updateStats(records);

        // Show/hide empty state
        if (records.isEmpty()) {
            textEmptyHistory.setVisibility(View.VISIBLE);
            recyclerTaskHistory.setVisibility(View.GONE);
        } else {
            textEmptyHistory.setVisibility(View.GONE);
            recyclerTaskHistory.setVisibility(View.VISIBLE);
        }
    }

    private void updateStats(List<TaskExecutionRecord> records) {
        if (records.isEmpty()) {
            cardStats.setVisibility(View.GONE);
            return;
        }

        cardStats.setVisibility(View.VISIBLE);

        int total = records.size();
        int successCount = 0;
        long totalDuration = 0;
        int durationCount = 0;

        for (TaskExecutionRecord record : records) {
            if (record.isSuccess()) {
                successCount++;
            }
            if (record.getDurationMillis() > 0) {
                totalDuration += record.getDurationMillis();
                durationCount++;
            }
        }

        int successRate = (successCount * 100) / total;
        long avgDuration = durationCount > 0 ? totalDuration / durationCount : 0;

        textTotalExecutions.setText("Total: " + total);
        textSuccessRate.setText("Success: " + successRate + "%");

        // Format average duration
        String avgDurationText;
        long avgSec = avgDuration / 1000;
        if (avgSec < 60) {
            avgDurationText = String.format("%.1fs", avgDuration / 1000.0);
        } else {
            long minutes = avgSec / 60;
            long seconds = avgSec % 60;
            avgDurationText = minutes + "m " + seconds + "s";
        }
        textAvgDuration.setText("Avg: " + avgDurationText);
    }

    @Override
    public void onTaskExecutionClick(TaskExecutionRecord record) {
        // TODO: Navigate to detail view showing full execution data
        // For now, just show a toast with basic info
        String message = "Task " + (record.isSuccess() ? "succeeded" : "failed") +
                "\nDuration: " + (record.getDurationMillis() / 1000) + "s" +
                "\nTokens: " + record.getTokensUsed() +
                "\nSteps: " + record.getIterations();

        if (!record.isSuccess() && record.getErrorMessage() != null) {
            message += "\n\nError: " + record.getErrorMessage();
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Execution Details")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }
}
