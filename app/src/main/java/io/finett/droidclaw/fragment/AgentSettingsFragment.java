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
import io.finett.droidclaw.shell.SshConfig;
import io.finett.droidclaw.shell.SshShellBackend;
import io.finett.droidclaw.util.CalendarPermissionHelper;
import io.finett.droidclaw.util.SettingsManager;

public class AgentSettingsFragment extends Fragment {

    private AutoCompleteTextView dropdownDefaultModel;
    private SwitchMaterial switchShellAccess;
    private AutoCompleteTextView dropdownSandboxMode;
    private TextInputEditText inputMaxIterations;
    private SwitchMaterial switchRequireApproval;
    private SwitchMaterial switchStreamResponses;
    private TextInputEditText inputShellTimeout;
    private SwitchMaterial switchBackgroundShellAccess;
    private SwitchMaterial switchBackgroundExec;
    private TextInputEditText inputCustomAllowlist;
    private TextInputEditText inputLlmConnectTimeout;
    private TextInputEditText inputLlmReadTimeout;
    private TextInputEditText inputLlmWriteTimeout;

    // SSH Terminal Backend
    private AutoCompleteTextView dropdownTerminalBackend;
    private View groupSshConnection;
    private TextInputEditText inputSshHost;
    private TextInputEditText inputSshPort;
    private TextInputEditText inputSshUser;
    private AutoCompleteTextView dropdownSshAuthType;
    private TextInputEditText inputSshPassword;
    private TextInputEditText inputSshPrivateKey;
    private SwitchMaterial switchSshVerifyHost;
    private MaterialButton buttonTestSshConnection;

    // Screen control views
    private SwitchMaterial switchScreenControl;
    private SwitchMaterial switchScreenControlTrustMode;
    private TextView textAccessibilityStatus;
    private MaterialButton buttonOpenAccessibilitySettings;

    // Calendar access views
    private SwitchMaterial switchCalendarAccess;
    private CalendarPermissionHelper calendarPermissionHelper;

    // Self-improvement views
    private SwitchMaterial switchGuidelinesLearning;

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        calendarPermissionHelper.handlePermissionResult(requestCode, permissions, grantResults,
                new CalendarPermissionHelper.PermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        // Switch stays on; tools become available on the next agent run.
                    }

