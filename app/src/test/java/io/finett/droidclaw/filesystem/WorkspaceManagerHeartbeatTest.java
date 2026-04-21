package io.finett.droidclaw.filesystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class WorkspaceManagerHeartbeatTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private WorkspaceManager workspaceManager;
    private File workspaceRoot;

    @Before
    public void setUp() throws IOException {
        workspaceRoot = tempFolder.newFolder("workspace");
    }

    @Test
    public void getHeartbeatFilePath_returnsCorrectPath() {
        String path = WorkspaceManager.getHeartbeatFilePath();
        assertEquals("Should return .agent/HEARTBEAT.md", ".agent/HEARTBEAT.md", path);
    }

    @Test
    public void getHeartbeatFilePath_isConsistent() {
        String path1 = WorkspaceManager.getHeartbeatFilePath();
        String path2 = WorkspaceManager.getHeartbeatFilePath();
        assertEquals("Should always return same path", path1, path2);
    }

    @Test
    public void heartbeatFile_structure_matchesExpected() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        String templateContent = "# System Heartbeat Checklist\n\n" +
                "Perform a comprehensive system health check.\n\n" +
                "## Checklist\n\n" +
                "- [ ] Review recent memories\n" +
                "- [ ] Check workspace for incomplete tasks\n" +
                "- [ ] Verify critical files are backed up\n" +
                "- [ ] Scan for urgent messages\n" +
                "- [ ] Assess pending actions\n\n" +
                "## Response Guidelines\n\n" +
                "If **all items are normal**, respond with:\n" +
                "```json\n{\"HEARTBEAT_OK\": true}\n```\n";

        java.nio.file.Files.write(heartbeatFile.toPath(), templateContent.getBytes());

        assertTrue("File should exist", heartbeatFile.exists());
        assertTrue("File should be readable", heartbeatFile.canRead());

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        String contentStr = content.toString();
        assertTrue("Should contain title", contentStr.contains("Heartbeat Checklist"));
        assertTrue("Should contain checklist items", contentStr.contains("[ ]"));
        assertTrue("Should mention HEARTBEAT_OK", contentStr.contains("HEARTBEAT_OK"));
        assertTrue("Should have response guidelines", contentStr.contains("Response Guidelines"));
    }

    @Test
    public void workspace_initialization_createsAgentDirectory() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        assertFalse("Agent dir should not exist before init", agentDir.exists());

        agentDir.mkdirs();
        new File(workspaceRoot, ".agent/memory").mkdirs();
        new File(workspaceRoot, ".agent/skills").mkdirs();
        new File(workspaceRoot, ".agent/config").mkdirs();

        assertTrue("Agent dir should exist after init", agentDir.exists());
        assertTrue("Memory dir should exist", new File(workspaceRoot, ".agent/memory").exists());
        assertTrue("Skills dir should exist", new File(workspaceRoot, ".agent/skills").exists());
    }

    @Test
    public void workspace_initialization_heartbeatTemplateCreated() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");
        assertFalse("Heartbeat file should not exist before creation", heartbeatFile.exists());

        java.nio.file.Files.write(heartbeatFile.toPath(), "# Heartbeat\n".getBytes());

        assertTrue("Heartbeat file should exist after creation", heartbeatFile.exists());
        assertNotNull("Heartbeat file should have content", heartbeatFile.length());
        assertTrue("Heartbeat file should be > 0 bytes", heartbeatFile.length() > 0);
    }

    @Test
    public void heartbeatFile_readWriteCycle() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        String originalContent = "# Test Heartbeat\n\n- [x] Check A\n- [x] Check B\n\n{\"HEARTBEAT_OK\": true}";
        java.nio.file.Files.write(heartbeatFile.toPath(), originalContent.getBytes());

        StringBuilder readContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                readContent.append(line).append("\n");
            }
        }

        String readStr = readContent.toString();
        assertTrue("Should contain title", readStr.contains("Test Heartbeat"));
        assertTrue("Should contain checks", readStr.contains("[x]"));
        assertTrue("Should contain HEARTBEAT_OK JSON", readStr.contains("\"HEARTBEAT_OK\": true"));
    }

    @Test
    public void heartbeatFile_modifyAndRead() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        java.nio.file.Files.write(heartbeatFile.toPath(), "# Initial\n".getBytes());

        String modifiedContent = "# Modified\n\n- [x] All checks passed\n\n{\"HEARTBEAT_OK\": true}";
        java.nio.file.Files.write(heartbeatFile.toPath(), modifiedContent.getBytes());

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        assertTrue("Should contain modified title", content.toString().contains("Modified"));
        assertFalse("Should not contain initial title", content.toString().contains("Initial"));
    }

    @Test
    public void heartbeatFile_deleteAndRecreate() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        java.nio.file.Files.write(heartbeatFile.toPath(), "# Heartbeat\n".getBytes());
        assertTrue("File should exist", heartbeatFile.exists());

        heartbeatFile.delete();
        assertFalse("File should not exist after delete", heartbeatFile.exists());

        java.nio.file.Files.write(heartbeatFile.toPath(), "# New Heartbeat\n".getBytes());
        assertTrue("File should exist after recreate", heartbeatFile.exists());

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        assertTrue("Should contain new title", content.toString().contains("New Heartbeat"));
    }

    @Test
    public void heartbeatFile_emptyFile() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        heartbeatFile.createNewFile();

        assertTrue("File should exist", heartbeatFile.exists());
        assertEquals("File should be empty", 0, heartbeatFile.length());
    }

    @Test
    public void heartbeatFile_largeContent() throws IOException {
        File agentDir = new File(workspaceRoot, ".agent");
        agentDir.mkdirs();

        File heartbeatFile = new File(workspaceRoot, ".agent/HEARTBEAT.md");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("- [ ] Check item ").append(i).append("\n");
        }
        sb.append("\n{\"HEARTBEAT_OK\": true} if all normal");

        java.nio.file.Files.write(heartbeatFile.toPath(), sb.toString().getBytes());

        assertTrue("File should exist", heartbeatFile.exists());
        assertTrue("File should be > 10KB", heartbeatFile.length() > 10000);

        StringBuilder readContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(heartbeatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                readContent.append(line).append("\n");
            }
        }

        assertTrue("Should contain all items", readContent.toString().contains("Check item 500"));
        assertTrue("Should contain HEARTBEAT_OK", readContent.toString().contains("HEARTBEAT_OK"));
    }
}
