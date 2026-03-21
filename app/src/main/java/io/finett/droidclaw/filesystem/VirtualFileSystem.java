package io.finett.droidclaw.filesystem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Virtual filesystem implementation providing sandboxed file operations.
 * All operations are confined to the workspace directory.
 */
public class VirtualFileSystem {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_LINES_PER_READ = 10000;

    private final PathValidator pathValidator;

    /**
     * Creates a VirtualFileSystem with the given WorkspaceManager.
     *
     * @param workspaceManager WorkspaceManager for workspace directory management
     */
    public VirtualFileSystem(WorkspaceManager workspaceManager) {
        this.pathValidator = workspaceManager.getPathValidator();
    }

    /**
     * Creates a VirtualFileSystem with the given PathValidator.
     * Useful for testing without needing Android Context.
     *
     * @param pathValidator PathValidator for path validation
     */
    public VirtualFileSystem(PathValidator pathValidator) {
        this.pathValidator = pathValidator;
    }

    /**
     * Reads the contents of a file.
     * 
     * @param path File path relative to workspace root
     * @param offset Starting line number (0-based), null for beginning
     * @param limit Maximum number of lines to read, null for all
     * @return FileReadResult containing file contents
     * @throws IOException if read fails
     * @throws SecurityException if path is invalid
     */
    public FileReadResult readFile(String path, Integer offset, Integer limit) 
            throws IOException, SecurityException {
        File file = pathValidator.validateAndResolve(path);

        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }

        if (!file.isFile()) {
            throw new IOException("Not a file: " + path);
        }

        if (file.length() > MAX_FILE_SIZE) {
            throw new IOException("File too large: " + path + " (max " + MAX_FILE_SIZE + " bytes)");
        }

        int startLine = offset != null ? offset : 0;
        int maxLines = limit != null ? limit : MAX_LINES_PER_READ;

