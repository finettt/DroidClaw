package io.finett.droidclaw.util;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.model.AgentConfig;

/**
 * Instrumented tests for the {@code guidelinesLearningEnabled} setting:
 * default value and persistence through SharedPreferences on a real device.
 *
 * <p>The device settings are shared state: the original flag value is saved
 * and restored after each test.</p>
 */
@RunWith(AndroidJUnit4.class)
public class GuidelinesConfigInstrumentedTest {

    private SettingsManager settingsManager;
    private boolean originalValue;

    @Before
    public void setUp() {
        settingsManager = new SettingsManager(getApplicationContext());
        AgentConfig config = settingsManager.getAgentConfig();
        originalValue = config != null && config.isGuidelinesLearningEnabled();
    }

    @After
    public void tearDown() {
        AgentConfig config = settingsManager.getAgentConfig();
        if (config != null) {
            config.setGuidelinesLearningEnabled(originalValue);
            settingsManager.setAgentConfig(config);
        }
    }

    @Test
    public void defaults_guidelinesLearningEnabled() {
        AgentConfig defaults = AgentConfig.getDefaults();
        assertTrue("Guidelines learning should be enabled in default config",
                defaults.isGuidelinesLearningEnabled());
    }

    @Test
    public void flagValue_persistsAcrossSettingsManagerInstances() {
        AgentConfig config = settingsManager.getAgentConfig();

        // Toggle to the opposite of the current value and persist.
        boolean toggled = !config.isGuidelinesLearningEnabled();
        config.setGuidelinesLearningEnabled(toggled);
        settingsManager.setAgentConfig(config);

        // A fresh SettingsManager instance reads from SharedPreferences.
        SettingsManager fresh = new SettingsManager(getApplicationContext());
        assertEquals("Flag must survive a save/load cycle",
                toggled, fresh.getAgentConfig().isGuidelinesLearningEnabled());

        // Toggle back and verify again (both directions persist).
        config.setGuidelinesLearningEnabled(!toggled);
        settingsManager.setAgentConfig(config);

        SettingsManager freshAgain = new SettingsManager(getApplicationContext());
        assertEquals("Flag must persist in both toggle directions",
                !toggled, freshAgain.getAgentConfig().isGuidelinesLearningEnabled());
    }
}
