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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WorkspaceManagerTest {
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
        when(mockAssets.open(anyString())).thenThrow(new IOException("Asset not available in unit test"));
        workspaceManager = new WorkspaceManager(mockContext);
    }

    @Test
    public void testInitialize() throws Exception {
        assertTrue(workspaceManager.initialize());

        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        assertTrue(workspaceRoot.exists());
        assertTrue(workspaceRoot.isDirectory());
    }

    @Test
    public void testInitializeCreatesStandardDirectories() throws Exception {
        workspaceManager.initialize();

        assertTrue(workspaceManager.getHomeDirectory().exists());
        assertTrue(workspaceManager.getDocumentsDirectory().exists());
        assertTrue(workspaceManager.getScriptsDirectory().exists());
        assertTrue(workspaceManager.getTempDirectory().exists());
        assertTrue(workspaceManager.getAgentDirectory().exists());
        assertTrue(workspaceManager.getSkillsDirectory().exists());
        assertTrue(workspaceManager.getMemoryDirectory().exists());
        assertTrue(workspaceManager.getConfigDirectory().exists());
    }

    @Test
    public void testInitializeIdempotent() throws Exception {
        assertTrue(workspaceManager.initialize());
        assertTrue(workspaceManager.initialize());

        assertTrue(workspaceManager.getWorkspaceRoot().exists());
    }

    @Test
    public void testGetDirectory() throws Exception {
        workspaceManager.initialize();

        File dir = workspaceManager.getDirectory("home/documents");
        assertNotNull(dir);
        assertTrue(dir.getCanonicalPath().contains("documents"));
    }

    @Test(expected = SecurityException.class)
    public void testGetDirectoryWithPathTraversal() throws Exception {
        workspaceManager.initialize();
        workspaceManager.getDirectory("../../etc/passwd");
    }

    @Test
    public void testClearTempDirectory() throws Exception {
        workspaceManager.initialize();

        File tempDir = workspaceManager.getTempDirectory();
        File tempFile = new File(tempDir, "temp_file.txt");
        tempFile.createNewFile();

        assertTrue(tempFile.exists());

        assertTrue(workspaceManager.clearTempDirectory());

        assertTrue(tempDir.exists());
        assertFalse(tempFile.exists());
    }

    @Test
    public void testClearTempDirectoryWhenEmpty() throws Exception {
        workspaceManager.initialize();

        assertTrue(workspaceManager.clearTempDirectory());
        assertTrue(workspaceManager.getTempDirectory().exists());
    }

    @Test
    public void testClearTempDirectoryWhenNotExists() {
        assertTrue(workspaceManager.clearTempDirectory());
    }

    @Test
    public void testGetStats() throws Exception {
        workspaceManager.initialize();

        File homeDir = workspaceManager.getHomeDirectory();
        File testFile = new File(homeDir, "test.txt");
        testFile.createNewFile();

        WorkspaceManager.WorkspaceStats stats = workspaceManager.getStats();
        
        assertNotNull(stats);
        assertTrue(stats.getFileCount() >= 1);
        assertTrue(stats.getDirectoryCount() >= 1);
        assertTrue(stats.getTotalSize() >= 0);
        assertNotNull(stats.getFormattedSize());
    }

    @Test
    public void testWorkspaceStatsFormatting() throws Exception {
        workspaceManager.initialize();

        WorkspaceManager.WorkspaceStats stats = workspaceManager.getStats();
        
        String formatted = stats.getFormattedSize();
        assertNotNull(formatted);
        assertTrue(formatted.contains("B") || formatted.contains("KB") ||
                   formatted.contains("MB") || formatted.contains("GB"));
    }

    @Test
    public void testGetPathValidator() throws Exception {
        PathValidator validator = workspaceManager.getPathValidator();
        assertNotNull(validator);
        assertEquals(workspaceManager.getWorkspaceRoot(), validator.getWorkspaceRoot());
    }

    @Test
    public void testDirectoryGetters() throws Exception {
        workspaceManager.initialize();

        assertNotNull(workspaceManager.getHomeDirectory());
        assertNotNull(workspaceManager.getDocumentsDirectory());
        assertNotNull(workspaceManager.getScriptsDirectory());
        assertNotNull(workspaceManager.getTempDirectory());
        assertNotNull(workspaceManager.getAgentDirectory());
        assertNotNull(workspaceManager.getSkillsDirectory());
        assertNotNull(workspaceManager.getMemoryDirectory());
        assertNotNull(workspaceManager.getConfigDirectory());

        File workspace = workspaceManager.getWorkspaceRoot();
        String workspacePath = workspace.getCanonicalPath();

        assertTrue(workspaceManager.getHomeDirectory().getCanonicalPath().startsWith(workspacePath));
        assertTrue(workspaceManager.getAgentDirectory().getCanonicalPath().startsWith(workspacePath));
    }

    @Test
    public void testWorkspaceStatsWithLargeFile() throws Exception {
        workspaceManager.initialize();

        File testFile = new File(workspaceManager.getHomeDirectory(), "large.txt");
        testFile.createNewFile();
        java.io.FileWriter writer = new java.io.FileWriter(testFile);
        for (int i = 0; i < 1000; i++) {
            writer.write("This is line " + i + "\n");
        }
        writer.close();

        WorkspaceManager.WorkspaceStats stats = workspaceManager.getStats();
        
        assertTrue(stats.getTotalSize() > 1000);
        assertTrue(stats.getFileCount() >= 1);
    }

    @Test
    public void testWorkspaceStatsFormattedSizes() {
        WorkspaceManager.WorkspaceStats smallStats = new WorkspaceManager.WorkspaceStats(
            new File(filesDir, "nonexistent")
        );

        String formatted = smallStats.getFormattedSize();
        assertNotNull(formatted);
        assertTrue(formatted.endsWith(" B") || formatted.contains("KB") ||
                   formatted.contains("MB") || formatted.contains("GB"));
    }

    @Test
    public void testInitializeWithSkills() throws Exception {
        assertTrue(workspaceManager.initializeWithSkills());

        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        assertTrue(workspaceRoot.exists());
        assertTrue(workspaceRoot.isDirectory());

        File skillsDir = workspaceManager.getSkillsDirectory();
        assertTrue(skillsDir.exists());
    }

    @Test
    public void testInitializeWithSkillsIdempotent() throws Exception {
        assertTrue(workspaceManager.initialize());
        assertTrue(workspaceManager.initializeWithSkills());

        assertTrue(workspaceManager.getWorkspaceRoot().exists());
    }

    // ==================== temp cleanup: nested trees ====================

    @Test
    public void testClearTempDirectory_withNestedSubdirectories() throws Exception {
        workspaceManager.initialize();

        File tempDir = workspaceManager.getTempDirectory();
        File nested = new File(tempDir, "level1/level2");
        assertTrue(nested.mkdirs());
        new File(tempDir, "top.txt").createNewFile();
        new File(nested, "deep.txt").createNewFile();

        assertTrue(workspaceManager.clearTempDirectory());

        assertTrue(tempDir.exists());
        assertEquals(0, tempDir.listFiles().length);
    }

    @Test
    public void testClearTempDirectory_mixedFilesAndDirs() throws Exception {
        workspaceManager.initialize();

        File tempDir = workspaceManager.getTempDirectory();
        new File(tempDir, "a.txt").createNewFile();
        File subDir = new File(tempDir, "subdir");
        assertTrue(subDir.mkdir());
        new File(subDir, "b.txt").createNewFile();

        assertTrue(workspaceManager.clearTempDirectory());
        assertTrue(tempDir.exists());
        assertFalse(new File(tempDir, "a.txt").exists());
        assertFalse(subDir.exists());
    }

    // ==================== stats: formatting boundaries ====================

    @Test
    public void testWorkspaceStats_kilobyteRange() throws Exception {
        workspaceManager.initialize();

        File testFile = new File(workspaceManager.getHomeDirectory(), "kb.bin");
        java.io.FileWriter writer = new java.io.FileWriter(testFile);
        char[] chunk = new char[1024];
        java.util.Arrays.fill(chunk, 'x');
        writer.write(chunk, 0, 1024);
        writer.write(chunk, 0, 1024); // 2 KB total
        writer.close();

        WorkspaceManager.WorkspaceStats stats = workspaceManager.getStats();
        assertTrue(stats.getTotalSize() >= 2048);
        assertTrue("expected KB formatting, got: " + stats.getFormattedSize(),
                stats.getFormattedSize().endsWith("KB"));
    }

    @Test
    public void testWorkspaceStats_emptyWorkspace_bytesFormatting() throws Exception {
        workspaceManager.initialize();

        WorkspaceManager.WorkspaceStats stats = workspaceManager.getStats();
        assertEquals(0, stats.getTotalSize());
        assertEquals("0 B", stats.getFormattedSize());
    }

    // ==================== static path getters ====================

    @Test
    public void testStaticPathGetters() {
        assertEquals(".agent/soul.md", WorkspaceManager.getSoulFilePath());
        assertEquals(".agent/user.md", WorkspaceManager.getUserFilePath());
        assertEquals(".agent/HEARTBEAT.md", WorkspaceManager.getHeartbeatFilePath());
    }

    @Test
    public void testGetHeartbeatFile_underAgentDir() throws Exception {
        workspaceManager.initialize();

        File heartbeat = workspaceManager.getHeartbeatFile();
        assertNotNull(heartbeat);
        assertTrue(heartbeat.getPath().endsWith(".agent/HEARTBEAT.md"));
        assertTrue(heartbeat.getCanonicalPath().startsWith(
                workspaceManager.getWorkspaceRoot().getCanonicalPath()));
    }
}
