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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.UUID;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.CronJobAdapter;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.scheduler.CronJobScheduler;

public class CronJobListFragment extends Fragment implements CronJobAdapter.OnCronJobClickListener, CronJobEditorDialog.OnCronJobSavedListener {

    private RecyclerView recyclerCronJobs;
    private TextView textEmptyState;
    private FloatingActionButton fabAddCronJob;
    private CronJobAdapter adapter;
    private TaskRepository taskRepository;
    private CronJobScheduler scheduler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cron_job_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskRepository = new TaskRepository(requireContext());
        scheduler = new CronJobScheduler(requireContext());

        recyclerCronJobs = view.findViewById(R.id.recycler_cron_jobs);
        textEmptyState = view.findViewById(R.id.text_empty_state);
        fabAddCronJob = view.findViewById(R.id.fab_add_cron_job);

        setupRecyclerView();
        setupFab();
        loadCronJobs();
    }

    private void setupRecyclerView() {
        adapter = new CronJobAdapter(this);
        recyclerCronJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerCronJobs.setAdapter(adapter);
    }

    private void setupFab() {
        fabAddCronJob.setOnClickListener(v -> showCronJobEditorDialog(null));
    }

    private void loadCronJobs() {
        List<CronJob> jobs = taskRepository.getCronJobs();
        adapter.submitList(jobs);


        if (jobs.isEmpty()) {
            textEmptyState.setVisibility(View.VISIBLE);
            recyclerCronJobs.setVisibility(View.GONE);
        } else {
            textEmptyState.setVisibility(View.GONE);
            recyclerCronJobs.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCronJobs();
    }

    @Override
    public void onCronJobClick(CronJob job) {

        Bundle args = new Bundle();
        args.putString("job_id", job.getId());
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.cronJobDetailFragment, args);
    }

    @Override
    public void onCronJobLongClick(CronJob job, View anchorView) {
        showCronJobMenu(job, anchorView);
    }

    private void showCronJobMenu(CronJob job, View anchorView) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(requireContext(), anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_cron_job_actions, popupMenu.getMenu());


        if (job.isPaused()) {
            popupMenu.getMenu().findItem(R.id.action_pause).setVisible(false);
            popupMenu.getMenu().findItem(R.id.action_resume).setVisible(true);
        } else {
            popupMenu.getMenu().findItem(R.id.action_pause).setVisible(true);
            popupMenu.getMenu().findItem(R.id.action_resume).setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_run_now) {
                runJobNow(job);
                return true;
            } else if (itemId == R.id.action_pause) {
                pauseJob(job);
                return true;
            } else if (itemId == R.id.action_resume) {
                resumeJob(job);
                return true;
            } else if (itemId == R.id.action_edit) {
                showCronJobEditorDialog(job);
                return true;
            } else if (itemId == R.id.action_history) {
                viewJobHistory(job);
                return true;
            } else if (itemId == R.id.action_delete) {
                deleteJob(job);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void runJobNow(CronJob job) {
        scheduler.executeJobNow(job.getId());
        Toast.makeText(requireContext(), "Job queued for execution", Toast.LENGTH_SHORT).show();
    }

    private void pauseJob(CronJob job) {
        job.setPaused(true);
        taskRepository.updateCronJob(job);
        scheduler.cancelJob(job.getId());
        loadCronJobs();
        Toast.makeText(requireContext(), "Job paused", Toast.LENGTH_SHORT).show();
    }

    private void resumeJob(CronJob job) {
        job.setPaused(false);
        job.setEnabled(true);
        taskRepository.updateCronJob(job);
        scheduler.scheduleJob(job);
        loadCronJobs();
        Toast.makeText(requireContext(), "Job resumed", Toast.LENGTH_SHORT).show();
    }

    private void deleteJob(CronJob job) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cron_job_delete)
                .setMessage(R.string.cron_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    taskRepository.deleteCronJob(job.getId());
                    taskRepository.deleteExecutionRecords(job.getId());
                    scheduler.cancelJob(job.getId());
                    loadCronJobs();
                    Toast.makeText(requireContext(), R.string.cron_job_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void viewJobHistory(CronJob job) {
        Bundle args = new Bundle();
        args.putString("job_id", job.getId());
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.taskHistoryFragment, args);
    }

    private void showCronJobEditorDialog(CronJob jobToEdit) {
        CronJobEditorDialog dialog = CronJobEditorDialog.newInstance(jobToEdit);
        dialog.show(getParentFragmentManager(), "CronJobEditorDialog");
    }

    /**
     * Called by CronJobEditorDialog when a job is saved.
     */
    @Override
    public void onCronJobSaved(CronJob job) {
        if (job.getId() == null || job.getId().isEmpty()) {
            job.setId(UUID.randomUUID().toString());
        }
        taskRepository.saveCronJob(job);


        scheduler.scheduleJob(job);

        loadCronJobs();
        Toast.makeText(requireContext(), R.string.cron_job_created, Toast.LENGTH_SHORT).show();
    }
}
