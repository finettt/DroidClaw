package io.finett.droidclaw.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.accessibility.AccessibilityBridge;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.util.SettingsManager;

public class AgentSettingsFragment extends Fragment {

    private AutoCompleteTextView dropdownDefaultModel;
    private SwitchMaterial switchShellAccess;
    private AutoCompleteTextView dropdownSandboxMode;
    private TextInputEditText inputMaxIterations;
    private SwitchMaterial switchRequireApproval;
    private TextInputEditText inputShellTimeout;
    private SwitchMaterial switchBackgroundShellAccess;
    private SwitchMaterial switchBackgroundExec;
    private TextInputEditText inputCustomAllowlist;
    private TextInputEditText inputLlmConnectTimeout;
    private TextInputEditText inputLlmReadTimeout;
    private TextInputEditText inputLlmWriteTimeout;

    // Screen control views
    private SwitchMaterial switchScreenControl;
    private SwitchMaterial switchScreenControlTrustMode;
    private TextView textAccessibilityStatus;
    private MaterialButton buttonOpenAccessibilitySettings;

    private Button buttonSave;

    private SettingsManager settingsManager;
    private AgentConfig agentConfig;
    private List<String> availableModels;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
        agentConfig = settingsManager.getAgentConfig();
        availableModels = settingsManager.getAllModelReferences();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupDropdowns();
        loadAgentSettings();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh accessibility status every time the fragment resumes (user may have
        // just come back from the system Accessibility Settings screen)
        updateAccessibilityStatus();
    }

    private void initViews(View view) {
        dropdownDefaultModel = view.findViewById(R.id.dropdown_default_model);
        switchShellAccess = view.findViewById(R.id.switch_shell_access);
        dropdownSandboxMode = view.findViewById(R.id.dropdown_sandbox_mode);
        inputMaxIterations = view.findViewById(R.id.input_max_iterations);
        switchRequireApproval = view.findViewById(R.id.switch_require_approval);
        inputShellTimeout = view.findViewById(R.id.input_shell_timeout);
        switchBackgroundShellAccess = view.findViewById(R.id.switch_background_shell_access);
        switchBackgroundExec = view.findViewById(R.id.switch_background_exec);
        inputCustomAllowlist = view.findViewById(R.id.input_custom_allowlist);
        inputLlmConnectTimeout = view.findViewById(R.id.input_llm_connect_timeout);
        inputLlmReadTimeout = view.findViewById(R.id.input_llm_read_timeout);
        inputLlmWriteTimeout = view.findViewById(R.id.input_llm_write_timeout);

        switchScreenControl = view.findViewById(R.id.switch_screen_control);
        switchScreenControlTrustMode = view.findViewById(R.id.switch_screen_control_trust_mode);
        textAccessibilityStatus = view.findViewById(R.id.text_accessibility_status);
        buttonOpenAccessibilitySettings = view.findViewById(R.id.button_open_accessibility_settings);

        buttonSave = view.findViewById(R.id.button_save);
    }

