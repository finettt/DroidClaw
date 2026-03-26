package io.finett.droidclaw.model;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Model} model class.
 */
public class ModelTest {

    private Model model;

    @Before
    public void setUp() {
        model = new Model();
    }

    // ==================== Constructor Tests ====================

    @Test
    public void defaultConstructor_initializesEmptyInputList() {
        Model m = new Model();
        assertNotNull(m.getInput());
        assertTrue(m.getInput().isEmpty());
    }

    @Test
    public void parameterizedConstructor_setsAllFields() {
        List<String> input = Arrays.asList("text", "image");
        Model m = new Model("gpt-4-vision", "GPT-4 Vision", "openai", true, input, 128000, 4096);
        
        assertEquals("gpt-4-vision", m.getId());
        assertEquals("GPT-4 Vision", m.getName());
        assertEquals("openai", m.getApi());
        assertTrue(m.isReasoning());
        assertEquals(2, m.getInput().size());
        assertTrue(m.getInput().contains("text"));
        assertTrue(m.getInput().contains("image"));
        assertEquals(128000, m.getContextWindow());
        assertEquals(4096, m.getMaxTokens());
    }

    @Test
    public void parameterizedConstructor_withNullInput_initializesEmptyList() {
        Model m = new Model("gpt-4", "GPT-4", "openai", false, null, 8192, 4096);
        
        assertNotNull(m.getInput());
        assertTrue(m.getInput().isEmpty());
    }

    @Test
    public void parameterizedConstructor_createsDefensiveCopyOfInput() {
        List<String> input = new ArrayList<>(Arrays.asList("text"));
        Model m = new Model("gpt-4", "GPT-4", "openai", false, input, 8192, 4096);
        
        // Modify original list
        input.add("image");
        
        // Model's list should not be affected
        assertEquals(1, m.getInput().size());
        assertFalse(m.getInput().contains("image"));
    }

    // ==================== Getter/Setter Tests ====================

    @Test
    public void setId_updatesId() {
        model.setId("claude-3-opus");
        assertEquals("claude-3-opus", model.getId());
    }

    @Test
    public void setName_updatesName() {
        model.setName("Claude 3 Opus");
        assertEquals("Claude 3 Opus", model.getName());
    }

    @Test
    public void setApi_updatesApi() {
        model.setApi("anthropic");
        assertEquals("anthropic", model.getApi());
    }

    @Test
    public void setReasoning_updatesReasoning() {
        model.setReasoning(true);
        assertTrue(model.isReasoning());
        
        model.setReasoning(false);
        assertFalse(model.isReasoning());
    }

    @Test
    public void setContextWindow_updatesContextWindow() {
        model.setContextWindow(200000);
        assertEquals(200000, model.getContextWindow());
    }

    @Test
    public void setMaxTokens_updatesMaxTokens() {
        model.setMaxTokens(8192);
        assertEquals(8192, model.getMaxTokens());
    }

    // ==================== Input List Tests ====================

    @Test
    public void setInput_withValidList_createsDefensiveCopy() {
        List<String> input = new ArrayList<>(Arrays.asList("text", "image"));
        model.setInput(input);
        
        // Modify original list
        input.add("audio");
        
        // Model's list should not be affected
        assertEquals(2, model.getInput().size());
        assertFalse(model.getInput().contains("audio"));
    }

    @Test
    public void setInput_withNull_initializesEmptyList() {
        model.setInput(null);
        
        assertNotNull(model.getInput());
        assertTrue(model.getInput().isEmpty());
    }

    // ==================== hasTextInput Tests ====================

    @Test
    public void hasTextInput_withTextInList_returnsTrue() {
        model.setInput(Arrays.asList("text", "image"));
        assertTrue(model.hasTextInput());
    }

    @Test
    public void hasTextInput_withoutTextInList_returnsFalse() {
        model.setInput(Arrays.asList("image"));
        assertFalse(model.hasTextInput());
    }

    @Test
    public void hasTextInput_withEmptyList_returnsFalse() {
        model.setInput(new ArrayList<>());
        assertFalse(model.hasTextInput());
    }

    // ==================== hasImageInput Tests ====================

    @Test
    public void hasImageInput_withImageInList_returnsTrue() {
        model.setInput(Arrays.asList("text", "image"));
        assertTrue(model.hasImageInput());
    }

    @Test
    public void hasImageInput_withoutImageInList_returnsFalse() {
        model.setInput(Arrays.asList("text"));
        assertFalse(model.hasImageInput());
    }

    @Test
    public void hasImageInput_withEmptyList_returnsFalse() {
        model.setInput(new ArrayList<>());
        assertFalse(model.hasImageInput());
    }

    // ==================== setTextInput Tests ====================

