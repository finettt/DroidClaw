package io.finett.droidclaw.connectivity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central coordinator for agent-to-agent discovery and server lifecycle.
 * <p>
 * Manages a list of {@link Transport} instances, coordinating their server
 * accept loops and peer discovery. Integrates with {@link ConnectionManager}
 * to register new connections from accepted peers and client-initiated
 * connections.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #initialize(List)} — set the transports to manage</li>
 *   <li>{@link #startAll()} — start servers and discovery on all transports</li>
 *   <li>{@link #stopAll()} — stop all servers and discovery</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * All transport operations (start, stop, connect) are dispatched onto a
 * single-threaded executor. Discovery events and connection notifications
 * are delivered to the {@link DiscoveryListener} on the main thread via
 * a {@link Handler}. The {@link #connectToPeer} method blocks the calling
 * thread until the connection is established or times out.
 *
 * <h3>Permission gating</h3>
 * Transports whose required runtime permissions are not granted are silently
 * skipped during {@link #startAll()} and an error is reported via
 * {@link DiscoveryListener#onError}.
 */
public class DiscoveryManager {

    private static DiscoveryManager instance;

    private final Context appContext;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final ConnectionManager connectionManager;
    private final AtomicBoolean running;

    private List<Transport> transports;
    private DiscoveryListener globalListener;
    private AgentConnection.MessageListener defaultMessageListener;

    /**
     * Listener for discovery and server lifecycle events.
     */
    public interface DiscoveryListener {
        /**
         * Called when a peer device is discovered (before a connection is established).
         *
         * @param peer information about the discovered peer
         */
        void onPeerDiscovered(PeerDiscoveryInfo peer);

        /**
         * Called when a transport's server has started accepting connections.
         *
         * @param transportName the display name of the transport
         * @param address       the local address the server is listening on
         */
        void onServerStarted(String transportName, String address);

        /**
         * Called when a transport's server has stopped.
         *
         * @param transportName the display name of the transport
         */
        void onServerStopped(String transportName);

        /**
         * Called when an error occurs on a transport.
         *
         * @param transportName the display name of the transport
         * @param error         a description of the error
         */
        void onError(String transportName, String error);

        /**
         * Called when a new connection has been established and registered
         * with the {@link ConnectionManager}.
         *
         * @param connection the established connection
         */
        void onConnectionEstablished(AgentConnection connection);
    }

    // ---------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------

    private DiscoveryManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "discovery-manager");
            t.setDaemon(true);
            return t;
        });
        this.connectionManager = ConnectionManager.getInstance();
        this.running = new AtomicBoolean(false);
        this.transports = Collections.emptyList();
    }

    /**
     * Returns the singleton DiscoveryManager instance, creating it if necessary.
     *
     * @param context a Context (application context will be retained)
     * @return the singleton instance
     */
    public static synchronized DiscoveryManager getInstance(Context context) {
        if (instance == null) {
            instance = new DiscoveryManager(context);
        }
        return instance;
    }

    /**
     * Initialize with the transports to use.
     * <p>
     * Must be called before {@link #startAll()}. Replaces any previously
     * configured transport list. The provided list is defensively copied.
     * </p>
     *
     * @param transports the list of Transport instances to manage
     */
    public void initialize(List<Transport> transports) {
        this.transports = transports != null
                ? new ArrayList<>(transports)
                : Collections.emptyList();
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /**
     * Start all servers and discovery on all enabled transports.
     * <p>
     * Iterates the transport list, checks runtime permissions, starts the
     * server accept loop, and starts peer discovery for transports that
     * support it. Results are reported asynchronously via the registered
     * {@link DiscoveryListener}.
     * </p>
     */
    public void startAll() {
        if (transports.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            for (Transport transport : transports) {
                startTransport(transport);
            }
            running.set(true);
        });
    }

    /**
     * Stop all servers and discovery on all transports.
     * <p>
     * Stops discovery first, then stops each server. Safe to call when
     * no transports are running.
     * </p>
     */
    public void stopAll() {
        executor.execute(() -> {
            for (Transport transport : transports) {
                try {
                    transport.stopDiscovery();
                } catch (Exception ignored) {
                    // best-effort stop
                }
                try {
                    transport.stopServer();
                } catch (Exception ignored) {
                    // best-effort stop
                }
                notifyServerStopped(transport.getDisplayName());
            }
            running.set(false);
        });
    }

    /**
     * Start discovery only, without affecting any running server accept loops.
     * <p>
     * Only starts discovery on transports that support it and have the
     * required permissions.
     * </p>
     */
    public void startDiscovery() {
        executor.execute(() -> {
            for (Transport transport : transports) {
                if (transport.supportsDiscovery()
                        && hasRequiredPermissions(appContext, transport)) {
                    transport.startDiscovery(new InternalDiscoveryCallback(transport));
                }
            }
        });
    }

    /**
     * Stop discovery on all transports. Safe to call even if no discovery
     * is in progress.
     */
    public void stopDiscovery() {
        executor.execute(() -> {
            for (Transport transport : transports) {
                try {
                    transport.stopDiscovery();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Outgoing connections
    // ---------------------------------------------------------------

    /**
     * Connect to a specific peer address using the given transport type.
     * <p>
     * This method blocks the calling thread until the connection is
     * established or the handshake times out (up to 10 seconds).
     * </p>
     *
     * @param address       the address of the remote peer (transport-dependent)
     * @param transportType the transport type identifier
     *                      (e.g., {@link Transport#TYPE_BLUETOOTH})
     * @return the established AgentConnection (already started with handshake
     *         initiated), or null if no matching transport was found or the
     *         connection failed
     */
    public AgentConnection connectToPeer(String address, String transportType) {
        Transport transport = findTransportByType(transportType);
        if (transport == null) {
            notifyError(transportType, "No transport found for type: " + transportType);
            return null;
        }

        try {
            AgentConnection connection = transport.connect(address, 0);
            if (connection != null) {
                // start() initiates the handshake and blocks until complete
                connection.start();
                String connId = connectionManager.addConnection(connection);
                if (connId != null) {
                    notifyConnectionEstablished(connection);
                }
            }
            return connection;
        } catch (IOException e) {
            notifyError(transport.getDisplayName(),
                    "Failed to connect to " + address + ": " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------

    /**
     * Returns whether any transports are currently running (servers started).
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns an unmodifiable view of the active transports list.
     */
    public List<Transport> getActiveTransports() {
        return Collections.unmodifiableList(transports);
    }

    /**
     * Sets a default MessageListener that is applied to every connection
     * before {@link AgentConnection#start()} is called. This ensures no
     * incoming messages (e.g. CHAT) are lost between the handshake and
     * any later listener registration.
     *
     * @param listener the listener to apply to all new connections, or null
     */
    public void setDefaultMessageListener(AgentConnection.MessageListener listener) {
        this.defaultMessageListener = listener;
    }

    /**
     * Registers a global listener for discovery and server lifecycle events.
     * Replaces any previously registered listener. Pass null to clear.
     *
     * @param listener the listener, or null to clear
     */
    public void setDiscoveryListener(DiscoveryListener listener) {
        this.globalListener = listener;
    }

    /**
     * Returns the currently registered DiscoveryListener, or null if none.
     */
    public DiscoveryListener getDiscoveryListener() {
        return globalListener;
    }

    /**
     * Check whether all required Android runtime permissions for a transport
     * are granted.
     *
     * @param context   a Context
     * @param transport the transport to check permissions for
     * @return true if all required permissions are granted; false if any
     *         required permission is denied
     */
    public boolean hasRequiredPermissions(Context context, Transport transport) {
        String[] permissions = transport.getRequiredPermissions();
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Internal: Transport lifecycle
    // ---------------------------------------------------------------

    private void startTransport(Transport transport) {
        // Check permissions first; skip transport if not all granted
        if (!hasRequiredPermissions(appContext, transport)) {
            notifyError(transport.getDisplayName(),
                    "Required permissions not granted");
            return;
        }

        // Start the server accept loop
        try {
            transport.startServer(0, new InternalAcceptCallback(transport));
            String address = transport.getLocalAddress();
            notifyServerStarted(transport.getDisplayName(), address);
        } catch (IOException e) {
            notifyError(transport.getDisplayName(),
                    "Failed to start server: " + e.getMessage());
            return;
        }

        // Start peer discovery if the transport supports it
        if (transport.supportsDiscovery()) {
            transport.startDiscovery(new InternalDiscoveryCallback(transport));
        }
    }

    private Transport findTransportByType(String transportType) {
        for (Transport transport : transports) {
            if (transport.getType().equals(transportType)) {
                return transport;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Internal: Callback implementations
    // ---------------------------------------------------------------

    /**
     * Callback for incoming connections from the transport accept loop.
     */
    private class InternalAcceptCallback implements Transport.AcceptCallback {
        private final Transport transport;

        InternalAcceptCallback(Transport transport) {
            this.transport = transport;
        }

        @Override
        public void onAccepted(InputStream inputStream,
                               OutputStream outputStream,
                               String remoteAddress) {
            // Dispatch to executor for serialized processing
            executor.execute(() -> {
                AgentConnection connection = new AgentConnection(
                        inputStream, outputStream, transport, remoteAddress);

                // Set the default MessageListener BEFORE start() so that
                // no incoming CHAT (or other non-protocol) messages are
                // lost between the handshake completing and any later
                // listener registration by AgentConnectivityService.
                if (defaultMessageListener != null) {
                    connection.setMessageListener(defaultMessageListener);
                }

                // Start the connection; this initiates the HELLO/HELLO_ACK
                // handshake on the reader/writer daemon threads.
                connection.start();

                // Register with ConnectionManager; deduplicates by address.
                String connId = connectionManager.addConnection(connection);
                if (connId == null) {
                    // A connection with this address already exists
                    connection.disconnect();
                    notifyError(transport.getDisplayName(),
                            "Duplicate connection rejected: " + remoteAddress);
                    return;
                }

                notifyConnectionEstablished(connection);
            });
        }

        @Override
        public void onError(Exception e) {
            notifyError(transport.getDisplayName(),
                    "Server accept error: " + e.getMessage());
        }
    }

    /**
     * Callback for peer discovery events from the transport.
     */
    private class InternalDiscoveryCallback implements Transport.DiscoveryCallback {
        private final Transport transport;

        InternalDiscoveryCallback(Transport transport) {
            this.transport = transport;
        }

        @Override
        public void onPeerFound(PeerDiscoveryInfo peer) {
            notifyPeerDiscovered(peer);
        }

        @Override
        public void onDiscoveryStarted() {
            // Reserved; can be exposed via DiscoveryListener if needed.
        }

        @Override
        public void onDiscoveryFinished() {
            // Reserved; Bluetooth discovery is one-shot and auto-restart
            // is handled by the DiscoveryManager if desired.
        }

        @Override
        public void onError(Exception e) {
            notifyError(transport.getDisplayName(),
                    "Discovery error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Internal: Notification helpers (post to main thread)
    // ---------------------------------------------------------------

    private void notifyPeerDiscovered(PeerDiscoveryInfo peer) {
        if (globalListener == null) return;
        mainHandler.post(() -> globalListener.onPeerDiscovered(peer));
    }

    private void notifyServerStarted(String transportName, String address) {
        if (globalListener == null) return;
        mainHandler.post(() -> globalListener.onServerStarted(transportName, address));
    }

    private void notifyServerStopped(String transportName) {
        if (globalListener == null) return;
        mainHandler.post(() -> globalListener.onServerStopped(transportName));
    }

    private void notifyError(String transportName, String error) {
        if (globalListener == null) return;
        mainHandler.post(() -> globalListener.onError(transportName, error));
    }

    private void notifyConnectionEstablished(AgentConnection connection) {
        if (globalListener == null) return;
        mainHandler.post(() -> globalListener.onConnectionEstablished(connection));
    }
}