    private void setupDropdowns() {
        if (availableModels.isEmpty()) {
            String[] noModels = {getString(R.string.agent_default_model_hint)};
            ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    noModels
            );
            dropdownDefaultModel.setAdapter(modelAdapter);
            dropdownDefaultModel.setEnabled(false);
        } else {
            String[] modelDisplayNames = new String[availableModels.size()];
            for (int i = 0; i < availableModels.size(); i++) {
                modelDisplayNames[i] = settingsManager.getModelDisplayName(availableModels.get(i));
            }
            ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    modelDisplayNames
            );
            dropdownDefaultModel.setAdapter(modelAdapter);
        }

        String[] sandboxModes = {
                getString(R.string.sandbox_strict),
                getString(R.string.sandbox_relaxed),
                getString(R.string.sandbox_full)
        };
        ArrayAdapter<String> sandboxAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                sandboxModes
        );
        dropdownSandboxMode.setAdapter(sandboxAdapter);
    }

    private void loadAgentSettings() {
        if (agentConfig != null) {
            String defaultModel = agentConfig.getDefaultModel();
            if (defaultModel != null && !defaultModel.isEmpty() && !availableModels.isEmpty()) {
                String displayName = settingsManager.getModelDisplayName(defaultModel);
                dropdownDefaultModel.setText(displayName, false);
            } else if (!availableModels.isEmpty()) {
                dropdownDefaultModel.setText(getString(R.string.agent_default_model_hint), false);
            }

            switchShellAccess.setChecked(agentConfig.isShellAccess());

            String sandboxMode = agentConfig.getSandboxMode();
            if ("relaxed".equals(sandboxMode)) {
                dropdownSandboxMode.setText(getString(R.string.sandbox_relaxed), false);
            } else if ("full".equals(sandboxMode)) {
                dropdownSandboxMode.setText(getString(R.string.sandbox_full), false);
            } else {
                dropdownSandboxMode.setText(getString(R.string.sandbox_strict), false);
            }

            inputMaxIterations.setText(String.valueOf(agentConfig.getMaxIterations()));
            switchRequireApproval.setChecked(agentConfig.isRequireApproval());
            inputShellTimeout.setText(String.valueOf(agentConfig.getShellTimeout()));
            switchBackgroundShellAccess.setChecked(agentConfig.isBackgroundShellEnabled());
            switchBackgroundExec.setChecked(agentConfig.isBackgroundExecEnabled());

            StringBuilder allowlistText = new StringBuilder();
            for (String path : agentConfig.getCustomAllowlist()) {
                if (allowlistText.length() > 0) allowlistText.append('\n');
                allowlistText.append(path);
            }
            inputCustomAllowlist.setText(allowlistText.toString());

            inputLlmConnectTimeout.setText(String.valueOf(agentConfig.getLlmConnectTimeout()));
            inputLlmReadTimeout.setText(String.valueOf(agentConfig.getLlmReadTimeout()));
            inputLlmWriteTimeout.setText(String.valueOf(agentConfig.getLlmWriteTimeout()));

            // Screen control
            switchScreenControl.setChecked(agentConfig.isScreenControlEnabled());
            switchScreenControlTrustMode.setChecked(agentConfig.isScreenControlTrustMode());
        }

        updateAccessibilityStatus();
    }

    /**
     * Update the accessibility permission status text and "Open Settings" button visibility
     * based on whether the accessibility service is currently connected.
     */
    private void updateAccessibilityStatus() {
        if (textAccessibilityStatus == null) return;
        boolean connected = AccessibilityBridge.isConnected();
        if (connected) {
            textAccessibilityStatus.setText(R.string.screen_control_accessibility_granted);
            textAccessibilityStatus.setTextColor(
                    requireContext().getColor(android.R.color.holo_green_dark));
        } else {
            textAccessibilityStatus.setText(R.string.screen_control_accessibility_not_granted);
            textAccessibilityStatus.setTextColor(
                    requireContext().getColor(android.R.color.darker_gray));
        }
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveAgentSettings());

        // When user enables screen control and permission is not granted, open system settings
        switchScreenControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !AccessibilityBridge.isConnected()) {
                openAccessibilitySettings();
            }
        });

        buttonOpenAccessibilitySettings.setOnClickListener(v -> openAccessibilitySettings());
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAgentSettings() {
        String maxIterationsStr = inputMaxIterations.getText().toString().trim();
        String shellTimeoutStr = inputShellTimeout.getText().toString().trim();

        int maxIterations;
        try {
            maxIterations = Integer.parseInt(maxIterationsStr);
            if (maxIterations < 1 || maxIterations > 50) {
                inputMaxIterations.setError("Max iterations must be between 1 and 50");
                return;
            }
        } catch (NumberFormatException e) {
            inputMaxIterations.setError(getString(R.string.validation_invalid_number));
            return;
        }

        int shellTimeout;
        try {
            shellTimeout = Integer.parseInt(shellTimeoutStr);
            if (shellTimeout < 5 || shellTimeout > 300) {
                inputShellTimeout.setError("Shell timeout must be between 5 and 300 seconds");
                return;
            }
        } catch (NumberFormatException e) {
            inputShellTimeout.setError(getString(R.string.validation_invalid_number));
            return;
        }

        String selectedModelDisplay = dropdownDefaultModel.getText().toString();
        String selectedModel = "";
        if (!availableModels.isEmpty()) {
            for (String modelRef : availableModels) {
                if (settingsManager.getModelDisplayName(modelRef).equals(selectedModelDisplay)) {
                    selectedModel = modelRef;
                    break;
                }
            }
        }

        String sandboxMode = "strict";
        String selectedSandbox = dropdownSandboxMode.getText().toString();
        if (selectedSandbox.equals(getString(R.string.sandbox_relaxed))) {
            sandboxMode = "relaxed";
        } else if (selectedSandbox.equals(getString(R.string.sandbox_full))) {
            sandboxMode = "full";
        }

        // Parse custom allowlist (one path per line)
        List<String> customAllowlist = new ArrayList<>();
        String allowlistRaw = inputCustomAllowlist.getText() != null
                ? inputCustomAllowlist.getText().toString() : "";
        for (String line : allowlistRaw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                customAllowlist.add(trimmed);
            }
        }

        // Validate network timeouts
        String llmConnectTimeoutStr = inputLlmConnectTimeout.getText().toString().trim();
        String llmReadTimeoutStr = inputLlmReadTimeout.getText().toString().trim();
        String llmWriteTimeoutStr = inputLlmWriteTimeout.getText().toString().trim();

        if (!validateTimeout(inputLlmConnectTimeout, llmConnectTimeoutStr)) return;
        if (!validateTimeout(inputLlmReadTimeout, llmReadTimeoutStr)) return;
        if (!validateTimeout(inputLlmWriteTimeout, llmWriteTimeoutStr)) return;

        int llmConnectTimeout = Integer.parseInt(llmConnectTimeoutStr);
        int llmReadTimeout = Integer.parseInt(llmReadTimeoutStr);
        int llmWriteTimeout = Integer.parseInt(llmWriteTimeoutStr);

        agentConfig.setDefaultModel(selectedModel);
        agentConfig.setShellAccess(switchShellAccess.isChecked());
        agentConfig.setSandboxMode(sandboxMode);
        agentConfig.setMaxIterations(maxIterations);
        agentConfig.setRequireApproval(switchRequireApproval.isChecked());
        agentConfig.setShellTimeout(shellTimeout);
        agentConfig.setBackgroundShellEnabled(switchBackgroundShellAccess.isChecked());
        agentConfig.setBackgroundExecEnabled(switchBackgroundExec.isChecked());
        agentConfig.setCustomAllowlist(customAllowlist);
        agentConfig.setLlmConnectTimeout(llmConnectTimeout);
        agentConfig.setLlmReadTimeout(llmReadTimeout);
        agentConfig.setLlmWriteTimeout(llmWriteTimeout);
        agentConfig.setScreenControlEnabled(switchScreenControl.isChecked());
        agentConfig.setScreenControlTrustMode(switchScreenControlTrustMode.isChecked());

        settingsManager.setAgentConfig(agentConfig);

        Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }

    private boolean validateTimeout(TextInputEditText input, String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 1 || seconds > 600) {
                input.setError("Timeout must be between 1 and 600 seconds");
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            input.setError(getString(R.string.validation_invalid_number));
            return false;
        }
    }
}