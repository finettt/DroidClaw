package io.finett.droidclaw.util;

import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import android.content.Intent;
import android.util.Log;

import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Utility class for Android instrumented tests.
 * Provides helper methods for test synchronization and waiting.
 */
public class TestUtils {
    
    private static final String TAG = "TestUtils";
    private static final long DEFAULT_TIMEOUT_MS = 10000; // Increased for CI
    private static final long POLL_INTERVAL_MS = 100;
    
    /**
     * Waits for Espresso to be idle.
     * This ensures all pending UI operations and animations are complete.
     */
    public static void waitForIdle() {
        onIdle();
    }
    
    /**
     * Waits for a specified duration.
     * Use sparingly - prefer IdlingResources when possible.
     *
     * @param millis Time to wait in milliseconds
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Polls until the root view is displayed, with a default timeout.
     * This is a best-effort approach suitable for CI emulators that may never report focus.
     * Does NOT throw if focus is never gained - logs a warning instead.
     */
    public static void waitForWindowFocus() {
        waitForWindowFocus(DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * Polls until the root view is displayed, with a configurable timeout.
     * This is a best-effort approach suitable for CI emulators.
     *
     * On headless CI emulators (-no-window), the window may never report has-window-focus=true,
     * but Espresso can still interact with views. This method logs a warning instead of
     * throwing an exception when focus isn't gained.
     *
     * @param timeoutMs Maximum time to wait for root view to be displayed
     */
    public static void waitForWindowFocus(long timeoutMs) {
        // Dismiss any system dialogs that might steal focus
        dismissSystemDialogs();
        
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception lastException = null;
        
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(isRoot())
                        .check(matches(isDisplayed()));
                Log.d(TAG, "Window focus gained successfully");
                return; // Success - window has focus
            } catch (Exception e) {
                lastException = e;
                sleep(POLL_INTERVAL_MS);
            }
        }
        
        // On CI emulators with -no-window, focus may never be reported
        // Log a warning but don't fail - Espresso can still work
        Log.w(TAG, "Window focus not gained within " + timeoutMs + "ms. " +
                "Proceeding anyway (headless emulator compatibility). " +
                "Last exception: " + (lastException != null ? lastException.getMessage() : "none"));
        
        // Give one more attempt to dismiss dialogs and settle
        dismissSystemDialogs();
        sleep(500);
    }
    
    /**
     * Dismisses system dialogs that might interfere with tests.
     * Best-effort - ignores exceptions.
     */
    private static void dismissSystemDialogs() {
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getTargetContext()
                    .sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception e) {
            Log.d(TAG, "Could not dismiss system dialogs: " + e.getMessage());
        }
    }
    
    /**
     * Waits for UI to be ready after activity launch or navigation.
     * Combines window focus polling with Espresso idle synchronization.
     */
    public static void waitForUiReady() {
        waitForWindowFocus();
        onIdle();
    }
    
    /**
     * Simple IdlingResource that waits for a fixed duration.
     * Useful for waiting for animations or async operations.
     */
    public static class SimpleIdlingResource implements IdlingResource {
        private final long waitTimeMs;
        private final long startTime;
        private ResourceCallback callback;
        
        public SimpleIdlingResource(long waitTimeMs) {
            this.waitTimeMs = waitTimeMs;
            this.startTime = System.currentTimeMillis();
        }
        
        @Override
        public String getName() {
            return SimpleIdlingResource.class.getName();
        }
        
        @Override
        public boolean isIdleNow() {
            long elapsed = System.currentTimeMillis() - startTime;
            boolean idle = elapsed >= waitTimeMs;
            
            if (idle && callback != null) {
                callback.onTransitionToIdle();
            }
            
            return idle;
        }
        
        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.callback = callback;
        }
    }
    
    /**
     * Waits for a specified duration using an IdlingResource.
     * This is better than Thread.sleep as it integrates with Espresso's synchronization.
     *
     * @param millis Time to wait in milliseconds
     */
    public static void waitFor(long millis) {
        SimpleIdlingResource idlingResource = new SimpleIdlingResource(millis);
        IdlingRegistry.getInstance().register(idlingResource);
        try {
            onIdle();
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }
    }
}