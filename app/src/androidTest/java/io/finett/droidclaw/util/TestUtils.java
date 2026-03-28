package io.finett.droidclaw.util;

import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;

/**
 * Utility class for Android instrumented tests.
 * Provides helper methods for test synchronization and waiting.
 */
public class TestUtils {
    
    private static final long DEFAULT_TIMEOUT_MS = 5000;
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
     * Polls until the root view is displayed, with a default timeout of 5 seconds.
     * This is a deterministic approach that waits only as long as needed.
     *
     * @throws AssertionError if root view is never displayed within timeout
     */
    public static void waitForWindowFocus() {
        waitForWindowFocus(DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * Polls until the root view is displayed, with a configurable timeout.
     * This is a deterministic approach that waits only as long as needed.
     *
     * @param timeoutMs Maximum time to wait for root view to be displayed
     * @throws AssertionError if root view is never displayed within timeout
     */
    public static void waitForWindowFocus(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception lastException = null;
        
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(isRoot())
                        .check(matches(isDisplayed()));
                return; // Success - window has focus
            } catch (Exception e) {
                lastException = e;
                sleep(POLL_INTERVAL_MS);
            }
        }
        
        throw new AssertionError("Root view was not displayed within " + timeoutMs + "ms", lastException);
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