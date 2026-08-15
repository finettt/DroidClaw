package io.finett.droidclaw.agent;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Instrumented tests for {@link GuidelinesManager} and the GUIDELINES.md
 * lifecycle on a real device: asset creation during workspace init,
 * save/load round-trip, size cap, backup handling.
 *
 * <p>The device workspace is shared state, so every test restores the
 * original GUIDELINES.md content afterwards (same approach as
 * {@code IdentityManagerTest}).</p>
 */
@RunWith(AndroidJUnit4.class)
public class GuidelinesManagerInstrumentedTest {

    private WorkspaceManager workspaceManager;
    private GuidelinesManager guidelinesManager;
    private File guidelinesFile;
    private String originalContent;
    private boolean existedBefore;

    @Before
    public void setUp() throws IOException {
        Context context = getApplicationContext();
        workspaceManager = new WorkspaceManager(context);
        workspaceManager.initializeWithSkills();
        guidelinesManager = new GuidelinesManager(workspaceManager);
        guidelinesFile = guidelinesManager.getGuidelinesFile();

        existedBefore = guidelinesFile != null && guidelinesFile.exists();
        originalContent = guidelinesManager.loadGuidelines();
    }

    @After
    public void tearDown() {
        if (guidelinesFile == null) {
            return;
        }
        if (existedBefore) {
            // Restore whatever was there before the test.
            guidelinesManager.saveGuidelines(originalContent);
        } else {
            // File did not exist before — remove test artifacts and recreate
            // the pristine template from assets.
            guidelinesFile.delete();
            File backup = new File(guidelinesFile.getParentFile(),
                    guidelinesFile.getName() + ".bak");
            if (backup.exists()) {
                backup.delete();
            }
            try {
                workspaceManager.initializeWithSkills();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    @Test
    public void initializeWithSkills_createsGuidelinesFile_fromAssetTemplate() {
        assertTrue("GUIDELINES.md should exist after workspace initialization",
                guidelinesFile.exists());

        String content = guidelinesManager.loadGuidelines();
        assertTrue("Guidelines should contain the template header",
                content.contains("GUIDELINES.md"));
        assertTrue("Guidelines template should have a Workflows section",
                content.contains("## Workflows"));
    }

    @Test
    public void guidelinesFile_livesInAgentDirectory() {
        File expected = new File(workspaceManager.getWorkspaceRoot(),
                WorkspaceManager.getGuidelinesFilePath());
        assertEquals("Guidelines file should be .agent/GUIDELINES.md",
                expected.getAbsolutePath(), guidelinesFile.getAbsolutePath());
    }

    @Test
    public void saveAndLoad_roundTrip_onDevice() {
        String content = "# GUIDELINES.md\n\n## Workflows\n\n- Always answer in the user's language.\n";
        assertTrue(guidelinesManager.saveGuidelines(content));
        assertEquals(content.trim(), guidelinesManager.loadGuidelines().trim());
    }

    @Test
    public void save_overSizeCap_rejected_andPreviousContentIntact() {
        String original = "# GUIDELINES.md\n\n- keep me\n";
        assertTrue(guidelinesManager.saveGuidelines(original));

        StringBuilder oversized = new StringBuilder();
        while (oversized.length() <= GuidelinesManager.MAX_GUIDELINES_SIZE) {
            oversized.append("- another workflow entry to fill the file up\n");
        }
        assertFalse("Oversized guidelines must be rejected",
                guidelinesManager.saveGuidelines(oversized.toString()));

        assertEquals("Previous content must survive a rejected write",
                original.trim(), guidelinesManager.loadGuidelines().trim());
    }

    @Test
    public void save_keepsBackupOfPreviousVersion() {
        assertTrue(guidelinesManager.saveGuidelines("version one"));
        assertTrue(guidelinesManager.saveGuidelines("version two"));

        File backup = new File(guidelinesFile.getParentFile(),
                guidelinesFile.getName() + ".bak");
        assertTrue("Backup file should exist after second save", backup.exists());
        assertTrue("Backup should be non-empty", backup.length() > 0);
        assertEquals("version two", guidelinesManager.loadGuidelines().trim());
    }

    @Test
    public void save_nullOrBlank_rejected() {
        String before = guidelinesManager.loadGuidelines();

        assertFalse(guidelinesManager.saveGuidelines(null));
        assertFalse(guidelinesManager.saveGuidelines(""));
        assertFalse(guidelinesManager.saveGuidelines("   \n\t  "));

        assertEquals("Rejected saves must not change the file",
                before.trim(), guidelinesManager.loadGuidelines().trim());
    }

    @Test
    public void loadGuidelines_missingFile_returnsEmpty() {
        assertTrue(guidelinesFile.delete());
        assertEquals("", guidelinesManager.loadGuidelines());
    }
}
