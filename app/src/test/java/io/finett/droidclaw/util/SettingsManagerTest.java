package io.finett.droidclaw.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsManagerTest {

    private SettingsManager settingsManager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        settingsManager = new SettingsManager(context);
    }

    @Test
    public void getApiKey_whenNotSet_returnsEmptyString() {
        assertEquals("", settingsManager.getApiKey());
    }

    @Test
    public void setApiKey_storesAndRetrievesValue() {
        settingsManager.setApiKey("test-api-key-123");

        assertEquals("test-api-key-123", settingsManager.getApiKey());
    }

    @Test
    public void getApiUrl_whenNotSet_returnsDefaultValue() {
        assertEquals("https://api.openai.com/v1/chat/completions", settingsManager.getApiUrl());
    }

    @Test
    public void setApiUrl_storesAndRetrievesValue() {
        settingsManager.setApiUrl("https://custom.api.com/v1/completions");

        assertEquals("https://custom.api.com/v1/completions", settingsManager.getApiUrl());
    }

    @Test
    public void getModelName_whenNotSet_returnsDefaultValue() {
        assertEquals("gpt-3.5-turbo", settingsManager.getModelName());
    }

    @Test
    public void setModelName_storesAndRetrievesValue() {
        settingsManager.setModelName("gpt-4");

        assertEquals("gpt-4", settingsManager.getModelName());
    }

    @Test
    public void getSystemPrompt_whenNotSet_returnsDefaultValue() {
        assertEquals("You are a helpful assistant.", settingsManager.getSystemPrompt());
    }

    @Test
    public void setSystemPrompt_storesAndRetrievesValue() {
        settingsManager.setSystemPrompt("You are a coding assistant.");

        assertEquals("You are a coding assistant.", settingsManager.getSystemPrompt());
    }

    @Test
    public void getMaxTokens_whenNotSet_returnsDefaultValue() {
        assertEquals(1024, settingsManager.getMaxTokens());
    }

    @Test
    public void setMaxTokens_storesAndRetrievesValue() {
        settingsManager.setMaxTokens(2048);

        assertEquals(2048, settingsManager.getMaxTokens());
    }

    @Test
    public void getTemperature_whenNotSet_returnsDefaultValue() {
        assertEquals(0.7f, settingsManager.getTemperature(), 0.001f);
    }

    @Test
    public void setTemperature_storesAndRetrievesValue() {
        settingsManager.setTemperature(0.9f);

        assertEquals(0.9f, settingsManager.getTemperature(), 0.001f);
    }

    @Test
    public void isConfigured_whenApiKeyNotSet_returnsFalse() {
        assertFalse(settingsManager.isConfigured());
    }

    @Test
    public void isConfigured_whenApiKeyIsEmpty_returnsFalse() {
        settingsManager.setApiKey("");

        assertFalse(settingsManager.isConfigured());
    }

    @Test
    public void isConfigured_whenApiKeyIsSet_returnsTrue() {
        settingsManager.setApiKey("some-api-key");

        assertTrue(settingsManager.isConfigured());
    }

    @Test
    public void settingsPersistAcrossInstances() {
        settingsManager.setApiKey("persistent-key");
        settingsManager.setApiUrl("https://persistent.api.com");
        settingsManager.setModelName("persistent-model");
        settingsManager.setSystemPrompt("Persistent prompt");
        settingsManager.setMaxTokens(512);
        settingsManager.setTemperature(0.5f);

        Context context = RuntimeEnvironment.getApplication();
        SettingsManager newInstance = new SettingsManager(context);

        assertEquals("persistent-key", newInstance.getApiKey());
        assertEquals("https://persistent.api.com", newInstance.getApiUrl());
        assertEquals("persistent-model", newInstance.getModelName());
        assertEquals("Persistent prompt", newInstance.getSystemPrompt());
        assertEquals(512, newInstance.getMaxTokens());
        assertEquals(0.5f, newInstance.getTemperature(), 0.001f);
    }
}