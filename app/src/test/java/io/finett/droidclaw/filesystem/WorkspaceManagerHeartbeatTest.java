package io.finett.droidclaw.filesystem;

import android.content.Context;
import android.content.res.AssetManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkspaceManager HEARTBEAT.md functionality.
 * Tests heartbeat file creation and path retrieval.
 */
@RunWith(MockitoJUnitRunner.class)
public class WorkspaceManagerHeartbeatTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private Context mockContext;

    @Mock
    private AssetManager mockAssets;

    private WorkspaceManager workspaceManager;
    private File filesDir;

    @Before
    public void setUp() throws IOException {
        filesDir = tempFolder.newFolder("app_files");
        when(mockContext.getFilesDir()).thenReturn(filesDir);
        when(mockContext.getAssets()).thenReturn(mockAssets);

        // Mock asset loading for skills - throw IOException to simulate missing assets in unit test
        when(mockAssets.open(anyString())).thenThrow(new IOException("Asset not available in unit test"));

        workspaceManager = new WorkspaceManager(mockContext);
    }

    // ========== HEARTBEAT FILE CREATION TESTS ==========

    @Test
    public void testCreateDefaultHeartbeatFile() throws Exception {
        workspaceManager.initialize();

        boolean created = workspaceManager.createDefaultHeartbeatFile();

        assertTrue(created);

        // Verify file exists
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        assertTrue(heartbeatFile.exists());
    }

    @Test
    public void testCreateDefaultHeartbeatFileReturnsFalseIfExists() throws Exception {
        workspaceManager.initialize();

        // Create the file first
        workspaceManager.createDefaultHeartbeatFile();

        // Try to create again
        boolean created = workspaceManager.createDefaultHeartbeatFile();

        assertFalse(created); // Should return false since it already exists
    }

    @Test
    public void testHeartbeatFileContainsExpectedContent() throws Exception {
        workspaceManager.initialize();
        workspaceManager.createDefaultHeartbeatFile();

        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        // Read and verify content
        java.util.Scanner scanner = new java.util.Scanner(heartbeatFile);
        StringBuilder content = new StringBuilder();
        while (scanner.hasNextLine()) {
            content.append(scanner.nextLine()).append("\n");
        }
        scanner.close();

        String contentStr = content.toString();
        assertTrue(contentStr.contains("Heartbeat Checklist"));
        assertTrue(contentStr.contains("HEARTBEAT_OK"));
        assertTrue(contentStr.contains("cron jobs"));
    }

    @Test
    public void testInitializeCreatesHeartbeatFile() throws Exception {
        workspaceManager.initializeWithSkills();

        // Verify heartbeat file was created during initialization
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        assertTrue(heartbeatFile.exists());
    }

    // ========== HEARTBEAT FILE PATH TESTS ==========

    @Test
    public void testGetHeartbeatFilePath() {
        String path = WorkspaceManager.getHeartbeatFilePath();

        assertNotNull(path);
        assertEquals(".agent/HEARTBEAT.md", path);
    }

    @Test
    public void testHeartbeatFilePathIsStatic() {
        // Should be callable without instance
        String path = WorkspaceManager.getHeartbeatFilePath();
        assertEquals(".agent/HEARTBEAT.md", path);
    }

    // ========== HEARTBEAT FILE INTEGRATION TESTS ==========

    @Test
    public void testHeartbeatFileInAgentDirectory() throws Exception {
        workspaceManager.initializeWithSkills();

        File agentDir = workspaceManager.getAgentDirectory();
        File heartbeatFile = new File(agentDir, "HEARTBEAT.md");

        assertTrue(heartbeatFile.exists());
    }

    @Test
    public void testHeartbeatFileNotOverwrittenOnReinitialize() throws Exception {
        workspaceManager.initialize();

        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        long originalModified = heartbeatFile.lastModified();

        // Wait a bit and reinitialize
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        workspaceManager.initialize();

        // File should not been modified
        assertEquals(originalModified, heartbeatFile.lastModified());
    }
}
