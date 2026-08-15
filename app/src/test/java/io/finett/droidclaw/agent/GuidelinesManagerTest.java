package io.finett.droidclaw.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Unit tests for {@link GuidelinesManager}: load/save round-trip, size cap,
 * empty-content rejection and backup handling.
 */
@RunWith(RobolectricTestRunner.class)
public class GuidelinesManagerTest {

    private WorkspaceManager workspaceManager;
    private GuidelinesManager guidelinesManager;

    @Before
    public void setUp() throws Exception {
        workspaceManager = new WorkspaceManager(RuntimeEnvironment.getApplication());
        workspaceManager.initialize();
        guidelinesManager = new GuidelinesManager(workspaceManager);
    }

    @Test
    public void loadGuidelines_missingFile_returnsEmpty() {
        assertEquals("", guidelinesManager.loadGuidelines());
    }

    @Test
    public void saveAndLoad_roundTrip() {
        String content = "# GUIDELINES.md\n\n## Workflows\n\n- Always use UTC.\n";
        assertTrue(guidelinesManager.saveGuidelines(content));

        String loaded = guidelinesManager.loadGuidelines();
        assertEquals(content.trim(), loaded.trim());
    }

    @Test
    public void saveGuidelines_nullOrEmpty_rejected() {
        assertFalse(guidelinesManager.saveGuidelines(null));
        assertFalse(guidelinesManager.saveGuidelines(""));
        assertFalse(guidelinesManager.saveGuidelines("   \n  "));
    }

    @Test
    public void saveGuidelines_overSizeCap_rejectedAndFileUntouched() {
        String original = "# GUIDELINES.md\n\n- small entry\n";
        assertTrue(guidelinesManager.saveGuidelines(original));

        StringBuilder oversized = new StringBuilder();
        while (oversized.length() <= GuidelinesManager.MAX_GUIDELINES_SIZE) {
            oversized.append("- another workflow entry to fill space\n");
        }
        assertFalse(guidelinesManager.saveGuidelines(oversized.toString()));

        assertEquals(original.trim(), guidelinesManager.loadGuidelines().trim());
    }

    @Test
    public void saveGuidelines_keepsBackupOfPreviousVersion() throws Exception {
        assertTrue(guidelinesManager.saveGuidelines("version one"));
        assertTrue(guidelinesManager.saveGuidelines("version two"));

        File backup = new File(guidelinesManager.getGuidelinesFile().getParentFile(),
                guidelinesManager.getGuidelinesFile().getName() + ".bak");
        assertTrue(backup.exists());
        assertEquals("version one", readFile(backup).trim());
        assertEquals("version two", guidelinesManager.loadGuidelines().trim());
    }

    private String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
