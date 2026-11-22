package io.finett.droidclaw;

import android.app.Application;
import android.os.StrictMode;

import com.google.android.material.color.DynamicColors;

public class DroidClawApplication extends Application {

    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG) {
            // Debug-only: surface main-thread disk/network violations in logcat during
            // development. Release builds are unaffected.
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
        DynamicColors.applyToActivitiesIfAvailable(this);
        super.onCreate();
    }
}
