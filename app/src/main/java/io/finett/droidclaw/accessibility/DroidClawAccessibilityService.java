package io.finett.droidclaw.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.finett.droidclaw.tool.ToolResult;

/**
 * Android Accessibility Service that allows the DroidClaw agent to read the visible UI
 * hierarchy and perform gestures on any on-screen content.
 *
 * <p>This service registers itself with {@link AccessibilityBridge} when connected and
 * unregisters on destroy. The bridge routes {@link AccessibilityCommand} objects here from
 * agent tool calls, passing a {@link CompletableFuture} that the service must complete.
 *
 * <p>All {@link #executeCommand} calls MUST arrive on the main thread (enforced by the
 * bridge posting via a main-thread Handler). Non-gesture commands complete the future
 * synchronously; gesture commands complete it from the gesture result callback.
 */
public class DroidClawAccessibilityService extends AccessibilityService {

    private static final String TAG = "DroidClawA11y";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityBridge.register(this);
        Log.i(TAG, "Service connected and registered with bridge");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No event-driven behaviour needed in v1 — the agent polls via GET_UI_TREE
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        AccessibilityBridge.unregister();
        super.onDestroy();
        Log.i(TAG, "Service destroyed and unregistered from bridge");
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    /**
     * Execute an {@link AccessibilityCommand}, completing {@code future} when done.
     * Must be called on the main thread.
     *
     * <p>Gesture commands complete the future from the gesture callback (async within the
     * main thread loop). All other commands complete the future synchronously before returning.
     */
    public void executeCommand(AccessibilityCommand command, CompletableFuture<ToolResult> future) {
        switch (command.getType()) {
            case GET_UI_TREE:
                future.complete(handleGetUiTree(command));
                break;
            case TAP:
                handleTapAsync(command, future);
                break;
            case SWIPE:
                handleSwipeAsync(command, future);
                break;
            case CLICK_NODE:
                future.complete(handleClickNode(command));
                break;
            case SET_TEXT:
                future.complete(handleSetText(command));
                break;
            case GLOBAL_ACTION:
                future.complete(handleGlobalAction(command));
                break;
            default:
                future.complete(ToolResult.error(
                        "Unknown accessibility command type: " + command.getType()));
        }
    }

    // ── GET_UI_TREE ───────────────────────────────────────────────────────────

