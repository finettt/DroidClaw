package io.finett.droidclaw.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.finett.droidclaw.model.HeartbeatConfig;

/**
 * Repository for heartbeat configuration.
 * Stores and retrieves heartbeat settings using SharedPreferences.
 */
public class HeartbeatConfigRepository {
    private static final String TAG = "HeartbeatConfigRepository";
    private static final String PREFS_NAME = "droidclaw_heartbeat";
    private static final String KEY_CONFIG = "heartbeat_config";

    private final SharedPreferences prefs;

    public HeartbeatConfigRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get the current heartbeat configuration.
     * Returns a Flow-like single value (synchronous version).
     */
    public HeartbeatConfig getConfig() {
        try {
            String jsonString = prefs.getString(KEY_CONFIG, null);
            if (jsonString == null || jsonString.isEmpty()) {
                Log.d(TAG, "No heartbeat config found, returning defaults");
                return HeartbeatConfig.getDefaults();
            }

            JSONObject jsonObject = new JSONObject(jsonString);

            boolean enabled = jsonObject.optBoolean("enabled", false);
            long intervalMillis = jsonObject.optLong("intervalMillis", 30 * 60 * 1000L);
            long lastRunTimestamp = jsonObject.optLong("lastRunTimestamp", 0);

            return new HeartbeatConfig(enabled, intervalMillis, lastRunTimestamp);
        } catch (JSONException e) {
            Log.e(TAG, "Error loading heartbeat config - returning defaults", e);
            prefs.edit().remove(KEY_CONFIG).apply();
            return HeartbeatConfig.getDefaults();
        }
    }

    /**
     * Update the heartbeat configuration.
     */
    public void updateConfig(HeartbeatConfig config) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("enabled", config.isEnabled());
            jsonObject.put("intervalMillis", config.getIntervalMillis());
            jsonObject.put("lastRunTimestamp", config.getLastRunTimestamp());

            prefs.edit().putString(KEY_CONFIG, jsonObject.toString()).apply();
            Log.d(TAG, "Updated heartbeat config: enabled=" + config.isEnabled());
        } catch (JSONException e) {
            Log.e(TAG, "Error saving heartbeat config", e);
        }
    }

    /**
     * Check if heartbeat is enabled.
     */
    public boolean isHeartbeatEnabled() {
        return getConfig().isEnabled();
    }

    /**
     * Check if heartbeat should run based on current time.
     */
    public boolean shouldRun(long currentTimeMillis) {
        return getConfig().shouldRun(currentTimeMillis);
    }

    /**
     * Update the last run timestamp.
     */
    public void updateLastRun(long timestamp) {
        HeartbeatConfig config = getConfig();
        config.setLastRunTimestamp(timestamp);
        updateConfig(config);
        Log.d(TAG, "Updated last run timestamp: " + timestamp);
    }
}
