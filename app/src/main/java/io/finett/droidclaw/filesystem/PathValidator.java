package io.finett.droidclaw.filesystem;

import java.io.File;
import java.io.IOException;

public class PathValidator {
    private final File workspaceRoot;

    public PathValidator(File workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("Workspace root cannot be null");
        }
        this.workspaceRoot = workspaceRoot;
    }

    public File validateAndResolve(String relativePath) throws SecurityException, IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Normalize path separators and prevent path traversal via backslashes
        String normalizedPath = relativePath.replace('\\', '/');

        String cleanPath = normalizedPath.startsWith("/")
            ? normalizedPath.substring(1)
            : normalizedPath;

        File targetFile = new File(workspaceRoot, cleanPath);

        String canonicalWorkspace = workspaceRoot.getCanonicalPath();
        String canonicalTarget = targetFile.getCanonicalPath();

        if (!canonicalTarget.startsWith(canonicalWorkspace)) {
            throw new SecurityException(
                "Path traversal detected: " + relativePath + 
                " resolves outside workspace"
            );
        }

        return targetFile;
    }

    public boolean isValid(String relativePath) {
        try {
            validateAndResolve(relativePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public File getWorkspaceRoot() {
        return workspaceRoot;
    }

    public String toRelativePath(File file) throws IllegalArgumentException {
        try {
            String canonicalWorkspace = workspaceRoot.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();

            if (!canonicalFile.startsWith(canonicalWorkspace)) {
                throw new IllegalArgumentException("File is not within workspace");
            }

            String relativePath = canonicalFile.substring(canonicalWorkspace.length());

            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }

            return relativePath.isEmpty() ? "." : relativePath;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot determine relative path", e);
        }
    }
}