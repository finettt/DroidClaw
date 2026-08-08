package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for {@link ListAppsTool}.
 *
 * <p>Tests use Robolectric's real Application context and real PackageManager.
 * Tests focus on structural correctness rather than specific app counts.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Tool registration and definition correctness</li>
 *   <li>execute() with show_system=true (default)</li>
 *   <li>execute() with show_system=false (filter system apps)</li>
 *   <li>Null/empty arguments handling</li>
 *   <li>Correct counting of user vs system apps</li>
 *   <li>requiresApproval() returning false</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
public class ListAppsToolTest {

    private ListAppsTool tool;

    @Before
    public void setUp() {
        tool = new ListAppsTool(RuntimeEnvironment.getApplication());
    }

    // ==================== Tool definition tests ====================

    @Test
    public void getName_returnsListApps() {
        assertEquals("list_apps", tool.getName());
    }

    @Test
    public void requiresApproval_returnsFalse() {
        assertFalse("list_apps is read-only and should not require approval",
                tool.requiresApproval());
    }

    @Test
    public void getDefinition_returnsCorrectDefinition() {
        ToolDefinition definition = tool.getDefinition();
        assertEquals("list_apps", definition.getFunction().getName());
        assertNotNull(definition.getFunction().getDescription());
        assertNotNull(definition.getFunction().getParameters());
        assertTrue(definition.getFunction().getDescription().toLowerCase().contains("installed"));
    }

    // ==================== execute() tests ====================

    @Test
    public void execute_defaultsToShowAllApps() {
        ToolResult result = tool.execute(new JsonObject());

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        assertTrue("Should have apps list", parsed.has("apps"));
        JsonArray apps = parsed.getAsJsonArray("apps");
        assertNotNull("Apps list should not be null", apps);
        assertTrue("Should have at least some apps installed", apps.size() > 0);
    }

    @Test
    public void execute_showSystemFalseExcludesSystemApps() {
        JsonObject args = new JsonObject();
        args.addProperty("show_system", false);

        ToolResult result = tool.execute(args);

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        JsonArray apps = parsed.getAsJsonArray("apps");
        // No system app flag should be true in the results
        for (int i = 0; i < apps.size(); i++) {
            JsonObject app = apps.get(i).getAsJsonObject();
            if (app.has("is_system_app")) {
                assertFalse("Should not have system apps when show_system=false",
                        app.get("is_system_app").getAsBoolean());
            }
        }
    }

    @Test
    public void execute_correctlyCountsUserAndSystemApps() {
        JsonObject args = new JsonObject();
        args.addProperty("show_system", false);

        ToolResult result = tool.execute(args);

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        int userApps = parsed.get("user_apps").getAsInt();
        int total = parsed.get("total").getAsInt();
        assertEquals("Total should match user apps when system apps excluded", userApps, total);
    }

    @Test
    public void execute_nullArgumentsDefaultsToShowAll() {
        ToolResult result = tool.execute(null);

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        JsonArray apps = parsed.getAsJsonArray("apps");
        assertNotNull("Apps list should not be null", apps);
    }

    @Test
    public void execute_noAppsParamDefaultsToShowAll() {
        JsonObject args = new JsonObject();

        ToolResult result = tool.execute(args);

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        JsonArray apps = parsed.getAsJsonArray("apps");
        assertNotNull("Apps list should not be null", apps);
    }

    @Test
    public void execute_showSystemTrueIncludesAllApps() {
        JsonObject args = new JsonObject();
        args.addProperty("show_system", true);

        ToolResult result = tool.execute(args);

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        int total = parsed.get("total").getAsInt();
        int userApps = parsed.get("user_apps").getAsInt();
        int systemApps = parsed.get("system_apps").getAsInt();
        assertEquals("Total should equal user + system apps", userApps + systemApps, total);
    }

    @Test
    public void execute_resultHasRequiredFields() {
        ToolResult result = tool.execute(new JsonObject());

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        assertTrue("Should have total field", parsed.has("total"));
        assertTrue("Should have user_apps field", parsed.has("user_apps"));
        assertTrue("Should have system_apps field", parsed.has("system_apps"));
        assertTrue("Should have apps field", parsed.has("apps"));
    }

    @Test
    public void execute_eachAppHasRequiredFields() {
        ToolResult result = tool.execute(new JsonObject());

        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        JsonArray apps = parsed.getAsJsonArray("apps");

        for (int i = 0; i < apps.size(); i++) {
            JsonObject app = apps.get(i).getAsJsonObject();
            assertTrue("App " + i + " should have name", app.has("name"));
            assertTrue("App " + i + " should have package_name", app.has("package_name"));
            assertTrue("App " + i + " should have is_system_app", app.has("is_system_app"));
        }
    }
}
