package io.finett.droidclaw.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsManagerTest {

    private SettingsManager settingsManager;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        settingsManager = new SettingsManager(context);
    }

    @Test
    public void getProviders_whenEmpty_returnsEmptyList() {
        assertTrue(settingsManager.getProviders().isEmpty());
    }

    @Test
    public void addProvider_storesProvider() {
        Provider provider = createTestProvider("test-provider", "Test Provider", 
                "https://api.test.com", "test-api-key");
        
        settingsManager.addProvider(provider);
        
        assertEquals(1, settingsManager.getProviderCount());
        Provider retrieved = settingsManager.getProvider("test-provider");
        assertNotNull(retrieved);
        assertEquals("Test Provider", retrieved.getName());
        assertEquals("https://api.test.com", retrieved.getBaseUrl());
        assertEquals("test-api-key", retrieved.getApiKey());
    }

    @Test
    public void updateProvider_updatesExistingProvider() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        settingsManager.addProvider(provider);
        
        provider.setName("Updated Provider");
        provider.setBaseUrl("https://updated.api.com");
        settingsManager.updateProvider(provider);
        
        Provider retrieved = settingsManager.getProvider("test-provider");
        assertEquals("Updated Provider", retrieved.getName());
        assertEquals("https://updated.api.com", retrieved.getBaseUrl());
    }

    @Test
    public void deleteProvider_removesProvider() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        settingsManager.addProvider(provider);
        
        settingsManager.deleteProvider("test-provider");
        
        assertEquals(0, settingsManager.getProviderCount());
        assertNull(settingsManager.getProvider("test-provider"));
    }

    @Test
    public void addModel_toProvider_storesModel() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        settingsManager.addProvider(provider);
        
        Model model = createTestModel("test-model", "Test Model", 4096);
        settingsManager.addModel("test-provider", model);
        
        Model retrieved = settingsManager.getModel("test-provider", "test-model");
        assertNotNull(retrieved);
        assertEquals("Test Model", retrieved.getName());
        assertEquals(4096, retrieved.getMaxTokens());
    }

    @Test
    public void deleteModel_fromProvider_removesModel() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 4096);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        
        settingsManager.deleteModel("test-provider", "test-model");
        
        assertNull(settingsManager.getModel("test-provider", "test-model"));
    }

    @Test
    public void getAgentConfig_returnsDefaults() {
        AgentConfig config = settingsManager.getAgentConfig();
        assertNotNull(config);
        assertEquals("", config.getDefaultModel());
        assertFalse(config.isShellAccess());
        assertEquals("strict", config.getSandboxMode());
        assertEquals(20, config.getMaxIterations());
        assertTrue(config.isRequireApproval());
        assertEquals(30, config.getShellTimeout());
    }

    @Test
    public void setDefaultModel_storesValue() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 4096);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        
        settingsManager.setDefaultModel("test-provider/test-model");
        
        assertEquals("test-provider/test-model", settingsManager.getDefaultModel());
    }

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
    public void getApiKey_whenNoProvider_returnsEmptyString() {
        assertEquals("", settingsManager.getApiKey());
    }

    @Test
    public void getApiUrl_whenNoProvider_returnsEmptyString() {
        assertEquals("", settingsManager.getApiUrl());
    }

    @Test
    public void getModelName_whenNoModelSelected_returnsEmptyString() {
        assertEquals("", settingsManager.getModelName());
    }

    @Test
    public void getMaxTokens_whenNoModelSelected_returnsDefault() {
        assertEquals(4096, settingsManager.getMaxTokens());
    }

    @Test
    public void getApiKey_whenProviderSelected_returnsProviderApiKey() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 8192);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        settingsManager.setDefaultModel("test-provider/test-model");
        
        assertEquals("test-api-key", settingsManager.getApiKey());
    }

    @Test
    public void getApiUrl_whenProviderSelected_returnsProviderBaseUrl() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 8192);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        settingsManager.setDefaultModel("test-provider/test-model");
        
        assertEquals("https://api.test.com", settingsManager.getApiUrl());
    }

    @Test
    public void getModelName_whenModelSelected_returnsModelId() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 8192);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        settingsManager.setDefaultModel("test-provider/test-model");
        
        assertEquals("test-model", settingsManager.getModelName());
    }

    @Test
    public void getMaxTokens_whenModelSelected_returnsModelMaxTokens() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        Model model = createTestModel("test-model", "Test Model", 8192);
        provider.addModel(model);
        settingsManager.addProvider(provider);
        settingsManager.setDefaultModel("test-provider/test-model");
        
        assertEquals(8192, settingsManager.getMaxTokens());
    }

    @Test
    public void isConfigured_whenNoProvider_returnsFalse() {
        assertFalse(settingsManager.isConfigured());
    }

    @Test
    public void isConfigured_whenProviderWithoutApiKey_returnsFalse() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "");
        settingsManager.addProvider(provider);
        
        assertFalse(settingsManager.isConfigured());
    }

    @Test
    public void isConfigured_whenProviderWithApiKey_returnsTrue() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        settingsManager.addProvider(provider);
        
        assertTrue(settingsManager.isConfigured());
    }

    @Test
    public void isOnboardingCompleted_whenNotSet_returnsFalse() {
        assertFalse(settingsManager.isOnboardingCompleted());
    }

    @Test
    public void setOnboardingCompleted_storesValue() {
        settingsManager.setOnboardingCompleted(true);
        assertTrue(settingsManager.isOnboardingCompleted());
    }

    @Test
    public void getUserName_whenNotSet_returnsEmptyString() {
        assertEquals("", settingsManager.getUserName());
    }

    @Test
    public void setUserName_storesValue() {
        settingsManager.setUserName("Test User");
        assertEquals("Test User", settingsManager.getUserName());
    }

    @Test
    public void resetOnboarding_clearsValues() {
        settingsManager.setOnboardingCompleted(true);
        settingsManager.setUserName("Test User");
        
        settingsManager.resetOnboarding();
        
        assertFalse(settingsManager.isOnboardingCompleted());
        assertEquals("", settingsManager.getUserName());
    }

    @Test
    public void getAllModelReferences_returnsAllModels() {
        Provider provider1 = createTestProvider("provider1", "Provider 1",
                "https://api1.com", "key1");
        provider1.addModel(createTestModel("model1", "Model 1", 4096));
        provider1.addModel(createTestModel("model2", "Model 2", 8192));
        
        Provider provider2 = createTestProvider("provider2", "Provider 2",
                "https://api2.com", "key2");
        provider2.addModel(createTestModel("model3", "Model 3", 4096));
        
        settingsManager.addProvider(provider1);
        settingsManager.addProvider(provider2);
        
        assertEquals(3, settingsManager.getAllModelReferences().size());
        assertTrue(settingsManager.getAllModelReferences().contains("provider1/model1"));
        assertTrue(settingsManager.getAllModelReferences().contains("provider1/model2"));
        assertTrue(settingsManager.getAllModelReferences().contains("provider2/model3"));
    }

    @Test
    public void getModelDisplayName_returnsFormattedName() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        provider.addModel(createTestModel("test-model", "Test Model", 4096));
        settingsManager.addProvider(provider);
        
        String displayName = settingsManager.getModelDisplayName("test-provider/test-model");
        assertEquals("Test Provider / Test Model", displayName);
    }

    @Test
    public void agentSettingsPersistAcrossInstances() {
        settingsManager.setShellAccessEnabled(true);
        settingsManager.setSandboxMode("relaxed");
        settingsManager.setMaxAgentIterations(30);
        settingsManager.setRequireApproval(false);
        settingsManager.setShellTimeoutSeconds(120);

        SettingsManager newInstance = new SettingsManager(context);

        assertTrue(newInstance.isShellAccessEnabled());
        assertEquals("relaxed", newInstance.getSandboxMode());
        assertEquals(30, newInstance.getMaxAgentIterations());
        assertFalse(newInstance.isRequireApproval());
        assertEquals(120, newInstance.getShellTimeoutSeconds());
    }

    @Test
    public void providerPersistsAcrossInstances() {
        Provider provider = createTestProvider("test-provider", "Test Provider",
                "https://api.test.com", "test-api-key");
        provider.addModel(createTestModel("test-model", "Test Model", 8192));
        settingsManager.addProvider(provider);
        settingsManager.setDefaultModel("test-provider/test-model");

        SettingsManager newInstance = new SettingsManager(context);

        Provider retrieved = newInstance.getProvider("test-provider");
        assertNotNull(retrieved);
        assertEquals("Test Provider", retrieved.getName());
        assertEquals("test-api-key", retrieved.getApiKey());
        assertEquals("test-provider/test-model", newInstance.getDefaultModel());
    }

    @Test
    public void onboardingPersistsAcrossInstances() {
        settingsManager.setOnboardingCompleted(true);
        settingsManager.setUserName("Test User");

        SettingsManager newInstance = new SettingsManager(context);

        assertTrue(newInstance.isOnboardingCompleted());
        assertEquals("Test User", newInstance.getUserName());
    }

    private Provider createTestProvider(String id, String name, String baseUrl, String apiKey) {
        Provider provider = new Provider();
        provider.setId(id);
        provider.setName(name);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setApi("openai-completions");
        return provider;
    }

    private Model createTestModel(String id, String name, int maxTokens) {
        Model model = new Model();
        model.setId(id);
        model.setName(name);
        model.setApi("openai-completions");
        model.setMaxTokens(maxTokens);
        model.setContextWindow(8192);
        model.setInput(Arrays.asList("text"));
        return model;
    }
}