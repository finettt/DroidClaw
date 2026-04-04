package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.CronJobAdapter;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Fragment for managing and viewing cron jobs.
 * Shows a list of all scheduled tasks with their status.
 */
public class CronJobListFragment extends Fragment implements CronJobAdapter.OnCronJobClickListener {
    private static final String TAG = "CronJobListFragment";

    private RecyclerView cronJobsList;
    private View emptyState;
    private TextView totalJobsText;
    private TextView activeJobsText;
    private TextView successRateText;

    private CronJobAdapter adapter;
    private TaskRepository taskRepository;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskRepository = new TaskRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cron_job_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cronJobsList = view.findViewById(R.id.cronJobsList);
        emptyState = view.findViewById(R.id.emptyState);
        totalJobsText = view.findViewById(R.id.totalJobsText);
        activeJobsText = view.findViewById(R.id.activeJobsText);
        successRateText = view.findViewById(R.id.successRateText);

        // Set up toolbar title in MainActivity
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                ((MainActivity) getActivity()).getSupportActionBar().setTitle("Scheduled Tasks");
                ((MainActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // Set up RecyclerView
        adapter = new CronJobAdapter(this);
        cronJobsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        cronJobsList.setAdapter(adapter);

        // Load data
        loadCronJobs();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCronJobs();
    }

    /**
     * Load and display cron jobs list.
     */
    private void loadCronJobs() {
        List<CronJob> jobs = taskRepository.getAllCronJobs();
        adapter.submitList(jobs);

        // Update stats
        totalJobsText.setText(String.valueOf(jobs.size()));
        
        long activeCount = jobs.stream().filter(CronJob::isEnabled).count();
        activeJobsText.setText(String.valueOf(activeCount));

        int overallSuccessRate = taskRepository.getOverallSuccessRate();
        successRateText.setText(overallSuccessRate + "%");

        // Show/hide empty state
        if (jobs.isEmpty()) {
            cronJobsList.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            cronJobsList.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCronJobClick(CronJob job) {
        // Navigate to task history for this job
        Bundle args = new Bundle();
        args.putString(TaskHistoryFragment.ARG_CRON_JOB_ID, job.getId());
        Navigation.findNavController(requireView()).navigate(R.id.taskHistoryFragment, args);
    }

    @Override
    public void onCronJobLongClick(CronJob job) {
        // Show context menu
        showCronJobMenu(job);
    }

    /**
     * Show context menu for cron job (toggle, delete, etc.)
     */
    private void showCronJobMenu(CronJob job) {
        String[] options = new String[]{
                job.isEnabled() ? "Pause" : "Resume",
                "Delete"
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(job.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            toggleCronJob(job);
                            break;
                        case 1:
                            confirmDeleteCronJob(job);
                            break;
                    }
                })
                .show();
    }

    /**
     * Toggle cron job enabled state.
     */
    private void toggleCronJob(CronJob job) {
        taskRepository.toggleCronJob(job.getId());
        loadCronJobs();
    }

    /**
     * Show confirmation dialog before deleting cron job.
     */
    private void confirmDeleteCronJob(CronJob job) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Task")
                .setMessage("Delete \"" + job.getName() + "\" and all its execution history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    taskRepository.deleteCronJob(job.getId());
                    loadCronJobs();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
