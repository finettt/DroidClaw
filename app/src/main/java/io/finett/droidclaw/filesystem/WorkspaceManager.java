package io.finett.droidclaw.filesystem;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class WorkspaceManager {
    private static final String TAG = "WorkspaceManager";

    private static final String[] BUILTIN_SKILLS = {
        "skill_creator",
        "web_search",
        "code_analysis",
        "data_processing",
        "task_automation",
        "document_editor"
    };
    private static final String WORKSPACE_DIR = "workspace";
    
    private static final String HOME_DIR = "home";
    private static final String DOCUMENTS_DIR = "home/documents";
    private static final String SCRIPTS_DIR = "home/scripts";
    private static final String NOTES_DIR = "home/notes";
    private static final String TMP_DIR = "tmp";
    private static final String AGENT_DIR = ".agent";
    private static final String MEMORY_DIR = ".agent/memory";
    private static final String SKILLS_DIR = ".agent/skills";
    private static final String CONFIG_DIR = ".agent/config";
    private static final String UPLOADS_DIR = "uploads";
    
    private static final String SOUL_FILE = ".agent/soul.md";
    private static final String USER_FILE = ".agent/user.md";
    private static final String HEARTBEAT_FILE = ".agent/HEARTBEAT.md";

    private final Context context;
    private final File workspaceRoot;
    private final PathValidator pathValidator;

    public WorkspaceManager(Context context) {
        this.context = context;
        this.workspaceRoot = new File(context.getFilesDir(), WORKSPACE_DIR);
        this.pathValidator = new PathValidator(workspaceRoot);
    }

    public boolean initialize() throws IOException {
        if (!workspaceRoot.exists()) {
            if (!workspaceRoot.mkdirs()) {
                throw new IOException("Failed to create workspace root: " + workspaceRoot);
            }
        }

        String[] standardDirs = {
            HOME_DIR,
            DOCUMENTS_DIR,
            SCRIPTS_DIR,
            NOTES_DIR,
            TMP_DIR,
            AGENT_DIR,
            MEMORY_DIR,
            SKILLS_DIR,
            CONFIG_DIR,
            UPLOADS_DIR
        };

        for (String dirPath : standardDirs) {
            File dir = new File(workspaceRoot, dirPath);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new IOException("Failed to create directory: " + dirPath);
                }
            }
        }

        return true;
    }

    public boolean initializeWithSkills() throws IOException {
        initialize();

        try {
            createIdentityFiles();
        } catch (IOException e) {
            Log.w(TAG, "Failed to create identity files", e);
            // Continue even if identity files fail
        }

        for (String skillName : BUILTIN_SKILLS) {
            try {
                copySkill(skillName);
            } catch (IOException e) {
                Log.w(TAG, "Failed to copy skill: " + skillName, e);
            }
        }

        return true;
    }

    private void copySkill(String skillName) throws IOException {
        File skillDir = new File(workspaceRoot, SKILLS_DIR + "/" + skillName);

        if (skillDir.exists()) {
            Log.d(TAG, "Skill already exists: " + skillName);
            return;
        }

        Log.d(TAG, "Copying skill: " + skillName);

        if (!skillDir.mkdirs()) {
            throw new IOException("Failed to create skill directory: " + skillName);
        }

        String skillMdPath = "skills/" + skillName + "/SKILL.md";
        try (InputStream inputStream = context.getAssets().open(skillMdPath)) {
            File skillMdFile = new File(skillDir, "SKILL.md");
            copyInputStreamToFile(inputStream, skillMdFile);
            Log.d(TAG, "Copied SKILL.md for: " + skillName);
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy SKILL.md for skill: " + skillName, e);
        }
    }

    private void copyInputStreamToFile(InputStream inputStream, File outputFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private void createIdentityFiles() throws IOException {
        createIdentityFile(SOUL_FILE, "identity/soul.md");
        createIdentityFile(USER_FILE, "identity/user.md");
        createHeartbeatTemplate();
    }

    private void createIdentityFile(String workspacePath, String assetPath) throws IOException {
        File file = new File(workspaceRoot, workspacePath);

        if (file.exists()) {
            Log.d(TAG, "Identity file already exists: " + workspacePath);
            return;
        }

        Log.d(TAG, "Creating identity file: " + workspacePath);

        try (InputStream inputStream = context.getAssets().open(assetPath)) {
            copyInputStreamToFile(inputStream, file);
            Log.d(TAG, "Created identity file: " + workspacePath);
        }
    }

    private void createHeartbeatTemplate() throws IOException {
        File file = new File(workspaceRoot, HEARTBEAT_FILE);

        if (file.exists()) {
            Log.d(TAG, "Heartbeat template already exists: " + HEARTBEAT_FILE);
            return;
        }

        Log.d(TAG, "Creating heartbeat template: " + HEARTBEAT_FILE);

        try (InputStream inputStream = context.getAssets().open("heartbeat/HEARTBEAT.md")) {
            copyInputStreamToFile(inputStream, file);
            Log.d(TAG, "Created heartbeat template: " + HEARTBEAT_FILE);
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy heartbeat template, using empty file", e);
            file.createNewFile();
        }
    }

    public File getWorkspaceRoot() {
        return workspaceRoot;
    }

    public PathValidator getPathValidator() {
        return pathValidator;
    }

    public File getDirectory(String relativePath) throws IOException {
        return pathValidator.validateAndResolve(relativePath);
    }

    public File getHomeDirectory() {
        return new File(workspaceRoot, HOME_DIR);
    }

    public File getDocumentsDirectory() {
        return new File(workspaceRoot, DOCUMENTS_DIR);
    }

    public File getScriptsDirectory() {
        return new File(workspaceRoot, SCRIPTS_DIR);
    }

    public File getTempDirectory() {
        return new File(workspaceRoot, TMP_DIR);
    }

    public File getAgentDirectory() {
        return new File(workspaceRoot, AGENT_DIR);
    }

    public File getSkillsDirectory() {
        return new File(workspaceRoot, SKILLS_DIR);
    }

    public File getMemoryDirectory() {
        return new File(workspaceRoot, MEMORY_DIR);
    }

    public File getConfigDirectory() {
        return new File(workspaceRoot, CONFIG_DIR);
    }

    public File getUploadsDirectory() {
        return new File(workspaceRoot, UPLOADS_DIR);
    }

    public static String getSoulFilePath() {
        return SOUL_FILE;
    }

    public static String getUserFilePath() {
        return USER_FILE;
    }

    public static String getHeartbeatFilePath() {
        return HEARTBEAT_FILE;
    }

    public File getHeartbeatFile() {
        if (workspaceRoot == null) {
            return null;
        }
        return new File(workspaceRoot, HEARTBEAT_FILE);
    }

    public boolean clearTempDirectory() {
        File tmpDir = getTempDirectory();
        if (!tmpDir.exists()) {
            return true;
        }

        return deleteRecursive(tmpDir) && tmpDir.mkdirs();
    }

    public WorkspaceStats getStats() {
        return new WorkspaceStats(workspaceRoot);
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    public static class WorkspaceStats {
        private final long totalSize;
        private final int fileCount;
        private final int directoryCount;

        public WorkspaceStats(File root) {
            long[] sizeAndFiles = calculateSize(root);
            this.totalSize = sizeAndFiles[0];
            this.fileCount = (int) sizeAndFiles[1];
            this.directoryCount = (int) sizeAndFiles[2];
        }

        private long[] calculateSize(File file) {
            long size = 0;
            int files = 0;
            int dirs = 0;

            if (file.isFile()) {
                return new long[]{file.length(), 1, 0};
            } else if (file.isDirectory()) {
                dirs = 1;
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        long[] childStats = calculateSize(child);
                        size += childStats[0];
                        files += childStats[1];
                        dirs += childStats[2];
                    }
                }
            }

            return new long[]{size, files, dirs};
        }

        public long getTotalSize() {
            return totalSize;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getDirectoryCount() {
            return directoryCount;
        }

        public String getFormattedSize() {
            if (totalSize < 1024) {
                return totalSize + " B";
            } else if (totalSize < 1024 * 1024) {
                return String.format("%.2f KB", totalSize / 1024.0);
            } else {
                return String.format("%.2f MB", totalSize / (1024.0 * 1024.0));
            }
        }
    }
}