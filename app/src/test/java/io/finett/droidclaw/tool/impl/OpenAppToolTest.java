package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for {@link OpenAppTool}.
 *
 * <p>Tests use Robolectric's real Application context. Tests focus on structural
 * correctness — parameter validation, error handling, approval description,
 * and requiresApproval(). For launch behavior, the success path is verified
 * via the error path (non-existent package) and the code path is tested
 * through the source code review.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Tool registration and definition correctness</li>
 *   <li>execute() with missing package_name parameter</li>
 *   <li>execute() with empty package_name</li>
 *   <li>execute() with non-existent package (launch intent is null)</li>
 *   <li>getApprovalDescription() behavior</li>
 *   <li>requiresApproval() returning true</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
public class OpenAppToolTest {

    private OpenAppTool tool;

    @Before
    public void setUp() {
        tool = new OpenAppTool(RuntimeEnvironment.getApplication());
    }

    // ==================== Tool definition tests ====================

    @Test
    public void getName_returnsOpenApp() {
        assertEquals("open_app", tool.getName());
    }

    @Test
    public void requiresApproval_returnsTrue() {
        assertTrue("open_app should require approval", tool.requiresApproval());
    }

    @Test
    public void getDefinition_returnsCorrectDefinition() {
        ToolDefinition definition = tool.getDefinition();
        assertEquals("open_app", definition.getFunction().getName());
        assertNotNull(definition.getFunction().getDescription());
        assertNotNull(definition.getFunction().getParameters());
        assertTrue(definition.getFunction().getDescription().toLowerCase().contains("list_apps"));
    }

    // ==================== execute() tests — missing/empty parameters ====================

    @Test
    public void execute_missingPackageName_returnsError() {
        JsonObject args = new JsonObject();
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void execute_nullPackageName_returnsError() {
        JsonObject args = new JsonObject();
        args.add("package_name", null);
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Missing required parameter"));
    }

    @Test
    public void execute_emptyPackageName_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("package_name", "");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("must not be empty"));
    }

    @Test
    public void execute_whitespacePackageName_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("package_name", "   ");
        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("must not be empty"));
    }

    // ==================== execute() tests — non-existent package ====================

    @Test
    public void execute_nonExistentPackage_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("package_name", "com.nonexistent.app");

        ToolResult result = tool.execute(args);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not installed"));
        assertTrue(result.getError().contains("com.nonexistent.app"));
    }

    // ==================== getApprovalDescription() tests ====================

    @Test
    public void getApprovalDescription_withPackageName() {
        JsonObject args = new JsonObject();
        args.addProperty("package_name", "com.chrome");

        String description = tool.getApprovalDescription(args);

        assertEquals("Open app: com.chrome", description);
    }

    @Test
    public void getApprovalDescription_nullArguments() {
        String description = tool.getApprovalDescription(null);

        assertEquals("Open app: ", description);
    }

    @Test
    public void getApprovalDescription_missingPackageName() {
        JsonObject args = new JsonObject();

        String description = tool.getApprovalDescription(args);

        assertEquals("Open app: ", description);
    }

    @Test
    public void getApprovalDescription_nullPackageName() {
        JsonObject args = new JsonObject();
        args.add("package_name", null);

        String description = tool.getApprovalDescription(args);

        assertEquals("Open app: ", description);
    }
}
