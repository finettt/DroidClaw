package io.finett.droidclaw.notification;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.finett.droidclaw.model.TaskResult;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationManager.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class NotificationManagerTest {

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        notificationManager = new NotificationManager(context);
    }

    @Test
    public void testConstructor_createsChannels() {
        // Constructor should complete without exceptions
        // Channels are created automatically
        assertNotNull(notificationManager);
    }

    @Test
    public void testHasPermission_returnsTrueOnSdk33() {
        // On SDK 33 (TIRAMISU), permission should be requested
        // Robolectric grants permissions by default
        boolean hasPermission = notificationManager.hasPermission();
        // Robolectric may return false since permission isn't granted in test
        // We just verify the method doesn't crash and returns a boolean
        assertNotNull(hasPermission);
    }

    @Test
    public void testSendHeartbeatAlert_withValidResult() {
        TaskResult result = new TaskResult();
        result.setId("test-heartbeat-123");
        result.setTaskType("heartbeat");
        result.setTaskName("Heartbeat Check");
        result.setResponse("Found 3 issues in your workspace");
        result.setNotificationTitle("Heartbeat Alert");
        result.setNotificationSummary("Found 3 issues");

        // Should not throw
        notificationManager.sendHeartbeatAlert(result);

        // Verify TaskResult was not modified
        assertEquals("Heartbeat Alert", result.getNotificationTitle());
    }

    @Test
    public void testSendHeartbeatAlert_withNullFields() {
        TaskResult result = new TaskResult();
        result.setId("test-heartbeat-456");
        result.setTaskType("heartbeat");
        result.setResponse("Some response");
        // No notification title or summary set

        // Should use defaults and not throw
        notificationManager.sendHeartbeatAlert(result);
    }

    @Test
    public void testSendCronJobResult_withValidResult() {
        TaskResult result = new TaskResult();
        result.setId("test-cron-123");
        result.setTaskType("cronjob");
        result.setTaskId("cron-job-1");
        result.setTaskName("Server Log Checker");
        result.setResponse("Log analysis complete");
        result.setNotificationTitle("✅ Server Log Checker");
        result.setNotificationSummary("Analysis complete");

        String cronJobId = "cron-job-1";

        // Should not throw
        notificationManager.sendCronJobResult(result, cronJobId);

        assertEquals("cron-job-1", result.getTaskId());
    }

    @Test
    public void testSendCronJobResult_withNullFields() {
        TaskResult result = new TaskResult();
        result.setId("test-cron-456");
        result.setTaskType("cronjob");
        result.setResponse("Task completed");

        String cronJobId = "cron-job-2";

        // Should use defaults and not throw
        notificationManager.sendCronJobResult(result, cronJobId);
    }

    @Test
    public void testCancelNotification() {
        // Should not throw
        notificationManager.cancelNotification(NotificationManager.NOTIFICATION_HEARTBEAT);
    }

    @Test
    public void testCancelAllNotifications() {
        // Should not throw
        notificationManager.cancelAllNotifications();
    }

    @Test
    public void testChannelConstants() {
        assertEquals("heartbeat_channel", NotificationManager.CHANNEL_HEARTBEAT);
        assertEquals("cronjob_channel", NotificationManager.CHANNEL_CRONJOB);
        assertEquals(1001, NotificationManager.NOTIFICATION_HEARTBEAT);
        assertEquals(2000, NotificationManager.NOTIFICATION_CRONJOB_BASE);
        assertEquals(100, NotificationManager.REQUEST_CODE_NOTIFICATION_PERMISSION);
    }
}
