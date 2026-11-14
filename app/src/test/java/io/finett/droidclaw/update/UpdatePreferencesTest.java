package io.finett.droidclaw.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Behavioural tests for {@link UpdatePreferences}: throttle, once-per-tag prompt, banner state. */
@RunWith(RobolectricTestRunner.class)
public class UpdatePreferencesTest {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private UpdatePreferences prefs;
    private SharedPreferences raw;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        prefs = new UpdatePreferences(context);
        raw = context.getSharedPreferences(UpdatePreferences.PREFS_NAME, Context.MODE_PRIVATE);
        raw.edit().clear().commit();
    }

    @Test
    public void isEnabled_defaultsOn() {
        assertTrue(prefs.isEnabled());
    }

    @Test
    public void shouldCheckNow_falseBeforeThrottle() {
        prefs.setLastCheckAt(1000L);
        // Just after the last check, the 24h window has not elapsed.
        assertFalse(prefs.shouldCheckNow(1000L + UpdatePreferences.THROTTLE_MS - 1));
    }

    @Test
    public void shouldCheckNow_trueAfterThrottle() {
        prefs.setLastCheckAt(1000L);
        assertTrue(prefs.shouldCheckNow(1000L + UpdatePreferences.THROTTLE_MS));
        assertTrue(prefs.shouldCheckNow(1000L + 2 * UpdatePreferences.THROTTLE_MS));
    }

    @Test
    public void shouldCheckNow_falseWhenToggleOff() {
        prefs.setEnabled(false);
        prefs.setLastCheckAt(1000L);
        // Even well past the 24h window, a disabled check issues no request.
        assertFalse(prefs.shouldCheckNow(1000L + 30 * UpdatePreferences.THROTTLE_MS));
    }

    @Test
    public void shouldPrompt_neverPromptedOrDifferentTag() {
        assertTrue(prefs.shouldPrompt("2026.11.07")); // nothing saved yet
        prefs.setPromptedTag("2026.11.07");
        assertFalse(prefs.shouldPrompt("2026.11.07")); // already prompted for this tag
        assertTrue(prefs.shouldPrompt("2026.10.10"));  // a different tag is still promptable
    }

    @Test
    public void setPromptedTag_nullClears() {
        prefs.setPromptedTag("2026.11.07");
        prefs.setPromptedTag(null);
        assertTrue(prefs.shouldPrompt("2026.11.07"));
    }

    @Test
    public void bannerTag_roundTrips() {
        prefs.setLastKnownNewerTag("2026.11.07");
        assertEquals("2026.11.07", prefs.getLastKnownNewerTag());
        prefs.setLastKnownNewerTag(null);
        assertEquals(null, prefs.getLastKnownNewerTag());
    }

    @Test
    public void bannerShownOnlyWhenKnownTagNewerThanCurrent() {
        // Mirrors InfoFragment.applyBanner: the banner appears when the known tag is a
        // CalVer tag strictly newer than the current build.
        String current = "2026.10.10";
        prefs.setLastKnownNewerTag("2026.11.07");
        assertTrue(bannerShouldShow(prefs.getLastKnownNewerTag(), current));

        prefs.setLastKnownNewerTag("2026.10.10");
        assertFalse(bannerShouldShow(prefs.getLastKnownNewerTag(), current));

        prefs.setLastKnownNewerTag("2026.10.09");
        assertFalse(bannerShouldShow(prefs.getLastKnownNewerTag(), current));
    }

    private static boolean bannerShouldShow(String knownNewerTag, String current) {
        return knownNewerTag != null
                && UpdateVersions.isCalVer(knownNewerTag)
                && UpdateVersions.compare(knownNewerTag, current) > 0;
    }
}
