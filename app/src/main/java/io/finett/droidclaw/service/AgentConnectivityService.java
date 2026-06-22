package io.finett.droidclaw.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

import io.finett.droidclaw.connectivity.AgentConnection;
import io.finett.droidclaw.connectivity.AgentMessage;
import io.finett.droidclaw.connectivity.ConnectionManager;
import io.finett.droidclaw.connectivity.DiscoveryManager;
import io.finett.droidclaw.connectivity.PeerDiscoveryInfo;
import io.finett.droidclaw.connectivity.PeerMessageHandler;
import io.finett.droidclaw.connectivity.Transport;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Foreground service that keeps agent-to-agent connectivity alive.
 *
 * <p>Owns the {@link DiscoveryManager} lifecycle -- starts servers and peer discovery
 * when the service starts, and stops everything when the service is destroyed.
 * Provides a persistent notification showing the connectivity status and
 * connected peer count.
 *
 * <p>UI clients bind to this service, register a {@link ConnectivityCallback},
 * and receive status updates. The Binder also exposes methods to configure
 * transports and start/stop connectivity on demand.
 */
public class AgentConnectivityService extends Service {

    private static final String TAG = "AgentConnectivityService";

    static final String CHANNEL_ID = "droidclaw_connectivity";
    private static final int NOTIFICATION_ID = 8804;

    // ==================== Binder ====================

