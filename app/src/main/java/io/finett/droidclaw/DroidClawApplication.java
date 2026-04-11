package io.finett.droidclaw;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class DroidClawApplication extends Application {

    @Override
    public void onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this);
        super.onCreate();
    }
}
