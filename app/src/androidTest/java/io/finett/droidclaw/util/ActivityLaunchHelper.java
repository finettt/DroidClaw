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

/**
 * Activity launch helper that ensures proper window focus before proceeding.
 * Uses a more robust approach than simple waiting.
 */
public class ActivityLaunchHelper {

    private static final String TAG = "ActivityLaunchHelper";
    private static final long INITIAL_WAIT_MS = 1000;
    private static final long FOCUS_TIMEOUT_MS = 15000;
    private static final long POLL_INTERVAL_MS = 250;

    /**
     * Launches an activity and waits for it to be fully ready with window focus
     * AND for the drawer layout (the activity's root view) to appear in the hierarchy.
     * More robust than simple ActivityScenario.launch().
     */
    public static <T extends Activity> ActivityScenario<T> launchAndWait(Class<T> activityClass) {
        // Dismiss any system dialogs first
        TestUtils.dismissSystemDialogs();

        // Launch the activity
        ActivityScenario<T> scenario = ActivityScenario.launch(activityClass);

        // Wait for initial setup
        TestUtils.sleep(INITIAL_WAIT_MS);

        // Poll for window focus
        long deadline = System.currentTimeMillis() + FOCUS_TIMEOUT_MS;
        boolean focusGained = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                // Try to interact with the root view
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

        // Wait for the drawer_layout (activity root) to appear in the view hierarchy.
        // This is essential because MainActivity uses drawerLayout.post() to defer
        // navigation, so the fragment may not be inflated immediately.
        waitForDrawerLayout();

        TestUtils.dismissSystemDialogs();

        return scenario;
    }

    /**
     * Polls until R.id.drawer_layout is displayed in the hierarchy, indicating
     * that MainActivity's setContentView() has completed and the activity is live.
     * Does not throw if not found – logs a warning and proceeds.
     */
    private static void waitForDrawerLayout() {
        long deadline = System.currentTimeMillis() + FOCUS_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.drawer_layout)).check(matches(isDisplayed()));
                Log.d(TAG, "drawer_layout is displayed");
                // Extra settle time for the deferred post() navigation to fire
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

    /**
     * Custom ViewAction that waits for the view to have window focus.
     */
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
            // Loop until the view has window focus or timeout
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                if (view.hasWindowFocus()) {
                    return;
                }
                uiController.loopMainThreadForAtLeast(50);
            }
            // Don't throw - just proceed anyway for headless emulators
            Log.w(TAG, "View did not get window focus within timeout");
        }
    }
}
