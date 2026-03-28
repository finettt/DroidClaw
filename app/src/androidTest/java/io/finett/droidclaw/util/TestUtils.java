package io.finett.droidclaw.util;

import static androidx.test.espresso.Espresso.onIdle;

import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;

/**
 * Utility class for Android instrumented tests.
 * Provides helper methods for test synchronization and waiting.
 */
public class TestUtils {
    
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