package io.finett.droidclaw.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.*;

public class ToolDefinitionTest {

    @Test
    public void testToolDefinitionCreation() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path", true)
            .build();

        ToolDefinition definition = new ToolDefinition("test_tool", "A test tool", parameters);

        assertEquals("function", definition.getType());
        assertNotNull(definition.getFunction());
        assertEquals("test_tool", definition.getFunction().getName());
        assertEquals("A test tool", definition.getFunction().getDescription());
    }

    @Test
    public void testToJson() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path", true)
            .build();

        ToolDefinition definition = new ToolDefinition("test_tool", "A test tool", parameters);
        JsonObject json = definition.toJson();

        assertEquals("function", json.get("type").getAsString());
        assertTrue(json.has("function"));
        
        JsonObject function = json.getAsJsonObject("function");
        assertEquals("test_tool", function.get("name").getAsString());
        assertEquals("A test tool", function.get("description").getAsString());
        assertTrue(function.has("parameters"));
    }

    @Test
    public void testFunctionDefinitionToJson() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder().build();
        ToolDefinition.FunctionDefinition function = 
            new ToolDefinition.FunctionDefinition("my_func", "Description", parameters);

        JsonObject json = function.toJson();

        assertEquals("my_func", json.get("name").getAsString());
        assertEquals("Description", json.get("description").getAsString());
        assertTrue(json.has("parameters"));
    }

    @Test
    public void testParametersBuilderWithString() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addString("name", "User name", true)
            .addString("optional_field", "Optional field", false)
            .build();

        assertEquals("object", params.get("type").getAsString());
        assertTrue(params.has("properties"));
        
        JsonObject properties = params.getAsJsonObject("properties");
        assertTrue(properties.has("name"));
        assertTrue(properties.has("optional_field"));
        
        assertEquals("string", properties.getAsJsonObject("name").get("type").getAsString());
        assertEquals("User name", properties.getAsJsonObject("name").get("description").getAsString());
        
        assertTrue(params.has("required"));
        JsonArray required = params.getAsJsonArray("required");
        assertEquals(1, required.size());
        assertEquals("name", required.get(0).getAsString());
    }

    @Test
    public void testParametersBuilderWithInteger() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addInteger("count", "Number of items", true)
            .addInteger("optional_count", "Optional count", false)
            .build();

        JsonObject properties = params.getAsJsonObject("properties");
        assertEquals("integer", properties.getAsJsonObject("count").get("type").getAsString());
        assertEquals("Number of items", properties.getAsJsonObject("count").get("description").getAsString());
        
        JsonArray required = params.getAsJsonArray("required");
        assertEquals(1, required.size());
        assertEquals("count", required.get(0).getAsString());
    }

    @Test
    public void testParametersBuilderWithBoolean() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addBoolean("enabled", "Is enabled", true)
            .addBoolean("optional_flag", "Optional flag", false)
            .build();

        JsonObject properties = params.getAsJsonObject("properties");
        assertEquals("boolean", properties.getAsJsonObject("enabled").get("type").getAsString());
        assertEquals("Is enabled", properties.getAsJsonObject("enabled").get("description").getAsString());
        
        JsonArray required = params.getAsJsonArray("required");
        assertEquals(1, required.size());
        assertEquals("enabled", required.get(0).getAsString());
    }

    @Test
    public void testParametersBuilderWithMultipleRequiredFields() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addString("field1", "First field", true)
            .addString("field2", "Second field", true)
            .addString("field3", "Third field", true)
            .build();

        JsonArray required = params.getAsJsonArray("required");
        assertEquals(3, required.size());
    }

    @Test
    public void testParametersBuilderWithNoRequiredFields() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addString("optional1", "Optional 1", false)
            .addInteger("optional2", "Optional 2", false)
            .build();

        assertFalse(params.has("required"));
    }

    @Test
    public void testParametersBuilderMixedTypes() {
        JsonObject params = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path", true)
            .addInteger("limit", "Max items", false)
            .addBoolean("recursive", "Include subdirs", false)
            .build();

        JsonObject properties = params.getAsJsonObject("properties");
        assertEquals(3, properties.size());
        
        assertEquals("string", properties.getAsJsonObject("path").get("type").getAsString());
        assertEquals("integer", properties.getAsJsonObject("limit").get("type").getAsString());
        assertEquals("boolean", properties.getAsJsonObject("recursive").get("type").getAsString());
    }

    @Test
    public void testEmptyParametersBuilder() {
        JsonObject params = new ToolDefinition.ParametersBuilder().build();

        assertEquals("object", params.get("type").getAsString());
        assertTrue(params.has("properties"));
        assertEquals(0, params.getAsJsonObject("properties").size());
    }

    @Test
    public void testFunctionDefinitionGetters() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder().build();
        ToolDefinition.FunctionDefinition function = 
            new ToolDefinition.FunctionDefinition("func", "desc", parameters);

        assertEquals("func", function.getName());
        assertEquals("desc", function.getDescription());
        assertNotNull(function.getParameters());
    }
}