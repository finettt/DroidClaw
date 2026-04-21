package io.finett.droidclaw.util;

import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.NoMatchingRootException;
import androidx.test.platform.app.InstrumentationRegistry;

import io.finett.droidclaw.R;

public class TestUtils {

    private static final String TAG = "TestUtils";
    private static final long DEFAULT_TIMEOUT_MS = 10000; // Increased for CI
    private static final long POLL_INTERVAL_MS = 250;
    public static void waitForIdle() {
        onIdle();
    }
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    public static void waitForWindowFocus() {
        waitForWindowFocus(DEFAULT_TIMEOUT_MS);
    }
    public static void waitForWindowFocus(long timeoutMs) {
        dismissSystemDialogs();

        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                onView(isRoot()).check(matches(isDisplayed()));
                Log.d(TAG, "Root view displayed successfully");
                sleep(300);
                return;
            } catch (NoMatchingRootException e) {
                sleep(POLL_INTERVAL_MS);
            } catch (Exception e) {
                Log.d(TAG, "Waiting for window focus: " + e.getMessage());
                sleep(POLL_INTERVAL_MS);
            }
        }

        Log.w(TAG, "Window focus not gained within " + timeoutMs + "ms. " +
                "Proceeding anyway (headless emulator compatibility).");

        dismissSystemDialogs();
        sleep(500);
    }
    public static void dismissSystemDialogs() {
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getTargetContext()
                    .sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception e) {
            Log.d(TAG, "Could not dismiss system dialogs: " + e.getMessage());
        }
    }
    public static void waitForUiReady() {
        waitForWindowFocus();
        onIdle();
    }
    public static void waitForChatFragment() {
        waitForChatFragment(DEFAULT_TIMEOUT_MS);
    }
    public static void waitForChatFragment(long timeoutMs) {
        waitForUiReady();

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.messageInput)).check(matches(isDisplayed()));
                Log.d(TAG, "ChatFragment is displayed");
                onIdle();
                sleep(300);
                return;
            } catch (Exception e) {
                Log.d(TAG, "Waiting for ChatFragment: " + e.getMessage());
                sleep(POLL_INTERVAL_MS);
            }
        }

        Log.w(TAG, "ChatFragment was not displayed within timeout");
    }
    public static void waitForViewDisplayed(org.hamcrest.Matcher<View> viewMatcher, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(viewMatcher).check(matches(isDisplayed()));
                return; // View is displayed
            } catch (Exception e) {
                sleep(POLL_INTERVAL_MS);
            }
        }
        Log.w(TAG, "View not displayed within timeout");
    }
    public static void waitForViewDisplayed(org.hamcrest.Matcher<View> viewMatcher) {
        waitForViewDisplayed(viewMatcher, DEFAULT_TIMEOUT_MS);
    }

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
    public static void waitFor(long millis) {
        SimpleIdlingResource idlingResource = new SimpleIdlingResource(millis);
        IdlingRegistry.getInstance().register(idlingResource);
        try {
            onIdle();
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }
    }
    public static int getDrawableResourceId(ImageView imageView) {
        if (imageView == null) {
            return -1;
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) {
            return -1;
        }
        try {
            String resourceName = imageView.getResources().getResourceEntryName(
                    imageView.getResources().getIdentifier(
                            drawable.toString(),
                            "drawable",
                            imageView.getContext().getPackageName()));
            return getDrawableResIdViaReflection(imageView);
        } catch (Exception e) {
            return -1;
        }
    }
    private static int getDrawableResIdViaReflection(ImageView imageView) {
        try {
            Drawable drawable = imageView.getDrawable();
            java.lang.reflect.Field field = drawable.getClass().getDeclaredField("mResource");
            field.setAccessible(true);
            return field.getInt(drawable);
        } catch (Exception e) {
            return -1;
        }
    }
}