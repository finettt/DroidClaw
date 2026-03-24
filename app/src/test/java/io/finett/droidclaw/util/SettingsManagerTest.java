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

    // ========================
    // Agent Settings Tests
    // ========================

    @Test
    public void isShellAccessEnabled_whenNotSet_returnsFalse() {
        assertFalse(settingsManager.isShellAccessEnabled());
    }

    @Test
    public void setShellAccessEnabled_storesAndRetrievesValue() {
        settingsManager.setShellAccessEnabled(true);

        assertTrue(settingsManager.isShellAccessEnabled());
    }

    @Test
    public void setShellAccessEnabled_canBeDisabled() {
        settingsManager.setShellAccessEnabled(true);
        settingsManager.setShellAccessEnabled(false);

        assertFalse(settingsManager.isShellAccessEnabled());
    }

    @Test
    public void getSandboxMode_whenNotSet_returnsStrict() {
        assertEquals("strict", settingsManager.getSandboxMode());
    }

    @Test
    public void setSandboxMode_storesAndRetrievesValue() {
        settingsManager.setSandboxMode("relaxed");

        assertEquals("relaxed", settingsManager.getSandboxMode());
    }

    @Test
    public void setSandboxMode_canBeSetToStrict() {
        settingsManager.setSandboxMode("relaxed");
        settingsManager.setSandboxMode("strict");

        assertEquals("strict", settingsManager.getSandboxMode());
    }

    @Test
    public void getMaxAgentIterations_whenNotSet_returnsDefault() {
        assertEquals(20, settingsManager.getMaxAgentIterations());
    }

    @Test
    public void setMaxAgentIterations_storesAndRetrievesValue() {
        settingsManager.setMaxAgentIterations(50);

        assertEquals(50, settingsManager.getMaxAgentIterations());
    }

    @Test
    public void setMaxAgentIterations_canBeSetToMinimum() {
        settingsManager.setMaxAgentIterations(1);

        assertEquals(1, settingsManager.getMaxAgentIterations());
    }

    @Test
    public void isRequireApproval_whenNotSet_returnsTrue() {
        assertTrue(settingsManager.isRequireApproval());
    }

    @Test
    public void setRequireApproval_storesAndRetrievesValue() {
        settingsManager.setRequireApproval(false);

        assertFalse(settingsManager.isRequireApproval());
    }

    @Test
    public void setRequireApproval_canBeEnabled() {
        settingsManager.setRequireApproval(false);
        settingsManager.setRequireApproval(true);

        assertTrue(settingsManager.isRequireApproval());
    }

    @Test
    public void getShellTimeoutSeconds_whenNotSet_returnsDefault() {
        assertEquals(30, settingsManager.getShellTimeoutSeconds());
    }

    @Test
    public void setShellTimeoutSeconds_storesAndRetrievesValue() {
        settingsManager.setShellTimeoutSeconds(60);

        assertEquals(60, settingsManager.getShellTimeoutSeconds());
    }

    @Test
    public void setShellTimeoutSeconds_canBeSetToMaximum() {
        settingsManager.setShellTimeoutSeconds(300);

        assertEquals(300, settingsManager.getShellTimeoutSeconds());
    }

    @Test
    public void agentSettingsPersistAcrossInstances() {
        settingsManager.setShellAccessEnabled(true);
        settingsManager.setSandboxMode("relaxed");
        settingsManager.setMaxAgentIterations(30);
        settingsManager.setRequireApproval(false);
        settingsManager.setShellTimeoutSeconds(120);

        Context context = RuntimeEnvironment.getApplication();
        SettingsManager newInstance = new SettingsManager(context);

        assertTrue(newInstance.isShellAccessEnabled());
        assertEquals("relaxed", newInstance.getSandboxMode());
        assertEquals(30, newInstance.getMaxAgentIterations());
        assertFalse(newInstance.isRequireApproval());
        assertEquals(120, newInstance.getShellTimeoutSeconds());
    }
}