package io.finett.droidclaw.accessibility;

import static org.junit.Assert.*;

import org.junit.Test;

import io.finett.droidclaw.accessibility.AccessibilityCommand.Type;

/**
 * Unit tests for {@link AccessibilityCommand}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Builder pattern — field values are set correctly</li>
 *   <li>Factory helper methods — all command types</li>
 *   <li>Default values — durationMs=300, depth=6</li>
 * </ul>
 */
public class AccessibilityCommandTest {

    // ==================== Factory helper tests ====================

    @Test
    public void getUiTree_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.getUiTree(8);

        assertEquals(Type.GET_UI_TREE, cmd.getType());
        assertEquals(8, cmd.getDepth());
        assertEquals(300, cmd.getDurationMs()); // default
        assertNull(cmd.getResourceId());
        assertNull(cmd.getText());
    }

    @Test
    public void tapAt_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.tapAt(100, 200);

        assertEquals(Type.TAP, cmd.getType());
        assertEquals(100, cmd.getX());
        assertEquals(200, cmd.getY());
        assertEquals(300, cmd.getDurationMs()); // default
    }

    @Test
    public void tapByResourceId_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.tapByResourceId(
                "com.example.app:id/button");

        assertEquals(Type.CLICK_NODE, cmd.getType());
        assertEquals("com.example.app:id/button", cmd.getResourceId());
        assertNull(cmd.getNodeText());
        assertNull(cmd.getText());
    }

    @Test
    public void tapByText_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.tapByText("Submit");

        assertEquals(Type.CLICK_NODE, cmd.getType());
        assertEquals("Submit", cmd.getNodeText());
        assertNull(cmd.getResourceId());
        assertNull(cmd.getText());
    }

    @Test
    public void swipe_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.swipe(100, 500, 100, 200, 500);

        assertEquals(Type.SWIPE, cmd.getType());
        assertEquals(100, cmd.getX());
        assertEquals(500, cmd.getY());
        assertEquals(100, cmd.getX2());
        assertEquals(200, cmd.getY2());
        assertEquals(500, cmd.getDurationMs());
    }

    @Test
    public void swipe_usesDefaultDuration() {
        AccessibilityCommand cmd = AccessibilityCommand.swipe(100, 500, 100, 200, 300);

        assertEquals(300, cmd.getDurationMs());
    }

    @Test
    public void setTextOnFocused_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.setTextOnFocused("hello");

        assertEquals(Type.SET_TEXT, cmd.getType());
        assertEquals("hello", cmd.getText());
        assertNull(cmd.getResourceId());
    }

    @Test
    public void setTextOnNode_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.setTextOnNode(
                "com.example.app:id/input", "hello");

        assertEquals(Type.SET_TEXT, cmd.getType());
        assertEquals("com.example.app:id/input", cmd.getResourceId());
        assertEquals("hello", cmd.getText());
    }

    @Test
    public void globalAction_createsCorrectCommand() {
        AccessibilityCommand cmd = AccessibilityCommand.globalAction(1);

        assertEquals(Type.GLOBAL_ACTION, cmd.getType());
        assertEquals(1, cmd.getGlobalAction());
    }

    // ==================== Builder pattern tests ====================

    @Test
    public void builder_setsAllFields() {
        AccessibilityCommand cmd = new AccessibilityCommand.Builder(Type.TAP)
                .x(100)
                .y(200)
                .durationMs(500)
                .build();

        assertEquals(Type.TAP, cmd.getType());
        assertEquals(100, cmd.getX());
        assertEquals(200, cmd.getY());
        assertEquals(500, cmd.getDurationMs());
    }

    @Test
    public void builder_defaultDurationMs() {
        AccessibilityCommand cmd = new AccessibilityCommand.Builder(Type.TAP)
                .x(100)
                .y(200)
                .build();

        assertEquals(300, cmd.getDurationMs()); // default
    }

    @Test
    public void builder_defaultDepth() {
        AccessibilityCommand cmd = new AccessibilityCommand.Builder(Type.GET_UI_TREE)
                .depth(4)
                .build();

        assertEquals(4, cmd.getDepth());
    }

    @Test
    public void builder_defaultDepthIsSix() {
        AccessibilityCommand cmd = new AccessibilityCommand.Builder(Type.GET_UI_TREE)
                .build();

        assertEquals(6, cmd.getDepth()); // default
    }

    // ==================== Default value tests ====================

    @Test
    public void factoryHelpers_defaultDurationMs() {
        assertEquals(300, AccessibilityCommand.tapAt(0, 0).getDurationMs());
        assertEquals(300, AccessibilityCommand.swipe(0, 0, 0, 0, 300).getDurationMs());
    }

    @Test
    public void factoryHelpers_defaultDepth() {
        assertEquals(6, AccessibilityCommand.getUiTree(6).getDepth());
    }
}
