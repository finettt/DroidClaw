package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.util.SettingsManager;

public class HeartbeatSettingsFragment extends Fragment {

    private SwitchMaterial switchHeartbeatEnabled;
    private AutoCompleteTextView dropdownHeartbeatInterval;
    private SwitchMaterial switchRespectActiveHours;
    private TextInputLayout layoutActiveHoursStart;
    private TextInputLayout layoutActiveHoursEnd;
    private TextInputEditText inputActiveHoursStart;
    private TextInputEditText inputActiveHoursEnd;
    private SwitchMaterial switchSendNotifications;
    private SwitchMaterial switchShowAlerts;
    private SwitchMaterial switchShowOkMessages;
    private SwitchMaterial switchRequireNetwork;
    private SwitchMaterial switchBatteryNotLow;
    private Button buttonSave;

    private SettingsManager settingsManager;
    private HeartbeatConfig heartbeatConfig;

    private static final long[] INTERVAL_OPTIONS = {15, 30, 60, 120, 240};

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
        heartbeatConfig = settingsManager.getHeartbeatConfig();
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
        setupDropdowns();
        loadHeartbeatSettings();
        setupListeners();
        updateActiveHoursVisibility();
    }

    private void initViews(View view) {
        switchHeartbeatEnabled = view.findViewById(R.id.switch_heartbeat_enabled);
        dropdownHeartbeatInterval = view.findViewById(R.id.dropdown_heartbeat_interval);
        switchRespectActiveHours = view.findViewById(R.id.switch_respect_active_hours);
        layoutActiveHoursStart = view.findViewById(R.id.layout_active_hours_start);
        layoutActiveHoursEnd = view.findViewById(R.id.layout_active_hours_end);
        inputActiveHoursStart = view.findViewById(R.id.input_active_hours_start);
        inputActiveHoursEnd = view.findViewById(R.id.input_active_hours_end);
        switchSendNotifications = view.findViewById(R.id.switch_send_notifications);
        switchShowAlerts = view.findViewById(R.id.switch_show_alerts);
        switchShowOkMessages = view.findViewById(R.id.switch_show_ok_messages);
        switchRequireNetwork = view.findViewById(R.id.switch_require_network);
        switchBatteryNotLow = view.findViewById(R.id.switch_battery_not_low);
        buttonSave = view.findViewById(R.id.button_save);
    }

    private void setupDropdowns() {
        // Setup interval dropdown
        String[] intervalDisplayNames = new String[INTERVAL_OPTIONS.length];
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] < 60) {
                intervalDisplayNames[i] = getString(R.string.heartbeat_interval_minutes, INTERVAL_OPTIONS[i]);
            } else {
                long hours = INTERVAL_OPTIONS[i] / 60;
                if (hours == 1) {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_1_hour);
                } else {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_hours, hours);
                }
            }
        }

        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                intervalDisplayNames
        );
        dropdownHeartbeatInterval.setAdapter(intervalAdapter);
    }

    private void loadHeartbeatSettings() {
        if (heartbeatConfig != null) {
            // Load enabled state
            switchHeartbeatEnabled.setChecked(heartbeatConfig.isEnabled());

            // Load interval
            loadIntervalSelection();

            // Load active hours
            switchRespectActiveHours.setChecked(heartbeatConfig.isRespectActiveHours());
            String startHours = heartbeatConfig.getActiveHoursStart();
            String endHours = heartbeatConfig.getActiveHoursEnd();
            if (startHours != null && !startHours.isEmpty()) {
                inputActiveHoursStart.setText(startHours);
            }
            if (endHours != null && !endHours.isEmpty()) {
                inputActiveHoursEnd.setText(endHours);
            }

            // Load notification settings
            switchSendNotifications.setChecked(heartbeatConfig.isSendNotifications());
            switchShowAlerts.setChecked(heartbeatConfig.isShowAlerts());
            switchShowOkMessages.setChecked(heartbeatConfig.isShowOkMessages());

            // Load advanced settings
            switchRequireNetwork.setChecked(heartbeatConfig.isRequireNetwork());
            switchBatteryNotLow.setChecked(heartbeatConfig.isBatteryNotLow());
        }
    }

    private void loadIntervalSelection() {
        long currentInterval = heartbeatConfig.getIntervalMinutes();
        String[] intervalDisplayNames = new String[INTERVAL_OPTIONS.length];
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] < 60) {
                intervalDisplayNames[i] = getString(R.string.heartbeat_interval_minutes, INTERVAL_OPTIONS[i]);
            } else {
                long hours = INTERVAL_OPTIONS[i] / 60;
                if (hours == 1) {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_1_hour);
                } else {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_hours, hours);
                }
            }
        }

        // Find matching interval
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] == currentInterval) {
                dropdownHeartbeatInterval.setText(intervalDisplayNames[i], false);
                return;
            }
        }

        // Default to 30 minutes if no match
        dropdownHeartbeatInterval.setText(intervalDisplayNames[1], false);
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveHeartbeatSettings());

        switchRespectActiveHours.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateActiveHoursVisibility();
        });
    }

    private void updateActiveHoursVisibility() {
        int visibility = switchRespectActiveHours.isChecked() ? View.VISIBLE : View.GONE;
        layoutActiveHoursStart.setVisibility(visibility);
        layoutActiveHoursEnd.setVisibility(visibility);
    }

    private void saveHeartbeatSettings() {
        // Get enabled state
        heartbeatConfig.setEnabled(switchHeartbeatEnabled.isChecked());

        // Get selected interval
        String selectedIntervalDisplay = dropdownHeartbeatInterval.getText().toString();
        long selectedInterval = 30; // Default
        String[] intervalDisplayNames = new String[INTERVAL_OPTIONS.length];
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] < 60) {
                intervalDisplayNames[i] = getString(R.string.heartbeat_interval_minutes, INTERVAL_OPTIONS[i]);
            } else {
                long hours = INTERVAL_OPTIONS[i] / 60;
                if (hours == 1) {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_1_hour);
                } else {
                    intervalDisplayNames[i] = getString(R.string.heartbeat_interval_hours, hours);
                }
            }
        }

        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (intervalDisplayNames[i].equals(selectedIntervalDisplay)) {
                selectedInterval = INTERVAL_OPTIONS[i];
                break;
            }
        }
        heartbeatConfig.setIntervalMinutes(selectedInterval);

        // Get active hours settings
        heartbeatConfig.setRespectActiveHours(switchRespectActiveHours.isChecked());

        // Validate and save active hours
        String startHours = inputActiveHoursStart.getText().toString().trim();
        String endHours = inputActiveHoursEnd.getText().toString().trim();

        if (switchRespectActiveHours.isChecked()) {
            if (!isValidTimeFormat(startHours)) {
                inputActiveHoursStart.setError(getString(R.string.heartbeat_invalid_time_format));
                return;
            }
            if (!isValidTimeFormat(endHours)) {
                inputActiveHoursEnd.setError(getString(R.string.heartbeat_invalid_time_format));
                return;
            }
        }

        heartbeatConfig.setActiveHoursStart(startHours);
        heartbeatConfig.setActiveHoursEnd(endHours);

        // Get notification settings
        heartbeatConfig.setSendNotifications(switchSendNotifications.isChecked());
        heartbeatConfig.setShowAlerts(switchShowAlerts.isChecked());
        heartbeatConfig.setShowOkMessages(switchShowOkMessages.isChecked());

        // Get advanced settings
        heartbeatConfig.setRequireNetwork(switchRequireNetwork.isChecked());
        heartbeatConfig.setBatteryNotLow(switchBatteryNotLow.isChecked());

        // Save to settings
        settingsManager.setHeartbeatConfig(heartbeatConfig);

        Toast.makeText(requireContext(), R.string.heartbeat_settings_saved, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.isEmpty()) {
            return false;
        }
        // Simple validation: should match HH:MM format
        return time.matches("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$");
    }
}
