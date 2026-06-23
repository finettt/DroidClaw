package io.finett.droidclaw;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.connectivity.DiscoveryManager;
import io.finett.droidclaw.connectivity.NetworkTransport;
import io.finett.droidclaw.connectivity.Transport;
import io.finett.droidclaw.util.SettingsManager;

public class DroidClawApplication extends Application {

    @Override
    public void onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this);
        super.onCreate();

        // Start the agent-to-agent connectivity server in the background
        // so this device can accept incoming peer connections even when
        // no chat session is active.
        initConnectivity();
    }

    /**
     * Initialize the DiscoveryManager with configured transports and start
     * the server accept loop. This runs once at app startup so the device
     * is always discoverable and reachable by peers.
     * <p>
     * If the connectivity setting is disabled, this is a no-op.
     */
    private void initConnectivity() {
        try {
            SettingsManager settingsManager = new SettingsManager(this);
            if (!settingsManager.getAgentConfig().isAgentAccessibilityEnabled()) {
                return;
            }

            DiscoveryManager dm = DiscoveryManager.getInstance(this);

            // Don't re-initialize if already configured
            if (!dm.getActiveTransports().isEmpty()) {
                if (!dm.isRunning()) {
                    dm.startAll();
                }
                return;
            }

            String transportSetting = settingsManager.getAgentConfig().getDiscoveryTransport();
            List<Transport> transports = new ArrayList<>();

            if ("network".equals(transportSetting) || "auto".equals(transportSetting)) {
                transports.add(new NetworkTransport());
            }
            if ("bluetooth".equals(transportSetting) || "auto".equals(transportSetting)) {
                try {
                    transports.add(
                            new io.finett.droidclaw.connectivity.BluetoothTransport());
                } catch (Exception ignored) {
                    // Bluetooth not available on this device
                }
            }

            if (!transports.isEmpty()) {
                dm.initialize(transports);
                dm.startAll();
                android.util.Log.d("DroidClawApp",
                        "Connectivity initialized with " + transportSetting
                        + " transport on " + transports.size() + " transport(s)");
            }
        } catch (Exception e) {
            android.util.Log.w("DroidClawApp",
                    "Failed to initialize connectivity", e);
        }
    }
}
