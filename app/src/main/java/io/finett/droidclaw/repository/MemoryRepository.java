package io.finett.droidclaw.repository;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Repository for managing memory files (MEMORY.md and daily notes).
 * 
 * Memory structure:
 * - MEMORY.md: Long-term memory with durable facts, preferences, decisions
 * - YYYY-MM-DD.md: Daily notes with running context and observations
 * 
 * Auto-loaded files:
 * - MEMORY.md (always)
 * - Today's note (current date)
 * - Yesterday's note (previous date)
 */
public class MemoryRepository {
    private static final String TAG = "MemoryRepository";
    private static final String LONG_TERM_FILE = "MEMORY.md";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final File memoryDir;
    
    public MemoryRepository(WorkspaceManager workspaceManager) {
        this.memoryDir = workspaceManager.getMemoryDirectory();
        ensureMemoryDirExists();
    }
    
    /**
     * Ensure memory directory exists.
     */
    private void ensureMemoryDirExists() {
        if (!memoryDir.exists()) {
            if (memoryDir.mkdirs()) {
                Log.d(TAG, "Created memory directory: " + memoryDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create memory directory: " + memoryDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * Read long-term memory file (MEMORY.md).
     * 
     * @return Content of MEMORY.md, or empty string if file doesn't exist
     * @throws IOException if file read fails
     */
    public String readLongTermMemory() throws IOException {
        File memoryFile = new File(memoryDir, LONG_TERM_FILE);
        if (!memoryFile.exists()) {
            Log.d(TAG, "MEMORY.md does not exist yet");
            return "";
        }
        
        String content = readFileContent(memoryFile);
        Log.d(TAG, "Read long-term memory: " + content.length() + " characters");
        return content;
    }
    
    /**
     * Read today's daily note.
     * 
     * @return Content of today's note, or empty string if doesn't exist
     * @throws IOException if file read fails
     */
    public String readTodayNote() throws IOException {
        return readDailyNote(LocalDate.now());
    }
    
    /**
     * Read yesterday's daily note.
     * 
     * @return Content of yesterday's note, or empty string if doesn't exist
     * @throws IOException if file read fails
     */
    public String readYesterdayNote() throws IOException {
        return readDailyNote(LocalDate.now().minusDays(1));
    }
    
    /**
     * Read daily note for a specific date.
     * 
     * @param date The date to read
     * @return Content of the daily note, or empty string if doesn't exist
     * @throws IOException if file read fails
     */
    public String readDailyNote(LocalDate date) throws IOException {
        String filename = getDailyNoteFilename(date);
        File noteFile = new File(memoryDir, filename);
        
        if (!noteFile.exists()) {
            Log.d(TAG, "Daily note does not exist: " + filename);
            return "";
        }
        
        String content = readFileContent(noteFile);
        Log.d(TAG, "Read daily note " + filename + ": " + content.length() + " characters");
        return content;
    }
    
    /**
     * Append content to today's daily note.
     * Creates the file if it doesn't exist.
     * 
     * @param content Content to append
     * @throws IOException if file write fails
     */
    public void appendToDailyNote(String content) throws IOException {
        String filename = getDailyNoteFilename(LocalDate.now());
        File noteFile = new File(memoryDir, filename);
        
        // Create file with header if it doesn't exist
        if (!noteFile.exists()) {
            String header = createDailyNoteHeader(LocalDate.now());
            writeToFile(noteFile, header, false);
            Log.d(TAG, "Created new daily note: " + filename);
        }
        
        // Append content with newlines
        writeToFile(noteFile, "\n" + content + "\n", true);
        Log.d(TAG, "Appended to daily note " + filename + ": " + content.length() + " characters");
    }
    
    /**
     * Append content to long-term memory (MEMORY.md).
     * Creates the file if it doesn't exist.
     * 
     * @param content Content to append
     * @throws IOException if file write fails
     */
    public void appendToLongTermMemory(String content) throws IOException {
        File memoryFile = new File(memoryDir, LONG_TERM_FILE);
        
        // Create file with header if it doesn't exist
        if (!memoryFile.exists()) {
            String header = "# Long-term Memory\n\n";
            writeToFile(memoryFile, header, false);
            Log.d(TAG, "Created new MEMORY.md file");
        }
        
        // Append content with newlines
        writeToFile(memoryFile, "\n" + content + "\n", true);
        Log.d(TAG, "Appended to MEMORY.md: " + content.length() + " characters");
    }
    
    /**
     * Get all daily note files, sorted by date (newest first).
     * 
     * @return List of daily note files
     */
    public List<File> getAllDailyNotes() {
        File[] files = memoryDir.listFiles((dir, name) -> 
            name.matches("\\d{4}-\\d{2}-\\d{2}\\.md")
        );
        
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        
        List<File> noteFiles = new ArrayList<>(Arrays.asList(files));
        // Sort by filename (which is date) in descending order
        noteFiles.sort((f1, f2) -> f2.getName().compareTo(f1.getName()));
        
        Log.d(TAG, "Found " + noteFiles.size() + " daily notes");
        return noteFiles;
    }
    
    /**
     * Get the long-term memory file.
     * 
     * @return MEMORY.md file (may not exist yet)
     */
    public File getLongTermMemoryFile() {
        return new File(memoryDir, LONG_TERM_FILE);
    }
    
    /**
     * Check if long-term memory file exists.
     * 
     * @return true if MEMORY.md exists
     */
    public boolean longTermMemoryExists() {
        return new File(memoryDir, LONG_TERM_FILE).exists();
    }
    
    /**
     * Get today's daily note file.
     * 
     * @return Today's note file (may not exist yet)
     */
    public File getTodayNoteFile() {
        String filename = getDailyNoteFilename(LocalDate.now());
        return new File(memoryDir, filename);
    }
    
    /**
     * Format daily note filename from date.
     * 
     * @param date The date
     * @return Filename in format YYYY-MM-DD.md
     */
    private String getDailyNoteFilename(LocalDate date) {
        return date.format(DATE_FORMATTER) + ".md";
    }
    
    /**
     * Create header for a new daily note.
     * 
     * @param date The date for the note
     * @return Formatted header
     */
    private String createDailyNoteHeader(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        return "# Daily Notes - " + formattedDate + "\n\n";
    }
    
    /**
     * Read entire file content.
     * 
     * @param file File to read
     * @return File content as string
     * @throws IOException if read fails
     */
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return content.toString();
    }
    
    /**
     * Write content to file.
     * 
     * @param file File to write to
     * @param content Content to write
     * @param append If true, append to file; if false, overwrite
     * @throws IOException if write fails
     */
    private void writeToFile(File file, String content, boolean append) throws IOException {
        try (FileWriter writer = new FileWriter(file, append)) {
            writer.write(content);
        }
    }
}