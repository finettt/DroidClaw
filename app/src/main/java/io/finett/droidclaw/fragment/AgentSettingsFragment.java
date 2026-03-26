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

import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.util.SettingsManager;

public class AgentSettingsFragment extends Fragment {

    private AutoCompleteTextView dropdownDefaultModel;
    private SwitchMaterial switchShellAccess;
    private AutoCompleteTextView dropdownSandboxMode;
    private TextInputEditText inputMaxIterations;
    private SwitchMaterial switchRequireApproval;
    private TextInputEditText inputShellTimeout;
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

    private void initViews(View view) {
        dropdownDefaultModel = view.findViewById(R.id.dropdown_default_model);
        switchShellAccess = view.findViewById(R.id.switch_shell_access);
        dropdownSandboxMode = view.findViewById(R.id.dropdown_sandbox_mode);
        inputMaxIterations = view.findViewById(R.id.input_max_iterations);
        switchRequireApproval = view.findViewById(R.id.switch_require_approval);
        inputShellTimeout = view.findViewById(R.id.input_shell_timeout);
        buttonSave = view.findViewById(R.id.button_save);
    }

    private void setupDropdowns() {
        // Setup default model dropdown
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

        // Setup sandbox mode dropdown
        String[] sandboxModes = {
                getString(R.string.sandbox_strict),
                getString(R.string.sandbox_relaxed)
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
            // Load default model
            String defaultModel = agentConfig.getDefaultModel();
            if (defaultModel != null && !defaultModel.isEmpty() && !availableModels.isEmpty()) {
                String displayName = settingsManager.getModelDisplayName(defaultModel);
                dropdownDefaultModel.setText(displayName, false);
            } else if (!availableModels.isEmpty()) {
                dropdownDefaultModel.setText(getString(R.string.agent_default_model_hint), false);
            }

            // Load other settings
            switchShellAccess.setChecked(agentConfig.isShellAccess());

            String sandboxMode = agentConfig.getSandboxMode();
            if ("relaxed".equals(sandboxMode)) {
                dropdownSandboxMode.setText(getString(R.string.sandbox_relaxed), false);
            } else {
                dropdownSandboxMode.setText(getString(R.string.sandbox_strict), false);
            }

            inputMaxIterations.setText(String.valueOf(agentConfig.getMaxIterations()));
            switchRequireApproval.setChecked(agentConfig.isRequireApproval());
            inputShellTimeout.setText(String.valueOf(agentConfig.getShellTimeout()));
        }
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveAgentSettings());
    }

    private void saveAgentSettings() {
        String maxIterationsStr = inputMaxIterations.getText().toString().trim();
        String shellTimeoutStr = inputShellTimeout.getText().toString().trim();

        // Validation
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

        // Get selected model
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

        // Get sandbox mode
        String sandboxMode = "strict";
        String selectedSandbox = dropdownSandboxMode.getText().toString();
        if (selectedSandbox.equals(getString(R.string.sandbox_relaxed))) {
            sandboxMode = "relaxed";
        }

        // Update agent config
        agentConfig.setDefaultModel(selectedModel);
        agentConfig.setShellAccess(switchShellAccess.isChecked());
        agentConfig.setSandboxMode(sandboxMode);
        agentConfig.setMaxIterations(maxIterations);
        agentConfig.setRequireApproval(switchRequireApproval.isChecked());
        agentConfig.setShellTimeout(shellTimeout);

        // Save to settings
        settingsManager.setAgentConfig(agentConfig);

        Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }
}