package io.finett.droidclaw.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Backing storage for the in-app update checker, held in a plain private
 * {@code SharedPreferences} file (not the encrypted settings store, which is
 * reserved for provider keys and agent configuration).
 *
 * <p>Keys:
 * <ul>
 *   <li>{@code update_check_enabled} — automatic check enabled (opt-out, default {@code true});</li>
 *   <li>{@code last_update_check_at} — epoch millis of the last startup attempt;</li>
 *   <li>{@code update_prompted_tag} — last tag the startup dialog showed (once-per-tag);</li>
 *   <li>{@code last_known_newer_tag} — most recent tag found newer than the build (drives the banner).</li>
 * </ul>
 */
public class UpdatePreferences {

    private static final String TAG = "UpdatePreferences";

    /** SharedPreferences file name. */
    public static final String PREFS_NAME = "update_check";
    /** Automatic startup check is on unless the user opts out. Default {@code true}. */
    public static final String KEY_ENABLED = "update_check_enabled";
    /** Epoch millis of the last startup check attempt. */
    public static final String KEY_LAST_CHECK_AT = "last_update_check_at";
    /** Last tag the startup dialog showed, used to prompt once per release. */
    public static final String KEY_PROMPTED_TAG = "update_prompted_tag";
    /** Last tag found newer than the running build; drives the Info-screen banner. */
    public static final String KEY_KNOWN_NEWER_TAG = "last_known_newer_tag";

    /** Minimum gap between startup checks. */
    public static final long THROTTLE_MS = 24L * 60 * 60 * 1000;

    private final SharedPreferences prefs;

    public UpdatePreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** @return {@code true} if the automatic startup check is enabled (default on). */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** @return epoch millis of the last startup check attempt (0 if never). */
    public long getLastCheckAt() {
        return prefs.getLong(KEY_LAST_CHECK_AT, 0L);
    }

    public void setLastCheckAt(long epochMillis) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, epochMillis).apply();
        Log.d(TAG, "Set last_update_check_at to " + epochMillis);
    }

    /** @return the last tag the startup dialog showed, or {@code null} if none. */
    public String getPromptedTag() {
        return prefs.getString(KEY_PROMPTED_TAG, null);
    }

    public void setPromptedTag(String tag) {
        if (tag == null) {
            prefs.edit().remove(KEY_PROMPTED_TAG).apply();
        } else {
            prefs.edit().putString(KEY_PROMPTED_TAG, tag).apply();
        }
    }

    /**
     * @param tag the tag to decide on
     * @return {@code true} if the startup dialog has not yet shown for {@code tag}
     *         (never prompted, or a different tag was shown before)
     */
    public boolean shouldPrompt(String tag) {
        String prompted = getPromptedTag();
        return prompted == null || !prompted.equals(tag);
    }

    /** @return the most recent tag found newer than the build, or {@code null}. */
    public String getLastKnownNewerTag() {
        return prefs.getString(KEY_KNOWN_NEWER_TAG, null);
    }

    public void setLastKnownNewerTag(String tag) {
        if (tag == null) {
            prefs.edit().remove(KEY_KNOWN_NEWER_TAG).apply();
        } else {
            prefs.edit().putString(KEY_KNOWN_NEWER_TAG, tag).apply();
        }
    }

    /**
     * @param nowMillis   the current time in epoch millis
     * @return {@code true} only if the check is enabled and at least
     *         {@link #THROTTLE_MS} has elapsed since the last startup attempt
     */
    public boolean shouldCheckNow(long nowMillis) {
        return isEnabled() && (nowMillis - getLastCheckAt()) >= THROTTLE_MS;
    }
}
