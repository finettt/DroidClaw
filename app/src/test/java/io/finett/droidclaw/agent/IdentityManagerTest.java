package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;

@RunWith(MockitoJUnitRunner.Silent.class)
public class IdentityManagerTest {

    @Mock
    private WorkspaceManager mockWorkspaceManager;

    private File tempDir;
    private IdentityManager identityManager;

    @Before
    public void setUp() throws IOException {
        tempDir = createTempDir();
        when(mockWorkspaceManager.getWorkspaceRoot()).thenReturn(tempDir);

        // Ensure .agent directory exists
        File agentDir = new File(tempDir, ".agent");
        agentDir.mkdirs();

        identityManager = new IdentityManager(null, mockWorkspaceManager);
    }

    @After
    public void tearDown() {
        deleteRecursive(tempDir);
    }

    @Test
    public void loadIdentity_withBothFiles_returnsCompleteContext() throws IOException {
        writeIdentityFile(".agent/soul.md", "# Soul\nI am DroidClaw.");
        writeIdentityFile(".agent/user.md", "# User\nUser preferences.");

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertNotNull("Identity context should not be null", identity);
        assertTrue("Should have soul content", identity.hasSoul());
        assertTrue("Should have user content", identity.hasUser());
        assertFalse("Identity should not be empty", identity.isEmpty());
        assertTrue("Soul content should contain expected text",
                identity.getSoulContent().contains("I am DroidClaw."));
        assertTrue("User content should contain expected text",
                identity.getUserContent().contains("User preferences."));
    }

    @Test
    public void loadIdentity_withMissingSoulFile_returnsEmptySoul() throws IOException {
        writeIdentityFile(".agent/user.md", "# User\nUser info");
        // soul.md does not exist

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Should not have soul when file missing", identity.hasSoul());
        assertTrue("Should still have user content", identity.hasUser());
        assertEquals("Soul content should be empty string", "", identity.getSoulContent());
    }

    @Test
    public void loadIdentity_withMissingUserFile_returnsEmptyUser() throws IOException {
        writeIdentityFile(".agent/soul.md", "# Soul\nSoul info");
        // user.md does not exist

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertTrue("Should have soul content", identity.hasSoul());
        assertFalse("Should not have user when file missing", identity.hasUser());
        assertEquals("User content should be empty string", "", identity.getUserContent());
    }

    @Test
    public void loadIdentity_withBothFilesMissing_returnsEmptyContext() throws IOException {
        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Should not have soul", identity.hasSoul());
        assertFalse("Should not have user", identity.hasUser());
        assertTrue("Identity should be empty", identity.isEmpty());
    }

    @Test
    public void loadIdentity_withEmptySoulFile_hasEmptySoul() throws IOException {
        writeIdentityFile(".agent/soul.md", "");
        writeIdentityFile(".agent/user.md", "User content");

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Empty file should result in hasSoul() = false", identity.hasSoul());
        assertTrue("Should still have user content", identity.hasUser());
    }

    @Test
    public void loadIdentity_withWhitespaceOnlySoulFile_hasEmptySoul() throws IOException {
        writeIdentityFile(".agent/soul.md", "   \n\t\n   ");
        writeIdentityFile(".agent/user.md", "User content");

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Whitespace-only file should result in hasSoul() = false", identity.hasSoul());
    }