    @Test
    public void setTextInput_enabledWhenNotPresent_addsText() {
        model.setInput(new ArrayList<>(Arrays.asList("image")));
        
        model.setTextInput(true);
        
        assertTrue(model.hasTextInput());
        assertEquals(2, model.getInput().size());
    }

    @Test
    public void setTextInput_enabledWhenAlreadyPresent_doesNotDuplicate() {
        model.setInput(new ArrayList<>(Arrays.asList("text", "image")));
        
        model.setTextInput(true);
        
        assertTrue(model.hasTextInput());
        assertEquals(2, model.getInput().size());
    }

    @Test
    public void setTextInput_disabledWhenPresent_removesText() {
        model.setInput(new ArrayList<>(Arrays.asList("text", "image")));
        
        model.setTextInput(false);
        
        assertFalse(model.hasTextInput());
        assertEquals(1, model.getInput().size());
        assertTrue(model.hasImageInput());
    }

    @Test
    public void setTextInput_disabledWhenNotPresent_doesNothing() {
        model.setInput(new ArrayList<>(Arrays.asList("image")));
        
        model.setTextInput(false);
        
        assertFalse(model.hasTextInput());
        assertEquals(1, model.getInput().size());
    }

    // ==================== setImageInput Tests ====================

    @Test
    public void setImageInput_enabledWhenNotPresent_addsImage() {
        model.setInput(new ArrayList<>(Arrays.asList("text")));
        
        model.setImageInput(true);
        
        assertTrue(model.hasImageInput());
        assertEquals(2, model.getInput().size());
    }

    @Test
    public void setImageInput_enabledWhenAlreadyPresent_doesNotDuplicate() {
        model.setInput(new ArrayList<>(Arrays.asList("text", "image")));
        
        model.setImageInput(true);
        
        assertTrue(model.hasImageInput());
        assertEquals(2, model.getInput().size());
    }

    @Test
    public void setImageInput_disabledWhenPresent_removesImage() {
        model.setInput(new ArrayList<>(Arrays.asList("text", "image")));
        
        model.setImageInput(false);
        
        assertFalse(model.hasImageInput());
        assertEquals(1, model.getInput().size());
        assertTrue(model.hasTextInput());
    }

    @Test
    public void setImageInput_disabledWhenNotPresent_doesNothing() {
        model.setInput(new ArrayList<>(Arrays.asList("text")));
        
        model.setImageInput(false);
        
        assertFalse(model.hasImageInput());
        assertEquals(1, model.getInput().size());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void model_withZeroContextWindow_acceptsValue() {
        model.setContextWindow(0);
        assertEquals(0, model.getContextWindow());
    }

    @Test
    public void model_withZeroMaxTokens_acceptsValue() {
        model.setMaxTokens(0);
        assertEquals(0, model.getMaxTokens());
    }

    @Test
    public void model_withNegativeContextWindow_acceptsValue() {
        model.setContextWindow(-1);
        assertEquals(-1, model.getContextWindow());
    }

    @Test
    public void model_withNegativeMaxTokens_acceptsValue() {
        model.setMaxTokens(-1);
        assertEquals(-1, model.getMaxTokens());
    }

    @Test
    public void model_withEmptyStrings_acceptsValues() {
        model.setId("");
        model.setName("");
        model.setApi("");
        
        assertEquals("", model.getId());
        assertEquals("", model.getName());
        assertEquals("", model.getApi());
    }

    @Test
    public void model_withSpecialCharacters_acceptsValues() {
        model.setId("claude-3-opus@v1.2.3");
        model.setName("Claude 3 Opus™ (Latest Version)");
        model.setApi("anthropic-v3");
        
        assertEquals("claude-3-opus@v1.2.3", model.getId());
        assertEquals("Claude 3 Opus™ (Latest Version)", model.getName());
        assertEquals("anthropic-v3", model.getApi());
    }

    @Test
    public void model_withLargeContextWindow_acceptsValue() {
        model.setContextWindow(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, model.getContextWindow());
    }

    @Test
    public void model_inputManipulation_worksCorrectly() {
        // Start with no input
        assertFalse(model.hasTextInput());
        assertFalse(model.hasImageInput());
        
        // Add text
        model.setTextInput(true);
        assertTrue(model.hasTextInput());
        assertFalse(model.hasImageInput());
        
        // Add image
        model.setImageInput(true);
        assertTrue(model.hasTextInput());
        assertTrue(model.hasImageInput());
        
        // Remove text
        model.setTextInput(false);
        assertFalse(model.hasTextInput());
        assertTrue(model.hasImageInput());
        
        // Remove image
        model.setImageInput(false);
        assertFalse(model.hasTextInput());
        assertFalse(model.hasImageInput());
    }
}