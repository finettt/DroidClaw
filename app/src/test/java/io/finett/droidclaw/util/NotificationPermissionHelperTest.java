package io.finett.droidclaw.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for NotificationPermissionHelper SDK version checks.
 * Tests the logic that determines when permission is required.
 */
@RunWith(RobolectricTestRunner.class)
public class NotificationPermissionHelperTest {

    private NotificationPermissionHelper permissionHelper;

    @Before
    public void setUp() {
        permissionHelper = new NotificationPermissionHelper(RuntimeEnvironment.getApplication());
    }

    // ==================== SDK VERSION CHECK TESTS ====================

    @Test
    @Config(sdk = Build.VERSION_CODES.S) // Android 12 (API 32) - below TIRAMISU
    public void hasNotificationPermission_returnsTrue_onAndroid12() {
        // POST_NOTIFICATIONS permission not required below API 33
        assertTrue("Permission should be granted on Android 12 (not required)",
                permissionHelper.hasNotificationPermission());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R) // Android 11 (API 30)
    public void hasNotificationPermission_returnsTrue_onAndroid11() {
        assertTrue("Permission should be granted on Android 11 (not required)",
                permissionHelper.hasNotificationPermission());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.P) // Android 9 (API 28)
    public void hasNotificationPermission_returnsTrue_onAndroid9() {
        assertTrue("Permission should be granted on Android 9 (not required)",
                permissionHelper.hasNotificationPermission());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.TIRAMISU) // Android 13 (API 33) - requires permission
    public void hasNotificationPermission_checksPermission_onAndroid13() {
        // On Android 13+, the permission check runs (will be denied in test environment)
        // This test verifies the code path executes without error
        permissionHelper.hasNotificationPermission();
        // In Robolectric test environment, permission state depends on configuration
        // The important thing is the method doesn't crash
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 (API 34)
    public void hasNotificationPermission_checksPermission_onAndroid14() {
        // On Android 14+, the permission check runs
        permissionHelper.hasNotificationPermission();
    }
}
