package io.finett.droidclaw.util;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoActivityResumedException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;

import org.hamcrest.Matcher;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import io.finett.droidclaw.R;

public class ActivityLaunchHelper {

    private static final String TAG = "ActivityLaunchHelper";
    private static final long INITIAL_WAIT_MS = 1500;
    private static final long FOCUS_TIMEOUT_MS = 20000;
    private static final long POLL_INTERVAL_MS = 300;
    private static final int MAX_LAUNCH_RETRIES = 3;

    /**
     * Launch an activity and wait for it to be fully resumed and displayed.
     * Retries the launch if the activity does not reach RESUMED state within the timeout.
     */
    public static <T extends Activity> ActivityScenario<T> launchAndWait(Class<T> activityClass) {
        TestUtils.dismissSystemDialogs();

        int attempt = 0;
        while (attempt < MAX_LAUNCH_RETRIES) {
            attempt++;
            try {
                return doLaunch(activityClass);
            } catch (NoActivityResumedException e) {
                Log.w(TAG, "Activity not resumed on attempt " + attempt
                        + "/" + MAX_LAUNCH_RETRIES + ": " + e.getMessage());
                TestUtils.sleep(2000);
                TestUtils.dismissSystemDialogs();
            } catch (RuntimeException e) {
                // Espresso may wrap NoActivityResumedException in a RuntimeException.
                // Unwrap and check before treating as a transient failure.
                if (isWrappedNoActivityResumed(e)) {
                    Log.w(TAG, "Activity not resumed (wrapped) on attempt " + attempt
                            + "/" + MAX_LAUNCH_RETRIES + ": " + e.getMessage());
                    TestUtils.sleep(2000);
                    TestUtils.dismissSystemDialogs();
                } else {
                    throw e;
                }
            }
        }

        // All retries exhausted — throw a clear error instead of a silent fallback.
        throw new RuntimeException("Failed to launch " + activityClass.getSimpleName()
                + " after " + MAX_LAUNCH_RETRIES + " attempts");
    }

    /**
     * Returns true if {@code re} is a RuntimeException wrapping a
     * {@link NoActivityResumedException} in its cause chain.
     */
    private static boolean isWrappedNoActivityResumed(RuntimeException re) {
        Throwable cause = re.getCause();
        while (cause != null) {
            if (cause instanceof NoActivityResumedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static <T extends Activity> ActivityScenario<T> doLaunch(Class<T> activityClass) {
        ActivityScenario<T> scenario = ActivityScenario.launch(activityClass);

        // Wait for activity to reach RESUMED state via onActivity callback.
        try {
            scenario.onActivity(activity -> {
                Log.d(TAG, "Activity RESUMED: " + activity.getClass().getSimpleName());
            });
        } catch (NoActivityResumedException e) {
            Log.w(TAG, "Activity not yet resumed after launch, waiting...");
            TestUtils.sleep(INITIAL_WAIT_MS);
            try {
                scenario.onActivity(activity -> { });
            } catch (NoActivityResumedException e2) {
                Log.w(TAG, "Activity still not resumed after initial wait");
                scenario.close();
                throw e2;
            }
        }

        // Additional wait for the activity to settle
        TestUtils.sleep(INITIAL_WAIT_MS);

        // Wait for window focus
        waitForWindowFocus();

        // Wait for drawer layout
        waitForDrawerLayout();

        TestUtils.dismissSystemDialogs();

        return scenario;
    }

    private static void waitForWindowFocus() {
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
