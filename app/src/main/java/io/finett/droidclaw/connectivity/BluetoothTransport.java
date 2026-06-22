package io.finett.droidclaw.connectivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bluetooth RFCOMM transport implementation for agent-to-agent communication.
 * <p>
 * Uses the Serial Port Profile (SPP) UUID for RFCOMM connections.
 * Supports both server (accepting incoming connections) and client
 * (initiating outgoing connections) modes, as well as device discovery.
 * </p>
 *
 * <h3>Thread safety</h3>
 * All long-running operations (accept loop, connect) run on daemon threads.
 * Callers are notified via the provided callbacks on those daemon threads;
 * UI updates must be posted to the main thread by the caller.
 */
public class BluetoothTransport implements Transport {

    /** SPP UUID for Bluetooth serial communication. */
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /** Service name advertised during Bluetooth discovery. */
    private static final String SERVICE_NAME = "DroidClaw";

    // Permission constants for readability
    private static final String PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH";
    private static final String PERMISSION_BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN";
    private static final String PERMISSION_ACCESS_FINE_LOCATION =
            "android.permission.ACCESS_FINE_LOCATION";
    private static final String PERMISSION_BLUETOOTH_CONNECT =
            "android.permission.BLUETOOTH_CONNECT";
    private static final String PERMISSION_BLUETOOTH_SCAN =
            "android.permission.BLUETOOTH_SCAN";

    // --- Server state ---
    private final AtomicReference<BluetoothServerSocket> serverSocketRef =
            new AtomicReference<>(null);
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    private Thread acceptThread;

    // --- Discovery state ---
    private final AtomicBoolean discovering = new AtomicBoolean(false);
    private BroadcastReceiver discoveryReceiver;
    private Context registeredContext;

    @Override
    public String getType() {
        return TYPE_BLUETOOTH;
    }

    @Override
    public void startServer(int port, AcceptCallback callback) throws IOException {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new IOException("Bluetooth adapter not available on this device");
        }
        if (!adapter.isEnabled()) {
            throw new IOException("Bluetooth is not enabled");
        }

        // Close any previous server socket
        stopServer();

