package io.finett.droidclaw.agent;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;

@RunWith(AndroidJUnit4.class)
public class IdentityManagerTest {

    private Context context;
    private WorkspaceManager workspaceManager;
    private IdentityManager identityManager;
    private File workspaceRoot;

    @Before
    public void setUp() throws IOException {
        context = getApplicationContext();
        workspaceManager = new WorkspaceManager(context);
        workspaceManager.initializeWithSkills();
        identityManager = new IdentityManager(context, workspaceManager);
        workspaceRoot = workspaceManager.getWorkspaceRoot();
        identityManager.clearCache();
    }

    @After
    public void tearDown() throws IOException {
        restoreIdentityFilesFromAssets();
        if (identityManager != null) {
            identityManager.clearCache();
        }
    }

    private void restoreIdentityFilesFromAssets() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());

        if (soulFile.exists()) {
            soulFile.delete();
        }
        if (userFile.exists()) {
            userFile.delete();
        }

        workspaceManager.initializeWithSkills();
    }

    @Test
    public void identityFilesExist_afterInitialization_returnsTrue() {
        assertTrue("Identity files should exist after initialization",
                identityManager.identityFilesExist());
    }

    @Test
    public void getIdentityMessages_withExistingFiles_returnsSystemMessages() throws IOException {
        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertNotNull("Should return message list", messages);
        assertFalse("Should have at least one message", messages.isEmpty());

        for (ChatMessage message : messages) {
            assertEquals("All identity messages should be TYPE_SYSTEM",
                    ChatMessage.TYPE_SYSTEM, message.getType());
            assertTrue("System messages should have content",
                    message.getContent() != null && !message.getContent().trim().isEmpty());
        }
    }

    @Test
    public void getIdentityMessages_cachesBetweenCalls_returnsSameContent() throws IOException {
        List<ChatMessage> firstCall = identityManager.getIdentityMessages();
        List<ChatMessage> secondCall = identityManager.getIdentityMessages();

        assertEquals("Cached calls should return same number of messages",
                firstCall.size(), secondCall.size());

        for (int i = 0; i < firstCall.size(); i++) {
            assertEquals("Cached messages should have same content",
                    firstCall.get(i).getContent(), secondCall.get(i).getContent());
        }
    }

    @Test
    public void clearCache_forcesReload_loadsNewContent() throws IOException {
        List<ChatMessage> initialMessages = identityManager.getIdentityMessages();
        String initialContent = initialMessages.get(0).getContent();

        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("# Modified Soul\nThis is updated content.");
        }

        identityManager.clearCache();

        List<ChatMessage> newMessages = identityManager.getIdentityMessages();
        String newContent = newMessages.get(0).getContent();

        assertFalse("Content should change after cache clear and file modification",
                initialContent.equals(newContent));
        assertTrue("New content should contain updated text",
                newContent.contains("Modified Soul"));
    }

    @Test
    public void loadIdentity_withBothFiles_returnsCompleteContext() throws IOException {
        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertNotNull("Identity context should not be null", identity);
        assertTrue("Should have soul content", identity.hasSoul());
        assertTrue("Should have user content", identity.hasUser());
        assertFalse("Identity should not be empty", identity.isEmpty());

        assertNotNull("Soul content should not be null", identity.getSoulContent());
        assertNotNull("User content should not be null", identity.getUserContent());
    }

    @Test
    public void loadIdentity_withMissingSoulFile_returnsEmptySoul() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        soulFile.delete();

        identityManager.clearCache();

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Should not have soul content when file missing", identity.hasSoul());
        assertTrue("Should still have user content", identity.hasUser());
    }

    @Test
    public void loadIdentity_withMissingUserFile_returnsEmptyUser() throws IOException {
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());
        userFile.delete();

        identityManager.clearCache();

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertTrue("Should have soul content", identity.hasSoul());
        assertFalse("Should not have user content when file missing", identity.hasUser());
    }

    @Test
    public void loadIdentity_withEmptySoulFile_hasEmptySoul() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("");
        }

        identityManager.clearCache();

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Empty file should result in hasSoul() = false", identity.hasSoul());
    }

    @Test
    public void loadIdentity_withWhitespaceOnlySoulFile_hasEmptySoul() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("   \n\t\n   ");
        }

        identityManager.clearCache();

        IdentityManager.IdentityContext identity = identityManager.loadIdentity();

        assertFalse("Whitespace-only file should result in hasSoul() = false", identity.hasSoul());
    }

    @Test
    public void getIdentityMessages_withEmptyFiles_returnsEmptyList() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());

        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("");
        }
        try (FileWriter writer = new FileWriter(userFile)) {
            writer.write("");
        }

        identityManager.clearCache();

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertTrue("Should return empty list when both files are empty", messages.isEmpty());
    }

    @Test
    public void identityContext_isEmpty_correctlyDetectsEmptyState() {
        IdentityManager.IdentityContext emptyContext = new IdentityManager.IdentityContext("", "");
        assertTrue("Context with empty strings should be empty", emptyContext.isEmpty());

        IdentityManager.IdentityContext whitespaceContext = new IdentityManager.IdentityContext("  ", "\n\t");
        assertTrue("Context with whitespace should be empty", whitespaceContext.isEmpty());

        IdentityManager.IdentityContext nullContext = new IdentityManager.IdentityContext(null, null);
        assertTrue("Context with null values should be empty", nullContext.isEmpty());

        IdentityManager.IdentityContext partialContext = new IdentityManager.IdentityContext("content", "");
        assertFalse("Context with one non-empty value should not be empty", partialContext.isEmpty());
    }

    @Test
    public void getIdentityMessages_preservesMessageOrder_soulBeforeUser() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());

        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("SOUL_CONTENT");
        }
        try (FileWriter writer = new FileWriter(userFile)) {
            writer.write("USER_CONTENT");
        }

        identityManager.clearCache();

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertEquals("Should have exactly 2 messages", 2, messages.size());
        assertTrue("First message should be soul content",
                messages.get(0).getContent().contains("SOUL_CONTENT"));
        assertTrue("Second message should be user content",
                messages.get(1).getContent().contains("USER_CONTENT"));
    }

    @Test
    public void getIdentityMessages_withOnlySoul_returnsSingleMessage() throws IOException {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());

        try (FileWriter writer = new FileWriter(soulFile)) {
            writer.write("ONLY_SOUL");
        }
        try (FileWriter writer = new FileWriter(userFile)) {
            writer.write("");
        }

        identityManager.clearCache();

        List<ChatMessage> messages = identityManager.getIdentityMessages();

        assertEquals("Should have exactly 1 message", 1, messages.size());
        assertTrue("Message should be soul content",
                messages.get(0).getContent().contains("ONLY_SOUL"));
    }

    @Test
    public void identityFilesExist_withMissingFiles_returnsFalse() {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());
        soulFile.delete();
        userFile.delete();

        assertFalse("Should return false when files don't exist",
                identityManager.identityFilesExist());
    }

    @Test
    public void identityFilesExist_withOnlyOneMissing_returnsFalse() {
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        soulFile.delete();

        assertFalse("Should return false when only one file exists",
                identityManager.identityFilesExist());
    }

    @Test
    public void toApiMessage_systemMessage_hasCorrectFormat() throws IOException {
        List<ChatMessage> messages = identityManager.getIdentityMessages();
        assertFalse("Should have messages", messages.isEmpty());

        ChatMessage systemMessage = messages.get(0);
        com.google.gson.JsonObject apiMessage = systemMessage.toApiMessage();

        assertEquals("Should have system role",
                "system", apiMessage.get("role").getAsString());
        assertNotNull("Should have content",
                apiMessage.get("content"));
        assertFalse("Content should not be empty",
                apiMessage.get("content").getAsString().isEmpty());
    }
}
