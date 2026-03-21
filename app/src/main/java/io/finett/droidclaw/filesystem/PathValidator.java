package io.finett.droidclaw.filesystem;

import java.io.File;
import java.io.IOException;

/**
 * Validates file paths to prevent path traversal attacks and ensure all operations
 * stay within the workspace sandbox.
 */
public class PathValidator {
    private final File workspaceRoot;

    public PathValidator(File workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("Workspace root cannot be null");
        }
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Validates and resolves a path relative to the workspace root.
     * 
     * @param relativePath Path relative to workspace root
     * @return Canonical File object within the workspace
     * @throws SecurityException if path traversal is detected
     * @throws IOException if path cannot be canonicalized
     */
    public File validateAndResolve(String relativePath) throws SecurityException, IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Normalize path separators - convert backslashes to forward slashes
        // This ensures consistent behavior across platforms and prevents
        // path traversal attacks using backslashes on Unix-like systems
        String normalizedPath = relativePath.replace('\\', '/');

        // Remove leading slash if present (all paths are relative)
        String cleanPath = normalizedPath.startsWith("/")
            ? normalizedPath.substring(1)
            : normalizedPath;

        // Create file relative to workspace root
        File targetFile = new File(workspaceRoot, cleanPath);

        // Get canonical paths to resolve .. and . and symlinks
        String canonicalWorkspace = workspaceRoot.getCanonicalPath();
        String canonicalTarget = targetFile.getCanonicalPath();

        // Ensure target is within workspace
        if (!canonicalTarget.startsWith(canonicalWorkspace)) {
            throw new SecurityException(
                "Path traversal detected: " + relativePath + 
                " resolves outside workspace"
            );
        }

        return targetFile;
    }

    /**
     * Checks if a path is valid without throwing exceptions.
     * 
     * @param relativePath Path to check
     * @return true if path is valid and within workspace
     */
    public boolean isValid(String relativePath) {
        try {
            validateAndResolve(relativePath);
            return true;
        } catch (Exception e) {
            return false;
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
     * Converts an absolute file path to a workspace-relative path.
     * 
     * @param file File to convert
     * @return Relative path string
     * @throws IllegalArgumentException if file is not within workspace
     */
    public String toRelativePath(File file) throws IllegalArgumentException {
        try {
            String canonicalWorkspace = workspaceRoot.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();

            if (!canonicalFile.startsWith(canonicalWorkspace)) {
                throw new IllegalArgumentException("File is not within workspace");
            }

            // Get relative path
            String relativePath = canonicalFile.substring(canonicalWorkspace.length());
            
            // Remove leading separator
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }

            return relativePath.isEmpty() ? "." : relativePath;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot determine relative path", e);
        }
    }
}