                    @Override
                    public void onPermissionDenied() {
                        switchCalendarAccess.setChecked(false);
                        Toast.makeText(requireContext(), R.string.calendar_permission_denied,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void initViews(View view) {
        dropdownDefaultModel = view.findViewById(R.id.dropdown_default_model);
        switchShellAccess = view.findViewById(R.id.switch_shell_access);
        dropdownSandboxMode = view.findViewById(R.id.dropdown_sandbox_mode);
        inputMaxIterations = view.findViewById(R.id.input_max_iterations);
        switchRequireApproval = view.findViewById(R.id.switch_require_approval);
        switchStreamResponses = view.findViewById(R.id.switch_stream_responses);
        inputShellTimeout = view.findViewById(R.id.input_shell_timeout);
        switchBackgroundShellAccess = view.findViewById(R.id.switch_background_shell_access);
        switchBackgroundExec = view.findViewById(R.id.switch_background_exec);
        inputCustomAllowlist = view.findViewById(R.id.input_custom_allowlist);
        inputLlmConnectTimeout = view.findViewById(R.id.input_llm_connect_timeout);
        inputLlmReadTimeout = view.findViewById(R.id.input_llm_read_timeout);
        inputLlmWriteTimeout = view.findViewById(R.id.input_llm_write_timeout);

        // SSH Terminal Backend
        dropdownTerminalBackend = view.findViewById(R.id.dropdown_terminal_backend);
        groupSshConnection = view.findViewById(R.id.group_ssh_connection);
        inputSshHost = view.findViewById(R.id.input_ssh_host);
        inputSshPort = view.findViewById(R.id.input_ssh_port);
        inputSshUser = view.findViewById(R.id.input_ssh_user);
        dropdownSshAuthType = view.findViewById(R.id.dropdown_ssh_auth_type);
        inputSshPassword = view.findViewById(R.id.input_ssh_password);
        inputSshPrivateKey = view.findViewById(R.id.input_ssh_private_key);
        switchSshVerifyHost = view.findViewById(R.id.switch_ssh_verify_host);
        buttonTestSshConnection = view.findViewById(R.id.button_test_ssh_connection);

        switchScreenControl = view.findViewById(R.id.switch_screen_control);
        switchScreenControlTrustMode = view.findViewById(R.id.switch_screen_control_trust_mode);
        textAccessibilityStatus = view.findViewById(R.id.text_accessibility_status);
        buttonOpenAccessibilitySettings = view.findViewById(R.id.button_open_accessibility_settings);

        switchCalendarAccess = view.findViewById(R.id.switch_calendar_access);
        calendarPermissionHelper = new CalendarPermissionHelper(requireContext());

        switchGuidelinesLearning = view.findViewById(R.id.switch_guidelines_learning);

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

        // Terminal backend dropdown
        String[] terminalBackends = {
                getString(R.string.terminal_backend_local),
                getString(R.string.terminal_backend_ssh)
        };
        ArrayAdapter<String> backendAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                terminalBackends
        );
        dropdownTerminalBackend.setAdapter(backendAdapter);

        // SSH auth type dropdown
        String[] authTypes = {
                getString(R.string.ssh_auth_password),
                getString(R.string.ssh_auth_key)
        };
        ArrayAdapter<String> authAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                authTypes
        );
        dropdownSshAuthType.setAdapter(authAdapter);
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
            switchStreamResponses.setChecked(agentConfig.isStreamResponses());
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

            // Calendar access
            switchCalendarAccess.setChecked(agentConfig.isCalendarEnabled());

            // Self-improvement
            switchGuidelinesLearning.setChecked(agentConfig.isGuidelinesLearningEnabled());

            // Terminal backend
            String backend = agentConfig.getShellBackend();
            if ("ssh".equals(backend)) {
                dropdownTerminalBackend.setText(getString(R.string.terminal_backend_ssh), false);
            } else {
                dropdownTerminalBackend.setText(getString(R.string.terminal_backend_local), false);
            }

            // SSH connection settings
            inputSshHost.setText(agentConfig.getSshHost());
            inputSshPort.setText(String.valueOf(agentConfig.getSshPort()));
            inputSshUser.setText(agentConfig.getSshUser());

            String authType = agentConfig.getSshAuthType();
            if ("key".equals(authType)) {
                dropdownSshAuthType.setText(getString(R.string.ssh_auth_key), false);
            } else {
                dropdownSshAuthType.setText(getString(R.string.ssh_auth_password), false);
            }

            inputSshPassword.setText(agentConfig.getSshPassword());
            inputSshPrivateKey.setText(agentConfig.getSshPrivateKeyPath());
            switchSshVerifyHost.setChecked(agentConfig.isSshVerifyHostKey());

            updateSshFieldsVisibility();
            updateSshAuthFieldsVisibility();
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

        // When the user enables calendar access, request the runtime permissions.
        // isPressed() guards against programmatic setChecked() during loadSettings().
        switchCalendarAccess.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && buttonView.isPressed()
                    && !CalendarPermissionHelper.hasCalendarPermission(requireContext())) {
                calendarPermissionHelper.requestCalendarPermissions(requireActivity());
            }
        });

        // Terminal backend change listener
        dropdownTerminalBackend.setOnItemClickListener((parent, view, position, id) -> {
            updateSshFieldsVisibility();
            updateSshAuthFieldsVisibility();
        });

        // SSH auth type change listener
        dropdownSshAuthType.setOnItemClickListener((parent, view, position, id) -> {
            updateSshAuthFieldsVisibility();
        });

        // Host key verification toggle — require explicit confirmation to disable
        switchSshVerifyHost.setOnCheckedChangeListener(null);
        switchSshVerifyHost.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                showDisableHostKeyWarning(() -> {
                    switchSshVerifyHost.setChecked(false);
                });
            }
        });

        // Test SSH connection button
        buttonTestSshConnection.setOnClickListener(v -> testSshConnection());
    }

    /**
     * Show/hide the SSH connection group based on the selected backend.
     * Validation is intentionally deferred to Save/Test — showing errors on
     * dropdown change is bad UX (user hasn't had a chance to type yet).
     */
    void updateSshFieldsVisibility() {
        String backendText = dropdownTerminalBackend.getText().toString();
        boolean isSsh = getString(R.string.terminal_backend_ssh).equals(backendText);

        groupSshConnection.setVisibility(isSsh ? View.VISIBLE : View.GONE);
    }

    /**
     * Show/hide password vs private key fields based on auth type.
     */
    void updateSshAuthFieldsVisibility() {
        String authTypeText = dropdownSshAuthType.getText().toString();
        boolean isKeyAuth = getString(R.string.ssh_auth_key).equals(authTypeText);

        int passwordVisibility = isKeyAuth ? View.GONE : View.VISIBLE;
        int keyVisibility = isKeyAuth ? View.VISIBLE : View.GONE;

        ((ViewGroup) inputSshPassword.getParent()).setVisibility(passwordVisibility);
        ((ViewGroup) inputSshPrivateKey.getParent()).setVisibility(keyVisibility);
    }

    /**
     * Test the SSH connection to verify settings are correct.
     */
    private void testSshConnection() {
        if (!validateSshSettings()) {
            return;
        }

        buttonTestSshConnection.setEnabled(false);
        buttonTestSshConnection.setText(R.string.ssh_test_connection);

        new Thread(() -> {
            try {
                SshConfig.Builder builder = new SshConfig.Builder()
                        .host(inputSshHost.getText().toString().trim())
                        .port(Integer.parseInt(inputSshPort.getText().toString().trim()))
                        .username(inputSshUser.getText().toString().trim())
                        .verifyHostKey(switchSshVerifyHost.isChecked());

                String authTypeText = dropdownSshAuthType.getText().toString();
                boolean isKeyAuth = getString(R.string.ssh_auth_key).equals(authTypeText);

                if (isKeyAuth) {
                    String keyPath = inputSshPrivateKey.getText().toString().trim();
                    if (!keyPath.isEmpty()) {
                        builder.privateKeyPath(keyPath);
                    }
                } else {
                    String password = inputSshPassword.getText().toString();
                    if (!password.isEmpty()) {
                        builder.password(password);
                    }
                }

                SshConfig sshConfig = builder.build();
                SshShellBackend sshBackend = new SshShellBackend(sshConfig);
                boolean success = sshBackend.testConnection();
                sshBackend.close();

                requireActivity().runOnUiThread(() -> {
                    buttonTestSshConnection.setEnabled(true);
                    if (success) {
                        Toast.makeText(requireContext(), R.string.ssh_connection_success,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.ssh_connection_failed_no_detail,
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    buttonTestSshConnection.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.ssh_connection_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Show a confirmation dialog before allowing the user to disable host key verification.
     *
     * @param onConfirmed callback invoked on the UI thread if the user confirms, or null if
     *                    the dialog is cancelled / activity is detached
     */
    private void showDisableHostKeyWarning(java.lang.Runnable onConfirmed) {
        if (!isAdded() || getActivity() == null) return;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.ssh_verify_host_warning_title)
                .setMessage(R.string.ssh_verify_host_warning_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (isAdded() && onConfirmed != null) {
                        onConfirmed.run();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    switchSshVerifyHost.setChecked(true);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Validate SSH settings when backend is set to SSH.
     */
    private boolean validateSshSettings() {
        boolean valid = true;

        String host = inputSshHost.getText().toString().trim();
        if (host.isEmpty()) {
            inputSshHost.setError(getString(R.string.ssh_host_required));
            valid = false;
        } else {
            inputSshHost.setError(null);
        }

        String user = inputSshUser.getText().toString().trim();
        if (user.isEmpty()) {
            inputSshUser.setError(getString(R.string.ssh_user_required));
            valid = false;
        } else {
            inputSshUser.setError(null);
        }

        String portStr = inputSshPort.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                inputSshPort.setError("Port must be between 1 and 65535");
                valid = false;
            } else {
                inputSshPort.setError(null);
            }
        } catch (NumberFormatException e) {
            inputSshPort.setError("Invalid port number");
            valid = false;
        }

        String authTypeText = dropdownSshAuthType.getText().toString();
        boolean isKeyAuth = getString(R.string.ssh_auth_key).equals(authTypeText);

        if (isKeyAuth) {
            String keyPath = inputSshPrivateKey.getText().toString().trim();
            if (keyPath.isEmpty()) {
                inputSshPrivateKey.setError("Private key path is required");
                valid = false;
            } else {
                inputSshPrivateKey.setError(null);
            }
        } else {
            String password = inputSshPassword.getText().toString().trim();
            if (password.isEmpty()) {
                inputSshPassword.setError("Password is required");
                valid = false;
            } else {
                inputSshPassword.setError(null);
            }
        }

        return valid;
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
        agentConfig.setStreamResponses(switchStreamResponses.isChecked());
        agentConfig.setShellTimeout(shellTimeout);
        agentConfig.setBackgroundShellEnabled(switchBackgroundShellAccess.isChecked());
        agentConfig.setBackgroundExecEnabled(switchBackgroundExec.isChecked());
        agentConfig.setCustomAllowlist(customAllowlist);
        agentConfig.setLlmConnectTimeout(llmConnectTimeout);
        agentConfig.setLlmReadTimeout(llmReadTimeout);
        agentConfig.setLlmWriteTimeout(llmWriteTimeout);
        agentConfig.setScreenControlEnabled(switchScreenControl.isChecked());
        agentConfig.setScreenControlTrustMode(switchScreenControlTrustMode.isChecked());
        agentConfig.setCalendarEnabled(switchCalendarAccess.isChecked());
        agentConfig.setGuidelinesLearningEnabled(switchGuidelinesLearning.isChecked());

        // Terminal backend
        String backendText = dropdownTerminalBackend.getText().toString();
        if (getString(R.string.terminal_backend_ssh).equals(backendText)) {
            agentConfig.setShellBackend("ssh");
        } else {
            agentConfig.setShellBackend("local");
        }

        // SSH connection settings
        agentConfig.setSshHost(inputSshHost.getText().toString().trim());
        try {
            String portStr = inputSshPort.getText().toString().trim();
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                inputSshPort.setError("Port must be between 1 and 65535");
                return;
            }
            agentConfig.setSshPort(port);
        } catch (NumberFormatException e) {
            inputSshPort.setError(getString(R.string.validation_invalid_number));
            return;
        }
        agentConfig.setSshUser(inputSshUser.getText().toString().trim());

        String authTypeText = dropdownSshAuthType.getText().toString();
        agentConfig.setSshAuthType(getString(R.string.ssh_auth_key).equals(authTypeText) ? "key" : "password");
        agentConfig.setSshPassword(inputSshPassword.getText().toString());
        agentConfig.setSshPrivateKeyPath(inputSshPrivateKey.getText().toString().trim());
        agentConfig.setSshVerifyHostKey(switchSshVerifyHost.isChecked());

        settingsManager.setAgentConfig(agentConfig);

        Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show();
        try {
            Navigation.findNavController(requireView()).navigateUp();
        } catch (IllegalStateException e) {
            // Fragment is not attached to a NavController (e.g. in tests with launchInContainer).
            // The activity will handle navigation; ignore the error.
        }
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