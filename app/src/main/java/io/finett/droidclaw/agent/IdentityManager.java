package io.finett.droidclaw.agent;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;

/**
 * Manages loading and formatting of identity documents (soul.md, user.md, and relationships.md).
 * These files provide the agent with persistent identity, user knowledge, and agent relationship
 * context across sessions.
 */
public class IdentityManager {
    private static final String TAG = "IdentityManager";

    private final Context context;
    private final WorkspaceManager workspaceManager;

    // Cached identity context for session
    private IdentityContext cachedIdentity;
    
    public IdentityManager(Context context, WorkspaceManager workspaceManager) {
        this.context = context;
        this.workspaceManager = workspaceManager;
    }
    
    /**
     * Loads identity files and returns them as system messages for the LLM.
     * Results are cached per session to avoid repeated file reads.
     *
     * @return List of ChatMessage with TYPE_SYSTEM containing identity context
     * @throws IOException if files cannot be read
     */
    public List<ChatMessage> getIdentityMessages() throws IOException {
        // Return cached identity if available
        if (cachedIdentity != null) {
            return createMessagesFromContext(cachedIdentity);
        }

        // Load identity files
        IdentityContext identity = loadIdentity();
        cachedIdentity = identity;

        return createMessagesFromContext(identity);
    }
    
    /**
     * Loads identity files from the workspace (soul.md, user.md, and relationships.md).
     *
     * @return IdentityContext containing content of all identity files
     * @throws IOException if files cannot be read
     */
    public IdentityContext loadIdentity() throws IOException {
        String soulContent = readIdentityFile(WorkspaceManager.getSoulFilePath());
        String userContent = readIdentityFile(WorkspaceManager.getUserFilePath());
        String relationshipsContent = readIdentityFile(WorkspaceManager.getRelationshipsFilePath());

        return new IdentityContext(soulContent, userContent, relationshipsContent);
    }
    
    /**
     * Checks if identity files exist in the workspace.
     *
     * @return true if soul.md and user.md exist (relationships.md is optional)
     */
    public boolean identityFilesExist() {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());

        return soulFile.exists() && userFile.exists();
    }
    
    /**
     * Clears the cached identity, forcing a reload on next access.
     * Call this when identity files are updated.
     */
    public void clearCache() {
        cachedIdentity = null;
        Log.d(TAG, "Identity cache cleared");
    }
    
    /**
     * Reads an identity file from the workspace.
     *
     * @param relativePath Path relative to workspace root
     * @return File contents as string
     * @throws IOException if file cannot be read
     */
    private String readIdentityFile(String relativePath) throws IOException {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File file = new File(workspaceRoot, relativePath);
        
        if (!file.exists()) {
            Log.w(TAG, "Identity file does not exist: " + relativePath);
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        Log.d(TAG, "Loaded identity file: " + relativePath + " (" + content.length() + " chars)");
        return content.toString();
    }

    /**
     * Creates ChatMessage list from IdentityContext.
     *
     * @param identity The identity context
     * @return List of system messages
     */
    private List<ChatMessage> createMessagesFromContext(IdentityContext identity) {
        List<ChatMessage> messages = new ArrayList<>();

        // Add soul.md as first system message if not empty
        if (identity.soulContent != null && !identity.soulContent.trim().isEmpty()) {
            ChatMessage soulMessage = new ChatMessage(identity.soulContent, ChatMessage.TYPE_SYSTEM);
            messages.add(soulMessage);
        }

        // Add user.md as second system message if not empty
        if (identity.userContent != null && !identity.userContent.trim().isEmpty()) {
            ChatMessage userMessage = new ChatMessage(identity.userContent, ChatMessage.TYPE_SYSTEM);
            messages.add(userMessage);
        }

        // Add relationships.md as third system message if not empty
        if (identity.relationshipsContent != null && !identity.relationshipsContent.trim().isEmpty()) {
            ChatMessage relationshipsMessage = new ChatMessage(identity.relationshipsContent, ChatMessage.TYPE_SYSTEM);
            messages.add(relationshipsMessage);
        }

        return messages;
    }

    /**
     * Container for identity file contents (soul.md, user.md, and relationships.md).
     */
    public static class IdentityContext {
        private final String soulContent;
        private final String userContent;
        private final String relationshipsContent;

        public IdentityContext(String soulContent, String userContent, String relationshipsContent) {
            this.soulContent = soulContent;
            this.userContent = userContent;
            this.relationshipsContent = relationshipsContent;
        }

        public String getSoulContent() {
            return soulContent;
        }

        public String getUserContent() {
            return userContent;
        }

        public String getRelationshipsContent() {
            return relationshipsContent;
        }

        public boolean hasSoul() {
            return soulContent != null && !soulContent.trim().isEmpty();
        }

        public boolean hasUser() {
            return userContent != null && !userContent.trim().isEmpty();
        }

        public boolean hasRelationships() {
            return relationshipsContent != null && !relationshipsContent.trim().isEmpty();
        }

        public boolean isEmpty() {
            return !hasSoul() && !hasUser() && !hasRelationships();
        }
    }
}