package io.finett.droidclaw.filesystem;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Manages the workspace directory structure for the agent's virtual filesystem.
 * Creates and initializes the workspace with standard directories on first use.
 */
public class WorkspaceManager {
    private static final String TAG = "WorkspaceManager";

    // Built-in skills to copy from app assets/skills
    private static final String[] BUILTIN_SKILLS = {
        "skill_creator",
        "web_search",
        "code_analysis",
        "data_processing",
        "task_automation"
    };
    private static final String WORKSPACE_DIR = "workspace";
    
    // Standard workspace directories
    private static final String HOME_DIR = "home";
    private static final String DOCUMENTS_DIR = "home/documents";
    private static final String SCRIPTS_DIR = "home/scripts";
    private static final String NOTES_DIR = "home/notes";
    private static final String TMP_DIR = "tmp";
    private static final String AGENT_DIR = ".agent";
    private static final String MEMORY_DIR = ".agent/memory";
    private static final String SKILLS_DIR = ".agent/skills";
    private static final String CONFIG_DIR = ".agent/config";
    
    // Identity files
    private static final String SOUL_FILE = ".agent/soul.md";
    private static final String USER_FILE = ".agent/user.md";

    private final Context context;
    private final File workspaceRoot;
    private final PathValidator pathValidator;

    public WorkspaceManager(Context context) {
        this.context = context;
        this.workspaceRoot = new File(context.getFilesDir(), WORKSPACE_DIR);
        this.pathValidator = new PathValidator(workspaceRoot);
    }

