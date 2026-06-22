package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.AgentMessage;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Accepts or rejects a pending file transfer from a peer agent.
 *
 * <p>Pending file transfers are registered by the incoming message handler
 * (the message listener on {@link AgentConnection}) and stored in a static
 * registry by transfer request ID. This tool allows the LLM agent to approve
 * or deny those pending transfers.
 *
 * <p>On accept, an optional {@code save_path} can be provided to override
 * where the received file should be saved in the workspace. On reject,
 * a {@link AgentMessage.Type#FILE_TRANSFER_REJECT} message is sent back to
 * the requesting peer.
 */
public class PeerReceiveFileControlTool implements Tool {

    private static final String NAME = "receive_file_control";

    /**
     * Static registry of pending file transfer requests.
     * Keyed by transfer request ID; value is a JsonObject with:
     * <ul>
     *   <li>{@code connection_id} — the connection the request arrived on</li>
     *   <li>{@code file_name} — original file name</li>
     *   <li>{@code file_size} — file size in bytes</li>
     *   <li>{@code sender_id} — the sending agent's ID</li>
     * </ul>
     */
    private static final ConcurrentHashMap<String, JsonObject> pendingTransfers = new ConcurrentHashMap<>();

    private final ToolDefinition definition;

    public PeerReceiveFileControlTool() {
        this.definition = createDefinition();
    }

    /**
     * Register a pending file transfer request so it can be accepted or rejected
     * via this tool. Called by the incoming message handler when a
     * FILE_TRANSFER_REQUEST arrives.
     *
     * @param transferId   the unique transfer ID
     * @param requestInfo  metadata JSON object describing the request
     */
    public static void registerPendingTransfer(String transferId, JsonObject requestInfo) {
        if (transferId != null && requestInfo != null) {
            pendingTransfers.put(transferId, requestInfo);
        }
    }

    /**
     * Remove a pending transfer from the registry (e.g. after it has been
     * accepted, rejected, or timed out).
     *
     * @param transferId the transfer ID to remove
     * @return the removed request info, or null if not found
     */
    public static JsonObject removePendingTransfer(String transferId) {
        return transferId != null ? pendingTransfers.remove(transferId) : null;
    }

    /**
     * Returns the number of currently pending file transfers.
     */
    public static int getPendingTransferCount() {
        return pendingTransfers.size();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("request_id",
                        "The ID of the file transfer request to accept or reject. "
                        + "This is obtained from incoming messages or from the agent's context.",
                        true)
                .addString("action",
                        "The action to take: \"accept\" to receive the file, or \"reject\" "
                        + "to decline the transfer.",
                        true)
                .addString("save_path",
                        "Optional path (relative to workspace root) where to save the "
                        + "received file. If not provided, the original file name from "
                        + "the request will be used in the workspace root.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Accept or reject a pending file transfer from a peer agent. "
                + "When accepted, the peer will begin sending FILE_CHUNK messages. "
                + "When rejected, a rejection message is sent to the peer.",
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
        String action = arguments.has("action") ? arguments.get("action").getAsString() : "unknown";
        String requestId = arguments.has("request_id")
                ? arguments.get("request_id").getAsString() : "unknown";
        return "File transfer " + action + " for request:\n" + requestId;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("request_id")) {
            return ToolResult.error("Missing required argument: request_id");
        }
        if (!arguments.has("action")) {
            return ToolResult.error("Missing required argument: action");
        }

        String requestId = arguments.get("request_id").getAsString().trim();
        String action = arguments.get("action").getAsString().trim().toLowerCase();
        String savePath = arguments.has("save_path")
                ? arguments.get("save_path").getAsString().trim() : null;

        if (requestId.isEmpty()) {
            return ToolResult.error("request_id must not be empty");
        }
        if (!action.equals("accept") && !action.equals("reject")) {
            return ToolResult.error("action must be \"accept\" or \"reject\", got: " + action);
        }

        // Look up the pending transfer
        JsonObject requestInfo = pendingTransfers.get(requestId);
        if (requestInfo == null) {
            return ToolResult.error("No pending file transfer found with request_id: " + requestId
                    + ". The request may have already been handled or timed out.");
        }

        String connectionId = requestInfo.has("connection_id")
                ? requestInfo.get("connection_id").getAsString() : null;
        if (connectionId == null) {
            pendingTransfers.remove(requestId);
            return ToolResult.error("Pending transfer has no associated connection");
        }

        ConnectionManager cm = ConnectionManager.getInstance();
        AgentConnection connection = cm.getConnection(connectionId);
        if (connection == null || !connection.isAlive()) {
            pendingTransfers.remove(requestId);
            return ToolResult.error("Connection for transfer " + requestId + " is no longer active");
        }

        JsonObject result = new JsonObject();
        result.addProperty("request_id", requestId);
        result.addProperty("action", action);
        result.addProperty("handled", true);

        if ("accept".equals(action)) {
            // Send FILE_TRANSFER_ACCEPT
            JsonObject acceptPayload = new JsonObject();
            acceptPayload.addProperty("transfer_id", requestId);
            if (savePath != null && !savePath.isEmpty()) {
                acceptPayload.addProperty("save_path", savePath);
                result.addProperty("save_path", savePath);
            }

            AgentMessage acceptMsg = new AgentMessage.Builder()
                    .type(AgentMessage.Type.FILE_TRANSFER_ACCEPT.name())
                    .senderId("local-agent")
                    .payload(acceptPayload)
                    .build();
            connection.sendMessage(acceptMsg);

            // Keep the pending request so the incoming handler can match chunks
            result.addProperty("message", "File transfer accepted. Waiting for chunks from peer.");

        } else {
            // Send FILE_TRANSFER_REJECT
            JsonObject rejectPayload = new JsonObject();
            rejectPayload.addProperty("transfer_id", requestId);

            AgentMessage rejectMsg = new AgentMessage.Builder()
                    .type(AgentMessage.Type.FILE_TRANSFER_REJECT.name())
                    .senderId("local-agent")
                    .payload(rejectPayload)
                    .build();
            connection.sendMessage(rejectMsg);

            // Remove from pending registry
            pendingTransfers.remove(requestId);
            result.addProperty("message", "File transfer rejected.");
        }

        return ToolResult.success(result);
    }
}
