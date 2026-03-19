package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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

        float temperature = temperatureSeekBar.getProgress() / 100f;

        // Save settings
        settingsManager.setApiKey(apiKey);
        settingsManager.setApiUrl(apiUrl);
        settingsManager.setModelName(modelName);
        settingsManager.setSystemPrompt(systemPrompt);
        settingsManager.setMaxTokens(maxTokens);
        settingsManager.setTemperature(temperature);

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }
}