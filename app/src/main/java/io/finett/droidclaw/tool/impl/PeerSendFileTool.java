package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.AgentMessage;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Sends a file from the local workspace to a connected peer agent.
 *
 * <p>The file is read from the workspace root, validated for size and path
 * constraints, then chunked into 64 KB {@link AgentMessage.Type#FILE_CHUNK}
 * messages and sent over the peer connection. A
 * {@link AgentMessage.Type#FILE_TRANSFER_REQUEST} is sent first, followed by
 * the chunked data, and finally a {@link AgentMessage.Type#FILE_COMPLETE}
 * message.
 *
 * <p>This operation requires user approval due to its potential to consume
 * bandwidth and expose file contents.
 */
public class PeerSendFileTool implements Tool {

    private static final String NAME = "send_file";
    private static final int CHUNK_SIZE = 64 * 1024; // 64 KB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB limit

    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;
    private final File workspaceRoot;

    public PeerSendFileTool(VirtualFileSystem vfs, File workspaceRoot) {
        this.vfs = vfs;
        this.workspaceRoot = workspaceRoot;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("target_agent_id",
                        "The connection ID of the peer agent to send the file to. "
                        + "Obtain connection IDs from the list_connected_agents tool.",
                        true)
                .addString("file_path",
                        "Path to the file relative to the workspace root.",
                        true)
                .build();

        return new ToolDefinition(
                NAME,
                "Send a file from the local workspace to a connected peer agent. "
                + "The file is validated, chunked into 64 KB blocks, and transferred "
                + "over the peer connection. The peer must accept the transfer.",
                parameters
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String getApprovalDescription(JsonObject arguments) {
        String filePath = arguments.has("file_path")
                ? arguments.get("file_path").getAsString() : "unknown file";
        String targetId = arguments.has("target_agent_id")
                ? arguments.get("target_agent_id").getAsString() : "unknown peer";
        return "Send file to peer agent (" + targetId + "):\n" + filePath;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("target_agent_id")) {
            return ToolResult.error("Missing required argument: target_agent_id");
        }
        if (!arguments.has("file_path")) {
            return ToolResult.error("Missing required argument: file_path");
        }

        String targetAgentId = arguments.get("target_agent_id").getAsString().trim();
        String filePath = arguments.get("file_path").getAsString().trim();

        if (targetAgentId.isEmpty()) {
            return ToolResult.error("target_agent_id must not be empty");
        }
        if (filePath.isEmpty()) {
            return ToolResult.error("file_path must not be empty");
        }

        // Validate file exists and get metadata
        VirtualFileSystem.FileInfo fileInfo;
        try {
            fileInfo = vfs.getFileInfo(filePath);
            if (fileInfo.isDirectory()) {
                return ToolResult.error("Cannot send a directory: " + filePath);
            }
        } catch (SecurityException e) {
            return ToolResult.error("Security error accessing file: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("File not found or inaccessible: " + filePath
                    + " (" + e.getMessage() + ")");
        }

        // Check file size limit
        long fileSize = fileInfo.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            return ToolResult.error("File too large to send: " + fileSize
                    + " bytes (max " + MAX_FILE_SIZE + " bytes)");
        }

        // Resolve full path and read file content
        File physicalFile = new File(workspaceRoot, filePath);
        if (!physicalFile.exists() || !physicalFile.isFile()) {
            return ToolResult.error("File does not exist on disk: " + physicalFile.getAbsolutePath());
        }

        // Look up peer connection
        ConnectionManager cm = ConnectionManager.getInstance();
        AgentConnection connection = cm.getConnection(targetAgentId);
        if (connection == null) {
            return ToolResult.error("No connection found with id: " + targetAgentId);
        }
        if (!connection.isAlive()) {
            return ToolResult.error("Connection " + targetAgentId + " is not alive");
        }

        String transferId = UUID.randomUUID().toString();
        String fileName = physicalFile.getName();

        try {
            byte[] fileBytes = readFileBytes(physicalFile);

            // Send FILE_TRANSFER_REQUEST
            JsonObject requestPayload = new JsonObject();
            requestPayload.addProperty("transfer_id", transferId);
            requestPayload.addProperty("file_name", fileName);
            requestPayload.addProperty("file_size", fileSize);
            requestPayload.addProperty("mime_type", detectMimeType(fileName));

            AgentMessage requestMsg = new AgentMessage.Builder()
                    .type(AgentMessage.Type.FILE_TRANSFER_REQUEST.name())
                    .senderId("local-agent")
                    .payload(requestPayload)
                    .build();
            connection.sendMessage(requestMsg);

            // Chunk and send file data
            String encodedContent = Base64.getEncoder().encodeToString(fileBytes);
            int encodedLen = encodedContent.length();
            int totalChunks = (int) Math.ceil((double) encodedLen / CHUNK_SIZE);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, encodedLen);
                String chunk = encodedContent.substring(start, end);

                JsonObject chunkPayload = new JsonObject();
                chunkPayload.addProperty("transfer_id", transferId);
                chunkPayload.addProperty("chunk_index", i);
                chunkPayload.addProperty("total_chunks", totalChunks);
                chunkPayload.addProperty("data", chunk);

                AgentMessage chunkMsg = new AgentMessage.Builder()
                        .type(AgentMessage.Type.FILE_CHUNK.name())
                        .senderId("local-agent")
                        .payload(chunkPayload)
                        .build();
                connection.sendMessage(chunkMsg);
            }

            // Send FILE_COMPLETE
            JsonObject completePayload = new JsonObject();
            completePayload.addProperty("transfer_id", transferId);
            completePayload.addProperty("file_name", fileName);
            completePayload.addProperty("total_chunks", totalChunks);
            completePayload.addProperty("total_bytes", fileBytes.length);

            AgentMessage completeMsg = new AgentMessage.Builder()
                    .type(AgentMessage.Type.FILE_COMPLETE.name())
                    .senderId("local-agent")
                    .payload(completePayload)
                    .build();
            connection.sendMessage(completeMsg);

            JsonObject result = new JsonObject();
            result.addProperty("transfer_id", transferId);
            result.addProperty("file_name", fileName);
            result.addProperty("file_size", fileBytes.length);
            result.addProperty("total_chunks", totalChunks);
            result.addProperty("target_agent_id", targetAgentId);
            result.addProperty("sent", true);

            return ToolResult.success(result);

        } catch (IOException e) {
            return ToolResult.error("Failed to read file for transfer: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to send file: " + e.getMessage());
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("File too large to read into memory");
        }
        byte[] buffer = new byte[(int) fileSize];
        try (InputStream is = new FileInputStream(file)) {
            int totalRead = 0;
            while (totalRead < buffer.length) {
                int read = is.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of stream");
                }
                totalRead += read;
            }
        }
        return buffer;
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml") || lower.endsWith(".html")) return "text/xml";
        if (lower.endsWith(".java") || lower.endsWith(".kt")) return "text/plain";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
