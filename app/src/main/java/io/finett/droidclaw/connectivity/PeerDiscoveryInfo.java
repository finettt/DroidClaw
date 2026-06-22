package io.finett.droidclaw.connectivity;

/**
 * Information about a discovered peer available for agent-to-agent communication.
 */
public class PeerDiscoveryInfo {
    private final String address;
    private final String displayName;
    private final String transportType;

    /**
     * Constructs a new PeerDiscoveryInfo.
     *
     * @param address        the address of the peer (e.g., MAC address or IP address)
     * @param displayName    a human-readable name for the peer (e.g., Bluetooth device name)
     * @param transportType  the transport type (e.g., Transport.TYPE_BLUETOOTH)
     */
    public PeerDiscoveryInfo(String address, String displayName, String transportType) {
        this.address = address;
        this.displayName = displayName;
        this.transportType = transportType;
    }

    /**
     * Returns the address of the discovered peer.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns a human-readable display name for the discovered peer.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the transport type identifier.
     */
    public String getTransportType() {
        return transportType;
    }
}
