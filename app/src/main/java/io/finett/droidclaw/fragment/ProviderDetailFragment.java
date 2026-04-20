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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.UUID;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ModelsAdapter;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;

public class ProviderDetailFragment extends Fragment {

    private TextInputEditText inputProviderName;
    private TextInputEditText inputBaseUrl;
    private TextInputEditText inputApiKey;
    private TextInputLayout tilProviderName;
    private TextInputLayout tilBaseUrl;
    private TextInputLayout tilApiKey;
    private AutoCompleteTextView dropdownApiType;
    private RecyclerView recyclerModels;
    private Button buttonAddModel;
    private Button buttonSave;
    private Button buttonDelete;

    private ModelsAdapter modelsAdapter;
    private SettingsManager settingsManager;
    private Provider provider;
    private String providerId;
    private boolean isNewProvider;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());

        if (getArguments() != null) {
            providerId = getArguments().getString("providerId");
        }

        if (providerId != null) {
            provider = settingsManager.getProvider(providerId);
            isNewProvider = (provider == null);
            if (isNewProvider) {

                provider = new Provider();
                provider.setId(UUID.randomUUID().toString());
                providerId = provider.getId();
            }
        } else {

            isNewProvider = true;
            provider = new Provider();
            provider.setId(UUID.randomUUID().toString());
            providerId = provider.getId();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_provider_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupApiTypeDropdown();
        setupModelsRecyclerView();
        loadProviderData();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();

        settingsManager = new SettingsManager(requireContext());

        Provider updatedProvider = settingsManager.getProvider(providerId);
        if (updatedProvider != null) {
            provider = updatedProvider;

            modelsAdapter.submitList(new java.util.ArrayList<>(provider.getModels()));
        }
    }

    private void initViews(View view) {
        inputProviderName = view.findViewById(R.id.input_provider_name);
        inputBaseUrl = view.findViewById(R.id.input_base_url);
        inputApiKey = view.findViewById(R.id.input_api_key);
        tilProviderName = view.findViewById(R.id.til_provider_name);
        tilBaseUrl = view.findViewById(R.id.til_base_url);
        tilApiKey = view.findViewById(R.id.til_api_key);
        dropdownApiType = view.findViewById(R.id.dropdown_api_type);
        recyclerModels = view.findViewById(R.id.recycler_models);
        buttonAddModel = view.findViewById(R.id.button_add_model);
        buttonSave = view.findViewById(R.id.button_save);
        buttonDelete = view.findViewById(R.id.button_delete);

        if (!isNewProvider) {
            buttonDelete.setVisibility(View.VISIBLE);
        }
    }

    private void setupApiTypeDropdown() {
        String[] apiTypes = {
                getString(R.string.api_type_openai_completions),
                getString(R.string.api_type_openai_responses),
                getString(R.string.api_type_anthropic),
                getString(R.string.api_type_google)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                apiTypes
        );
        dropdownApiType.setAdapter(adapter);
    }

    private void setupModelsRecyclerView() {
        recyclerModels.setLayoutManager(new LinearLayoutManager(requireContext()));
        modelsAdapter = new ModelsAdapter();
        modelsAdapter.setOnModelClickListener(this::navigateToModelDetail);
        recyclerModels.setAdapter(modelsAdapter);
    }

    private void loadProviderData() {
        if (provider != null) {
            if (provider.getName() != null) {
                inputProviderName.setText(provider.getName());
            }
            if (provider.getBaseUrl() != null) {
                inputBaseUrl.setText(provider.getBaseUrl());
            }
            if (provider.getApiKey() != null) {
                inputApiKey.setText(provider.getApiKey());
            }
            if (provider.getApi() != null) {
                dropdownApiType.setText(getApiTypeDisplayName(provider.getApi()), false);
            } else {
                dropdownApiType.setText(getString(R.string.api_type_openai_completions), false);
            }

            modelsAdapter.submitList(provider.getModels());
        }
    }

    private void setupListeners() {
        buttonAddModel.setOnClickListener(v -> navigateToNewModel());
        buttonSave.setOnClickListener(v -> saveProvider());
        buttonDelete.setOnClickListener(v -> confirmDeleteProvider());
        

        inputProviderName.addTextChangedListener(new SimpleTextWatcher(() ->
            tilProviderName.setError(null)));
        inputBaseUrl.addTextChangedListener(new SimpleTextWatcher(() ->
            tilBaseUrl.setError(null)));
        inputApiKey.addTextChangedListener(new SimpleTextWatcher(() ->
            tilApiKey.setError(null)));
    }

    private void saveProvider() {
        String name = inputProviderName.getText().toString().trim();
        String baseUrl = inputBaseUrl.getText().toString().trim();
        String apiKey = inputApiKey.getText().toString().trim();
        String apiTypeDisplay = dropdownApiType.getText().toString();


        if (name.isEmpty()) {
            tilProviderName.setError(getString(R.string.validation_required));
            return;
        }

        if (baseUrl.isEmpty()) {
            tilBaseUrl.setError(getString(R.string.validation_required));
            return;
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            tilBaseUrl.setError(getString(R.string.validation_invalid_url));
            return;
        }

        if (apiKey.isEmpty()) {
            tilApiKey.setError(getString(R.string.validation_required));
            return;
        }

        String apiType = getApiTypeValue(apiTypeDisplay);


        provider.setName(name);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setApi(apiType);


        if (isNewProvider) {
            settingsManager.addProvider(provider);
            isNewProvider = false;
        } else {
            settingsManager.updateProvider(provider);
        }

        Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }

    private void confirmDeleteProvider() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_provider)
                .setMessage(R.string.delete_provider_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    settingsManager.deleteProvider(providerId);
                    Toast.makeText(requireContext(), R.string.delete_provider, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).navigateUp();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void navigateToModelDetail(Model model) {
        Bundle args = new Bundle();
        args.putString("providerId", provider.getId());
        args.putString("modelId", model.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_providerDetailFragment_to_modelDetailFragment, args);
    }

    private void navigateToNewModel() {

        updateProviderFromForm();
        
        Bundle args = new Bundle();
        args.putString("providerId", provider.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_providerDetailFragment_to_modelDetailFragment, args);
    }
    
    private void updateProviderFromForm() {
        String name = inputProviderName.getText().toString().trim();
        String baseUrl = inputBaseUrl.getText().toString().trim();
        String apiKey = inputApiKey.getText().toString().trim();
        String apiTypeDisplay = dropdownApiType.getText().toString();
        String apiType = getApiTypeValue(apiTypeDisplay);
        
        provider.setName(name);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setApi(apiType);
        

        if (isNewProvider) {
            settingsManager.addProvider(provider);
            isNewProvider = false;
        } else {
            settingsManager.updateProvider(provider);
        }
    }

    private String getApiTypeDisplayName(String apiType) {
        if (apiType == null) {
            return getString(R.string.api_type_openai_completions);
        }
        switch (apiType) {
            case "openai-completions":
                return getString(R.string.api_type_openai_completions);
            case "openai-responses":
                return getString(R.string.api_type_openai_responses);
            case "anthropic":
                return getString(R.string.api_type_anthropic);
            case "google":
                return getString(R.string.api_type_google);
            default:
                return getString(R.string.api_type_openai_completions);
        }
    }

    private String getApiTypeValue(String displayName) {
        if (displayName.equals(getString(R.string.api_type_openai_completions))) {
            return "openai-completions";
        } else if (displayName.equals(getString(R.string.api_type_openai_responses))) {
            return "openai-responses";
        } else if (displayName.equals(getString(R.string.api_type_anthropic))) {
            return "anthropic";
        } else if (displayName.equals(getString(R.string.api_type_google))) {
            return "google";
        }
        return "openai-completions";
    }


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