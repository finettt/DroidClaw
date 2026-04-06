package io.finett.droidclaw.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for TaskSecurityConfig.
 * Tests security configuration, sandbox settings, resource limits, and emergency controls.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskSecurityConfigTest {

    private TaskSecurityConfig config;

    @Before
    public void setUp() {
        config = new TaskSecurityConfig();
    }

    // ========== CONSTRUCTOR AND DEFAULTS TESTS ==========

    @Test
    public void testDefaultConstructor() {
        assertNotNull(config);
    }

    @Test
    public void testDefaultSandboxSettings() {
        assertTrue(config.isRestrictToWorkspace());
        assertFalse(config.isBlockDestructiveOps());
        assertFalse(config.isBlockShellAccess());
        assertFalse(config.isBlockPythonAccess());
        assertNotNull(config.getBlockedTools());
        assertEquals(0, config.getBlockedTools().size());
    }

    @Test
    public void testDefaultResourceLimits() {
        assertEquals(600, config.getMaxExecutionTimeSeconds());
        assertEquals(10, config.getMaxIterations());
        assertEquals(20, config.getMaxToolCalls());
        assertEquals(50000, config.getMaxTokenUsage());
        assertEquals(5000, config.getMaxMemoryContextSize());
    }

    @Test
    public void testDefaultEmergencySettings() {
        assertFalse(config.isEmergencyDisable());
        assertEquals("", config.getEmergencyDisableReason());
        assertEquals(0, config.getEmergencyDisableTimestamp());
        assertFalse(config.isEmergencyActive());
    }

    // ========== SANDBOX SETTINGS TESTS ==========

    @Test
    public void testSetRestrictToWorkspace() {
        config.setRestrictToWorkspace(false);
        assertFalse(config.isRestrictToWorkspace());

        config.setRestrictToWorkspace(true);
        assertTrue(config.isRestrictToWorkspace());
    }

    @Test
    public void testSetBlockDestructiveOps() {
        config.setBlockDestructiveOps(true);
        assertTrue(config.isBlockDestructiveOps());

        config.setBlockDestructiveOps(false);
        assertFalse(config.isBlockDestructiveOps());
    }

    @Test
    public void testSetBlockShellAccess() {
        config.setBlockShellAccess(true);
        assertTrue(config.isBlockShellAccess());
        assertFalse(config.isShellAllowed());

        config.setBlockShellAccess(false);
        assertFalse(config.isBlockShellAccess());
        assertTrue(config.isShellAllowed());
    }

    @Test
    public void testSetBlockPythonAccess() {
        config.setBlockPythonAccess(true);
        assertTrue(config.isBlockPythonAccess());
        assertFalse(config.isPythonAllowed());

        config.setBlockPythonAccess(false);
        assertFalse(config.isBlockPythonAccess());
        assertTrue(config.isPythonAllowed());
    }

    // ========== BLOCKED TOOLS TESTS ==========

    @Test
    public void testAddBlockedTool() {
        assertEquals(0, config.getBlockedTools().size());

        config.addBlockedTool("shell_exec");
        assertEquals(1, config.getBlockedTools().size());
        assertTrue(config.isToolBlocked("shell_exec"));
    }

    @Test
    public void testRemoveBlockedTool() {
        config.addBlockedTool("shell_exec");
        config.addBlockedTool("python_exec");
        assertEquals(2, config.getBlockedTools().size());

        config.removeBlockedTool("shell_exec");
        assertEquals(1, config.getBlockedTools().size());
        assertFalse(config.isToolBlocked("shell_exec"));
        assertTrue(config.isToolBlocked("python_exec"));
    }

    @Test
    public void testAddNullToolName() {
        config.addBlockedTool(null);
        assertEquals(0, config.getBlockedTools().size());
    }

    @Test
    public void testRemoveNullToolName() {
        config.addBlockedTool("shell_exec");
        config.removeBlockedTool(null);
        assertEquals(1, config.getBlockedTools().size());
    }

    @Test
    public void testSetBlockedTools() {
        Set<String> tools = new HashSet<>();
        tools.add("shell_exec");
        tools.add("python_exec");

        config.setBlockedTools(tools);

        assertEquals(2, config.getBlockedTools().size());
        assertTrue(config.isToolBlocked("shell_exec"));
        assertTrue(config.isToolBlocked("python_exec"));
    }

    @Test
    public void testSetBlockedToolsWithNull() {
        config.addBlockedTool("shell_exec");
        config.setBlockedTools(null);

        assertNotNull(config.getBlockedTools());
        assertEquals(0, config.getBlockedTools().size());
    }

    @Test
    public void testIsToolBlockedNonExistent() {
        assertFalse(config.isToolBlocked("non_existent_tool"));
    }

    // ========== RESOURCE LIMITS TESTS ==========

    @Test
    public void testSetMaxExecutionTimeSeconds() {
        config.setMaxExecutionTimeSeconds(300);
        assertEquals(300, config.getMaxExecutionTimeSeconds());
    }

    @Test
    public void testGetMaxExecutionTimeMs() {
        config.setMaxExecutionTimeSeconds(600);
        assertEquals(600000L, config.getMaxExecutionTimeMs());
    }

    @Test
    public void testSetMaxIterations() {
        config.setMaxIterations(5);
        assertEquals(5, config.getMaxIterations());
    }

    @Test
    public void testSetMaxToolCalls() {
        config.setMaxToolCalls(50);
        assertEquals(50, config.getMaxToolCalls());
    }

    @Test
    public void testSetMaxTokenUsage() {
        config.setMaxTokenUsage(100000);
        assertEquals(100000, config.getMaxTokenUsage());
    }

    @Test
    public void testSetMaxMemoryContextSize() {
        config.setMaxMemoryContextSize(10000);
        assertEquals(10000, config.getMaxMemoryContextSize());
    }

    // ========== EMERGENCY CONTROLS TESTS ==========

    @Test
    public void testActivateEmergencyDisable() {
        assertFalse(config.isEmergencyActive());

        long beforeTime = System.currentTimeMillis();
        config.activateEmergencyDisable("Security breach detected");
        long afterTime = System.currentTimeMillis();

        assertTrue(config.isEmergencyActive());
        assertEquals("Security breach detected", config.getEmergencyDisableReason());
        assertTrue(config.getEmergencyDisableTimestamp() >= beforeTime);
        assertTrue(config.getEmergencyDisableTimestamp() <= afterTime);
    }

    @Test
    public void testDeactivateEmergencyDisable() {
        config.activateEmergencyDisable("Test reason");
        assertTrue(config.isEmergencyActive());

        config.deactivateEmergencyDisable();

        assertFalse(config.isEmergencyActive());
        assertEquals("", config.getEmergencyDisableReason());
        assertEquals(0, config.getEmergencyDisableTimestamp());
    }

    @Test
    public void testSetEmergencyDisableDirectly() {
        config.setEmergencyDisable(true);
        assertTrue(config.isEmergencyDisable());
        assertTrue(config.getEmergencyDisableTimestamp() > 0);

        config.setEmergencyDisable(false);
        assertFalse(config.isEmergencyDisable());
        assertEquals(0, config.getEmergencyDisableTimestamp());
        assertEquals("", config.getEmergencyDisableReason());
    }

    @Test
    public void testSetEmergencyDisableReason() {
        config.setEmergencyDisableReason("Custom reason");
        assertEquals("Custom reason", config.getEmergencyDisableReason());
    }

    @Test
    public void testEmergencyDisableTimestampOnActivate() {
        long beforeTime = System.currentTimeMillis();
        config.activateEmergencyDisable("Test");
        long afterTime = System.currentTimeMillis();

        long timestamp = config.getEmergencyDisableTimestamp();
        assertTrue(timestamp >= beforeTime);
        assertTrue(timestamp <= afterTime);
    }

    // ========== HELPER METHODS TESTS ==========

    @Test
    public void testIsShellAllowedWhenNotBlocked() {
        config.setBlockShellAccess(false);
        assertTrue(config.isShellAllowed());
    }

    @Test
    public void testIsShellAllowedWhenBlocked() {
        config.setBlockShellAccess(true);
        assertFalse(config.isShellAllowed());
    }

    @Test
    public void testIsPythonAllowedWhenNotBlocked() {
        config.setBlockPythonAccess(false);
        assertTrue(config.isPythonAllowed());
    }

    @Test
    public void testIsPythonAllowedWhenBlocked() {
        config.setBlockPythonAccess(true);
        assertFalse(config.isPythonAllowed());
    }

    @Test
    public void testIsToolBlockedWithEmptySet() {
        config.setBlockedTools(new HashSet<>());
        assertFalse(config.isToolBlocked("any_tool"));
    }

    // ========== COMPREHENSIVE CONFIG TESTS ==========

    @Test
    public void testFullConfigurationSetup() {
        // Configure a restrictive security setup
        config.setRestrictToWorkspace(true);
        config.setBlockDestructiveOps(true);
        config.setBlockShellAccess(true);
        config.setBlockPythonAccess(false);
        config.addBlockedTool("file_delete");
        config.addBlockedTool("network_access");
        config.setMaxExecutionTimeSeconds(300);
        config.setMaxIterations(5);
        config.setMaxToolCalls(10);
        config.setMaxTokenUsage(20000);
        config.setMaxMemoryContextSize(2000);

        // Verify all settings
        assertTrue(config.isRestrictToWorkspace());
        assertTrue(config.isBlockDestructiveOps());
        assertTrue(config.isBlockShellAccess());
        assertFalse(config.isBlockPythonAccess());
        assertTrue(config.isToolBlocked("file_delete"));
        assertTrue(config.isToolBlocked("network_access"));
        assertEquals(300, config.getMaxExecutionTimeSeconds());
        assertEquals(5, config.getMaxIterations());
        assertEquals(10, config.getMaxToolCalls());
        assertEquals(20000, config.getMaxTokenUsage());
        assertEquals(2000, config.getMaxMemoryContextSize());
    }

    @Test
    public void testEmergencyControlWorkflow() {
        // Normal operation
        assertFalse(config.isEmergencyActive());
        assertTrue(config.isShellAllowed());

        // Emergency situation
        config.activateEmergencyDisable("Critical vulnerability");
        assertTrue(config.isEmergencyActive());
        assertEquals("Critical vulnerability", config.getEmergencyDisableReason());

        // Resolution
        config.deactivateEmergencyDisable();
        assertFalse(config.isEmergencyActive());
        assertEquals("", config.getEmergencyDisableReason());
    }

    // ========== EDGE CASES TESTS ==========

    @Test
    public void testMaxExecutionTimeZero() {
        config.setMaxExecutionTimeSeconds(0);
        assertEquals(0, config.getMaxExecutionTimeSeconds());
        assertEquals(0L, config.getMaxExecutionTimeMs());
    }

    @Test
    public void testMaxExecutionTimeVeryLarge() {
        config.setMaxExecutionTimeSeconds(86400); // 24 hours
        assertEquals(86400, config.getMaxExecutionTimeSeconds());
        assertEquals(86400000L, config.getMaxExecutionTimeMs());
    }

    @Test
    public void testDuplicateToolBlocking() {
        config.addBlockedTool("shell_exec");
        config.addBlockedTool("shell_exec");
        config.addBlockedTool("shell_exec");

        assertEquals(1, config.getBlockedTools().size());
    }

    @Test
    public void testActivateEmergencyDisableMultipleTimes() {
        long firstTime = System.currentTimeMillis();
        config.activateEmergencyDisable("First reason");

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long secondTime = System.currentTimeMillis();
        config.activateEmergencyDisable("Second reason");

        assertEquals("Second reason", config.getEmergencyDisableReason());
        assertTrue(config.getEmergencyDisableTimestamp() >= secondTime);
    }

    @Test
    public void testDeactivateWhenNotActive() {
        // Should not throw any exception
        config.deactivateEmergencyDisable();

        assertFalse(config.isEmergencyActive());
    }
}
