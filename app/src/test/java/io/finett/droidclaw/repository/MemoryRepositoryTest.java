package io.finett.droidclaw.repository;

import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

import io.finett.droidclaw.agent.MemoryContextBuilder;
import io.finett.droidclaw.filesystem.WorkspaceManager;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MemoryRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private Context mockContext;

    private MemoryRepository memoryRepository;
    private File memoryDir;
    private WorkspaceManager workspaceManager;

    @Before
    public void setUp() throws IOException {
        File workspaceRoot = tempFolder.newFolder("workspace");
        File agentDir = new File(workspaceRoot, ".agent");
        memoryDir = new File(agentDir, "memory");
        memoryDir.mkdirs();

        workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.getMemoryDirectory()).thenReturn(memoryDir);

        memoryRepository = new MemoryRepository(workspaceManager);
    }

    @Test
    public void testReadLongTermMemory_fileExists_returnsContent() throws IOException {
        File memoryFile = new File(memoryDir, "MEMORY.md");
        java.io.FileWriter writer = new java.io.FileWriter(memoryFile);
        writer.write("# Long-term Memory\n\nTest content");
        writer.close();

        String content = memoryRepository.readLongTermMemory();

        assertEquals("Should return file content", "# Long-term Memory\n\nTest content\n", content);
    }

    @Test
    public void testReadLongTermMemory_fileNotExists_returnsEmpty() throws IOException {
        String content = memoryRepository.readLongTermMemory();

        assertEquals("Should return empty string when file doesn't exist", "", content);
    }

    @Test
    public void testReadTodayNote_returnsTodayNoteContent() throws IOException {
        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File todayFile = new File(memoryDir, todayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(todayFile);
        writer.write("# Today's Note\n\nCurrent context");
        writer.close();

        String content = memoryRepository.readTodayNote();

        assertEquals("Should return today's note content", "# Today's Note\n\nCurrent context\n", content);
    }

    @Test
    public void testReadYesterdayNote_returnsYesterdayNoteContent() throws IOException {
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Yesterday's Note\n\nPrevious context");
        writer.close();

        String content = memoryRepository.readYesterdayNote();

        assertEquals("Should return yesterday's note content", "# Yesterday's Note\n\nPrevious context\n", content);
    }

    @Test
    public void testReadDailyNote_forSpecificDate_returnsContent() throws IOException {
        LocalDate testDate = LocalDate.of(2025, 12, 15);
        String filename = testDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File noteFile = new File(memoryDir, filename);
        java.io.FileWriter writer = new java.io.FileWriter(noteFile);
        writer.write("# Note for Dec 15\n\nTest data");
        writer.close();

        String content = memoryRepository.readDailyNote(testDate);

        assertEquals("Should return note for specific date", "# Note for Dec 15\n\nTest data\n", content);
    }

    @Test
    public void testReadDailyNote_dateNotExists_returnsEmpty() throws IOException {
        LocalDate testDate = LocalDate.of(2025, 12, 15);
        String content = memoryRepository.readDailyNote(testDate);

        assertEquals("Should return empty when date doesn't exist", "", content);
    }

    @Test
    public void testAppendToDailyNote_createsFileIfNotExists() throws IOException {
        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File todayFile = new File(memoryDir, todayFilename);

        assertFalse(todayFile.exists());

        memoryRepository.appendToDailyNote("New entry");

        assertTrue(todayFile.exists());
        String content = readFile(todayFile);
        assertTrue("Should create file with header", content.contains("# Daily Notes"));
        assertTrue("Should append content", content.contains("New entry"));
    }

    @Test
    public void testAppendToDailyNote_appendsToExistingFile() throws IOException {
        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File todayFile = new File(memoryDir, todayFilename);

        java.io.FileWriter writer = new java.io.FileWriter(todayFile);
        writer.write("# Daily Notes - " + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + "\n\n");
        writer.close();

        memoryRepository.appendToDailyNote("New entry");

        String content = readFile(todayFile);
        assertTrue("Should append to existing file", content.contains("New entry"));
        assertTrue("Should preserve header", content.contains("# Daily Notes"));
    }

    @Test
    public void testAppendToLongTermMemory_createsFileIfNotExists() throws IOException {
        File memoryFile = new File(memoryDir, "MEMORY.md");
        assertFalse(memoryFile.exists());

        memoryRepository.appendToLongTermMemory("New long-term fact");

        assertTrue(memoryFile.exists());
        String content = readFile(memoryFile);
        assertTrue("Should create file with header", content.contains("# Long-term Memory"));
        assertTrue("Should append content", content.contains("New long-term fact"));
    }

    @Test
    public void testAppendToLongTermMemory_appendsToExistingFile() throws IOException {
        File memoryFile = new File(memoryDir, "MEMORY.md");
        java.io.FileWriter writer = new java.io.FileWriter(memoryFile);
        writer.write("# Long-term Memory\n\n");
        writer.close();

        memoryRepository.appendToLongTermMemory("Another fact");

        String content = readFile(memoryFile);
        assertTrue("Should append to existing file", content.contains("Another fact"));
        assertTrue("Should preserve header", content.contains("# Long-term Memory"));
    }

    @Test
    public void testGetAllDailyNotes_returnsSortedFiles() throws IOException {
        createDailyNoteFile(LocalDate.of(2025, 12, 10));
        createDailyNoteFile(LocalDate.of(2025, 12, 15));
        createDailyNoteFile(LocalDate.of(2025, 12, 20));

        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        new File(memoryDir, todayFilename).createNewFile();

        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        new File(memoryDir, yesterdayFilename).createNewFile();

        java.util.List<File> notes = memoryRepository.getAllDailyNotes();

        assertEquals("Should return all daily notes", 5, notes.size());

        String firstDate = notes.get(0).getName().replace(".md", "");
        LocalDate firstDateParsed = LocalDate.parse(firstDate);
        assertTrue("Should be sorted newest first", firstDateParsed.equals(LocalDate.now()) ||
                   firstDateParsed.equals(LocalDate.now().minusDays(1)));
    }

    @Test
    public void testGetAllDailyNotes_empty_returnsEmptyList() {
        java.util.List<File> notes = memoryRepository.getAllDailyNotes();

        assertTrue("Should return empty list when no notes exist", notes.isEmpty());
    }

    @Test
    public void testLongTermMemoryExists_returnsCorrectValue() throws IOException {
        File memoryFile = new File(memoryDir, "MEMORY.md");
        assertFalse(memoryRepository.longTermMemoryExists());

        memoryFile.createNewFile();
        assertTrue(memoryRepository.longTermMemoryExists());
    }

    @Test
    public void testGetLongTermMemoryFile_returnsFile() {
        File file = memoryRepository.getLongTermMemoryFile();

        assertNotNull("Should return file object", file);
        assertEquals("Should be MEMORY.md", "MEMORY.md", file.getName());
        assertEquals("Should be in memory directory", memoryDir, file.getParentFile());
    }

    @Test
    public void testGetTodayNoteFile_returnsFile() {
        File file = memoryRepository.getTodayNoteFile();

        assertNotNull("Should return file object", file);
        String expectedName = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        assertEquals("Should be today's date", expectedName, file.getName());
    }

    @Test
    public void testReadLongTermMemory_ioException_handling() throws IOException {
        // Create a mock workspace manager that throws exception
        WorkspaceManager mockManager = mock(WorkspaceManager.class);
        when(mockManager.getMemoryDirectory()).thenReturn(memoryDir);

        MemoryRepository repo = new MemoryRepository(mockManager);

        // This should not throw - it returns empty on error
        String content = repo.readLongTermMemory();
        assertEquals("", content);
    }

    @Test
    public void testBuildMemoryContext_ioException_handling() throws IOException {
        // Create a mock workspace manager that throws exception on file operations
        WorkspaceManager mockManager = mock(WorkspaceManager.class);
        when(mockManager.getMemoryDirectory()).thenReturn(memoryDir);

        MemoryRepository repo = new MemoryRepository(mockManager);
        MemoryContextBuilder builder = new MemoryContextBuilder(repo);

        // This should not throw - it returns empty on error
        String context = builder.buildMemoryContext();
        assertEquals("", context);
    }

    @Test
    public void testHasMemory_noMemory_returnsFalse() throws IOException {
        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);
        assertFalse(builder.hasMemory());
    }

    @Test
    public void testHasMemory_withLongTerm_returnsTrue() throws IOException {
        File memoryFile = new File(memoryDir, "MEMORY.md");
        memoryFile.createNewFile();

        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);
        assertTrue(builder.hasMemory());
    }

    @Test
    public void testHasMemory_withToday_returnsTrue() throws IOException {
        String todayFilename = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File todayFile = new File(memoryDir, todayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(todayFile);
        writer.write("# Today's Note\n\nSome content");
        writer.close();

        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);
        assertTrue(builder.hasMemory());
    }

    @Test
    public void testHasMemory_withYesterday_returnsTrue() throws IOException {
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Yesterday's Note\n\nSome content");
        writer.close();

        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);
        assertTrue(builder.hasMemory());
    }

    @Test
    public void testHasMemory_withAnyMemory_returnsTrue() throws IOException {
        String yesterdayFilename = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File yesterdayFile = new File(memoryDir, yesterdayFilename);
        java.io.FileWriter writer = new java.io.FileWriter(yesterdayFile);
        writer.write("# Yesterday's Note\n\nSome content");
        writer.close();

        MemoryContextBuilder builder = new MemoryContextBuilder(memoryRepository);
        assertTrue(builder.hasMemory());
    }

    private void createDailyNoteFile(LocalDate date) throws IOException {
        String filename = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        File file = new File(memoryDir, filename);
        file.createNewFile();
    }

    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