        List<String> lines = new ArrayList<>();
        int totalLines = 0;
        int linesRead = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (totalLines >= startLine && linesRead < maxLines) {
                    lines.add(line);
                    linesRead++;
                }
                totalLines++;
            }
        }

        String content = String.join("\n", lines);
        boolean truncated = totalLines > (startLine + linesRead);

        return new FileReadResult(content, totalLines, linesRead, truncated);
    }

    /**
     * Writes content to a file.
     * 
     * @param path File path relative to workspace root
     * @param content Content to write
     * @param append If true, append to existing file
     * @return true if successful
     * @throws IOException if write fails
     * @throws SecurityException if path is invalid
     */
    public boolean writeFile(String path, String content, boolean append) 
            throws IOException, SecurityException {
        File file = pathValidator.validateAndResolve(path);

        // Create parent directories if needed
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directories for: " + path);
            }
        }

        // Check content size
        if (content.length() > MAX_FILE_SIZE) {
            throw new IOException("Content too large (max " + MAX_FILE_SIZE + " bytes)");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            writer.write(content);
        }

        return true;
    }

    /**
     * Lists files and directories in a path.
     * 
     * @param path Directory path relative to workspace root
     * @param recursive If true, list recursively
     * @return FileListResult containing file information
     * @throws IOException if listing fails
     * @throws SecurityException if path is invalid
     */
    public FileListResult listFiles(String path, boolean recursive) 
            throws IOException, SecurityException {
        File dir = pathValidator.validateAndResolve(path != null ? path : ".");

        if (!dir.exists()) {
            throw new IOException("Directory not found: " + path);
        }

        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + path);
        }

        List<FileInfo> files = new ArrayList<>();
        listFilesRecursive(dir, recursive, files);

        return new FileListResult(files);
    }

    private void listFilesRecursive(File dir, boolean recursive, List<FileInfo> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String relativePath = pathValidator.toRelativePath(file);
            result.add(new FileInfo(
                relativePath,
                file.isDirectory(),
                file.length(),
                file.lastModified()
            ));

            if (recursive && file.isDirectory()) {
                listFilesRecursive(file, true, result);
            }
        }
    }

    /**
     * Deletes a file or empty directory.
     * 
     * @param path File path relative to workspace root
     * @return true if successful
     * @throws IOException if deletion fails
     * @throws SecurityException if path is invalid
     */
    public boolean deleteFile(String path) throws IOException, SecurityException {
        File file = pathValidator.validateAndResolve(path);

        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }

        if (file.isDirectory() && file.list() != null && file.list().length > 0) {
            throw new IOException("Cannot delete non-empty directory: " + path);
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete: " + path);
        }

        return true;
    }

    /**
     * Searches for content in files.
     * 
     * @param path Directory to search in
     * @param pattern Regex pattern to search for
     * @param filePattern Glob pattern to filter files (e.g., "*.txt")
     * @return FileSearchResult containing matches
     * @throws IOException if search fails
     * @throws SecurityException if path is invalid
     */
    public FileSearchResult searchFiles(String path, String pattern, String filePattern) 
            throws IOException, SecurityException {
        File dir = pathValidator.validateAndResolve(path != null ? path : ".");

        if (!dir.exists()) {
            throw new IOException("Directory not found: " + path);
        }

        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + path);
        }

        Pattern regex = Pattern.compile(pattern);
        Pattern fileRegex = filePattern != null ? globToRegex(filePattern) : null;

        List<SearchMatch> matches = new ArrayList<>();
        searchRecursive(dir, regex, fileRegex, matches);

        return new FileSearchResult(matches);
    }

    private void searchRecursive(File dir, Pattern pattern, Pattern filePattern, 
                                 List<SearchMatch> matches) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                searchRecursive(file, pattern, filePattern, matches);
            } else if (file.isFile()) {
                String relativePath = pathValidator.toRelativePath(file);
                
                // Check if file matches the file pattern
                if (filePattern != null && !filePattern.matcher(relativePath).matches()) {
                    continue;
                }

                // Search file contents
                searchInFile(file, relativePath, pattern, matches);
            }
        }
    }

    private void searchInFile(File file, String relativePath, Pattern pattern, 
                             List<SearchMatch> matches) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    matches.add(new SearchMatch(relativePath, lineNumber, line.trim()));
                }
                lineNumber++;
            }
        }
    }

    private Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                default:
                    regex.append(c);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    /**
     * Gets file information.
     * 
     * @param path File path relative to workspace root
     * @return FileInfo object
     * @throws IOException if file doesn't exist
     * @throws SecurityException if path is invalid
     */
    public FileInfo getFileInfo(String path) throws IOException, SecurityException {
        File file = pathValidator.validateAndResolve(path);

        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }

        return new FileInfo(
            pathValidator.toRelativePath(file),
            file.isDirectory(),
            file.length(),
            file.lastModified()
        );
    }

    /**
     * Result of a file read operation.
     */
    public static class FileReadResult {
        private final String content;
        private final int totalLines;
        private final int linesRead;
        private final boolean truncated;

        public FileReadResult(String content, int totalLines, int linesRead, boolean truncated) {
            this.content = content;
            this.totalLines = totalLines;
            this.linesRead = linesRead;
            this.truncated = truncated;
        }

        public String getContent() {
            return content;
        }

        public int getTotalLines() {
            return totalLines;
        }

        public int getLinesRead() {
            return linesRead;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * Result of a file list operation.
     */
    public static class FileListResult {
        private final List<FileInfo> files;

        public FileListResult(List<FileInfo> files) {
            this.files = files;
        }

        public List<FileInfo> getFiles() {
            return files;
        }
    }

    /**
     * Information about a file or directory.
     */
    public static class FileInfo {
        private final String path;
        private final boolean isDirectory;
        private final long size;
        private final long lastModified;

        public FileInfo(String path, boolean isDirectory, long size, long lastModified) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getPath() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * Result of a file search operation.
     */
    public static class FileSearchResult {
        private final List<SearchMatch> matches;

        public FileSearchResult(List<SearchMatch> matches) {
            this.matches = matches;
        }

        public List<SearchMatch> getMatches() {
            return matches;
        }
    }

    /**
     * A single search match.
     */
    public static class SearchMatch {
        private final String file;
        private final int lineNumber;
        private final String line;

        public SearchMatch(String file, int lineNumber, String line) {
            this.file = file;
            this.lineNumber = lineNumber;
            this.line = line;
        }

        public String getFile() {
            return file;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getLine() {
            return line;
        }
    }
}