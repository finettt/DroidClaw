package io.finett.droidclaw.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AgentConfigTest {

    private AgentConfig config;

    @Before
    public void setUp() {
        config = new AgentConfig();
    }

    @Test
    public void defaultConstructor_createsInstance() {
        AgentConfig c = new AgentConfig();
        assertNotNull(c);
    }

    @Test
    public void parameterizedConstructor_setsAllFields() {
        AgentConfig c = new AgentConfig(
            "openai/gpt-4",
            true,
            "relaxed",
            50,
            false,
            60
        );
        
        assertEquals("openai/gpt-4", c.getDefaultModel());
        assertTrue(c.isShellAccess());
        assertEquals("relaxed", c.getSandboxMode());
        assertEquals(50, c.getMaxIterations());
        assertFalse(c.isRequireApproval());
        assertEquals(60, c.getShellTimeout());
    }

    @Test
    public void getDefaults_returnsDefaultConfiguration() {
        AgentConfig defaults = AgentConfig.getDefaults();
        
        assertNotNull(defaults);
        assertEquals("", defaults.getDefaultModel());
        assertFalse(defaults.isShellAccess());
        assertEquals("strict", defaults.getSandboxMode());
        assertEquals(20, defaults.getMaxIterations());
        assertTrue(defaults.isRequireApproval());
        assertEquals(30, defaults.getShellTimeout());
    }

    @Test
    public void getDefaults_returnsNewInstance() {
        AgentConfig defaults1 = AgentConfig.getDefaults();
        AgentConfig defaults2 = AgentConfig.getDefaults();
        
        assertNotSame(defaults1, defaults2);
    }

    @Test
    public void setDefaultModel_updatesDefaultModel() {
        config.setDefaultModel("anthropic/claude-3-opus");
        assertEquals("anthropic/claude-3-opus", config.getDefaultModel());
    }

    @Test
    public void setShellAccess_updatesShellAccess() {
        config.setShellAccess(true);
        assertTrue(config.isShellAccess());
        
        config.setShellAccess(false);
        assertFalse(config.isShellAccess());
    }

    @Test
    public void setSandboxMode_updatesSandboxMode() {
        config.setSandboxMode("relaxed");
        assertEquals("relaxed", config.getSandboxMode());
        
        config.setSandboxMode("strict");
        assertEquals("strict", config.getSandboxMode());
    }

    @Test
    public void setMaxIterations_updatesMaxIterations() {
        config.setMaxIterations(100);
        assertEquals(100, config.getMaxIterations());
    }

    @Test
    public void setRequireApproval_updatesRequireApproval() {
        config.setRequireApproval(true);
        assertTrue(config.isRequireApproval());
        
        config.setRequireApproval(false);
        assertFalse(config.isRequireApproval());
    }

    @Test
    public void setShellTimeout_updatesShellTimeout() {
        config.setShellTimeout(120);
        assertEquals(120, config.getShellTimeout());
    }

    @Test
    public void getDefaultProviderId_withValidFormat_returnsProviderId() {
        config.setDefaultModel("openai/gpt-4");
        assertEquals("openai", config.getDefaultProviderId());
    }

    @Test
    public void getDefaultProviderId_withNull_returnsNull() {
        config.setDefaultModel(null);
        assertNull(config.getDefaultProviderId());
    }

    @Test
    public void getDefaultProviderId_withoutSlash_returnsNull() {
        config.setDefaultModel("gpt-4");
        assertNull(config.getDefaultProviderId());
    }

    @Test
    public void getDefaultProviderId_withEmptyString_returnsNull() {
        config.setDefaultModel("");
        assertNull(config.getDefaultProviderId());
    }

    @Test
    public void getDefaultProviderId_withMultipleSlashes_returnsFirstPart() {
        config.setDefaultModel("provider/namespace/model");
        assertEquals("provider", config.getDefaultProviderId());
    }

    @Test
    public void getDefaultProviderId_withTrailingSlash_returnsProviderId() {
        config.setDefaultModel("openai/");
        assertEquals("openai", config.getDefaultProviderId());
    }

    @Test
    public void getDefaultModelId_withValidFormat_returnsModelId() {
        config.setDefaultModel("openai/gpt-4");
        assertEquals("gpt-4", config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withNull_returnsNull() {
        config.setDefaultModel(null);
        assertNull(config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withoutSlash_returnsNull() {
        config.setDefaultModel("gpt-4");
        assertNull(config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withEmptyString_returnsNull() {
        config.setDefaultModel("");
        assertNull(config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withMultipleSlashes_returnsRemainingPart() {
        config.setDefaultModel("provider/namespace/model");
        assertEquals("namespace/model", config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withTrailingSlash_returnsEmptyString() {
        config.setDefaultModel("openai/");
        assertEquals("", config.getDefaultModelId());
    }

    @Test
    public void getDefaultModelId_withComplexModelId_returnsFullModelId() {
        config.setDefaultModel("anthropic/claude-3-opus-20240229");
        assertEquals("claude-3-opus-20240229", config.getDefaultModelId());
    }

    @Test
    public void setDefaultModelFromIds_withValidIds_setsCorrectFormat() {
        config.setDefaultModelFromIds("openai", "gpt-4");
        assertEquals("openai/gpt-4", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_withNullProviderId_setsEmptyString() {
        config.setDefaultModelFromIds(null, "gpt-4");
        assertEquals("", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_withNullModelId_setsEmptyString() {
        config.setDefaultModelFromIds("openai", null);
        assertEquals("", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_withBothNull_setsEmptyString() {
        config.setDefaultModelFromIds(null, null);
        assertEquals("", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_withEmptyStrings_setsSlashFormat() {
        config.setDefaultModelFromIds("", "");
        assertEquals("/", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_withComplexIds_setsCorrectFormat() {
        config.setDefaultModelFromIds("anthropic-ai", "claude-3-opus-20240229");
        assertEquals("anthropic-ai/claude-3-opus-20240229", config.getDefaultModel());
    }

    @Test
    public void setDefaultModelFromIds_thenGetIds_returnsCorrectValues() {
        config.setDefaultModelFromIds("openai", "gpt-4-turbo");
        
        assertEquals("openai", config.getDefaultProviderId());
        assertEquals("gpt-4-turbo", config.getDefaultModelId());
    }

    @Test
    public void setDefaultModel_thenGetIds_returnsCorrectValues() {
        config.setDefaultModel("anthropic/claude-3-sonnet");
        
        assertEquals("anthropic", config.getDefaultProviderId());
        assertEquals("claude-3-sonnet", config.getDefaultModelId());
    }

    @Test
    public void config_withZeroMaxIterations_acceptsValue() {
        config.setMaxIterations(0);
        assertEquals(0, config.getMaxIterations());
    }

    @Test
    public void config_withNegativeMaxIterations_acceptsValue() {
        config.setMaxIterations(-1);
        assertEquals(-1, config.getMaxIterations());
    }

    @Test
    public void config_withZeroTimeout_acceptsValue() {
        config.setShellTimeout(0);
        assertEquals(0, config.getShellTimeout());
    }

    @Test
    public void config_withNegativeTimeout_acceptsValue() {
        config.setShellTimeout(-1);
        assertEquals(-1, config.getShellTimeout());
    }

    @Test
    public void config_withVeryLargeMaxIterations_acceptsValue() {
        config.setMaxIterations(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, config.getMaxIterations());
    }

    @Test
    public void config_withVeryLargeTimeout_acceptsValue() {
        config.setShellTimeout(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, config.getShellTimeout());
    }

    @Test
    public void config_withCustomSandboxMode_acceptsValue() {
        config.setSandboxMode("custom-mode");
        assertEquals("custom-mode", config.getSandboxMode());
    }

    @Test
    public void config_withSpecialCharactersInModel_handlesCorrectly() {
        config.setDefaultModel("provider-with-dashes/model_with_underscores");
        assertEquals("provider-with-dashes", config.getDefaultProviderId());
        assertEquals("model_with_underscores", config.getDefaultModelId());
    }

    @Test
    public void config_withLeadingSlash_handlesCorrectly() {
        config.setDefaultModel("/model-id");
        assertEquals("", config.getDefaultProviderId());
        assertEquals("model-id", config.getDefaultModelId());
    }

    @Test
    public void config_multipleSetsAndGets_maintainsCorrectState() {
        config.setDefaultModel("openai/gpt-4");
        config.setShellAccess(true);
        config.setSandboxMode("relaxed");
        config.setMaxIterations(50);
        config.setRequireApproval(false);
        config.setShellTimeout(60);
        
        assertEquals("openai/gpt-4", config.getDefaultModel());
        assertTrue(config.isShellAccess());
        assertEquals("relaxed", config.getSandboxMode());
        assertEquals(50, config.getMaxIterations());
        assertFalse(config.isRequireApproval());
        assertEquals(60, config.getShellTimeout());
        
        config.setDefaultModel("anthropic/claude-3");
        config.setShellAccess(false);
        config.setSandboxMode("strict");
        config.setMaxIterations(20);
        config.setRequireApproval(true);
        config.setShellTimeout(30);
        
        assertEquals("anthropic/claude-3", config.getDefaultModel());
        assertFalse(config.isShellAccess());
        assertEquals("strict", config.getSandboxMode());
        assertEquals(20, config.getMaxIterations());
        assertTrue(config.isRequireApproval());
        assertEquals(30, config.getShellTimeout());
    }
}