package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.*;

public class ToolResultTest {

    @Test
    public void testSuccessWithString() {
        ToolResult result = ToolResult.success("Operation completed");

        assertTrue(result.isSuccess());
        assertEquals("Operation completed", result.getContent());
        assertNull(result.getError());
    }

    @Test
    public void testSuccessWithJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("status", "ok");
        json.addProperty("count", 42);

        ToolResult result = ToolResult.success(json);

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("\"status\":\"ok\""));
        assertTrue(result.getContent().contains("\"count\":42"));
        assertNull(result.getError());
    }

    @Test
    public void testError() {
        ToolResult result = ToolResult.error("Something went wrong");

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        assertEquals("Something went wrong", result.getError());
    }

    @Test
    public void testToJsonSuccess() {
        ToolResult result = ToolResult.success("Success message");
        String json = result.toJson();

        assertEquals("Success message", json);
    }

    @Test
    public void testToJsonSuccessWithNull() {
        ToolResult result = ToolResult.success((String) null);
        String json = result.toJson();

        assertEquals("", json);
    }

    @Test
    public void testToJsonError() {
        ToolResult result = ToolResult.error("Error occurred");
        String json = result.toJson();

        assertNotNull(json);
        JsonObject errorJson = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("Error occurred", errorJson.get("error").getAsString());
    }

    @Test
    public void testToJsonErrorWithNull() {
        ToolResult result = ToolResult.error(null);
        String json = result.toJson();

        assertNotNull(json);
        JsonObject errorJson = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("Unknown error", errorJson.get("error").getAsString());
    }

    @Test
    public void testToStringSuccess() {
        ToolResult result = ToolResult.success("Done");
        String str = result.toString();

        assertEquals("Success: Done", str);
    }

    @Test
    public void testToStringError() {
        ToolResult result = ToolResult.error("Failed");
        String str = result.toString();

        assertEquals("Error: Failed", str);
    }

    @Test
    public void testSuccessWithComplexJson() {
        JsonObject json = new JsonObject();
        json.addProperty("path", "test.txt");
        json.addProperty("size", 1024);
        json.addProperty("success", true);

        ToolResult result = ToolResult.success(json);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("test.txt"));
        assertTrue(content.contains("1024"));
    }

    @Test
    public void testErrorWithSpecialCharacters() {
        ToolResult result = ToolResult.error("File not found: /path/to/file.txt");
        
        assertFalse(result.isSuccess());
        assertEquals("File not found: /path/to/file.txt", result.getError());
    }

    @Test
    public void testSuccessWithEmptyString() {
        ToolResult result = ToolResult.success("");
        
        assertTrue(result.isSuccess());
        assertEquals("", result.getContent());
    }

    @Test
    public void testErrorWithEmptyString() {
        ToolResult result = ToolResult.error("");
        
        assertFalse(result.isSuccess());
        assertEquals("", result.getError());
    }
}