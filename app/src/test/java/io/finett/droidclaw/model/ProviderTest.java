package io.finett.droidclaw.model;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Provider} model class.
 */
public class ProviderTest {

    private Provider provider;

    @Before
    public void setUp() {
        provider = new Provider();
    }

    // ==================== Constructor Tests ====================

    @Test
    public void defaultConstructor_initializesEmptyModelsList() {
        Provider p = new Provider();
        assertNotNull(p.getModels());
        assertTrue(p.getModels().isEmpty());
    }

    @Test
    public void parameterizedConstructor_setsAllFields() {
        Provider p = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        
        assertEquals("openai", p.getId());
        assertEquals("OpenAI", p.getName());
        assertEquals("https://api.openai.com", p.getBaseUrl());
        assertEquals("sk-test", p.getApiKey());
        assertEquals("openai", p.getApi());
        assertNotNull(p.getModels());
        assertTrue(p.getModels().isEmpty());
    }

    // ==================== Getter/Setter Tests ====================

    @Test
    public void setId_updatesId() {
        provider.setId("anthropic");
        assertEquals("anthropic", provider.getId());
    }

    @Test
    public void setName_updatesName() {
        provider.setName("Anthropic");
        assertEquals("Anthropic", provider.getName());
    }

    @Test
    public void setBaseUrl_updatesBaseUrl() {
        provider.setBaseUrl("https://api.anthropic.com");
        assertEquals("https://api.anthropic.com", provider.getBaseUrl());
    }

    @Test
    public void setApiKey_updatesApiKey() {
        provider.setApiKey("sk-ant-test");
        assertEquals("sk-ant-test", provider.getApiKey());
    }

    @Test
    public void setApi_updatesApi() {
        provider.setApi("anthropic");
        assertEquals("anthropic", provider.getApi());
    }

    // ==================== Models List Tests ====================

    @Test
    public void setModels_withValidList_createsDefensiveCopy() {
        List<Model> models = new ArrayList<>();
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        models.add(model);
        
        provider.setModels(models);
        
        // Modify original list
        models.add(new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048));
        
        // Provider's list should not be affected
        assertEquals(1, provider.getModels().size());
    }

    @Test
    public void setModels_withNull_initializesEmptyList() {
        provider.setModels(null);
        
        assertNotNull(provider.getModels());
        assertTrue(provider.getModels().isEmpty());
    }

    @Test
    public void addModel_withValidModel_addsToList() {
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        
        provider.addModel(model);
        
        assertEquals(1, provider.getModels().size());
        assertEquals(model, provider.getModels().get(0));
    }

    @Test
    public void addModel_withNull_doesNotAddToList() {
        provider.addModel(null);
        
        assertTrue(provider.getModels().isEmpty());
    }

    @Test
    public void addModel_multipleModels_addsAllToList() {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048);
        
        provider.addModel(model1);
        provider.addModel(model2);
        
        assertEquals(2, provider.getModels().size());
    }

    @Test
    public void removeModel_withExistingModel_removesFromList() {
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        provider.addModel(model);
        
        provider.removeModel(model);
        
        assertTrue(provider.getModels().isEmpty());
    }

    @Test
    public void removeModel_withNull_doesNotThrowException() {
        provider.removeModel(null);
        // Should not throw exception
        assertTrue(provider.getModels().isEmpty());
    }

    @Test
    public void removeModel_withNonExistingModel_doesNotAffectList() {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048);
        provider.addModel(model1);
        
        provider.removeModel(model2);
        
        assertEquals(1, provider.getModels().size());
        assertEquals(model1, provider.getModels().get(0));
    }

    // ==================== getModelById Tests ====================

    @Test
    public void getModelById_withExistingId_returnsModel() {
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        provider.addModel(model);
        
        Model result = provider.getModelById("gpt-4");
        
        assertNotNull(result);
        assertEquals("gpt-4", result.getId());
    }

    @Test
    public void getModelById_withNonExistingId_returnsNull() {
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        provider.addModel(model);
        
        Model result = provider.getModelById("gpt-3.5");
        
        assertNull(result);
    }

    @Test
    public void getModelById_withNull_returnsNull() {
        Model model = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        provider.addModel(model);
        
        Model result = provider.getModelById(null);
        
        assertNull(result);
    }

    @Test
    public void getModelById_withEmptyList_returnsNull() {
        Model result = provider.getModelById("gpt-4");
        
        assertNull(result);
    }

    @Test
    public void getModelById_withMultipleModels_returnsCorrectModel() {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048);
        Model model3 = new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text", "image"), 100000, 4096);
        provider.addModel(model1);
        provider.addModel(model2);
        provider.addModel(model3);
        
        Model result = provider.getModelById("gpt-3.5");
        
        assertNotNull(result);
        assertEquals("GPT-3.5", result.getName());
    }

    // ==================== getModelCount Tests ====================

    @Test
    public void getModelCount_withEmptyList_returnsZero() {
        assertEquals(0, provider.getModelCount());
    }

    @Test
    public void getModelCount_withModels_returnsCorrectCount() {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048);
        provider.addModel(model1);
        provider.addModel(model2);
        
        assertEquals(2, provider.getModelCount());
    }

    @Test
    public void getModelCount_afterRemoval_returnsUpdatedCount() {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048);
        provider.addModel(model1);
        provider.addModel(model2);
        
        provider.removeModel(model1);
        
        assertEquals(1, provider.getModelCount());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void provider_withEmptyStrings_acceptsValues() {
        provider.setId("");
        provider.setName("");
        provider.setBaseUrl("");
        provider.setApiKey("");
        provider.setApi("");
        
        assertEquals("", provider.getId());
        assertEquals("", provider.getName());
        assertEquals("", provider.getBaseUrl());
        assertEquals("", provider.getApiKey());
        assertEquals("", provider.getApi());
    }

    @Test
    public void provider_withSpecialCharacters_acceptsValues() {
        provider.setId("provider-with-dashes_and_underscores");
        provider.setName("Provider™ with Special©Characters®");
        provider.setBaseUrl("https://api.example.com/v1?key=value&other=123");
        provider.setApiKey("sk-ant-api01-ABC123-xyz");
        
        assertEquals("provider-with-dashes_and_underscores", provider.getId());
        assertEquals("Provider™ with Special©Characters®", provider.getName());
        assertEquals("https://api.example.com/v1?key=value&other=123", provider.getBaseUrl());
        assertEquals("sk-ant-api01-ABC123-xyz", provider.getApiKey());
    }
}