    public class LocalBinder extends Binder {
        public AgentConnectivityService getService() {
            return AgentConnectivityService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    // ==================== UI callback ====================

    /**
     * Callback interface for UI components to receive connectivity status changes.
     */
    public interface ConnectivityCallback {
        /**
         * Called when the running state or peer count changes.
         *
         * @param isRunning true if servers are started and listening
         * @param peerCount number of currently connected peers
         */
        void onStatusChanged(boolean isRunning, int peerCount);

        /**
         * Called when a peer device is discovered via Bluetooth or other transports.
         *
         * @param peer information about the discovered peer
         */
        void onPeerDiscovered(PeerDiscoveryInfo peer);

        /**
         * Called when an error occurs on a transport.
         *
         * @param transportName the display name of the transport
         * @param error         a description of the error
         */
        void onError(String transportName, String error);
    }

    private ConnectivityCallback callback;

    // ==================== Peer message handling ====================

    private PeerMessageHandler peerMessageHandler;

    // ==================== Fields ====================

    private NotificationManager notificationManager;

    // ==================== Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        // startForeground() must be called within 5 seconds on Android 15+.
        // Doing it here in onCreate() is the earliest possible point.
        startForeground(NOTIFICATION_ID, buildNotification(false, 0));

        peerMessageHandler = new PeerMessageHandler(this, new SettingsManager(this));

        Log.d(TAG, "AgentConnectivityService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Initialize DiscoveryManager and register the global listener.
        DiscoveryManager discoveryManager = DiscoveryManager.getInstance(this);
        discoveryManager.setDiscoveryListener(createDiscoveryListener());

        // Set the default MessageListener so every new connection gets
        // it BEFORE connection.start() runs (fixes a race where early
        // CHAT messages could be silently dropped).
        discoveryManager.setDefaultMessageListener(createMessageListener());

        // Start all configured transports (no-op if no transports are configured yet).
        discoveryManager.startAll();

        updateNotification();
        Log.d(TAG, "AgentConnectivityService started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DiscoveryManager.getInstance(this).stopAll();
        Log.d(TAG, "AgentConnectivityService destroyed");
    }

    // ==================== Static helpers ====================

    /**
     * Start the foreground connectivity service. Safe to call when already running.
     *
     * @param context any Context (application context will be retained by the service)
     */
    public static void ensureRunning(Context context) {
        Intent intent = new Intent(context, AgentConnectivityService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stop the connectivity service, shutting down all transports and discovery.
     *
     * @param context any Context
     */
    public static void stop(Context context) {
        context.stopService(new Intent(context, AgentConnectivityService.class));
    }

    // ==================== Public API (via Binder) ====================

    /**
     * Register a {@link ConnectivityCallback} to receive updates.
     * The callback is immediately invoked with the current state.
     *
     * @param cb the callback, or null to clear
     */
    public void registerCallback(ConnectivityCallback cb) {
        this.callback = cb;
        if (cb != null) {
            DiscoveryManager dm = DiscoveryManager.getInstance(this);
            cb.onStatusChanged(dm.isRunning(), ConnectionManager.getInstance().getCount());
        }
    }

    /**
     * Unregister the connectivity callback. Connectivity keeps running in the background.
     */
    public void unregisterCallback() {
        this.callback = null;
    }

    /**
     * Returns whether any transports are currently running (servers started).
     */
    public boolean isConnectivityRunning() {
        return DiscoveryManager.getInstance(this).isRunning();
    }

    /**
     * Returns the number of currently connected peers.
     */
    public int getPeerCount() {
        return ConnectionManager.getInstance().getCount();
    }

    /**
     * Initialize the DiscoveryManager with the given transports and start them.
     *
     * <p>Replaces any previously configured transport list. Call this after binding
     * to set up the specific transports the user has enabled (e.g., Bluetooth, TCP).
     *
     * @param transports the list of Transport instances to use
     */
    public void initializeTransports(List<Transport> transports) {
        DiscoveryManager dm = DiscoveryManager.getInstance(this);
        dm.initialize(transports);
        dm.setDiscoveryListener(createDiscoveryListener());
        dm.startAll();
        updateNotification();
    }

    /**
     * Stop all transports and discovery without stopping the service.
     * The service and its notification remain active.
     */
    public void stopConnectivity() {
        DiscoveryManager.getInstance(this).stopAll();
        updateNotification();
    }

    // ==================== Per-connection MessageListener ====================

    /**
     * Creates the MessageListener applied to every new AgentConnection
     * (via {@link DiscoveryManager#setDefaultMessageListener}) before
     * {@link AgentConnection#start()} is called.
     * <p>
     * Routes incoming CHAT messages to the {@link PeerMessageHandler}
     * for agent processing. Cleans up the connection on disconnect.
     */
    private AgentConnection.MessageListener createMessageListener() {
        return new AgentConnection.MessageListener() {
            @Override
            public void onMessageReceived(AgentConnection conn, AgentMessage msg) {
                if (AgentMessage.Type.CHAT.name().equals(msg.getType())) {
                    peerMessageHandler.handleIncomingChat(conn, msg);
                }
            }

            @Override
            public void onConnectionStateChanged(AgentConnection conn,
                    AgentConnection.ConnectionState newState,
                    AgentConnection.ConnectionState oldState) {
                if (newState == AgentConnection.ConnectionState.DISCONNECTED) {
                    ConnectionManager.getInstance().removeConnection(
                            conn.getConnectionId());
                    updateNotification();
                }
            }

            @Override
            public void onError(AgentConnection conn, String error) {
                Log.w(TAG, "Connection error [" + conn.getPeerAddress() + "]: " + error);
            }
        };
    }

    // ==================== Discovery listener ====================

    private DiscoveryManager.DiscoveryListener createDiscoveryListener() {
        return new DiscoveryManager.DiscoveryListener() {
            @Override
            public void onPeerDiscovered(PeerDiscoveryInfo peer) {
                Log.d(TAG, "Peer discovered: " + peer.getDisplayName()
                        + " [" + peer.getAddress() + "]");
                if (callback != null) {
                    callback.onPeerDiscovered(peer);
                }
            }

            @Override
            public void onServerStarted(String transportName, String address) {
                Log.d(TAG, "Server started: " + transportName + " at " + address);
                updateNotification();
            }

            @Override
            public void onServerStopped(String transportName) {
                Log.d(TAG, "Server stopped: " + transportName);
                updateNotification();
            }

            @Override
            public void onError(String transportName, String error) {
                Log.e(TAG, "Error on " + transportName + ": " + error);
                if (callback != null) {
                    callback.onError(transportName, error);
                }
            }

            @Override
            public void onConnectionEstablished(AgentConnection connection) {
                Log.d(TAG, "Connection established: " + connection.getPeerAddress());
                // The MessageListener is already set via setDefaultMessageListener
                // in onStartCommand, so no need to wire it here.
                updateNotification();
                if (callback != null) {
                    callback.onStatusChanged(true, ConnectionManager.getInstance().getCount());
                }
            }
        };
    }

    // ==================== Notification ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DroidClaw Connectivity",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when agent-to-agent connectivity is active");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification() {
        DiscoveryManager dm = DiscoveryManager.getInstance(this);
        int peerCount = ConnectionManager.getInstance().getCount();
        notificationManager.notify(NOTIFICATION_ID, buildNotification(dm.isRunning(), peerCount));
    }

    private Notification buildNotification(boolean isRunning, int peerCount) {
        String title;
        if (isRunning) {
            if (peerCount > 0) {
                title = "DroidClaw -- " + peerCount + " peer"
                        + (peerCount == 1 ? "" : "s") + " connected";
            } else {
                title = "DroidClaw -- agent connectivity active";
            }
        } else {
            title = "DroidClaw -- connectivity inactive";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText("Tap to open DroidClaw")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
}