    /**
     * Initializes the workspace directory structure.
     * Creates all standard directories if they don't exist.
     * 
     * @return true if initialization successful
     * @throws IOException if directory creation fails
     */
    public boolean initialize() throws IOException {
        // Create workspace root
        if (!workspaceRoot.exists()) {
            if (!workspaceRoot.mkdirs()) {
                throw new IOException("Failed to create workspace root: " + workspaceRoot);
            }
        }

        // Create standard directories
        String[] standardDirs = {
            HOME_DIR,
            DOCUMENTS_DIR,
            SCRIPTS_DIR,
            NOTES_DIR,
            TMP_DIR,
            AGENT_DIR,
            MEMORY_DIR,
            SKILLS_DIR,
            CONFIG_DIR
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

    /**
     * Initializes the workspace and copies built-in skills from app resources.
     * Creates all standard directories and copies skill directories if they don't exist.
     *
     * @return true if initialization successful
     * @throws IOException if directory creation or skill copying fails
     */
    public boolean initializeWithSkills() throws IOException {
        // Initialize standard directories
        initialize();

        // Create identity files
        try {
            createIdentityFiles();
        } catch (IOException e) {
            Log.w(TAG, "Failed to create identity files", e);
            // Continue even if identity files fail
        }

        // Copy built-in skills
        for (String skillName : BUILTIN_SKILLS) {
            try {
                copySkill(skillName);
            } catch (IOException e) {
                Log.w(TAG, "Failed to copy skill: " + skillName, e);
                // Continue with other skills
            }
        }

        return true;
    }

    /**
     * Copies a built-in skill from app resources to the workspace.
     *
     * @param skillName Name of the skill to copy
     * @throws IOException if copy fails
     */
    private void copySkill(String skillName) throws IOException {
        File skillDir = new File(workspaceRoot, SKILLS_DIR + "/" + skillName);

        // Skip if skill already exists
        if (skillDir.exists()) {
            Log.d(TAG, "Skill already exists: " + skillName);
            return;
        }

        Log.d(TAG, "Copying skill: " + skillName);

        // Create skill directory
        if (!skillDir.mkdirs()) {
            throw new IOException("Failed to create skill directory: " + skillName);
        }

        // Copy SKILL.md file
        String skillMdPath = "skills/" + skillName + "/SKILL.md";
        try (InputStream inputStream = context.getAssets().open(skillMdPath)) {
            File skillMdFile = new File(skillDir, "SKILL.md");
            copyInputStreamToFile(inputStream, skillMdFile);
            Log.d(TAG, "Copied SKILL.md for: " + skillName);
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy SKILL.md for skill: " + skillName, e);
            // Don't fail if SKILL.md is missing - skill directory is still created
        }
    }

    /**
     * Copies an InputStream to a file.
     *
     * @param inputStream Input stream to copy
     * @param outputFile Output file
     * @throws IOException if copy fails
     */
    private void copyInputStreamToFile(InputStream inputStream, File outputFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Creates identity files (soul.md and user.md) from app assets if they don't exist.
     *
     * @throws IOException if file creation fails
     */
    private void createIdentityFiles() throws IOException {
        createIdentityFile(SOUL_FILE, "identity/soul.md");
        createIdentityFile(USER_FILE, "identity/user.md");
    }

    /**
     * Creates an identity file from app assets if it doesn't exist.
     *
     * @param workspacePath Path in workspace (e.g., ".agent/soul.md")
     * @param assetPath Path in assets (e.g., "identity/soul.md")
     * @throws IOException if file creation fails
     */
    private void createIdentityFile(String workspacePath, String assetPath) throws IOException {
        File file = new File(workspaceRoot, workspacePath);
        
        // Skip if file already exists
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

    /**
     * Gets the workspace root directory.
     *
     * @return Workspace root file
     */
    public File getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Gets the path validator for this workspace.
     * 
     * @return PathValidator instance
     */
    public PathValidator getPathValidator() {
        return pathValidator;
    }

    /**
     * Gets a specific workspace directory.
     * 
     * @param relativePath Path relative to workspace root
     * @return Directory file
     */
    public File getDirectory(String relativePath) throws IOException {
        return pathValidator.validateAndResolve(relativePath);
    }

    /**
     * Gets the home directory.
     * 
     * @return Home directory file
     */
    public File getHomeDirectory() {
        return new File(workspaceRoot, HOME_DIR);
    }

    /**
     * Gets the documents directory.
     * 
     * @return Documents directory file
     */
    public File getDocumentsDirectory() {
        return new File(workspaceRoot, DOCUMENTS_DIR);
    }

    /**
     * Gets the scripts directory.
     * 
     * @return Scripts directory file
     */
    public File getScriptsDirectory() {
        return new File(workspaceRoot, SCRIPTS_DIR);
    }

    /**
     * Gets the temporary directory.
     * 
     * @return Temporary directory file
     */
    public File getTempDirectory() {
        return new File(workspaceRoot, TMP_DIR);
    }

    /**
     * Gets the agent directory.
     * 
     * @return Agent directory file
     */
    public File getAgentDirectory() {
        return new File(workspaceRoot, AGENT_DIR);
    }

    /**
     * Gets the skills directory.
     * 
     * @return Skills directory file
     */
    public File getSkillsDirectory() {
        return new File(workspaceRoot, SKILLS_DIR);
    }

    /**
     * Gets the memory directory.
     * 
     * @return Memory directory file
     */
    public File getMemoryDirectory() {
        return new File(workspaceRoot, MEMORY_DIR);
    }

    /**
     * Gets the config directory.
     * 
     * @return Config directory file
     */
    public File getConfigDirectory() {
        return new File(workspaceRoot, CONFIG_DIR);
    }

    /**
     * Gets the soul.md identity file path.
     *
     * @return Soul file path relative to workspace
     */
    public static String getSoulFilePath() {
        return SOUL_FILE;
    }

    /**
     * Gets the user.md identity file path.
     *
     * @return User file path relative to workspace
     */
    public static String getUserFilePath() {
        return USER_FILE;
    }

    /**
     * Clears the temporary directory.
     *
     * @return true if successful
     */
    public boolean clearTempDirectory() {
        File tmpDir = getTempDirectory();
        if (!tmpDir.exists()) {
            return true;
        }

        return deleteRecursive(tmpDir) && tmpDir.mkdirs();
    }

    /**
     * Gets workspace statistics.
     * 
     * @return WorkspaceStats object
     */
    public WorkspaceStats getStats() {
        return new WorkspaceStats(workspaceRoot);
    }

    /**
     * Recursively deletes a directory and all its contents.
     * 
     * @param file File or directory to delete
     * @return true if successful
     */
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

    /**
     * Statistics about workspace usage.
     */
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