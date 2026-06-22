package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.connectivity.DiscoveryManager;
import io.finett.droidclaw.connectivity.PeerDiscoveryInfo;
import io.finett.droidclaw.connectivity.Transport;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Scans for nearby DroidClaw agents via Bluetooth or WiFi discovery.
 *
 * <p>Starts discovery on all configured transports via {@link DiscoveryManager},
 * collects results for the specified scan duration, then stops discovery and
 * returns a JSON list of discovered peers.
 *
 * <p>This tool requires that at least one transport with discovery support
 * (e.g., BluetoothTransport) is initialized in the DiscoveryManager and that
 * the required Android runtime permissions are granted.
 */
public class PeerDiscoverTool implements Tool {

    private static final String NAME = "discover_nearby_agents";
    private static final int DEFAULT_SCAN_DURATION_SECONDS = 10;
    private static final int MAX_SCAN_DURATION_SECONDS = 60;

    private final Context context;
    private final ToolDefinition definition;

    public PeerDiscoverTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addInteger("scan_duration_seconds",
                        "How long to scan for nearby agents in seconds. "
                        + "Default: 10. Max: 60.",
                        false)
                .build();

        return new ToolDefinition(
                NAME,
                "Scan for nearby DroidClaw agents via Bluetooth or WiFi. "
                + "Returns a list of discovered peers with their addresses, "
                + "display names, and transport types.",
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
        int scanDuration = DEFAULT_SCAN_DURATION_SECONDS;
        if (arguments.has("scan_duration_seconds")) {
            scanDuration = arguments.get("scan_duration_seconds").getAsInt();
            if (scanDuration < 1) {
                return ToolResult.error("scan_duration_seconds must be at least 1");
            }
            if (scanDuration > MAX_SCAN_DURATION_SECONDS) {
                return ToolResult.error("scan_duration_seconds must not exceed "
                        + MAX_SCAN_DURATION_SECONDS);
            }
        }

        DiscoveryManager dm = DiscoveryManager.getInstance(context);

        // Check if DiscoveryManager is running; start discovery if not already active
        boolean wasRunning = dm.isRunning();
        if (!wasRunning) {
            dm.startDiscovery();
        }

        // Collect discovered peers
        final List<PeerDiscoveryInfo> discoveredPeers = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>(null);

        DiscoveryManager.DiscoveryListener listener = new DiscoveryManager.DiscoveryListener() {
            @Override
            public void onPeerDiscovered(PeerDiscoveryInfo peer) {
                synchronized (discoveredPeers) {
                    discoveredPeers.add(peer);
                }
            }

            @Override
            public void onServerStarted(String transportName, String address) {
                // Not relevant for discovery results
            }

            @Override
            public void onServerStopped(String transportName) {
                // Not relevant for discovery results
            }

            @Override
            public void onError(String transportName, String error) {
                errorRef.set(transportName + ": " + error);
                latch.countDown();
            }

            @Override
            public void onConnectionEstablished(
                    io.finett.droidclaw.connectivity.AgentConnection connection) {
                // Not relevant for discovery results
            }
        };

        DiscoveryManager.DiscoveryListener previousListener = dm.getDiscoveryListener();
        dm.setDiscoveryListener(listener);

        try {
            // Wait for the scan duration
            boolean completed = latch.await(scanDuration, TimeUnit.SECONDS);
            if (!completed) {
                // Normal timeout - scan finished without error
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Discovery scan was interrupted");
        } finally {
            // Stop discovery if we started it
            if (!wasRunning) {
                dm.stopDiscovery();
            }
            // Restore the previous listener so AgentConnectivityService
            // continues to receive discovery and connection events.
            dm.setDiscoveryListener(previousListener);
        }

        // Check for errors
        String error = errorRef.get();
        if (error != null && discoveredPeers.isEmpty()) {
            return ToolResult.error("Discovery error: " + error);
        }

        // Build result
        JsonArray peersArray = new JsonArray();
        synchronized (discoveredPeers) {
            for (PeerDiscoveryInfo peer : discoveredPeers) {
                JsonObject peerJson = new JsonObject();
                peerJson.addProperty("address", peer.getAddress());
                peerJson.addProperty("display_name", peer.getDisplayName());
                peerJson.addProperty("transport_type", peer.getTransportType());
                peersArray.add(peerJson);
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("scan_duration_seconds", scanDuration);
        result.addProperty("peers_found", discoveredPeers.size());
        if (error != null) {
            result.addProperty("warning", error);
        }
        result.add("peers", peersArray);

        return ToolResult.success(result);
    }
}
