package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

public class SettingsFragment extends Fragment {
    private EditText apiKeyInput;
    private EditText apiUrlInput;
    private EditText modelNameInput;
    private EditText systemPromptInput;
    private EditText maxTokensInput;
    private SeekBar temperatureSeekBar;
    private TextView temperatureValue;
    private Button saveButton;
    private SettingsManager settingsManager;
    
    // Agent settings views
    private SwitchMaterial shellAccessSwitch;
    private AutoCompleteTextView sandboxModeDropdown;
    private EditText maxIterationsInput;
    private SwitchMaterial requireApprovalSwitch;
    private EditText shellTimeoutInput;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        loadSettings();
        setupListeners();
    }

    private void initViews(View view) {
        apiKeyInput = view.findViewById(R.id.apiKeyInput);
        apiUrlInput = view.findViewById(R.id.apiUrlInput);
        modelNameInput = view.findViewById(R.id.modelNameInput);
        systemPromptInput = view.findViewById(R.id.systemPromptInput);
        maxTokensInput = view.findViewById(R.id.maxTokensInput);
        temperatureSeekBar = view.findViewById(R.id.temperatureSeekBar);
        temperatureValue = view.findViewById(R.id.temperatureValue);
        saveButton = view.findViewById(R.id.saveButton);
        
        // Agent settings views
        shellAccessSwitch = view.findViewById(R.id.shellAccessSwitch);
        sandboxModeDropdown = view.findViewById(R.id.sandboxModeDropdown);
        maxIterationsInput = view.findViewById(R.id.maxIterationsInput);
        requireApprovalSwitch = view.findViewById(R.id.requireApprovalSwitch);
        shellTimeoutInput = view.findViewById(R.id.shellTimeoutInput);
        
        // Setup sandbox mode dropdown
        setupSandboxModeDropdown();
    }
    
    private void setupSandboxModeDropdown() {
        String[] sandboxModes = {
            getString(R.string.sandbox_strict),
            getString(R.string.sandbox_relaxed)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            sandboxModes
        );
        sandboxModeDropdown.setAdapter(adapter);
    }

    private void loadSettings() {
        apiKeyInput.setText(settingsManager.getApiKey());
        apiUrlInput.setText(settingsManager.getApiUrl());
        modelNameInput.setText(settingsManager.getModelName());
        systemPromptInput.setText(settingsManager.getSystemPrompt());
        maxTokensInput.setText(String.valueOf(settingsManager.getMaxTokens()));
        
        float temperature = settingsManager.getTemperature();
        int progress = (int) (temperature * 100);
        temperatureSeekBar.setProgress(progress);
        updateTemperatureLabel(temperature);
        
        // Load agent settings
        shellAccessSwitch.setChecked(settingsManager.isShellAccessEnabled());
        
        String sandboxMode = settingsManager.getSandboxMode();
        if ("relaxed".equals(sandboxMode)) {
            sandboxModeDropdown.setText(getString(R.string.sandbox_relaxed), false);
        } else {
            sandboxModeDropdown.setText(getString(R.string.sandbox_strict), false);
        }
        
        maxIterationsInput.setText(String.valueOf(settingsManager.getMaxAgentIterations()));
        requireApprovalSwitch.setChecked(settingsManager.isRequireApproval());
        shellTimeoutInput.setText(String.valueOf(settingsManager.getShellTimeoutSeconds()));
    }

    private void setupListeners() {
        temperatureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float temperature = progress / 100f;
                updateTemperatureLabel(temperature);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void updateTemperatureLabel(float temperature) {
        temperatureValue.setText(String.format("%.2f", temperature));
    }

    private void saveSettings() {
        String apiKey = apiKeyInput.getText().toString().trim();
        String apiUrl = apiUrlInput.getText().toString().trim();
        String modelName = modelNameInput.getText().toString().trim();
        String systemPrompt = systemPromptInput.getText().toString().trim();
        String maxTokensStr = maxTokensInput.getText().toString().trim();
        String maxIterationsStr = maxIterationsInput.getText().toString().trim();
        String shellTimeoutStr = shellTimeoutInput.getText().toString().trim();

        // Validation
        if (apiKey.isEmpty()) {
            apiKeyInput.setError("API key is required");
            return;
        }

        if (apiUrl.isEmpty()) {
            apiUrlInput.setError("API URL is required");
            return;
        }

        if (modelName.isEmpty()) {
            modelNameInput.setError("Model name is required");
            return;
        }

        int maxTokens;
        try {
            maxTokens = Integer.parseInt(maxTokensStr);
            if (maxTokens < 1 || maxTokens > 128000) {
                maxTokensInput.setError("Max tokens must be between 1 and 128000");
                return;
            }
        } catch (NumberFormatException e) {
            maxTokensInput.setError("Invalid number");
            return;
        }

        // Validate agent settings
        int maxIterations;
        try {
            maxIterations = Integer.parseInt(maxIterationsStr);
            if (maxIterations < 1 || maxIterations > 50) {
                maxIterationsInput.setError("Max iterations must be between 1 and 50");
                return;
            }
        } catch (NumberFormatException e) {
            maxIterationsInput.setError("Invalid number");
            return;
        }
        
        int shellTimeout;
        try {
            shellTimeout = Integer.parseInt(shellTimeoutStr);
            if (shellTimeout < 5 || shellTimeout > 300) {
                shellTimeoutInput.setError("Shell timeout must be between 5 and 300 seconds");
                return;
            }
        } catch (NumberFormatException e) {
            shellTimeoutInput.setError("Invalid number");
            return;
        }

        float temperature = temperatureSeekBar.getProgress() / 100f;
        
        // Determine sandbox mode from dropdown selection
        String sandboxMode = "strict";
        String selectedSandbox = sandboxModeDropdown.getText().toString();
        if (selectedSandbox.equals(getString(R.string.sandbox_relaxed))) {
            sandboxMode = "relaxed";
        }

        // Save settings
        settingsManager.setApiKey(apiKey);
        settingsManager.setApiUrl(apiUrl);
        settingsManager.setModelName(modelName);
        settingsManager.setSystemPrompt(systemPrompt);
        settingsManager.setMaxTokens(maxTokens);
        settingsManager.setTemperature(temperature);
        
        // Save agent settings
        settingsManager.setShellAccessEnabled(shellAccessSwitch.isChecked());
        settingsManager.setSandboxMode(sandboxMode);
        settingsManager.setMaxAgentIterations(maxIterations);
        settingsManager.setRequireApproval(requireApprovalSwitch.isChecked());
        settingsManager.setShellTimeoutSeconds(shellTimeout);

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }
}