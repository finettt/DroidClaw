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

public class MemoryRepository {
    private static final String TAG = "MemoryRepository";
    private static final String LONG_TERM_FILE = "MEMORY.md";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final File memoryDir;
    
    public MemoryRepository(WorkspaceManager workspaceManager) {
        this.memoryDir = workspaceManager.getMemoryDirectory();
        ensureMemoryDirExists();
    }
    
    private void ensureMemoryDirExists() {
        if (!memoryDir.exists()) {
            if (memoryDir.mkdirs()) {
                Log.d(TAG, "Created memory directory: " + memoryDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create memory directory: " + memoryDir.getAbsolutePath());
            }
        }
    }
    
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
    
    public String readTodayNote() throws IOException {
        return readDailyNote(LocalDate.now());
    }
    
    public String readYesterdayNote() throws IOException {
        return readDailyNote(LocalDate.now().minusDays(1));
    }
    
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
    
    public void appendToDailyNote(String content) throws IOException {
        String filename = getDailyNoteFilename(LocalDate.now());
        File noteFile = new File(memoryDir, filename);

        if (!noteFile.exists()) {
            String header = createDailyNoteHeader(LocalDate.now());
            writeToFile(noteFile, header, false);
            Log.d(TAG, "Created new daily note: " + filename);
        }

        writeToFile(noteFile, "\n" + content + "\n", true);
        Log.d(TAG, "Appended to daily note " + filename + ": " + content.length() + " characters");
    }
    
    public void appendToLongTermMemory(String content) throws IOException {
        File memoryFile = new File(memoryDir, LONG_TERM_FILE);

        if (!memoryFile.exists()) {
            writeToFile(memoryFile, "# Long-term Memory\n\n", false);
            Log.d(TAG, "Created new MEMORY.md file");
        }

        writeToFile(memoryFile, "\n" + content + "\n", true);
        Log.d(TAG, "Appended to MEMORY.md: " + content.length() + " characters");
    }
    
    public List<File> getAllDailyNotes() {
        File[] files = memoryDir.listFiles((dir, name) ->
            name.matches("\\d{4}-\\d{2}-\\d{2}\\.md")
        );

        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<File> noteFiles = new ArrayList<>(Arrays.asList(files));
        noteFiles.sort((f1, f2) -> f2.getName().compareTo(f1.getName()));
        
        Log.d(TAG, "Found " + noteFiles.size() + " daily notes");
        return noteFiles;
    }
    
    public File getLongTermMemoryFile() {
        return new File(memoryDir, LONG_TERM_FILE);
    }
    
    public boolean longTermMemoryExists() {
        return new File(memoryDir, LONG_TERM_FILE).exists();
    }
    
    public File getTodayNoteFile() {
        String filename = getDailyNoteFilename(LocalDate.now());
        return new File(memoryDir, filename);
    }
    
    private String getDailyNoteFilename(LocalDate date) {
        return date.format(DATE_FORMATTER) + ".md";
    }
    
    private String createDailyNoteHeader(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        return "# Daily Notes - " + formattedDate + "\n\n";
    }
    
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
    
    private void writeToFile(File file, String content, boolean append) throws IOException {
        try (FileWriter writer = new FileWriter(file, append)) {
            writer.write(content);
        }
    }
}