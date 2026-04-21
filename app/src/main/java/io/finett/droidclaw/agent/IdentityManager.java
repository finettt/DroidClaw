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
    
    public IdentityContext loadIdentity() throws IOException {
        String soulContent = readIdentityFile(WorkspaceManager.getSoulFilePath());
        String userContent = readIdentityFile(WorkspaceManager.getUserFilePath());
        
        return new IdentityContext(soulContent, userContent);
    }
    
    public boolean identityFilesExist() {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File soulFile = new File(workspaceRoot, WorkspaceManager.getSoulFilePath());
        File userFile = new File(workspaceRoot, WorkspaceManager.getUserFilePath());
        
        return soulFile.exists() && userFile.exists();
    }
    
    public void clearCache() {
        cachedIdentity = null;
        Log.d(TAG, "Identity cache cleared");
    }
    
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
        
        return messages;
    }
    
    public static class IdentityContext {
        private final String soulContent;
        private final String userContent;
        
        public IdentityContext(String soulContent, String userContent) {
            this.soulContent = soulContent;
            this.userContent = userContent;
        }
        
        public String getSoulContent() {
            return soulContent;
        }
        
        public String getUserContent() {
            return userContent;
        }
        
        public boolean hasSoul() {
            return soulContent != null && !soulContent.trim().isEmpty();
        }
        
        public boolean hasUser() {
            return userContent != null && !userContent.trim().isEmpty();
        }
        
        public boolean isEmpty() {
            return !hasSoul() && !hasUser();
        }
    }
}