    private ToolResult handleGetUiTree(AccessibilityCommand command) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return ToolResult.error("No active window available. "
                    + "The screen may be locked or no app is in the foreground.");
        }
        try {
            JsonObject tree = AccessibilityNodeSerializer.serialize(root, command.getDepth());
            return ToolResult.success(tree);
        } finally {
            root.recycle();
        }
    }

    // ── TAP ───────────────────────────────────────────────────────────────────

    private void handleTapAsync(AccessibilityCommand command,
                                CompletableFuture<ToolResult> future) {
        int x = command.getX();
        int y = command.getY();

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                JsonObject result = new JsonObject();
                result.addProperty("action", "tap");
                result.addProperty("x", x);
                result.addProperty("y", y);
                result.addProperty("success", true);
                future.complete(ToolResult.success(result));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                future.complete(ToolResult.error(
                        "Tap gesture was cancelled at (" + x + ", " + y + ")"));
            }
        }, null);

        if (!accepted) {
            future.complete(ToolResult.error(
                    "Failed to dispatch tap gesture at (" + x + ", " + y + ")"));
        }
    }

    // ── SWIPE ─────────────────────────────────────────────────────────────────

    private void handleSwipeAsync(AccessibilityCommand command,
                                  CompletableFuture<ToolResult> future) {
        int x1 = command.getX();
        int y1 = command.getY();
        int x2 = command.getX2();
        int y2 = command.getY2();
        int duration = Math.max(50, command.getDurationMs());

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                JsonObject result = new JsonObject();
                result.addProperty("action", "swipe");
                result.addProperty("from_x", x1);
                result.addProperty("from_y", y1);
                result.addProperty("to_x", x2);
                result.addProperty("to_y", y2);
                result.addProperty("duration_ms", duration);
                result.addProperty("success", true);
                future.complete(ToolResult.success(result));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                future.complete(ToolResult.error("Swipe gesture was cancelled"));
            }
        }, null);

        if (!accepted) {
            future.complete(ToolResult.error("Failed to dispatch swipe gesture"));
        }
    }

    // ── CLICK_NODE ────────────────────────────────────────────────────────────

    private ToolResult handleClickNode(AccessibilityCommand command) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return ToolResult.error("No active window to find node in");
        }

        try {
            AccessibilityNodeInfo target = findNode(root, command.getResourceId(), command.getNodeText());
            if (target == null) {
                String selector = command.getResourceId() != null
                        ? "resourceId=" + command.getResourceId()
                        : "text=" + command.getNodeText();
                return ToolResult.error("No node found matching: " + selector);
            }

            boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            target.recycle();

            JsonObject result = new JsonObject();
            result.addProperty("action", "click_node");
            if (command.getResourceId() != null) {
                result.addProperty("resourceId", command.getResourceId());
            }
            if (command.getNodeText() != null) {
                result.addProperty("text", command.getNodeText());
            }
            result.addProperty("success", clicked);
            if (!clicked) {
                result.addProperty("error", "performAction(ACTION_CLICK) returned false");
            }
            return ToolResult.success(result);
        } finally {
            root.recycle();
        }
    }

    // ── SET_TEXT ──────────────────────────────────────────────────────────────

    private ToolResult handleSetText(AccessibilityCommand command) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return ToolResult.error("No active window to find editable node in");
        }

        try {
            AccessibilityNodeInfo target;

            if (command.getResourceId() != null) {
                target = findNode(root, command.getResourceId(), null);
                if (target == null) {
                    return ToolResult.error("No editable node found with resourceId="
                            + command.getResourceId());
                }
            } else {
                target = findFocusedEditable(root);
                if (target == null) {
                    return ToolResult.error(
                            "No focused editable field found. Tap a text field first.");
                }
            }

            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    command.getText());
            boolean set = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            target.recycle();

            JsonObject result = new JsonObject();
            result.addProperty("action", "set_text");
            result.addProperty("text", command.getText());
            result.addProperty("success", set);
            if (!set) {
                result.addProperty("error",
                        "ACTION_SET_TEXT returned false — field may not be editable");
            }
            return ToolResult.success(result);
        } finally {
            root.recycle();
        }
    }

    // ── GLOBAL_ACTION ─────────────────────────────────────────────────────────

    private ToolResult handleGlobalAction(AccessibilityCommand command) {
        boolean performed = performGlobalAction(command.getGlobalAction());

        JsonObject result = new JsonObject();
        result.addProperty("action", "global_action");
        result.addProperty("global_action_id", command.getGlobalAction());
        result.addProperty("success", performed);
        if (!performed) {
            result.addProperty("error", "performGlobalAction returned false");
        }
        return ToolResult.success(result);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * BFS through the node tree to find the first node matching resourceId OR text.
     * Caller is responsible for recycling the returned node.
     */
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo root,
                                           String resourceId, String text) {
        if (resourceId != null) {
            List<AccessibilityNodeInfo> found =
                    root.findAccessibilityNodeInfosByViewId(resourceId);
            if (!found.isEmpty()) {
                for (int i = 1; i < found.size(); i++) found.get(i).recycle();
                return found.get(0);
            }
        }

        if (text != null) {
            List<AccessibilityNodeInfo> found =
                    root.findAccessibilityNodeInfosByText(text);
            if (!found.isEmpty()) {
                for (int i = 1; i < found.size(); i++) found.get(i).recycle();
                return found.get(0);
            }
        }

        return null;
    }

    /**
     * Walk the tree to find an editable, focused node (for typing without a resource id).
     */
    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isFocused() && node.isEditable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findFocusedEditable(child);
            if (found != null) {
                if (found != child) child.recycle();
                return found;
            }
            child.recycle();
        }
        return null;
    }
}