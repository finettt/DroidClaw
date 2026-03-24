package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

public class SettingsFragment extends Fragment {
    private TabLayout tabLayout;
    private LinearLayout modelsContainer;
    private LinearLayout providersContainer;
    private LinearLayout agentContainer;
    private Button resetOnboardingButton;
    private Button saveButton;

    private SettingsManager settingsManager;

    // Model views
    private EditText defaultModelInput;
    private LinearLayout modelsListContainer;

    // Provider views
    private LinearLayout providersListContainer;

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
        return inflater.inflate(R.layout.fragment_settings_reorganized, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabLayout);
        modelsContainer = view.findViewById(R.id.modelsContainer);
        providersContainer = view.findViewById(R.id.providersContainer);
        agentContainer = view.findViewById(R.id.agentContainer);
        resetOnboardingButton = view.findViewById(R.id.resetOnboardingButton);

        setupTabs();
        initAgentViews(view);
        initModelsList(view);
        loadSettings();
        setupListeners();
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.settings_models));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.settings_providers));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.settings_agent));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateVisibleSection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Show first section by default
        updateVisibleSection(0);
    }

    private void updateVisibleSection(int position) {
        modelsContainer.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        providersContainer.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        agentContainer.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void initAgentViews(View view) {
        shellAccessSwitch = view.findViewById(R.id.shellAccessSwitch);
        sandboxModeDropdown = view.findViewById(R.id.sandboxModeDropdown);
        maxIterationsInput = view.findViewById(R.id.maxIterationsInput);
        requireApprovalSwitch = view.findViewById(R.id.requireApprovalSwitch);
        shellTimeoutInput = view.findViewById(R.id.shellTimeoutInput);
        defaultModelInput = view.findViewById(R.id.defaultModelInput);
        providersListContainer = view.findViewById(R.id.providersListContainer);
        saveButton = view.findViewById(R.id.saveButton);

        setupSandboxModeDropdown();
        setupProvidersList();
    }

    private void initModelsList(View view) {
        modelsListContainer = view.findViewById(R.id.modelsListContainer);
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

    private void setupProvidersList() {
        // Load predefined providers
        List<SettingsManager.ProviderConfig> predefinedProviders = getPredefinedProviders();
        settingsManager.setProviders(predefinedProviders);

        // Display providers
        providersListContainer.removeAllViews();
        for (SettingsManager.ProviderConfig provider : settingsManager.getProviders()) {
            View providerView = createProviderView(provider);
            providersListContainer.addView(providerView);
        }
    }

    private List<SettingsManager.ProviderConfig> getPredefinedProviders() {
        List<SettingsManager.ProviderConfig> providers = new ArrayList<>();
        providers.add(new SettingsManager.ProviderConfig("OpenAI", "https://api.openai.com/v1", "", true));
        providers.add(new SettingsManager.ProviderConfig("Anthropic", "https://api.anthropic.com/v1", "", true));
        providers.add(new SettingsManager.ProviderConfig("OpenRouter", "https://openrouter.ai/api/v1", "", true));
        return providers;
    }

    private View createProviderView(SettingsManager.ProviderConfig provider) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_provider, providersListContainer, false);
        TextView nameTextView = view.findViewById(R.id.providerName);
        TextView typeTextView = view.findViewById(R.id.providerType);
        Button removeButton = view.findViewById(R.id.removeProviderButton);

        nameTextView.setText(provider.getName());
        typeTextView.setText(provider.isPredefined() ? R.string.predefined_provider : R.string.custom_provider);
        removeButton.setOnClickListener(v -> {
            settingsManager.removeProvider(provider.getName());
            setupProvidersList();
            Toast.makeText(requireContext(), "Provider removed", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void loadSettings() {
        defaultModelInput.setText(settingsManager.getDefaultModel());

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
        resetOnboardingButton.setOnClickListener(v -> showResetOnboardingDialog());
        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void showResetOnboardingDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_onboarding)
            .setMessage(R.string.reset_onboarding_confirm)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                settingsManager.resetOnboarding();
                Toast.makeText(requireContext(), "Onboarding reset", Toast.LENGTH_SHORT).show();
                // Navigate back to onboarding
                Bundle args = new Bundle();
                args.putBoolean("reset", true);
                Navigation.findNavController(requireView()).navigate(R.id.onboardingFragment, args);
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    public void saveSettings() {
        String defaultModel = defaultModelInput.getText().toString().trim();

        // Validate default model
        if (defaultModel.isEmpty()) {
            defaultModelInput.setError("Default model is required");
            return;
        }

        int maxIterations;
        try {
            maxIterations = Integer.parseInt(maxIterationsInput.getText().toString().trim());
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
            shellTimeout = Integer.parseInt(shellTimeoutInput.getText().toString().trim());
            if (shellTimeout < 5 || shellTimeout > 300) {
                shellTimeoutInput.setError("Shell timeout must be between 5 and 300 seconds");
                return;
            }
        } catch (NumberFormatException e) {
            shellTimeoutInput.setError("Invalid number");
            return;
        }

        // Determine sandbox mode from dropdown selection
        String sandboxMode = "strict";
        String selectedSandbox = sandboxModeDropdown.getText().toString();
        if (selectedSandbox.equals(getString(R.string.sandbox_relaxed))) {
            sandboxMode = "relaxed";
        }

        // Save settings
        settingsManager.setDefaultModel(defaultModel);
        settingsManager.setShellAccessEnabled(shellAccessSwitch.isChecked());
        settingsManager.setSandboxMode(sandboxMode);
        settingsManager.setMaxAgentIterations(maxIterations);
        settingsManager.setRequireApproval(requireApprovalSwitch.isChecked());
        settingsManager.setShellTimeoutSeconds(shellTimeout);

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupProvidersList(); // Refresh provider list
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear references to views to prevent memory leaks
        tabLayout = null;
        modelsContainer = null;
        providersContainer = null;
        agentContainer = null;
        resetOnboardingButton = null;
        saveButton = null;
        shellAccessSwitch = null;
        sandboxModeDropdown = null;
        maxIterationsInput = null;
        requireApprovalSwitch = null;
        shellTimeoutInput = null;
        defaultModelInput = null;
        providersListContainer = null;
    }
}
