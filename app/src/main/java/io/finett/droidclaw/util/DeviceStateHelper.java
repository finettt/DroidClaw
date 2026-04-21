package io.finett.droidclaw.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.PowerManager;

/**
 * Utility class for checking device state and handle edge cases.
 * Helps workers make decisions about whether to proceed with execution.
 */
public class DeviceStateHelper {

    /**
     * Check if device is in airplane mode (no connectivity expected).
     */
    public static boolean isAirplaneModeOn(Context context) {
        return android.provider.Settings.System.getInt(
                context.getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Check if device currently has network connectivity.
     */
    public static boolean hasNetworkConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null &&
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        
        // For API level 22, use deprecated method
        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Check if battery is in critical low state (< 15%).
     */
    public static boolean isBatteryCritical(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) return false;
        
        int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return batteryLevel > 0 && batteryLevel < 15;
    }

    /**
     * Check if device is in power saving mode.
     */
    public static boolean isPowerSavingMode(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) return false;
        
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1 
                && powerManager.isPowerSaveMode();
    }

    /**
     * Check if storage is critically low (< 100MB free).
     */
    public static boolean isStorageCritical(Context context) {
        try {
            java.io.File dataDir = context.getFilesDir();
            if (dataDir != null) {
                long freeSpace = dataDir.getUsableSpace();
                long freeMB = freeSpace / (1024 * 1024);
                return freeMB < 100; // Less than 100MB
            }
        } catch (Exception e) {
    
        }
        return false;
    }

    /**
     * Get current battery level as percentage (0-100).
     */
    public static int getBatteryLevel(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) return 100;
        
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Check if task should be skipped due to edge case conditions.
     * Returns true if task should proceed, false if it should skip.
     */
    public static boolean shouldExecuteTask(Context context) {

        if (isAirplaneModeOn(context)) {
            return false;
        }


        if (isBatteryCritical(context)) {
            return false;
        }


        if (isStorageCritical(context)) {
            return false;
        }



        
        return true;
    }

    /**
     * Get human-readable description of device state.
     */
    public static String getDeviceStateDescription(Context context) {
        StringBuilder sb = new StringBuilder();
        
        if (isAirplaneModeOn(context)) {
            sb.append("Airplane mode: ON\n");
        }
        
        sb.append("Network: ").append(hasNetworkConnection(context) ? "Connected" : "Disconnected").append("\n");
        sb.append("Battery: ").append(getBatteryLevel(context)).append("%\n");
        
        if (isBatteryCritical(context)) {
            sb.append("Battery status: CRITICAL\n");
        }
        
        if (isPowerSavingMode(context)) {
            sb.append("Power saving: ON\n");
        }
        
        if (isStorageCritical(context)) {
            sb.append("Storage: CRITICAL\n");
        }
        
        return sb.toString();
    }
}
