package io.finett.droidclaw.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;

public class OnboardingFragment extends Fragment {
    private static final int ANIMATION_DURATION = 300;
    
    private SettingsManager settingsManager;
    
    // Section containers
    private ViewGroup sectionWelcome;
    private ViewGroup sectionName;
    private ViewGroup sectionProvider;
    
    // Section 1: Welcome
    private Button btnNext1;
    private Button btnSkip1;
    
    // Section 2: Name
    private TextInputLayout tilName;
    private TextInputEditText etName;
    private Button btnNext2;
    private Button btnSkip2;
    
    // Section 3: Provider
    private TextInputLayout tilProviderName;
    private TextInputEditText etProviderName;
    private TextInputLayout tilBaseUrl;
    private TextInputEditText etBaseUrl;
    private TextInputLayout tilApiKey;
    private TextInputEditText etApiKey;
    private TextInputLayout tilApiType;
    private AutoCompleteTextView actvApiType;
    private Button btnGetStarted;
    private Button btnSkip3;
    
    private int currentSection = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        settingsManager = new SettingsManager(requireContext());
        
        initializeViews(view);
        setupListeners();
        setupApiTypeDropdown();
        
        // Show first section
        showSection(1);
    }

    private void initializeViews(View view) {
        // Section containers
        sectionWelcome = view.findViewById(R.id.section_welcome);
        sectionName = view.findViewById(R.id.section_name);
        sectionProvider = view.findViewById(R.id.section_provider);
        
        // Section 1: Welcome
        btnNext1 = view.findViewById(R.id.btn_next_1);
        btnSkip1 = view.findViewById(R.id.btn_skip_1);
        
        // Section 2: Name
        tilName = view.findViewById(R.id.til_name);
        etName = view.findViewById(R.id.et_name);
        btnNext2 = view.findViewById(R.id.btn_next_2);
        btnSkip2 = view.findViewById(R.id.btn_skip_2);
        
        // Section 3: Provider
        tilProviderName = view.findViewById(R.id.til_provider_name);
        etProviderName = view.findViewById(R.id.et_provider_name);
        tilBaseUrl = view.findViewById(R.id.til_base_url);
        etBaseUrl = view.findViewById(R.id.et_base_url);
        tilApiKey = view.findViewById(R.id.til_api_key);
        etApiKey = view.findViewById(R.id.et_api_key);
        tilApiType = view.findViewById(R.id.til_api_type);
        actvApiType = view.findViewById(R.id.actv_api_type);
        btnGetStarted = view.findViewById(R.id.btn_get_started);
        btnSkip3 = view.findViewById(R.id.btn_skip_3);
    }

    private void setupListeners() {
        // Section 1: Welcome
        btnNext1.setOnClickListener(v -> transitionToSection(2));
        btnSkip1.setOnClickListener(v -> skipOnboarding());
        
        // Section 2: Name
        btnNext2.setOnClickListener(v -> {
            if (validateName()) {
                String name = etName.getText().toString().trim();
                settingsManager.setUserName(name);
                transitionToSection(3);
            }
        });
        btnSkip2.setOnClickListener(v -> skipOnboarding());
        
        // Clear error on input
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilName.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Section 3: Provider
        btnGetStarted.setOnClickListener(v -> {
            if (validateProvider()) {
                saveProviderAndComplete();
            }
        });
        btnSkip3.setOnClickListener(v -> skipOnboarding());
        
        // Clear errors on input
        setupErrorClearingListeners();
    }

    private void setupErrorClearingListeners() {
        etProviderName.addTextChangedListener(new SimpleTextWatcher(() ->
            tilProviderName.setError(null)));
        etBaseUrl.addTextChangedListener(new SimpleTextWatcher(() ->
            tilBaseUrl.setError(null)));
        etApiKey.addTextChangedListener(new SimpleTextWatcher(() ->
            tilApiKey.setError(null)));
        actvApiType.addTextChangedListener(new SimpleTextWatcher(() ->
            tilApiType.setError(null)));
    }

    private void setupApiTypeDropdown() {
        String[] apiTypes = new String[]{
            "openai-completions",
            "openai-responses",
            "anthropic",
            "google"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            apiTypes
        );
        actvApiType.setAdapter(adapter);
        actvApiType.setText("openai-completions", false);
    }

    private boolean validateName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.onboarding_name_error));
            return false;
        }
        return true;
    }

    private boolean validateProvider() {
        boolean valid = true;
        
        String providerName = etProviderName.getText().toString().trim();
        if (providerName.isEmpty()) {
            tilProviderName.setError(getString(R.string.validation_required));
            valid = false;
        }
        
        String baseUrl = etBaseUrl.getText().toString().trim();
        if (baseUrl.isEmpty()) {
            tilBaseUrl.setError(getString(R.string.validation_required));
            valid = false;
        } else if (!isValidUrl(baseUrl)) {
            tilBaseUrl.setError(getString(R.string.validation_invalid_url));
            valid = false;
        }
        
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            tilApiKey.setError(getString(R.string.validation_required));
            valid = false;
        }
        
        String apiType = actvApiType.getText().toString().trim();
        if (apiType.isEmpty()) {
            tilApiType.setError(getString(R.string.validation_required));
            valid = false;
        }
        
        return valid;
    }

    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private void saveProviderAndComplete() {
        // Create provider
        String providerId = UUID.randomUUID().toString();
        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setName(etProviderName.getText().toString().trim());
        provider.setBaseUrl(etBaseUrl.getText().toString().trim());
        provider.setApiKey(etApiKey.getText().toString().trim());
        provider.setApi(actvApiType.getText().toString().trim());
        
        // Add a default model placeholder (user can configure later)
        Model defaultModel = new Model();
        defaultModel.setId("default-model");
        defaultModel.setName("Default Model");
        defaultModel.setApi(provider.getApi());
        defaultModel.setReasoning(false);
        defaultModel.setContextWindow(4096);
        defaultModel.setMaxTokens(4096);
        defaultModel.getInput().add("text");
        provider.addModel(defaultModel);
        
        // Save provider
        settingsManager.addProvider(provider);
        
        // Set as default model
        settingsManager.setDefaultModel(providerId + "/default-model");
        
        // Mark onboarding as completed
        settingsManager.setOnboardingCompleted(true);
        
        // Navigate to chat
        navigateToChat();
    }

    private void skipOnboarding() {
        settingsManager.setOnboardingCompleted(true);
        navigateToChat();
    }

    private void navigateToChat() {
        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(R.id.action_onboardingFragment_to_chatFragment);
        }
    }

    private void transitionToSection(int targetSection) {
        if (targetSection == currentSection) {
            return;
        }
        
        ViewGroup currentView = getSectionView(currentSection);
        ViewGroup targetView = getSectionView(targetSection);
        
        if (currentView == null || targetView == null) {
            return;
        }
        
        // Fade out current section
        currentView.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentView.setVisibility(View.GONE);
                    currentSection = targetSection;
                    
                    // Fade in target section
                    targetView.setAlpha(0f);
                    targetView.setVisibility(View.VISIBLE);
                    targetView.animate()
                        .alpha(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setListener(null)
                        .start();
                }
            })
            .start();
    }

    private void showSection(int section) {
        sectionWelcome.setVisibility(section == 1 ? View.VISIBLE : View.GONE);
        sectionName.setVisibility(section == 2 ? View.VISIBLE : View.GONE);
        sectionProvider.setVisibility(section == 3 ? View.VISIBLE : View.GONE);
        
        sectionWelcome.setAlpha(section == 1 ? 1f : 0f);
        sectionName.setAlpha(section == 2 ? 1f : 0f);
        sectionProvider.setAlpha(section == 3 ? 1f : 0f);
        
        currentSection = section;
    }

    private ViewGroup getSectionView(int section) {
        switch (section) {
            case 1: return sectionWelcome;
            case 2: return sectionName;
            case 3: return sectionProvider;
            default: return null;
        }
    }

    // Simple TextWatcher helper
    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }
}