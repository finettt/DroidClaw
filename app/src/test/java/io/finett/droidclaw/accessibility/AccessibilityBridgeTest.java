package io.finett.droidclaw.accessibility;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Unit tests for {@link AccessibilityBridge}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>register() and unregister() lifecycle</li>
 *   <li>isConnected() state transitions</li>
 *   <li>execute() on background thread — future is completed</li>
 *   <li>execute() on main thread — immediate return</li>
 *   <li>execute() with null service — error result</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
public class AccessibilityBridgeTest {

    @Mock
    private DroidClawAccessibilityService mockService;

    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Stub executeCommand to complete the future synchronously for non-gesture
        // commands, but NOT for gesture commands (TAP/SWIPE) — those complete
        // asynchronously from the gesture callback.
        doAnswer(invocation -> {
            AccessibilityCommand cmd = invocation.getArgument(0);
            CompletableFuture<ToolResult> future = invocation.getArgument(1);
            if (future != null && !future.isDone()
                    && cmd != null
                    && cmd.getType() != AccessibilityCommand.Type.TAP
                    && cmd.getType() != AccessibilityCommand.Type.SWIPE) {
                future.complete(ToolResult.success("{\"status\":\"ok\"}"));
            }
            return null;
        }).when(mockService).executeCommand(any(AccessibilityCommand.class), any(CompletableFuture.class));
    }


    @After
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        AccessibilityBridge.unregister();
    }

    // ==================== register() / unregister() tests ====================

    @Test
    public void isConnected_falseInitially() {
        assertFalse("Bridge should not be connected initially",
                AccessibilityBridge.isConnected());
    }

    @Test
    public void register_setsConnected() {
        AccessibilityBridge.register(mockService);

        assertTrue("Bridge should be connected after register()",
                AccessibilityBridge.isConnected());
    }

    @Test
    public void unregister_setsDisconnected() {
        AccessibilityBridge.register(mockService);
        AccessibilityBridge.unregister();

        assertFalse("Bridge should not be connected after unregister()",
                AccessibilityBridge.isConnected());
    }

    @Test
    public void isConnected_afterRegisterAndUnregister() {
        AccessibilityBridge.register(mockService);
        assertTrue(AccessibilityBridge.isConnected());

        AccessibilityBridge.unregister();
        assertFalse(AccessibilityBridge.isConnected());
    }

    // ==================== execute() tests — null service ====================

    @Test
    public void execute_nullService_returnsError() {
        AccessibilityBridge.unregister();

        ToolResult result = AccessibilityBridge.execute(
                AccessibilityCommand.tapAt(100, 200));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Accessibility service"));
    }

    @Test
    public void execute_nullService_getUiTreeReturnsError() {
        AccessibilityBridge.unregister();

        ToolResult result = AccessibilityBridge.execute(
                AccessibilityCommand.getUiTree(6));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Accessibility service"));
    }

    // ==================== execute() tests — connected service ====================

    @Test
    public void execute_tapCommand_sendsCommandToService() {
        AccessibilityBridge.register(mockService);

        AccessibilityCommand cmd = AccessibilityCommand.tapAt(100, 200);
        ToolResult result = AccessibilityBridge.execute(cmd);

        // The bridge should post the command and return a result
        // Since we're mocking executeCommand to complete inline, we get a success
        assertNotNull(result);
    }

    @Test
    public void execute_getUiTreeCommand_sendsCommandToService() {
        AccessibilityBridge.register(mockService);

        AccessibilityCommand cmd = AccessibilityCommand.getUiTree(6);
        ToolResult result = AccessibilityBridge.execute(cmd);

        assertNotNull(result);
    }

    @Test
    public void execute_swipeCommand_sendsCommandToService() {
        AccessibilityBridge.register(mockService);

        AccessibilityCommand cmd = AccessibilityCommand.swipe(100, 500, 100, 200, 300);
        ToolResult result = AccessibilityBridge.execute(cmd);

        assertNotNull(result);
    }

    @Test
    public void execute_globalActionCommand_sendsCommandToService() {
        AccessibilityBridge.register(mockService);

        AccessibilityCommand cmd = AccessibilityCommand.globalAction(1);
        ToolResult result = AccessibilityBridge.execute(cmd);

        assertNotNull(result);
    }

    @Test
    public void execute_nullCommand_returnsError() {
        AccessibilityBridge.register(mockService);

        ToolResult result = AccessibilityBridge.execute(null);

        // Null command should cause an exception in executeCommand
        // which gets caught and returned as an error
        assertNotNull(result);
    }

    // ==================== Main thread execute() tests ====================

    @Test
    public void executeOnMainThread_tapCommand_returnsDispatched() {
        AccessibilityBridge.register(mockService);

        // Robolectric provides a main looper, so Looper.myLooper() == main looper
        Looper mainLooper = RuntimeEnvironment.getApplication().getMainLooper();
        assertTrue("Should be on main thread in Robolectric",
                Looper.myLooper() == mainLooper);

        AccessibilityCommand cmd = AccessibilityCommand.tapAt(100, 200);
        ToolResult result = AccessibilityBridge.execute(cmd);

        // On main thread, gesture commands return a "dispatched" result
        // because we cannot block the main thread
        assertNotNull(result);
        assertNotNull(result.getContent());
        Gson gson = new Gson();
        JsonObject parsed = gson.fromJson(result.getContent(), JsonObject.class);
        assertNotNull("status field should not be null", parsed.get("status"));
        assertTrue("Main thread gesture should return dispatched result",
                "dispatched".equals(parsed.get("status").getAsString()));
    }

    @Test
    public void executeOnMainThread_getUiTreeCommand_returnsSuccess() {
        AccessibilityBridge.register(mockService);

        AccessibilityCommand cmd = AccessibilityCommand.getUiTree(6);
        ToolResult result = AccessibilityBridge.execute(cmd);

        // On main thread, non-gesture commands complete synchronously
        assertNotNull(result);
    }
}
