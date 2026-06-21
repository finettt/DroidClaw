package io.finett.droidclaw.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Converts an {@link AccessibilityNodeInfo} tree into a compact JSON representation
 * suitable for inclusion in an LLM prompt.
 *
 * <p>Only semantically useful fields are included: class name, resource id, text,
 * content description, interactability flags, and screen bounds. Depth is capped to
 * keep token count manageable.
 */
public final class AccessibilityNodeSerializer {

    private AccessibilityNodeSerializer() {}

    /**
     * Serialize an entire window tree starting from {@code root}.
     *
     * @param root      the root {@link AccessibilityNodeInfo} (may be null)
     * @param maxDepth  maximum recursion depth (recommended: 6)
     * @return a {@link JsonObject} with {@code package} and {@code nodes} keys
     */
    public static JsonObject serialize(AccessibilityNodeInfo root, int maxDepth) {
        JsonObject result = new JsonObject();

        if (root == null) {
            result.addProperty("error", "No accessible window content available");
            return result;
        }

        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "unknown";
        result.addProperty("package", pkg);

        JsonArray nodes = new JsonArray();
        serializeNode(root, nodes, 0, maxDepth);
        result.add("nodes", nodes);

        return result;
    }

    private static void serializeNode(AccessibilityNodeInfo node, JsonArray out,
                                      int depth, int maxDepth) {
        if (node == null) return;

        JsonObject obj = new JsonObject();

        // Class name (short form for readability)
        CharSequence cls = node.getClassName();
        if (cls != null) {
            String clsStr = cls.toString();
            int dot = clsStr.lastIndexOf('.');
            obj.addProperty("class", dot >= 0 ? clsStr.substring(dot + 1) : clsStr);
        }

        // Resource ID (most useful for targeting)
        String rid = node.getViewIdResourceName();
        if (rid != null && !rid.isEmpty()) {
            obj.addProperty("resourceId", rid);
        }

        // Visible text
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            obj.addProperty("text", text.toString());
        }

        // Content description (accessibility label)
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.length() > 0) {
            obj.addProperty("contentDescription", cd.toString());
        }

        // Hint text (input fields)
        CharSequence hint = node.getHintText();
        if (hint != null && hint.length() > 0) {
            obj.addProperty("hint", hint.toString());
        }

        // Interactability
        if (node.isClickable())    obj.addProperty("clickable", true);
        if (node.isLongClickable()) obj.addProperty("longClickable", true);
        if (node.isScrollable())   obj.addProperty("scrollable", true);
        if (node.isEditable())     obj.addProperty("editable", true);
        if (node.isEnabled())      obj.addProperty("enabled", true);
        if (node.isChecked())      obj.addProperty("checked", true);
        if (node.isSelected())     obj.addProperty("selected", true);

        // Screen bounds (for coordinate-based taps)
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.isEmpty()) {
            JsonObject boundsObj = new JsonObject();
            boundsObj.addProperty("left", bounds.left);
            boundsObj.addProperty("top", bounds.top);
            boundsObj.addProperty("right", bounds.right);
            boundsObj.addProperty("bottom", bounds.bottom);
            boundsObj.addProperty("centerX", bounds.centerX());
            boundsObj.addProperty("centerY", bounds.centerY());
            obj.add("bounds", boundsObj);
        }

        out.add(obj);

        // Recurse into children if within depth limit
        if (depth < maxDepth) {
            int childCount = node.getChildCount();
            if (childCount > 0) {
                JsonArray children = new JsonArray();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        serializeNode(child, children, depth + 1, maxDepth);
                        child.recycle();
                    }
                }
                if (children.size() > 0) {
                    obj.add("children", children);
                }
            }
        }
    }
}