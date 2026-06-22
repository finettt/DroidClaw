package io.finett.droidclaw.connectivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

/**
 * Information about a peer in the agent-to-agent network.
 * <p>
 * Exchanged during the HELLO / HELLO_ACK handshake to identify
 * the remote agent and its capabilities.
 * </p>
 */
public class PeerInfo {

    private final String agentId;
    private final String deviceName;
    private final String address;
    private final String transportType;
    private final String agentVersion;
    private final List<String> capabilities;
    private final JsonObject metadata;

    /**
     * Constructs a new PeerInfo.
     *
     * @param agentId       unique identifier for this agent instance
     * @param deviceName    human-readable device name
     * @param address       transport address of the peer
     * @param transportType transport type identifier
     * @param agentVersion  version string of the agent software
     * @param capabilities  list of capability identifiers (may be null)
     * @param metadata      arbitrary metadata as a JsonObject (may be null)
     */
    public PeerInfo(String agentId, String deviceName, String address,
                    String transportType, String agentVersion,
                    List<String> capabilities, JsonObject metadata) {
        this.agentId = agentId;
        this.deviceName = deviceName;
        this.address = address;
        this.transportType = transportType;
        this.agentVersion = agentVersion;
        this.capabilities = capabilities != null
                ? Collections.unmodifiableList(capabilities)
                : Collections.emptyList();
        this.metadata = metadata != null ? metadata.deepCopy() : null;
    }

    /**
     * Returns the unique agent identifier.
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns the human-readable device name.
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Returns the transport address of the peer.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns the transport type identifier.
     */
    public String getTransportType() {
        return transportType;
    }

    /**
     * Returns the agent software version string.
     */
    public String getAgentVersion() {
        return agentVersion;
    }

    /**
     * Returns the list of capability identifiers.
     */
    public List<String> getCapabilities() {
        return capabilities;
    }

    /**
     * Returns a deep copy of the metadata, or null if none was set.
     */
    public JsonObject getMetadata() {
        return metadata != null ? metadata.deepCopy() : null;
    }

    /**
     * Serializes this PeerInfo to its JSON string representation.
     */
    public String toJson() {
        return getGson().toJson(this);
    }

    /**
     * Deserializes a PeerInfo from its JSON string representation.
     *
     * @param json the JSON string
     * @return the deserialized PeerInfo
     */
    public static PeerInfo fromJson(String json) {
        return getGson().fromJson(json, PeerInfo.class);
    }

    private static Gson getGson() {
        return new GsonBuilder()
                .setLenient()
                .create();
    }
}
