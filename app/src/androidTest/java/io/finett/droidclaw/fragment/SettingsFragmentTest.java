package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.testing.TestNavHostController;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;

@RunWith(AndroidJUnit4.class)
public class SettingsFragmentTest {

    private static final String PREFS_NAME = "droidclaw_settings";

    @Before
    public void setUp() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void launch_loadsSavedSettingsIntoViews() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("api_key", "saved-key")
                .putString("api_url", "https://example.com/chat")
                .putString("model_name", "test-model")
                .putString("system_prompt", "Be helpful")
                .putInt("max_tokens", 2048)
                .putFloat("temperature", 1.25f)
                .commit();

        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                EditText apiKeyInput = fragment.requireView().findViewById(R.id.apiKeyInput);
                EditText apiUrlInput = fragment.requireView().findViewById(R.id.apiUrlInput);
                EditText modelNameInput = fragment.requireView().findViewById(R.id.modelNameInput);
                EditText systemPromptInput = fragment.requireView().findViewById(R.id.systemPromptInput);
                EditText maxTokensInput = fragment.requireView().findViewById(R.id.maxTokensInput);
                SeekBar temperatureSeekBar = fragment.requireView().findViewById(R.id.temperatureSeekBar);
                TextView temperatureValue = fragment.requireView().findViewById(R.id.temperatureValue);

                assertEquals("saved-key", apiKeyInput.getText().toString());
                assertEquals("https://example.com/chat", apiUrlInput.getText().toString());
                assertEquals("test-model", modelNameInput.getText().toString());
                assertEquals("Be helpful", systemPromptInput.getText().toString());
                assertEquals("2048", maxTokensInput.getText().toString());
                assertEquals(125, temperatureSeekBar.getProgress());
                assertEquals("1.25", temperatureValue.getText().toString());
            });
        }
    }

    @Test
    public void save_withEmptyApiKey_setsValidationError() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                EditText apiKeyInput = fragment.requireView().findViewById(R.id.apiKeyInput);
                fragment.requireView().findViewById(R.id.saveButton).performClick();

                assertEquals("API key is required", apiKeyInput.getError().toString());
            });
        }
    }

    @Test
    public void save_withInvalidMaxTokens_setsValidationError() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                ((EditText) fragment.requireView().findViewById(R.id.apiKeyInput)).setText("fresh-key");
                ((EditText) fragment.requireView().findViewById(R.id.apiUrlInput)).setText("https://example.com/v1");
                ((EditText) fragment.requireView().findViewById(R.id.modelNameInput)).setText("model-x");
                ((EditText) fragment.requireView().findViewById(R.id.maxTokensInput)).setText("0");

                EditText maxTokensInput = fragment.requireView().findViewById(R.id.maxTokensInput);
                fragment.requireView().findViewById(R.id.saveButton).performClick();

                assertEquals("Max tokens must be between 1 and 128000", maxTokensInput.getError().toString());
            });
        }
    }

    @Test
    public void save_withValidValues_persistsSettings() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                ((EditText) fragment.requireView().findViewById(R.id.apiKeyInput)).setText("fresh-key");
                ((EditText) fragment.requireView().findViewById(R.id.apiUrlInput)).setText("https://example.com/v1");
                ((EditText) fragment.requireView().findViewById(R.id.modelNameInput)).setText("model-x");
                ((EditText) fragment.requireView().findViewById(R.id.systemPromptInput)).setText("System prompt");
                ((EditText) fragment.requireView().findViewById(R.id.maxTokensInput)).setText("4096");
                ((SeekBar) fragment.requireView().findViewById(R.id.temperatureSeekBar)).setProgress(65);

                fragment.requireView().findViewById(R.id.saveButton).performClick();

                SharedPreferences prefs = fragment.requireContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                assertEquals("fresh-key", prefs.getString("api_key", ""));
                assertEquals("https://example.com/v1", prefs.getString("api_url", ""));
                assertEquals("model-x", prefs.getString("model_name", ""));
                assertEquals("System prompt", prefs.getString("system_prompt", ""));
                assertEquals(4096, prefs.getInt("max_tokens", 0));
                assertEquals(0.65f, prefs.getFloat("temperature", 0f), 0.0001f);
                assertNull(((EditText) fragment.requireView().findViewById(R.id.maxTokensInput)).getError());
            });
        }
    }
    private void attachNavController(SettingsFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
    }
}