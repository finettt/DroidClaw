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

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;

public class ModelDetailFragment extends Fragment {

    private TextInputEditText inputModelId;
    private TextInputEditText inputModelName;
    private AutoCompleteTextView dropdownApiType;
    private TextInputEditText inputContextWindow;
    private TextInputEditText inputMaxTokens;
    private SwitchMaterial switchReasoning;
    private MaterialCheckBox checkboxInputText;
    private MaterialCheckBox checkboxInputImage;
    private Button buttonSave;
    private Button buttonDelete;

    private SettingsManager settingsManager;
    private String providerId;
    private String modelId;
    private Provider provider;
    private Model model;
    private boolean isNewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());

        if (getArguments() != null) {
            providerId = getArguments().getString("providerId");
            modelId = getArguments().getString("modelId");
        }

        if (providerId != null) {
            provider = settingsManager.getProvider(providerId);
            if (provider == null) {
                // Invalid provider, shouldn't happen
                requireActivity().onBackPressed();
                return;
            }

            if (modelId != null) {
                model = provider.getModelById(modelId);
                isNewModel = (model == null);
                if (isNewModel) {
                    model = new Model();
                }
            } else {
                isNewModel = true;
                model = new Model();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_model_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupApiTypeDropdown();
        loadModelData();
        setupListeners();
    }

    private void initViews(View view) {
        inputModelId = view.findViewById(R.id.input_model_id);
        inputModelName = view.findViewById(R.id.input_model_name);
        dropdownApiType = view.findViewById(R.id.dropdown_api_type);
        inputContextWindow = view.findViewById(R.id.input_context_window);
        inputMaxTokens = view.findViewById(R.id.input_max_tokens);
        switchReasoning = view.findViewById(R.id.switch_reasoning);
        checkboxInputText = view.findViewById(R.id.checkbox_input_text);
        checkboxInputImage = view.findViewById(R.id.checkbox_input_image);
        buttonSave = view.findViewById(R.id.button_save);
        buttonDelete = view.findViewById(R.id.button_delete);

        if (!isNewModel) {
            buttonDelete.setVisibility(View.VISIBLE);
            inputModelId.setEnabled(false); // Don't allow changing model ID
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

    private void loadModelData() {
        if (model != null) {
            if (model.getId() != null) {
                inputModelId.setText(model.getId());
            }
            if (model.getName() != null) {
                inputModelName.setText(model.getName());
            }
            if (model.getApi() != null) {
                dropdownApiType.setText(getApiTypeDisplayName(model.getApi()), false);
            } else {
                dropdownApiType.setText(getString(R.string.api_type_openai_completions), false);
            }
            
            inputContextWindow.setText(String.valueOf(model.getContextWindow()));
            inputMaxTokens.setText(String.valueOf(model.getMaxTokens()));
            switchReasoning.setChecked(model.isReasoning());
            checkboxInputText.setChecked(model.hasTextInput());
            checkboxInputImage.setChecked(model.hasImageInput());
        } else {
            // Defaults for new model
            dropdownApiType.setText(getString(R.string.api_type_openai_completions), false);
            inputContextWindow.setText("4096");
            inputMaxTokens.setText("4096");
            checkboxInputText.setChecked(true);
        }
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveModel());
        buttonDelete.setOnClickListener(v -> confirmDeleteModel());
    }

    private void saveModel() {
        String id = inputModelId.getText().toString().trim();
        String name = inputModelName.getText().toString().trim();
        String apiTypeDisplay = dropdownApiType.getText().toString();
        String contextWindowStr = inputContextWindow.getText().toString().trim();
        String maxTokensStr = inputMaxTokens.getText().toString().trim();

        // Validation
        if (id.isEmpty()) {
            inputModelId.setError(getString(R.string.validation_required));
            return;
        }

        if (name.isEmpty()) {
            inputModelName.setError(getString(R.string.validation_required));
            return;
        }

        int contextWindow;
        try {
            contextWindow = Integer.parseInt(contextWindowStr);
            if (contextWindow < 1) {
                inputContextWindow.setError(getString(R.string.validation_invalid_number));
                return;
            }
        } catch (NumberFormatException e) {
            inputContextWindow.setError(getString(R.string.validation_invalid_number));
            return;
        }

        int maxTokens;
        try {
            maxTokens = Integer.parseInt(maxTokensStr);
            if (maxTokens < 1) {
                inputMaxTokens.setError(getString(R.string.validation_invalid_number));
                return;
            }
        } catch (NumberFormatException e) {
            inputMaxTokens.setError(getString(R.string.validation_invalid_number));
            return;
        }

        String apiType = getApiTypeValue(apiTypeDisplay);
        boolean reasoning = switchReasoning.isChecked();
        
        List<String> inputTypes = new ArrayList<>();
        if (checkboxInputText.isChecked()) {
            inputTypes.add("text");
        }
        if (checkboxInputImage.isChecked()) {
            inputTypes.add("image");
        }

        if (inputTypes.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one input type", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update model object
        model.setId(id);
        model.setName(name);
        model.setApi(apiType);
        model.setContextWindow(contextWindow);
        model.setMaxTokens(maxTokens);
        model.setReasoning(reasoning);
        model.setInput(inputTypes);

        // Save to settings
        if (isNewModel) {
            settingsManager.addModel(providerId, model);
        } else {
            settingsManager.updateModel(providerId, model);
        }

        Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }

    private void confirmDeleteModel() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_model)
                .setMessage(R.string.delete_model_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    settingsManager.deleteModel(providerId, modelId);
                    Toast.makeText(requireContext(), R.string.delete_model, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).navigateUp();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
}