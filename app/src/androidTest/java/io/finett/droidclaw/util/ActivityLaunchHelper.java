package io.finett.droidclaw.util;

import android.app.Activity;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;

import android.view.View;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import io.finett.droidclaw.R;

public class ActivityLaunchHelper {

    private static final String TAG = "ActivityLaunchHelper";
    private static final long INITIAL_WAIT_MS = 1000;
    private static final long FOCUS_TIMEOUT_MS = 15000;
    private static final long POLL_INTERVAL_MS = 250;

        public static <T extends Activity> ActivityScenario<T> launchAndWait(Class<T> activityClass) {
        TestUtils.dismissSystemDialogs();

        ActivityScenario<T> scenario = ActivityScenario.launch(activityClass);

        TestUtils.sleep(INITIAL_WAIT_MS);

        // Poll for window focus
        long deadline = System.currentTimeMillis() + FOCUS_TIMEOUT_MS;
        boolean focusGained = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                onView(isRoot()).perform(new WaitForFocusAction());
                focusGained = true;
                break;
            } catch (Exception e) {
                Log.d(TAG, "Waiting for focus: " + e.getMessage());
                TestUtils.sleep(POLL_INTERVAL_MS);
            }
        }

        if (!focusGained) {
            Log.w(TAG, "Window focus not gained within timeout, proceeding anyway");
        }

        waitForDrawerLayout();

        TestUtils.dismissSystemDialogs();

        return scenario;
    }

        private static void waitForDrawerLayout() {
        long deadline = System.currentTimeMillis() + FOCUS_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.drawer_layout)).check(matches(isDisplayed()));
                Log.d(TAG, "drawer_layout is displayed");
                TestUtils.sleep(800);
                return;
            } catch (Exception e) {
                Log.d(TAG, "Waiting for drawer_layout: " + e.getMessage());
                TestUtils.sleep(POLL_INTERVAL_MS);
            }
        }
        Log.w(TAG, "drawer_layout not found within timeout, proceeding anyway");
        TestUtils.sleep(500);
    }

        private static class WaitForFocusAction implements ViewAction {
        @Override
        public Matcher<View> getConstraints() {
            return isRoot();
        }

        @Override
        public String getDescription() {
            return "Wait for window focus";
        }

        @Override
        public void perform(UiController uiController, View view) {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                if (view.hasWindowFocus()) {
                    return;
                }
                uiController.loopMainThreadForAtLeast(50);
            }
            Log.w(TAG, "View did not get window focus within timeout");
        }
    }
}