    @Test
    public void loadIdentity_withWhitespaceOnlyUserFile_hasEmptyUser() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul content");
        writeIdentityFile(".agent/user.md", "  \n  \t  ");

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertTrue("Should have soul content", identity.hasSoul());
        assertFalse("Whitespace-only user file should result in hasUser() = false", identity.hasUser());
    }

    @Test
    public void loadIdentity_withMultilineContent_preservesContent() throws IOException {
        String soulContent = "# Soul\n\nLine 1\nLine 2\nLine 3\n";
        writeIdentityFile(".agent/soul.md", soulContent);
        writeIdentityFile(".agent/user.md", "User");

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertTrue("Should contain Line 1", identity.getSoulContent().contains("Line 1"));
        assertTrue("Should contain Line 2", identity.getSoulContent().contains("Line 2"));
        assertTrue("Should contain Line 3", identity.getSoulContent().contains("Line 3"));
    }

    @Test
    public void getIdentityMessages_cachesBetweenCalls_returnsSameContent() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul content");
        writeIdentityFile(".agent/user.md", "User content");

        List<ChatMessage> firstCall = identityManager.getIdentityMessages();
        List<ChatMessage> secondCall = identityManager.getIdentityMessages();

        assertEquals("Cached calls should return same number of messages",
                firstCall.size(), secondCall.size());
        for (int i = 0; i < firstCall.size(); i++) {
            assertEquals("Cached messages should have same content",
                    firstCall.get(i).getContent(), secondCall.get(i).getContent());
        }
        // Workspace root is queried three times on first call (once per file), zero on second (cache hit)
        verify(mockWorkspaceManager, times(3)).getWorkspaceRoot();
    }

    @Test
    public void clearCache_forcesReload_loadsNewContent() throws IOException {
        writeIdentityFile(".agent/soul.md", "Original soul");
        writeIdentityFile(".agent/user.md", "Original user");

        List<ChatMessage> initialMessages = identityManager.getIdentityMessages();
        String initialContent = initialMessages.get(0).getContent();

        // Modify the file
        writeIdentityFile(".agent/soul.md", "Modified soul");

        // Clear cache and reload
        identityManager.clearCache();
        List<ChatMessage> newMessages = identityManager.getIdentityMessages();
        String newContent = newMessages.get(0).getContent();

        assertFalse("Content should change after cache clear and file modification",
                initialContent.equals(newContent));
        assertTrue("New content should contain updated text",
                newContent.contains("Modified soul"));
    }

    @Test
    public void clearCache_canBeCalledMultipleTimes() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul");
        writeIdentityFile(".agent/user.md", "User");

        identityManager.clearCache();
        identityManager.clearCache();
        identityManager.clearCache();

        List<ChatMessage> messages = identityManager.getIdentityMessages();
        assertFalse("Should still load messages after multiple clears", messages.isEmpty());
    }

    @Test
    public void getIdentityMessages_withBothFiles_returnsSystemMessages() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul content");
        writeIdentityFile(".agent/user.md", "User content");

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertNotNull("Should return message list", messages);
        assertEquals("Should have exactly 2 messages", 2, messages.size());

        for (ChatMessage message : messages) {
            assertEquals("All identity messages should be TYPE_SYSTEM",
                    ChatMessage.TYPE_SYSTEM, message.getType());
            assertTrue("System messages should have content",
                    message.getContent() != null && !message.getContent().trim().isEmpty());
        }
    }

    @Test
    public void getIdentityMessages_preservesMessageOrder_soulBeforeUser() throws IOException {
        writeIdentityFile(".agent/soul.md", "SOUL_CONTENT");
        writeIdentityFile(".agent/user.md", "USER_CONTENT");

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertEquals("Should have exactly 2 messages", 2, messages.size());
        assertTrue("First message should be soul content",
                messages.get(0).getContent().contains("SOUL_CONTENT"));
        assertTrue("Second message should be user content",
                messages.get(1).getContent().contains("USER_CONTENT"));
    }

    @Test
    public void getIdentityMessages_withOnlySoul_returnsSingleMessage() throws IOException {
        writeIdentityFile(".agent/soul.md", "ONLY_SOUL");
        // user.md does not exist

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertEquals("Should have exactly 1 message", 1, messages.size());
        assertTrue("Message should be soul content",
                messages.get(0).getContent().contains("ONLY_SOUL"));
    }

    @Test
    public void getIdentityMessages_withOnlyUser_returnsSingleMessage() throws IOException {
        // soul.md does not exist
        writeIdentityFile(".agent/user.md", "ONLY_USER");

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertEquals("Should have exactly 1 message", 1, messages.size());
        assertTrue("Message should be user content",
                messages.get(0).getContent().contains("ONLY_USER"));
    }

    @Test
    public void getIdentityMessages_withEmptyFiles_returnsEmptyList() throws IOException {
        writeIdentityFile(".agent/soul.md", "");
        writeIdentityFile(".agent/user.md", "");

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertTrue("Should return empty list when both files are empty", messages.isEmpty());
    }

    @Test
    public void getIdentityMessages_withNoFiles_returnsEmptyList() throws IOException {
        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertTrue("Should return empty list when no files exist", messages.isEmpty());
    }

    @Test
    public void identityFilesExist_withAllFiles_returnsTrue() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul");
        writeIdentityFile(".agent/user.md", "User");
        writeIdentityFile(".agent/relationships.md", "Relationships");

        assertTrue("Identity files should exist", identityManager.identityFilesExist());
    }

    @Test
    public void identityFilesExist_withNoFiles_returnsFalse() {
        assertFalse("Should return false when files don't exist",
                identityManager.identityFilesExist());
    }

    @Test
    public void identityFilesExist_withOnlySoulFile_returnsFalse() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul");
        // user.md missing

        assertFalse("Should return false when only soul file exists",
                identityManager.identityFilesExist());
    }

    @Test
    public void identityFilesExist_withOnlyUserFile_returnsFalse() throws IOException {
        // soul.md missing
        writeIdentityFile(".agent/user.md", "User");

        assertFalse("Should return false when only user file exists",
                identityManager.identityFilesExist());
    }

    @Test
    public void identityFilesExist_afterFileDeletion_returnsFalse() throws IOException {
        writeIdentityFile(".agent/soul.md", "Soul");
        writeIdentityFile(".agent/user.md", "User");
        writeIdentityFile(".agent/relationships.md", "Relationships");

        assertTrue("Files should exist initially", identityManager.identityFilesExist());

        // Delete one file
        new File(tempDir, ".agent/soul.md").delete();

        assertFalse("Should return false after deleting one file",
                identityManager.identityFilesExist());
    }

    @Test
    public void identityContext_isEmpty_withEmptyStrings() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("", "");
        assertTrue("Context with empty strings should be empty", context.isEmpty());
        assertFalse("Should not have soul", context.hasSoul());
        assertFalse("Should not have user", context.hasUser());
    }

    @Test
    public void identityContext_isEmpty_withWhitespace() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("  ", "\n\t");
        assertTrue("Context with whitespace should be empty", context.isEmpty());
    }

    @Test
    public void identityContext_isEmpty_withNulls() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext(null, null);
        assertTrue("Context with null values should be empty", context.isEmpty());
        assertFalse("Should not have soul", context.hasSoul());
        assertFalse("Should not have user", context.hasUser());
    }

    @Test
    public void identityContext_isEmpty_withPartialContent() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("content", "");
        assertFalse("Context with one non-empty value should not be empty", context.isEmpty());
        assertTrue("Should have soul", context.hasSoul());
        assertFalse("Should not have user", context.hasUser());
    }

    @Test
    public void identityContext_isEmpty_withBothContent() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("soul", "user");
        assertFalse("Context with both values should not be empty", context.isEmpty());
        assertTrue("Should have soul", context.hasSoul());
        assertTrue("Should have user", context.hasUser());
    }

    @Test
    public void identityContext_getters_returnCorrectValues() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("soul_val", "user_val");

        assertEquals("getSoulContent should return soul value", "soul_val", context.getSoulContent());
        assertEquals("getUserContent should return user value", "user_val", context.getUserContent());
    }

    @Test
    public void identityContext_hasSoul_withNullSoul() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext(null, "user");

        assertFalse("Should not have soul with null", context.hasSoul());
        assertTrue("Should have user", context.hasUser());
    }

    @Test
    public void identityContext_hasUser_withNullUser() {
        IdentityManager.IdentityContext context = new IdentityManager.IdentityContext("soul", null);

        assertTrue("Should have soul", context.hasSoul());
        assertFalse("Should not have user with null", context.hasUser());
    }

    private File createTempDir() {
        File temp = new File(System.getProperty("java.io.tmpdir"), "identity_test_" + System.currentTimeMillis());
        temp.mkdirs();
        return temp;
    }

    private void writeIdentityFile(String relativePath, String content) throws IOException {
        File file = new File(tempDir, relativePath);
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
