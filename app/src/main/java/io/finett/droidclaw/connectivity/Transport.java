package io.finett.droidclaw.connectivity;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Transport abstraction for agent-to-agent communication.
 * <p>
 * Implementations provide connectivity over different physical or virtual links
 * such as Bluetooth RFCOMM, TCP sockets, or USB. Each transport can act as
 * both a server (accepting incoming connections) and a client (initiating
 * outgoing connections), and may optionally support peer discovery.
 * </p>
 */
public interface Transport {

    String TYPE_BLUETOOTH = "bluetooth";
    String TYPE_TCP = "tcp";

    /**
     * Returns the transport type identifier.
     */
    String getType();

    /**
     * Starts listening for incoming connections on the given port.
     * <p>
     * The implementation SHOULD spawn a daemon background thread for the accept
     * loop. Each accepted connection is reported via {@link AcceptCallback#onAccepted}.
     * If the server cannot be started (e.g., permission denied, adapter off),
     * the implementation MUST call {@link AcceptCallback#onError}.
     * </p>
     *
     * @param port     the port to listen on (ignored by transports with fixed ports,
     *                 such as Bluetooth)
     * @param callback the callback for accepted connections and errors
     * @throws IOException if the server socket cannot be created
     */
    void startServer(int port, AcceptCallback callback) throws IOException;

    /**
     * Connects to a remote peer at the given address and port.
     * <p>
     * The format of {@code address} is transport-dependent (e.g., a MAC address
     * for Bluetooth, an IP address for TCP).
     * </p>
     *
     * @param address the address of the remote peer
     * @param port    the port to connect to (ignored by transports with fixed ports)
     * @return an {@link AgentConnection} wrapping the I/O streams
     * @throws IOException if the connection fails
     */
    AgentConnection connect(String address, int port) throws IOException;

    /**
     * Starts peer discovery for this transport.
     * <p>
     * Results are reported via the provided callback. If discovery is already
     * in progress, this call SHOULD be a no-op.
     * </p>
     *
     * @param callback the callback for discovered peers and lifecycle events
     */
    void startDiscovery(DiscoveryCallback callback);

    /**
     * Stops any ongoing peer discovery. Safe to call even if not discovering.
     */
    void stopDiscovery();

    /**
     * Stops the server and releases all associated resources.
     * Safe to call even if the server is not running.
     */
    void stopServer();

    /**
     * Returns whether the server is currently running and accepting connections.
     */
    boolean isServerRunning();

    /**
     * Returns whether this transport supports peer discovery.
     */
    boolean supportsDiscovery();

    /**
     * Returns whether this transport is currently available on the device.
     * <p>
     * This checks hardware availability (e.g., Bluetooth adapter present and
     * enabled) but does NOT check runtime permissions.
     * </p>
     *
     * @param context a Android context
     * @return true if the transport can be used
     */
    boolean isAvailable(Context context);

    /**
     * Returns the array of Android permissions required to use this transport.
     * <p>
     * Permissions are grouped by API level: the caller should request only those
     * that apply to the current SDK version.
     * </p>
     *
     * @return an array of permission strings
     */
    String[] getRequiredPermissions();

    /**
     * Returns a string representation of the local endpoint address
     * (e.g., the Bluetooth MAC address).
     *
     * @return the local address, or an empty string if unavailable
     */
    String getLocalAddress();

    /**
     * Returns a human-readable display name for this transport.
     */
    String getDisplayName();

    /**
     * Callback for receiving incoming connections while acting as a server.
     */
    interface AcceptCallback {
        /**
         * Called when a new connection is accepted.
         *
         * @param inputStream    the input stream from the remote peer
         * @param outputStream   the output stream to the remote peer
         * @param remoteAddress  the address of the connecting peer
         */
        void onAccepted(InputStream inputStream, OutputStream outputStream, String remoteAddress);

        /**
         * Called when an error occurs while accepting connections.
         *
         * @param e the exception describing the error
         */
        void onError(Exception e);
    }

    /**
     * Callback for peer discovery results and lifecycle events.
     */
    interface DiscoveryCallback {
        /**
         * Called when a peer is discovered.
         *
         * @param peer information about the discovered peer
         */
        void onPeerFound(PeerDiscoveryInfo peer);

        /**
         * Called when discovery has started.
         */
        void onDiscoveryStarted();

        /**
         * Called when discovery has finished.
         */
        void onDiscoveryFinished();

        /**
         * Called when an error occurs during discovery.
         *
         * @param e the exception describing the error
         */
        void onError(Exception e);
    }
}
