package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.connectivity.PeerInfo;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Lists all currently connected peer agents.
 *
 * <p>Returns a JSON array with details for each active connection including
 * the connection ID (for use with {@link PeerSendMessageTool} and other
 * peer-targeted tools), peer identity information, transport type, and
 * connection duration.
 */
public class PeerListConnectionsTool implements Tool {

    private static final String NAME = "list_connected_agents";
    private final ToolDefinition definition;

    public PeerListConnectionsTool() {
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .build();

        return new ToolDefinition(
                NAME,
                "List all currently connected peer agents. "
                + "Returns connection ID, peer agent ID, device name, transport type, "
                + "and connection duration for each active peer connection.",
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
        ConnectionManager cm = ConnectionManager.getInstance();
        List<AgentConnection> connections = cm.getAllConnections();

        JsonArray peersArray = new JsonArray();

        for (AgentConnection conn : connections) {
            JsonObject peerJson = new JsonObject();
            peerJson.addProperty("connection_id", conn.getConnectionId());
            peerJson.addProperty("peer_address", conn.getPeerAddress());
            peerJson.addProperty("alive", conn.isAlive());
            peerJson.addProperty("state", conn.getState().name());

            // Transport info
            if (conn.getTransport() != null) {
                peerJson.addProperty("transport_type", conn.getTransport().getType());
                peerJson.addProperty("transport_name", conn.getTransport().getDisplayName());
            } else {
                peerJson.addProperty("transport_type", "unknown");
            }

            // Peer info from handshake
            PeerInfo peerInfo = conn.getPeerInfo();
            if (peerInfo != null) {
                peerJson.addProperty("agent_id", peerInfo.getAgentId());
                peerJson.addProperty("device_name", peerInfo.getDeviceName());
                peerJson.addProperty("agent_version", peerInfo.getAgentVersion());
            }

            // Connection duration
            long now = System.currentTimeMillis();
            long lastActivity = conn.getLastActivityTimestamp();
            long durationMs = now - lastActivity;
            peerJson.addProperty("last_activity_ms_ago", durationMs);
            peerJson.addProperty("duration_formatted", formatDuration(durationMs));

            peersArray.add(peerJson);
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", connections.size());
        result.add("connections", peersArray);

        return ToolResult.success(result);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }
}
