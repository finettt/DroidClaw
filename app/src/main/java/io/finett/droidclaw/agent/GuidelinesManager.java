package io.finett.droidclaw.agent;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Loads and saves {@code .agent/GUIDELINES.md} — the distilled operational
 * guidelines that are injected into every new conversation and updated by the
 * post-response reflection pass ({@link GuidelinesReflector}).
 *
 * <p>Writes are atomic (write to temp file, then rename) and keep a single
 * {@code .bak} copy of the previous version, so a bad reflection output can
 * never leave the agent without working guidelines.</p>
 */
public class GuidelinesManager {
    private static final String TAG = "GuidelinesManager";

    /** Hard cap for the guidelines file to keep per-session context cost bounded. */
    public static final int MAX_GUIDELINES_SIZE = 8 * 1024; // chars

    private final WorkspaceManager workspaceManager;

    public GuidelinesManager(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    public File getGuidelinesFile() {
        return workspaceManager.getGuidelinesFile();
    }

    /**
     * @return current guidelines content, or an empty string when the file is
     *         missing or unreadable.
     */
    public String loadGuidelines() {
        File file = getGuidelinesFile();
        if (file == null || !file.exists()) {
            Log.d(TAG, "GUIDELINES.md does not exist yet");
            return "";
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read GUIDELINES.md", e);
            return "";
        }

        Log.d(TAG, "Loaded guidelines: " + content.length() + " chars");
        return content.toString();
    }

    /**
     * Persist new guidelines content. Validates the size cap, keeps a backup of
     * the previous version and writes atomically.
     *
     * @return true when the file was updated.
     */
    public boolean saveGuidelines(String content) {
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "Refusing to save empty guidelines");
            return false;
        }
        if (content.length() > MAX_GUIDELINES_SIZE) {
            Log.w(TAG, "Refusing to save guidelines: " + content.length()
                    + " chars exceeds cap of " + MAX_GUIDELINES_SIZE);
            return false;
        }

        File file = getGuidelinesFile();
        if (file == null) {
            Log.e(TAG, "Workspace root unavailable, cannot save guidelines");
            return false;
        }

        File backupFile = new File(file.getParentFile(), file.getName() + ".bak");
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");

        try {
            // Keep one backup of the previous version.
            if (file.exists()) {
                copyFile(file, backupFile);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                writer.write(content);
                writer.flush();
            }

            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Could not delete old guidelines file before rename");
            }
            if (!tmpFile.renameTo(file)) {
                Log.e(TAG, "Failed to move temp guidelines file into place");
                return false;
            }

            Log.d(TAG, "Saved guidelines: " + content.length() + " chars");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save guidelines", e);
            if (tmpFile.exists() && !tmpFile.delete()) {
                Log.w(TAG, "Could not clean up temp guidelines file");
            }
            return false;
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
