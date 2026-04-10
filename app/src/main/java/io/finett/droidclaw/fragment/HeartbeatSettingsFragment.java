package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.repository.TaskRepository;
import io.finett.droidclaw.service.TaskScheduler;
import io.finett.droidclaw.util.NotificationPermissionHelper;

public class HeartbeatSettingsFragment extends Fragment {

    private static final String TAG = "HeartbeatSettings";

    // Interval options in milliseconds
    private static final long INTERVAL_15_MIN = 15 * 60 * 1000L;
    private static final long INTERVAL_30_MIN = 30 * 60 * 1000L;
    private static final long INTERVAL_60_MIN = 60 * 60 * 1000L;
    private static final long INTERVAL_120_MIN = 120 * 60 * 1000L;

    private SwitchMaterial switchHeartbeatEnabled;
    private AutoCompleteTextView dropdownInterval;
    private TextView textLastRun;
    private TextView textLastStatus;
    private MaterialButton buttonRunNow;
    private MaterialButton buttonEditHeartbeat;

    private HeartbeatConfigRepository configRepository;
    private TaskRepository taskRepository;
    private TaskScheduler taskScheduler;
    private NotificationPermissionHelper permissionHelper;
    private HeartbeatConfig config;

    private final long[] intervalOptions = {
            INTERVAL_15_MIN,
            INTERVAL_30_MIN,
            INTERVAL_60_MIN,
            INTERVAL_120_MIN
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configRepository = new HeartbeatConfigRepository(requireContext());
        taskRepository = new TaskRepository(requireContext());
        taskScheduler = new TaskScheduler(requireContext());
        permissionHelper = new NotificationPermissionHelper(requireContext());
        config = configRepository.getConfig();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_heartbeat_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupIntervalDropdown();
        loadHeartbeatSettings();
        setupListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // This will be handled by the permission helper callback
    }

    private void initViews(View view) {
        switchHeartbeatEnabled = view.findViewById(R.id.switch_heartbeat_enabled);
        dropdownInterval = view.findViewById(R.id.dropdown_interval);
        textLastRun = view.findViewById(R.id.text_last_run);
        textLastStatus = view.findViewById(R.id.text_last_status);
        buttonRunNow = view.findViewById(R.id.button_run_now);
        buttonEditHeartbeat = view.findViewById(R.id.button_edit_heartbeat);
    }

    private void setupIntervalDropdown() {
        String[] intervalLabels = {
                "15 minutes",
                "30 minutes",
                "1 hour",
                "2 hours"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                intervalLabels
        );
        dropdownInterval.setAdapter(adapter);
    }

    private void loadHeartbeatSettings() {
        // Load enabled state
        switchHeartbeatEnabled.setChecked(config.isEnabled());

        // Load interval
        long interval = config.getIntervalMillis();
        String intervalLabel = getIntervalLabel(interval);
        dropdownInterval.setText(intervalLabel, false);

        // Load last run timestamp
        long lastRun = config.getLastRunTimestamp();
        if (lastRun > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            textLastRun.setText(sdf.format(new Date(lastRun)));
        } else {
            textLastRun.setText(R.string.heartbeat_never);
        }

        // Load last status
        loadLastStatus();
    }

    private void loadLastStatus() {
        // Get most recent heartbeat task result
        TaskResult lastResult = taskRepository.getLastHeartbeatResult();
        if (lastResult != null) {
            String healthy = lastResult.getMetadataValue("healthy");
            if ("true".equals(healthy)) {
                textLastStatus.setText("✓ System Healthy");
                textLastStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
            } else {
                textLastStatus.setText("⚠ Issues Detected");
                textLastStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
            }
        } else {
            textLastStatus.setText(R.string.heartbeat_no_data);
            textLastStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        }
    }

    private String getIntervalLabel(long interval) {
        if (interval == INTERVAL_15_MIN) return "15 minutes";
        if (interval == INTERVAL_30_MIN) return "30 minutes";
        if (interval == INTERVAL_60_MIN) return "1 hour";
        if (interval == INTERVAL_120_MIN) return "2 hours";
        return "30 minutes"; // default
    }

    private long getIntervalFromLabel(String label) {
        if (label.equals("15 minutes")) return INTERVAL_15_MIN;
        if (label.equals("30 minutes")) return INTERVAL_30_MIN;
        if (label.equals("1 hour")) return INTERVAL_60_MIN;
        if (label.equals("2 hours")) return INTERVAL_120_MIN;
        return INTERVAL_30_MIN; // default
    }

    private void setupListeners() {
        // Save on toggle change
        switchHeartbeatEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check notification permission before enabling
                permissionHelper.checkAndRequestPermission(requireActivity(), new NotificationPermissionHelper.PermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        enableHeartbeat();
                    }

                    @Override
                    public void onPermissionDenied() {
                        // Still enable heartbeat, but without notifications
                        Toast.makeText(requireContext(), "Heartbeat enabled (notifications disabled)", Toast.LENGTH_SHORT).show();
                        enableHeartbeat();
                    }
                });
            } else {
                config.setEnabled(false);
                configRepository.updateConfig(config);
                taskScheduler.cancelHeartbeat();
                Toast.makeText(requireContext(), "Heartbeat disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Save on interval change
        dropdownInterval.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLabel = dropdownInterval.getText().toString();
            long selectedInterval = getIntervalFromLabel(selectedLabel);
            config.setIntervalMillis(selectedInterval);
            configRepository.updateConfig(config);

            // Reschedule with new interval if enabled
            if (config.isEnabled()) {
                taskScheduler.scheduleHeartbeat(config);
            }
        });

        // Run now button
        buttonRunNow.setOnClickListener(v -> {
            taskScheduler.runTaskNow("heartbeat", "heartbeat");
            Toast.makeText(requireContext(), "Heartbeat task queued", Toast.LENGTH_SHORT).show();
        });

        // Edit HEARTBEAT.md button
        buttonEditHeartbeat.setOnClickListener(v -> {
            // Navigate to file browser to edit HEARTBEAT.md
            Bundle bundle = new Bundle();
            bundle.putString("file_path", ".agent/HEARTBEAT.md");
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_heartbeatSettingsFragment_to_fileBrowserFragment, bundle);
        });
    }

    /**
     * Enable heartbeat after permission is granted.
     */
    private void enableHeartbeat() {
        config.setEnabled(true);
        configRepository.updateConfig(config);
        taskScheduler.scheduleHeartbeat(config);
        Toast.makeText(requireContext(), "Heartbeat enabled", Toast.LENGTH_SHORT).show();
    }
}
