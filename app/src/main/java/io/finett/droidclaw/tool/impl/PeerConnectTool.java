package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.connectivity.DiscoveryManager;
import io.finett.droidclaw.connectivity.PeerInfo;
import io.finett.droidclaw.connectivity.Transport;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Connects to a discovered peer agent.
 *
 * <p>Accepts an address in the format {@code transport://address} (e.g.,
 * {@code bluetooth://00:11:22:33:44:55} or {@code tcp://192.168.1.42:9876})
 * and an optional explicit transport type. Parses the address, finds the
 * matching {@link Transport} in the {@link DiscoveryManager}, and initiates
 * an outbound connection.
 *
 * <p>This operation requires user approval because it establishes a
 * communication channel to a remote device.
 */
public class PeerConnectTool implements Tool {

    private static final String NAME = "connect_to_agent";

    private final Context context;
    private final ToolDefinition definition;

    public PeerConnectTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("address",
                        "The address of the peer agent to connect to. "
                        + "Format: \"transport://address\" where transport is one of "
                        + "\"bluetooth\" or \"tcp\". "
                        + "Examples: \"bluetooth://00:11:22:33:44:55\", "
                        + "\"tcp://192.168.1.42:9876\". "
                        + "The address can be obtained from discover_nearby_agents.",
                        true)
                .addString("transport_type",
                        "Optional explicit transport type override "
                        + "(e.g., \"bluetooth\" or \"tcp\"). "
                        + "If not provided, the transport is inferred from the address prefix.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Connect to a discovered peer agent. "
                + "Establishes an agent-to-agent connection using the specified "
                + "transport and address. The handshake is initiated automatically.",
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
        String address = arguments.has("address")
                ? arguments.get("address").getAsString() : "unknown address";
        return "Connect to peer agent at:\n" + address;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        if (!arguments.has("address")) {
            return ToolResult.error("Missing required argument: address");
        }

        String address = arguments.get("address").getAsString().trim();
        if (address.isEmpty()) {
            return ToolResult.error("address must not be empty");
        }

        // Determine transport type
        String transportType = arguments.has("transport_type")
                ? arguments.get("transport_type").getAsString().trim()
                : null;

        String connectAddress = address;

        // Parse "transport://addr" format
        if (address.contains("://")) {
            String[] parts = address.split("://", 2);
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                if (transportType == null) {
                    transportType = normalizeTransportType(parts[0]);
                }
                connectAddress = parts[1];
            }
        }

        // Default to bluetooth if no transport type could be determined
        if (transportType == null || transportType.isEmpty()) {
            transportType = Transport.TYPE_BLUETOOTH;
        }

        // Validate transport type
        if (!Transport.TYPE_BLUETOOTH.equals(transportType)
                && !Transport.TYPE_TCP.equals(transportType)) {
            return ToolResult.error("Unsupported transport type: " + transportType
                    + ". Supported types: \"bluetooth\", \"tcp\"");
        }

        DiscoveryManager dm = DiscoveryManager.getInstance(context);

        // Ensure transports are initialized and running
        if (dm.getActiveTransports().isEmpty()) {
            // Auto-initialize from settings when no transports configured yet
            initTransportsFromSettings(dm);
        }
        if (dm.getActiveTransports().isEmpty()) {
            return ToolResult.error("No transports configured. "
                    + "Enable agent-to-agent communication in settings first.");
        }
        if (!dm.isRunning()) {
            dm.startAll();
        }

        // Attempt connection
        AgentConnection connection = dm.connectToPeer(connectAddress, transportType);
        if (connection == null) {
            return ToolResult.error("Failed to connect to " + connectAddress
                    + " via " + transportType
                    + ". Check that the peer is reachable and the transport is available.");
        }

        // Build result
        JsonObject result = new JsonObject();
        result.addProperty("connection_id", connection.getConnectionId());
        result.addProperty("peer_address", connection.getPeerAddress());
        result.addProperty("transport_type", transportType);
        result.addProperty("state", connection.getState().name());
        result.addProperty("connected", connection.isAlive());

        PeerInfo peerInfo = connection.getPeerInfo();
        if (peerInfo != null) {
            result.addProperty("peer_agent_id", peerInfo.getAgentId());
            result.addProperty("peer_device_name", peerInfo.getDeviceName());
            result.addProperty("peer_agent_version", peerInfo.getAgentVersion());
        }

        return ToolResult.success(result);
    }

    /**
     * Maps a URL scheme or short name to a canonical transport type.
     */
    /**
     * Maps a URL scheme or short name to a canonical transport type.
     */
    private String normalizeTransportType(String raw) {
        if (raw == null) return Transport.TYPE_BLUETOOTH;
        String lower = raw.toLowerCase().trim();
        if ("bluetooth".equals(lower) || "bt".equals(lower)) {
            return Transport.TYPE_BLUETOOTH;
        }
        if ("tcp".equals(lower) || "wifi".equals(lower) || "network".equals(lower)
                || "ethernet".equals(lower)) {
            return Transport.TYPE_TCP;
        }
        return lower;
    }

    /**
     * Auto-initialize the DiscoveryManager with transports from settings
     * when no transports are configured yet. This allows the connect tool
     * to work without requiring AgentConnectivityService to be started first.
     */
    private void initTransportsFromSettings(DiscoveryManager dm) {
        try {
            SettingsManager settingsManager = new SettingsManager(context);
            String transportSetting = settingsManager.getAgentConfig().getDiscoveryTransport();
            int networkPort = settingsManager.getAgentConfig().getNetworkPort();

            List<Transport> transports = new ArrayList<>();

            if ("network".equals(transportSetting) || "auto".equals(transportSetting)) {
                transports.add(new io.finett.droidclaw.connectivity.NetworkTransport());
            }
            if ("bluetooth".equals(transportSetting) || "auto".equals(transportSetting)) {
                try {
                    transports.add(new io.finett.droidclaw.connectivity.BluetoothTransport());
                } catch (Exception ignored) {
                    // Bluetooth not available on this device (e.g. emulator)
                }
            }

            if (!transports.isEmpty()) {
                dm.initialize(transports);
            }
        } catch (Exception e) {
            android.util.Log.w("PeerConnectTool", "Failed to init transports from settings", e);
        }
    }
}
