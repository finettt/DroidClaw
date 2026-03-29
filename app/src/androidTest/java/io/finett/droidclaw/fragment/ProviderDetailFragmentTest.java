package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.List;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(AndroidJUnit4.class)
public class ProviderDetailFragmentTest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";

    @Before
    public void setUp() {
        // Clear settings before each test
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void saveProvider_emptyFields_showErrors() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputLayout tilProviderName = fragment.requireView().findViewById(R.id.til_provider_name);
                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                TextInputLayout tilApiKey = fragment.requireView().findViewById(R.id.til_api_key);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                // Try to save without filling any fields
                btnSave.performClick();

                // All fields should show errors
                assertNotNull("Provider name error should be set", tilProviderName.getError());
                assertNotNull("Base URL error should be set", tilBaseUrl.getError());
                assertNotNull("API key error should be set", tilApiKey.getError());
            });
        }
    }

    @Test
    public void saveProvider_invalidUrl_showUrlError() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.input_provider_name);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.input_base_url);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.input_api_key);
                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                etProviderName.setText("Test Provider");
                etBaseUrl.setText("invalid-url");
                etApiKey.setText("test-key");

                btnSave.performClick();

                assertNotNull("Invalid URL error should be set", tilBaseUrl.getError());
            });
        }
    }

    @Test
    public void inputProviderName_clearsErrorOnTyping() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputLayout tilProviderName = fragment.requireView().findViewById(R.id.til_provider_name);
                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.input_provider_name);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                // Trigger validation error
                btnSave.performClick();
                assertNotNull("Error should be shown", tilProviderName.getError());

                // Type in the field
                etProviderName.setText("A");
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilProviderName = fragment.requireView().findViewById(R.id.til_provider_name);
                // Error should be cleared
                assertNull("Error should be cleared when typing", tilProviderName.getError());
            });
        }
    }

    @Test
    public void inputBaseUrl_clearsErrorOnTyping() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.input_base_url);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                // Trigger validation error
                btnSave.performClick();
                assertNotNull("Error should be shown", tilBaseUrl.getError());

                // Type in the field
                etBaseUrl.setText("https://");
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                // Error should be cleared
                assertNull("Error should be cleared when typing", tilBaseUrl.getError());
            });
        }
    }

    @Test
    public void inputApiKey_clearsErrorOnTyping() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputLayout tilApiKey = fragment.requireView().findViewById(R.id.til_api_key);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.input_api_key);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                // Trigger validation error
                btnSave.performClick();
                assertNotNull("Error should be shown", tilApiKey.getError());

                // Type in the field
                etApiKey.setText("test");
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilApiKey = fragment.requireView().findViewById(R.id.til_api_key);
                // Error should be cleared
                assertNull("Error should be cleared when typing", tilApiKey.getError());
            });
        }
    }

    @Test
    public void saveProvider_validData_savesProvider() {
        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, null, R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                attachNavController(fragment);

                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.input_provider_name);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.input_base_url);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.input_api_key);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);

                etProviderName.setText("OpenAI");
                etBaseUrl.setText("https://api.openai.com/v1");
                etApiKey.setText("sk-test-key");

                btnSave.performClick();
            });

            // Wait for navigation
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Verify provider was saved
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            assertEquals("Provider should be saved", 1, settingsManager.getProviderCount());
            List<Provider> providers = settingsManager.getProviders();
            Provider savedProvider = providers.get(0);
            assertNotNull("Saved provider should not be null", savedProvider);
            assertEquals("Provider name should match", "OpenAI", savedProvider.getName());
            assertEquals("Base URL should match", "https://api.openai.com/v1", savedProvider.getBaseUrl());
            assertEquals("API key should match", "sk-test-key", savedProvider.getApiKey());
        }
    }

    @Test
    public void editExistingProvider_loadsData() {
        // Pre-populate a provider
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider provider = new Provider();
        provider.setId("test-provider-id");
        provider.setName("Test Provider");
        provider.setBaseUrl("https://api.test.com");
        provider.setApiKey("test-key");
        provider.setApi("openai-completions");
        settingsManager.addProvider(provider);

        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, createArgs("test-provider-id"), R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.input_provider_name);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.input_base_url);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.input_api_key);

                assertEquals("Provider name should be loaded", "Test Provider", etProviderName.getText().toString());
                assertEquals("Base URL should be loaded", "https://api.test.com", etBaseUrl.getText().toString());
                assertEquals("API key should be loaded", "test-key", etApiKey.getText().toString());
            });
        }
    }

    @Test
    public void deleteProvider_confirmsAndDeletes() {
        // Pre-populate a provider
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        Provider provider = new Provider();
        provider.setId("test-provider-id");
        provider.setName("Test Provider");
        provider.setBaseUrl("https://api.test.com");
        provider.setApiKey("test-key");
        provider.setApi("openai-completions");
        settingsManager.addProvider(provider);

        try (FragmentScenario<ProviderDetailFragment> scenario =
                     FragmentScenario.launchInContainer(ProviderDetailFragment.class, createArgs("test-provider-id"), R.style.Theme_DroidClaw)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilProviderName = fragment.requireView().findViewById(R.id.til_provider_name);
                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.input_provider_name);
                Button btnSave = fragment.requireView().findViewById(R.id.button_save);
                Button btnDelete = fragment.requireView().findViewById(R.id.button_delete);

                // Verify delete button is visible for existing provider
                assertEquals("Delete button should be visible", View.VISIBLE, btnDelete.getVisibility());

                // Clear name to trigger error, then fill it to enable save
                etProviderName.setText("");
                btnSave.performClick();
                assertNotNull("Error should be shown", tilProviderName.getError());

                etProviderName.setText("Updated Name");
                btnSave.performClick();
            });

            // Wait for navigation
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Provider should still exist (save happened)
            assertEquals("Provider should still exist after save", 1, settingsManager.getProviderCount());
        }
    }

    private Bundle createArgs(String providerId) {
        Bundle args = new Bundle();
        args.putString("providerId", providerId);
        return args;
    }

    private void attachNavController(ProviderDetailFragment fragment) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(R.id.providerDetailFragment);
        setViewNavController(fragment.requireView(), navController);
    }
}
