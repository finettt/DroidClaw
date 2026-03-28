package io.finett.droidclaw.util;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import io.finett.droidclaw.R;

/**
 * Helper class to provide themed context for tests that need Material Design attributes.
 * This prevents inflation errors when testing views that require Material Design theme.
 */
public class TestThemeHelper {
    
    /**
     * Returns a context wrapped with the app's Material Design theme.
     * Use this context when creating views in tests to avoid inflation errors.
     * 
     * @return Context wrapped with Theme.DroidClaw
     */
    public static Context getThemedContext() {
        return new ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_DroidClaw
        );
    }
    
    /**
     * Returns a context wrapped with a specific theme.
     * 
     * @param themeResId The resource ID of the theme to apply
     * @return Context wrapped with the specified theme
     */
    public static Context getThemedContext(int themeResId) {
        return new ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            themeResId
        );
    }
}