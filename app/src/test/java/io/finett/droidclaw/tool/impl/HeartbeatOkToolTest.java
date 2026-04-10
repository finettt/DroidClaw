package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for HeartbeatOkTool.
 * Tests the tool that allows the agent to explicitly mark heartbeat as healthy.
 */
public class HeartbeatOkToolTest {

    private HeartbeatOkTool heartbeatOkTool;

    @Before
    public void setUp() {
        heartbeatOkTool = new HeartbeatOkTool();
    }

    // ==================== BASIC TOOL PROPERTIES ====================

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("heartbeat_ok", heartbeatOkTool.getName());
    }

    @Test
    public void getDefinition_returnsNonNull() {
        ToolDefinition definition = heartbeatOkTool.getDefinition();
        assertNotNull("Tool definition should not be null", definition);
    }

    @Test
    public void getDefinition_hasCorrectName() {
        ToolDefinition definition = heartbeatOkTool.getDefinition();
        assertEquals("heartbeat_ok", definition.getFunction().getName());
    }

    @Test
    public void getDefinition_hasDescription() {
        ToolDefinition definition = heartbeatOkTool.getDefinition();
        String description = definition.getFunction().getDescription();

        assertNotNull("Description should not be null", description);
        assertFalse("Description should not be empty", description.isEmpty());
        assertTrue("Description should mention health check",
                description.toLowerCase().contains("health"));
    }

    @Test
    public void getDefinition_hasNoParameters() {
        ToolDefinition definition = heartbeatOkTool.getDefinition();
        // The tool should have no required parameters (parameters is null for no-arg tools)
        JsonObject parameters = definition.getFunction().getParameters();
        assertTrue("Parameters should be null for heartbeat_ok", parameters == null);
    }

    // ==================== TOOL EXECUTION TESTS ====================

    @Test
    public void execute_withNullArguments_succeeds() {
        ToolResult result = heartbeatOkTool.execute(null);

        assertTrue("Should succeed", result.isSuccess());
        assertNotNull("Content should not be null", result.getContent());
        assertTrue("Content should contain HEARTBEAT_OK",
                result.getContent().contains("HEARTBEAT_OK"));
    }

    @Test
    public void execute_withEmptyArguments_succeeds() {
        JsonObject arguments = new JsonObject();
        ToolResult result = heartbeatOkTool.execute(arguments);

        assertTrue("Should succeed", result.isSuccess());
        assertNotNull("Content should not be null", result.getContent());
    }

    @Test
    public void execute_returnsSuccessMessage() {
        ToolResult result = heartbeatOkTool.execute(null);

        String content = result.getContent();
        assertTrue("Should contain success message",
                content.contains("System health check passed"));
        assertTrue("Should contain HEARTBEAT_OK marker",
                content.contains("HEARTBEAT_OK"));
    }

    @Test
    public void execute_multipleTimes_consistentResults() {
        ToolResult result1 = heartbeatOkTool.execute(null);
        ToolResult result2 = heartbeatOkTool.execute(null);

        assertEquals("Results should be identical",
                result1.getContent(), result2.getContent());
        assertTrue("Both should succeed", result1.isSuccess() && result2.isSuccess());
    }

    // ==================== TOOL BEHAVIOR TESTS ====================

    @Test
    public void doesNotRequireApproval() {
        // HeartbeatOkTool should not require approval (default is false)
        assertFalse("Should not require approval", heartbeatOkTool.requiresApproval());
    }

    @Test
    public void implementsToolInterface() {
        // Verify it properly implements the Tool interface
        Tool tool = heartbeatOkTool;
        assertNotNull("Should be a valid Tool instance", tool);
        assertEquals("Tool name should match", "heartbeat_ok", tool.getName());
    }
}
