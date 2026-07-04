package io.finett.droidclaw.fragment;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Instrumented tests for {@link AgentSettingsFragment}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Fragment initialization and view setup</li>
 *   <li>SSH settings visibility toggling</li>
 *   <li>Authentication type field visibility</li>
 *   <li>Settings persistence (save/load cycle)</li>
 *   <li>Validation behavior</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class AgentSettingsFragmentInstrumentedTest {

    @Before
    public void setUp() {
        new SettingsManager(getApplicationContext()).clear();
    }

    @Test
    public void launch_displaysAllFields() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Basic fields
                assertNotNull("Default model dropdown should exist",
                        view.findViewById(R.id.dropdown_default_model));
                assertNotNull("Shell access switch should exist",
                        view.findViewById(R.id.switch_shell_access));
                assertNotNull("Sandbox mode dropdown should exist",
                        view.findViewById(R.id.dropdown_sandbox_mode));
                assertNotNull("Max iterations input should exist",
                        view.findViewById(R.id.input_max_iterations));
                assertNotNull("Require approval switch should exist",
                        view.findViewById(R.id.switch_require_approval));
                assertNotNull("Shell timeout input should exist",
                        view.findViewById(R.id.input_shell_timeout));

                // SSH fields
                assertNotNull("Terminal backend dropdown should exist",
                        view.findViewById(R.id.dropdown_terminal_backend));
                assertNotNull("SSH host input should exist",
                        view.findViewById(R.id.input_ssh_host));
                assertNotNull("SSH port input should exist",
                        view.findViewById(R.id.input_ssh_port));
                assertNotNull("SSH user input should exist",
                        view.findViewById(R.id.input_ssh_user));
                assertNotNull("SSH auth type dropdown should exist",
                        view.findViewById(R.id.dropdown_ssh_auth_type));
                assertNotNull("SSH password input should exist",
                        view.findViewById(R.id.input_ssh_password));
                assertNotNull("SSH private key input should exist",
                        view.findViewById(R.id.input_ssh_private_key));
                assertNotNull("SSH verify host switch should exist",
                        view.findViewById(R.id.switch_ssh_verify_host));
                assertNotNull("Test connection button should exist",
                        view.findViewById(R.id.button_test_ssh_connection));
            });
        }
    }

    @Test
    public void launch_sshConnectionGroupHiddenByDefault() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                View sshGroup = view.findViewById(R.id.group_ssh_connection);
                assertNotNull("SSH connection group should exist", sshGroup);
                assertEquals("SSH connection group should be hidden by default",
                        View.GONE, sshGroup.getVisibility());
            });
        }
    }

    @Test
    public void switchToSshBackend_showsSshFields() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Initially hidden
                View sshGroup = view.findViewById(R.id.group_ssh_connection);
                assertEquals(View.GONE, sshGroup.getVisibility());

                // Switch to SSH backend
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                backendDropdown.setText(getString(R.string.terminal_backend_ssh), false);

                // Force dropdown to register selection
                backendDropdown.performClick();

                // SSH group should now be visible
                assertTrue("SSH connection group should be visible after switching to SSH backend",
                        sshGroup.getVisibility() == View.VISIBLE);
            });
        }
    }

    @Test
    public void switchToLocalBackend_hidesSshFields() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Switch to SSH first
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                backendDropdown.setText(getString(R.string.terminal_backend_ssh), false);

                View sshGroup = view.findViewById(R.id.group_ssh_connection);
                assertTrue("SSH group should be visible when SSH is selected",
                        sshGroup.getVisibility() == View.VISIBLE);

                // Switch back to local
                backendDropdown.setText(getString(R.string.terminal_backend_local), false);

                // SSH group should be hidden again
                assertEquals("SSH connection group should be hidden when local is selected",
                        View.GONE, sshGroup.getVisibility());
            });
        }
    }

    @Test
    public void switchToPasswordAuth_showsPasswordField() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Set up SSH backend first
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                backendDropdown.setText(getString(R.string.terminal_backend_ssh), false);

                // Switch to password auth
                android.widget.AutoCompleteTextView authDropdown =
                        view.findViewById(R.id.dropdown_ssh_auth_type);
                authDropdown.setText(getString(R.string.ssh_auth_password), false);

                TextInputEditText passwordInput = view.findViewById(R.id.input_ssh_password);
                TextInputEditText keyInput = view.findViewById(R.id.input_ssh_private_key);

                assertNotNull("Password input should exist", passwordInput);
                assertNotNull("Key input should exist", keyInput);

                assertTrue("Password field should be visible for password auth",
                        passwordInput.getParent() instanceof View &&
                        ((View) passwordInput.getParent()).getVisibility() == View.VISIBLE);
                assertTrue("Key field should be hidden for password auth",
                        keyInput.getParent() instanceof View &&
                        ((View) keyInput.getParent()).getVisibility() == View.GONE);
            });
        }
    }

    @Test
    public void switchToKeyAuth_showsKeyField() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Set up SSH backend first
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                backendDropdown.setText(getString(R.string.terminal_backend_ssh), false);

                // Switch to key auth
                android.widget.AutoCompleteTextView authDropdown =
                        view.findViewById(R.id.dropdown_ssh_auth_type);
                authDropdown.setText(getString(R.string.ssh_auth_key), false);

                TextInputEditText passwordInput = view.findViewById(R.id.input_ssh_password);
                TextInputEditText keyInput = view.findViewById(R.id.input_ssh_private_key);

                assertNotNull("Password input should exist", passwordInput);
                assertNotNull("Key input should exist", keyInput);

                assertTrue("Key field should be visible for key auth",
                        keyInput.getParent() instanceof View &&
                        ((View) keyInput.getParent()).getVisibility() == View.VISIBLE);
                assertTrue("Password field should be hidden for key auth",
                        passwordInput.getParent() instanceof View &&
                        ((View) passwordInput.getParent()).getVisibility() == View.GONE);
            });
        }
    }

    @Test
    public void saveAndLoad_sshSettingsPersisted() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        AgentConfig originalConfig = settingsManager.getAgentConfig();

        // Set SSH config
        originalConfig.setShellBackend("ssh");
        originalConfig.setSshHost("192.168.1.100");
        originalConfig.setSshPort(2222);
        originalConfig.setSshUser("testuser");
        originalConfig.setSshAuthType("password");
        originalConfig.setSshPassword("testpass");
        originalConfig.setSshVerifyHostKey(false);
        settingsManager.setAgentConfig(originalConfig);

        // Save to persistent storage
        settingsManager.saveToJson();

        // Load settings in fragment
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check backend dropdown
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                assertEquals(getString(R.string.terminal_backend_ssh),
                        backendDropdown.getText().toString());

                // Check SSH fields
                TextInputEditText hostInput = view.findViewById(R.id.input_ssh_host);
                TextInputEditText portInput = view.findViewById(R.id.input_ssh_port);
                TextInputEditText userInput = view.findViewById(R.id.input_ssh_user);
                TextInputEditText passwordInput = view.findViewById(R.id.input_ssh_password);
                SwitchMaterial verifySwitch = view.findViewById(R.id.switch_ssh_verify_host);

                assertEquals("192.168.1.100", hostInput.getText().toString());
                assertEquals("2222", portInput.getText().toString());
                assertEquals("testuser", userInput.getText().toString());
                assertEquals("testpass", passwordInput.getText().toString());
                assertFalse("Verify host key should be unchecked", verifySwitch.isChecked());
            });
        }
    }

    @Test
    public void saveAndLoad_keyAuthSettingsPersisted() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        AgentConfig originalConfig = settingsManager.getAgentConfig();

        // Set SSH config with key auth
        originalConfig.setShellBackend("ssh");
        originalConfig.setSshHost("server.example.com");
        originalConfig.setSshPort(22);
        originalConfig.setSshUser("admin");
        originalConfig.setSshAuthType("key");
        originalConfig.setSshPrivateKeyPath("/home/user/.ssh/id_rsa");
        originalConfig.setSshVerifyHostKey(true);
        settingsManager.setAgentConfig(originalConfig);
        settingsManager.saveToJson();

        // Load settings in fragment
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check backend dropdown
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                assertEquals(getString(R.string.terminal_backend_ssh),
                        backendDropdown.getText().toString());

                // Check auth type
                android.widget.AutoCompleteTextView authDropdown =
                        view.findViewById(R.id.dropdown_ssh_auth_type);
                assertEquals(getString(R.string.ssh_auth_key), authDropdown.getText().toString());

                // Check key field
                TextInputEditText keyInput = view.findViewById(R.id.input_ssh_private_key);
                assertEquals("/home/user/.ssh/id_rsa", keyInput.getText().toString());

                // Check verify host switch
                SwitchMaterial verifySwitch = view.findViewById(R.id.switch_ssh_verify_host);
                assertTrue("Verify host key should be checked", verifySwitch.isChecked());
            });
        }
    }

    @Test
    public void save_sshSettingsUpdatedInStorage() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Set SSH config
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                backendDropdown.setText(getString(R.string.terminal_backend_ssh), false);

                TextInputEditText hostInput = view.findViewById(R.id.input_ssh_host);
                hostInput.setText("test-server.example.com");

                TextInputEditText portInput = view.findViewById(R.id.input_ssh_port);
                portInput.setText("2222");

                TextInputEditText userInput = view.findViewById(R.id.input_ssh_user);
                userInput.setText("deploy");

                TextInputEditText passwordInput = view.findViewById(R.id.input_ssh_password);
                passwordInput.setText("secret123");

                SwitchMaterial verifySwitch = view.findViewById(R.id.switch_ssh_verify_host);
                verifySwitch.setChecked(false);

                // Click save button
                MaterialButton saveButton = view.findViewById(R.id.button_save);
                saveButton.performClick();
            });
        }

        // Verify settings were saved
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        AgentConfig savedConfig = settingsManager.getAgentConfig();

        assertEquals("ssh", savedConfig.getShellBackend());
        assertEquals("test-server.example.com", savedConfig.getSshHost());
        assertEquals(2222, savedConfig.getSshPort());
        assertEquals("deploy", savedConfig.getSshUser());
        assertEquals("secret123", savedConfig.getSshPassword());
        assertFalse(savedConfig.isSshVerifyHostKey());
    }

    @Test
    public void defaultValues_loadedCorrectly() {
        // Clear all settings first
        new SettingsManager(getApplicationContext()).clear();

        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                // Check default backend (local)
                android.widget.AutoCompleteTextView backendDropdown =
                        view.findViewById(R.id.dropdown_terminal_backend);
                assertEquals(getString(R.string.terminal_backend_local),
                        backendDropdown.getText().toString());

                // Check SSH group is hidden
                View sshGroup = view.findViewById(R.id.group_ssh_connection);
                assertEquals(View.GONE, sshGroup.getVisibility());

                // Check default SSH port
                TextInputEditText portInput = view.findViewById(R.id.input_ssh_port);
                assertEquals("22", portInput.getText().toString());

                // Check verify host is enabled by default
                SwitchMaterial verifySwitch = view.findViewById(R.id.switch_ssh_verify_host);
                assertTrue("Verify host key should be enabled by default", verifySwitch.isChecked());
            });
        }
    }

    @Test
    public void testConnectionButton_exists() {
        try (FragmentScenario<AgentSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(AgentSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                MaterialButton testButton = view.findViewById(R.id.button_test_ssh_connection);
                assertNotNull("Test connection button should exist", testButton);
                assertTrue("Test button should be enabled", testButton.isEnabled());
                assertEquals(getString(R.string.ssh_test_connection), testButton.getText().toString());
            });
        }
    }

    // ==================== Helper Methods ====================

    private String getString(int resId) {
        return getApplicationContext().getString(resId);
    }
}
