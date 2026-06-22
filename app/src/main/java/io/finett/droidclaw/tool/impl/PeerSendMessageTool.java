package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.AgentMessage;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Sends a chat message to a connected peer agent.
 *
 * <p>Looks up the connection by target agent (connection) ID, creates a CHAT
 * {@link AgentMessage}, and enqueues it for delivery. Returns success or failure
 * (but does not guarantee the peer received it — the message is merely enqueued).
 */
public class PeerSendMessageTool implements Tool {

    private static final String NAME = "send_message";
    private final ToolDefinition definition;

    public PeerSendMessageTool() {
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("target_agent_id",
                        "The connection ID of the peer agent to send the message to. "
                        + "Obtain connection IDs from the list_connected_agents tool.",
                        true)
                .addString("message",
                        "The text content of the chat message to send.",
                        true)
                .build();

        return new ToolDefinition(
                NAME,
                "Send a chat message to a connected peer agent. "
                + "The message is enqueued for delivery via the peer connection.",
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
        return false;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("target_agent_id")) {
            return ToolResult.error("Missing required argument: target_agent_id");
        }
        if (!arguments.has("message")) {
            return ToolResult.error("Missing required argument: message");
        }

        String targetAgentId = arguments.get("target_agent_id").getAsString().trim();
        String message = arguments.get("message").getAsString();

        if (targetAgentId.isEmpty()) {
            return ToolResult.error("target_agent_id must not be empty");
        }
        if (message.isEmpty()) {
            return ToolResult.error("message must not be empty");
        }

        ConnectionManager cm = ConnectionManager.getInstance();
        AgentConnection connection = cm.getConnection(targetAgentId);
        if (connection == null) {
            return ToolResult.error("No connection found with id: " + targetAgentId
                    + ". Use list_connected_agents to see active connections.");
        }

        if (!connection.isAlive()) {
            return ToolResult.error("Connection " + targetAgentId + " is not alive (state: "
                    + connection.getState() + ")");
        }

        String senderId = "local-agent";
        AgentMessage chatMsg = AgentMessage.createChat(senderId, message);
        connection.sendMessage(chatMsg);

        JsonObject result = new JsonObject();
        result.addProperty("sent", true);
        result.addProperty("target_agent_id", targetAgentId);
        result.addProperty("message_id", chatMsg.getId());
        result.addProperty("message_length", message.length());

        return ToolResult.success(result);
    }
}
