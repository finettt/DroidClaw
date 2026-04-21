package io.finett.droidclaw.util;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import io.finett.droidclaw.R;

public class TestThemeHelper {
    
        public static Context getThemedContext() {
        return new ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_DroidClaw
        );
    }
    
        public static Context getThemedContext(int themeResId) {
        return new ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            themeResId
        );
    }
}