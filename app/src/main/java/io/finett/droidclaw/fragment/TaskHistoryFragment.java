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

import java.util.List;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.TaskRecordAdapter;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Fragment for viewing execution history of a specific cron job.
 */
public class TaskHistoryFragment extends Fragment implements TaskRecordAdapter.OnTaskRecordClickListener {
    private static final String TAG = "TaskHistoryFragment";
    public static final String ARG_CRON_JOB_ID = "cronJobId";

    private String cronJobId;

    private RecyclerView taskRecordsList;
    private View emptyState;

    private TaskRecordAdapter adapter;
    private TaskRepository taskRepository;
    private CronJob currentJob;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get cron job ID from arguments
        if (getArguments() != null) {
            cronJobId = getArguments().getString(ARG_CRON_JOB_ID);
        }

        taskRepository = new TaskRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskRecordsList = view.findViewById(R.id.taskRecordsList);
        emptyState = view.findViewById(R.id.emptyState);

        // Load cron job
        if (cronJobId != null) {
            currentJob = taskRepository.getCronJob(cronJobId);
        }

        // Set up toolbar title in MainActivity
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                String title = currentJob != null ? currentJob.getName() : "Task History";
                ((MainActivity) getActivity()).getSupportActionBar().setTitle(title);
                ((MainActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // Set up RecyclerView
        adapter = new TaskRecordAdapter(this);
        taskRecordsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        taskRecordsList.setAdapter(adapter);

        // Load data
        loadTaskRecords();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTaskRecords();
    }

    /**
     * Load and display task records.
     */
    private void loadTaskRecords() {
        if (cronJobId == null) {
            return;
        }

        List<TaskRecord> records = taskRepository.getTaskRecordsForJob(cronJobId);
        adapter.submitList(records);

        // Show/hide empty state
        if (records.isEmpty()) {
            taskRecordsList.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            taskRecordsList.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTaskRecordClick(TaskRecord record) {
        // Navigate to task chat (full conversation view)
        Bundle args = new Bundle();
        args.putString(TaskChatFragment.ARG_RECORD_ID, record.getId());
        Navigation.findNavController(requireView()).navigate(R.id.taskChatFragment, args);
    }

    @Override
    public void onViewFullChatClick(TaskRecord record) {
        // Same as clicking - navigate to task chat
        onTaskRecordClick(record);
    }
}
