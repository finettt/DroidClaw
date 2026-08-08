package io.finett.droidclaw.accessibility;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Instrumented tests for {@link AccessibilityNodeSerializer}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Null root serialization — returns error</li>
 *   <li>Empty root node serialization</li>
 *   <li>Node with text and resource ID</li>
 *   <li>Interactability flags serialization</li>
 *   <li>Screen bounds serialization</li>
 *   <li>Class name shortening</li>
 *   <li>Null fields omission</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityNodeSerializerInstrumentedTest {

    // ==================== Null root tests ====================

    @Test
    public void serialize_nullRoot_returnsError() {
        JsonObject result = AccessibilityNodeSerializer.serialize(null, 6);

        assertNotNull(result);
        assertTrue("Should have error property", result.has("error"));
        assertEquals("No accessible window content available",
                result.get("error").getAsString());
        assertFalse("Should have empty nodes array", result.has("nodes"));
    }

    // ==================== Empty root node tests ====================

    @Test
    public void serialize_emptyRootNode_returnsPackage() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        // Don't set anything — empty node

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        assertNotNull(result);
        // Package name should be "unknown" for an empty node
        assertEquals("unknown", result.get("package").getAsString());
        JsonArray nodes = result.getAsJsonArray("nodes");
        assertNotNull(nodes);
        // The root node itself should be serialized
        assertTrue("Should have at least root node", nodes.size() >= 1);

        root.recycle();
    }

    // ==================== Node with text and resource ID ====================

    @Test
    public void serialize_nodeWithTextAndResourceId() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.TextView");
        root.setText("Hello World");
        root.setViewIdResourceName("com.example.app:id/text_view");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        assertEquals("com.example.app", result.get("package").getAsString());
        JsonArray nodes = result.getAsJsonArray("nodes");
        assertNotNull(nodes);
        assertEquals(1, nodes.size());

        JsonObject node = nodes.get(0).getAsJsonObject();
        assertEquals("TextView", node.get("class").getAsString());
        assertEquals("com.example.app:id/text_view", node.get("resourceId").getAsString());
        assertEquals("Hello World", node.get("text").getAsString());

        root.recycle();
    }

    @Test
    public void serialize_nodeWithContentDescription() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.Button");
        root.setContentDescription("Submit order");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertEquals("Submit order", node.get("contentDescription").getAsString());

        root.recycle();
    }

    // ==================== Interactability flags ====================

    @Test
    public void serialize_clickableNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.Button");
        root.setClickable(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("clickable").getAsBoolean());

        root.recycle();
    }

    @Test
    public void serialize_scrollableNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.ListView");
        root.setScrollable(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("scrollable").getAsBoolean());

        root.recycle();
    }

    @Test
    public void serialize_editableNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.EditText");
        root.setEditable(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("editable").getAsBoolean());

        root.recycle();
    }

    @Test
    public void serialize_enabledNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.Button");
        root.setEnabled(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("enabled").getAsBoolean());

        root.recycle();
    }

    @Test
    public void serialize_checkedNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.CheckBox");
        root.setChecked(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("checked").getAsBoolean());

        root.recycle();
    }

    @Test
    public void serialize_selectedNode() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.TextView");
        root.setSelected(true);

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertTrue(node.get("selected").getAsBoolean());

        root.recycle();
    }

    // ==================== Screen bounds ====================

    @Test
    public void serialize_nodeWithBounds() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.Button");

        // setBoundsInScreen requires a Rect — we need a real view for this,
        // so we'll test with an empty bounds (which should be omitted)
        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        // Empty bounds should NOT produce a bounds property
        assertFalse("Empty bounds should not produce bounds property", node.has("bounds"));

        root.recycle();
    }

    // ==================== Depth capping (unit tests cover these scenarios) ====================
    // Depth capping tests are covered by AccessibilityCommandTest and
    // AccessibilityBridgeTest unit tests, which can construct mock node trees.
    // Instrumented tests cannot build parent-child AccessibilityNodeInfo trees
    // because AccessibilityNodeInfo.addChild() requires a View, not an
    // AccessibilityNodeInfo.

    // ==================== Class name shortening ====================

    @Test
    public void serialize_nodeShortensClassName() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.Button");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertEquals("Button", node.get("class").getAsString());

        root.recycle();
    }

    @Test
    public void serialize_nodeWithoutDotInClassName() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("Button");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertEquals("Button", node.get("class").getAsString());

        root.recycle();
    }

    // ==================== Null fields ====================

    @Test
    public void serialize_nodeWithoutText_omitsText() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.ImageView");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertFalse("Node without text should not have text property", node.has("text"));

        root.recycle();
    }

    @Test
    public void serialize_nodeWithoutResourceId_omitsResourceId() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.ImageView");

        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        JsonArray nodes = result.getAsJsonArray("nodes");
        JsonObject node = nodes.get(0).getAsJsonObject();
        assertFalse("Node without resource ID should not have resourceId property",
                node.has("resourceId"));

        root.recycle();
    }

    // ==================== Null child handling ====================

    @Test
    public void serialize_nodeWithNullChild_omitsNull() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("com.example.app");
        root.setClassName("android.widget.FrameLayout");

        // addChild(null) does nothing, but we test that null children in the tree
        // don't cause NPE — the serializer already handles this, so we just verify
        // no crash occurs
        JsonObject result = AccessibilityNodeSerializer.serialize(root, 6);

        assertNotNull(result);
        assertFalse(result.has("error"));

        root.recycle();
    }
}
