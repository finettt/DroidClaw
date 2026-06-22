package io.finett.droidclaw.connectivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * BroadcastReceiver for Bluetooth adapter state changes, discovery lifecycle,
 * and device discovery events.
 * <p>
 * Register and unregister via {@link android.content.Context#registerReceiver}.
 * Use {@link android.content.IntentFilter} with the desired actions:
 * <ul>
 *   <li>{@link BluetoothAdapter#ACTION_STATE_CHANGED}</li>
 *   <li>{@link BluetoothDevice#ACTION_FOUND}</li>
 *   <li>{@link BluetoothAdapter#ACTION_DISCOVERY_STARTED}</li>
 *   <li>{@link BluetoothAdapter#ACTION_DISCOVERY_FINISHED}</li>
 * </ul>
 * </p>
 */
public class BluetoothStateReceiver extends BroadcastReceiver {

    /**
     * Listener for Bluetooth state and discovery events.
     */
    public interface Listener {
        /**
         * Called when the Bluetooth adapter state changes.
         *
         * @param previousState the previous adapter state
         *                      (one of {@link BluetoothAdapter#STATE_OFF}, etc.)
         * @param currentState  the current adapter state
         */
        void onAdapterStateChanged(int previousState, int currentState);

        /**
         * Called when a Bluetooth device is discovered.
         *
         * @param device the discovered device
         * @param rssi   the RSSI (signal strength) of the discovered device
         */
        void onDeviceFound(BluetoothDevice device, int rssi);

        /**
         * Called when Bluetooth discovery starts.
         */
        void onDiscoveryStarted();

        /**
         * Called when Bluetooth discovery finishes.
         */
        void onDiscoveryFinished();
    }

    private final Listener listener;

    /**
     * Constructs a new BluetoothStateReceiver.
     *
     * @param listener the listener to forward events to
     */
    public BluetoothStateReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        final String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case BluetoothAdapter.ACTION_STATE_CHANGED: {
                final int previousState = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);
                final int currentState = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                listener.onAdapterStateChanged(previousState, currentState);
                break;
            }
            case BluetoothDevice.ACTION_FOUND: {
                final BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }
                final int rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI, (short) Short.MIN_VALUE);
                if (device != null) {
                    listener.onDeviceFound(device, rssi);
                }
                break;
            }
            case BluetoothAdapter.ACTION_DISCOVERY_STARTED: {
                listener.onDiscoveryStarted();
                break;
            }
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                listener.onDiscoveryFinished();
                break;
            }
        }
    }
}
