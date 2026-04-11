package io.finett.droidclaw.util;

import android.app.Activity;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;

import android.view.View;

import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

/**
 * Activity launch helper that ensures proper window focus before proceeding.
 * Uses a more robust approach than simple waiting.
 */
public class ActivityLaunchHelper {

    private static final String TAG = "ActivityLaunchHelper";
    private static final long INITIAL_WAIT_MS = 1000;
    private static final long FOCUS_TIMEOUT_MS = 10000;
    private static final long POLL_INTERVAL_MS = 250;

    /**
     * Launches an activity and waits for it to be fully ready with window focus.
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
                androidx.test.espresso.Espresso.onView(isRoot())
                        .perform(new WaitForFocusAction());
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

        // Extra time for UI to settle
        TestUtils.sleep(500);
        TestUtils.dismissSystemDialogs();

        return scenario;
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