        final BluetoothServerSocket serverSocket;
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID);
        } catch (IOException e) {
            throw new IOException("Failed to create Bluetooth server socket: " + e.getMessage(), e);
        }

        serverSocketRef.set(serverSocket);
        serverRunning.set(true);

        acceptThread = new Thread(() -> {
            while (serverRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    BluetoothSocket socket = serverSocket.accept();
                    if (!serverRunning.get()) {
                        // Server was stopped while we were accepting
                        try { socket.close(); } catch (IOException ignored) {}
                        return;
                    }
                    if (socket == null) continue;

                    final InputStream inputStream;
                    final OutputStream outputStream;
                    try {
                        inputStream = socket.getInputStream();
                        outputStream = socket.getOutputStream();
                    } catch (IOException e) {
                        try { socket.close(); } catch (IOException ignored) {}
                        callback.onError(new IOException("Failed to obtain streams from accepted socket", e));
                        continue;
                    }

                    final String remoteAddress = socket.getRemoteDevice() != null
                            ? socket.getRemoteDevice().getAddress()
                            : "unknown";

                    callback.onAccepted(inputStream, outputStream, remoteAddress);
                } catch (IOException e) {
                    if (serverRunning.get()) {
                        callback.onError(e);
                    }
                    // If server was stopped, exit silently
                    break;
                }
            }
        }, "bt-accept-loop");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public AgentConnection connect(String address, int port) throws IOException {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("address must not be null or empty");
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("Invalid Bluetooth MAC address: " + address);
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new IOException("Bluetooth adapter not available on this device");
        }
        if (!adapter.isEnabled()) {
            throw new IOException("Bluetooth is not enabled");
        }

        // Cancel discovery before connecting (required by Bluetooth API)
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        BluetoothDevice device = adapter.getRemoteDevice(address);
        BluetoothSocket socket;
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        } catch (IOException e) {
            throw new IOException("Failed to create RFCOMM socket: " + e.getMessage(), e);
        }

        try {
            socket.connect();
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) {}
            throw new IOException("Failed to connect to " + address + ": " + e.getMessage(), e);
        }

        final InputStream inputStream;
        final OutputStream outputStream;
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) {}
            throw new IOException("Failed to obtain I/O streams from connected socket", e);
        }

        return new AgentConnection(inputStream, outputStream, this, address);
    }

    @Override
    public void startDiscovery(DiscoveryCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            callback.onError(new IOException("Bluetooth adapter not available on this device"));
            return;
        }
        if (!adapter.isEnabled()) {
            callback.onError(new IOException("Bluetooth is not enabled"));
            return;
        }

        // If already discovering, report as started and return
        if (discovering.get()) {
            callback.onDiscoveryStarted();
            callback.onDiscoveryFinished();
            return;
        }

        discovering.set(true);

        // Register receiver — the caller must provide a Context via
        // setDiscoveryContext() before calling startDiscovery().
        if (registeredContext == null) {
            discovering.set(false);
            callback.onError(new IllegalStateException(
                    "No Context registered for discovery. Call setDiscoveryContext first."));
            return;
        }

        // Unregister any previous receiver
        unregisterDiscoveryReceiver();

        discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action == null) return;

                switch (action) {
                    case BluetoothDevice.ACTION_FOUND: {
                        BluetoothDevice device;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            device = intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        } else {
                            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        }
                        if (device != null) {
                            String name = device.getName();
                            if (name == null) {
                                name = device.getAddress();
                            }
                            callback.onPeerFound(new PeerDiscoveryInfo(
                                    device.getAddress(), name, TYPE_BLUETOOTH));
                        }
                        break;
                    }
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED: {
                        callback.onDiscoveryStarted();
                        break;
                    }
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                        callback.onDiscoveryFinished();
                        // Discovery is one-shot; do not auto-restart
                        break;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registeredContext.registerReceiver(discoveryReceiver, filter,
                        Context.RECEIVER_EXPORTED);
            } else {
                registeredContext.registerReceiver(discoveryReceiver, filter);
            }
        } catch (Exception e) {
            discovering.set(false);
            discoveryReceiver = null;
            callback.onError(new IOException("Failed to register discovery receiver: "
                    + e.getMessage(), e));
            return;
        }

        if (!adapter.startDiscovery()) {
            // Discovery failed to start
            unregisterDiscoveryReceiver();
            discovering.set(false);
            callback.onError(new IOException("Failed to start Bluetooth discovery"));
        }
    }

    @Override
    public void stopDiscovery() {
        discovering.set(false);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            adapter.cancelDiscovery();
        }

        unregisterDiscoveryReceiver();
        registeredContext = null;
    }

    @Override
    public boolean supportsDiscovery() {
        return true;
    }

    @Override
    public boolean isAvailable(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    @Override
    public String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ (Android 12+): BLUETOOTH_SCAN replaces ACCESS_FINE_LOCATION
            // for BT discovery; BLUETOOTH_CONNECT replaces BLUETOOTH/BLUETOOTH_ADMIN
            return new String[]{
                    PERMISSION_BLUETOOTH_CONNECT,
                    PERMISSION_BLUETOOTH_SCAN
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23-30: need BLUETOOTH, BLUETOOTH_ADMIN, and location for discovery
            // We include BLUETOOTH/BLUETOOTH_ADMIN for completeness even though they
            // are normal permissions (auto-granted).
            return new String[]{
                    PERMISSION_BLUETOOTH,
                    PERMISSION_BLUETOOTH_ADMIN,
                    PERMISSION_ACCESS_FINE_LOCATION
            };
        } else {
            // Below API 23 (not applicable with minSdk 24, but for safety)
            return new String[]{
                    PERMISSION_BLUETOOTH,
                    PERMISSION_BLUETOOTH_ADMIN
            };
        }
    }

    @Override
    public String getLocalAddress() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return "";
        }
        // Note: On Android 10+ (API 29+), the local Bluetooth MAC address is
        // not available via getAddress() for privacy reasons. It returns
        // "02:00:00:00:00:00" as a placeholder. This is expected behavior.
        String address = adapter.getAddress();
        return address != null ? address : "";
    }

    @Override
    public String getDisplayName() {
        return "Bluetooth";
    }

    // ---- Public helper methods ----

    /**
     * Registers a Context for use with discovery.
     * <p>
     * Call this before {@link #startDiscovery(DiscoveryCallback)} to provide
     * a Context for registering the discovery BroadcastReceiver. The context
     * should be the application context to avoid memory leaks.
     * </p>
     *
     * @param context a Context (preferably the application context)
     */
    public void setDiscoveryContext(Context context) {
        this.registeredContext = context != null ? context.getApplicationContext() : null;
    }

    @Override
    public boolean isServerRunning() {
        return serverRunning.get();
    }

    @Override
    public void stopServer() {
        serverRunning.set(false);

        BluetoothServerSocket socket = serverSocketRef.getAndSet(null);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        if (acceptThread != null && acceptThread.isAlive()) {
            acceptThread.interrupt();
            try {
                acceptThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            acceptThread = null;
        }
    }

    /**
     * Releases all resources held by this transport: stops the server,
     * cancels discovery, and unregisters all receivers.
     */
    public void dispose() {
        stopServer();
        stopDiscovery();
    }

    // ---- Private helpers ----

    private void unregisterDiscoveryReceiver() {
        if (discoveryReceiver != null && registeredContext != null) {
            try {
                registeredContext.unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was not registered
            }
            discoveryReceiver = null;
        }
    }
}
