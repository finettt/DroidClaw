package io.finett.droidclaw.accessibility;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.finett.droidclaw.tool.ToolResult;

/**
 * Singleton bridge between agent tools and the {@link DroidClawAccessibilityService}.
 *
 * <p>Tools call {@link #execute(AccessibilityCommand)} synchronously on a background thread.
 * The bridge posts the command to the service's main-thread handler and blocks on a
 * {@link CompletableFuture} until the service completes the command (or a timeout fires).
 */
public final class AccessibilityBridge {

    private static final String TAG = "AccessibilityBridge";
    private static final long COMMAND_TIMEOUT_SECONDS = 10;

    private static volatile DroidClawAccessibilityService serviceInstance;

    private AccessibilityBridge() {
        // Singleton — no instantiation
    }

    /**
     * Called by {@link DroidClawAccessibilityService#onServiceConnected()} to register itself.
     */
    public static void register(DroidClawAccessibilityService service) {
        serviceInstance = service;
        Log.i(TAG, "Accessibility service registered");
    }

    /**
     * Called by {@link DroidClawAccessibilityService#onDestroy()} to unregister itself.
     */
    public static void unregister() {
        serviceInstance = null;
        Log.i(TAG, "Accessibility service unregistered");
    }

    /**
     * Returns {@code true} if the accessibility service is currently connected and usable.
     */
    public static boolean isConnected() {
        return serviceInstance != null;
    }

    /**
     * Execute an accessibility command synchronously.
     *
     * <p>This method MUST be called from a background thread — it blocks until the service
     * completes the command or a timeout occurs.
     *
     * @param command the command to execute
     * @return a {@link ToolResult} with the outcome
     */
    public static ToolResult execute(AccessibilityCommand command) {
        DroidClawAccessibilityService service = serviceInstance;
        if (service == null) {
            return ToolResult.error("Accessibility service is not connected. "
                    + "Please enable DroidClaw in Settings → Accessibility.");
        }

        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        boolean onMainThread = Looper.myLooper() == Looper.getMainLooper();

        if (onMainThread) {
            // We're already on the main thread — calling future.get() here would deadlock
            // because the Handler.post() would never run. Invoke the service directly.
            // For synchronous commands (GET_UI_TREE, CLICK_NODE, SET_TEXT, GLOBAL_ACTION),
            // the future is completed inline and future.getNow(...) returns immediately.
            // For gesture commands (TAP, SWIPE), we cannot block here at all — instead we
            // return a synchronous result that informs the caller the gesture was dispatched.
            try {
                service.executeCommand(command, future);
            } catch (Exception e) {
                Log.e(TAG, "Command execution failed", e);
                return ToolResult.error("Accessibility command failed: " + e.getMessage());
            }

            // If the command completed synchronously (non-gesture), return its result now.
            if (future.isDone()) {
                try {
                    return future.get();
                } catch (Exception e) {
                    return ToolResult.error("Accessibility command failed: " + e.getMessage());
                }
            }

            // Gesture commands are dispatched asynchronously by the framework. We cannot
            // block the main thread waiting for their callback (it runs on the main thread
            // too). Return a "dispatched" result so the agent loop can continue. The user
            // should call screen_get_ui_tree afterwards to verify the gesture took effect.
            return ToolResult.success("{\"status\":\"dispatched\","
                    + "\"message\":\"Gesture dispatched. Wait briefly then call "
                    + "screen_get_ui_tree to verify the effect.\"}");
        }

        // Background thread caller — safe to post and block on the future.
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                service.executeCommand(command, future);
            } catch (Exception e) {
                Log.e(TAG, "Command execution failed", e);
                future.complete(ToolResult.error("Accessibility command failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return ToolResult.error("Accessibility command timed out after "
                    + COMMAND_TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            return ToolResult.error("Accessibility command interrupted: " + e.getMessage());
        }
    }
}