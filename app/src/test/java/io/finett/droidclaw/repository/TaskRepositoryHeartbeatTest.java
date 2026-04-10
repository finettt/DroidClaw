package io.finett.droidclaw.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import io.finett.droidclaw.model.TaskResult;

/**
 * Additional unit tests for TaskRepository, focusing on heartbeat-related methods.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskRepositoryHeartbeatTest {

    private TaskRepository taskRepository;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        taskRepository = new TaskRepository(context);

        // Clear test data
        context.getSharedPreferences("droidclaw_tasks", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    // ==================== getLastHeartbeatResult() TESTS ====================

    @Test
    public void getLastHeartbeatResult_returnsNull_whenNoResults() {
        TaskResult result = taskRepository.getLastHeartbeatResult();
        assertNull("Should return null when no heartbeat results", result);
    }

    @Test
    public void getLastHeartbeatResult_returnsLatest() {
        // Save multiple heartbeat results
        TaskResult oldResult = new TaskResult("old", TaskResult.TYPE_HEARTBEAT,
                1000L, "Old heartbeat");
        oldResult.putMetadata("healthy", "true");

        TaskResult newResult = new TaskResult("new", TaskResult.TYPE_HEARTBEAT,
                2000L, "New heartbeat");
        newResult.putMetadata("healthy", "false");

        taskRepository.saveTaskResult(oldResult);
        taskRepository.saveTaskResult(newResult);

        TaskResult latest = taskRepository.getLastHeartbeatResult();

        assertNotNull("Should return a result", latest);
        assertEquals("Should return latest result", "new", latest.getId());
        assertEquals("Should have correct timestamp", 2000L, latest.getTimestamp());
    }

    @Test
    public void getLastHeartbeatResult_ignoresNonHeartbeatResults() {
        // Save a cron job result
        TaskResult cronResult = new TaskResult("cron", TaskResult.TYPE_CRON_JOB,
                1000L, "Cron job result");
        taskRepository.saveTaskResult(cronResult);

        TaskResult heartbeat = taskRepository.getLastHeartbeatResult();
        assertNull("Should not return cron result", heartbeat);
    }

    @Test
    public void getLastHeartbeatResult_withMixedResults() {
        TaskResult cronResult = new TaskResult("cron", TaskResult.TYPE_CRON_JOB,
                3000L, "Cron job");
        TaskResult heartbeat1 = new TaskResult("hb1", TaskResult.TYPE_HEARTBEAT,
                1000L, "Heartbeat 1");
        TaskResult heartbeat2 = new TaskResult("hb2", TaskResult.TYPE_HEARTBEAT,
                2000L, "Heartbeat 2");

        taskRepository.saveTaskResult(cronResult);
        taskRepository.saveTaskResult(heartbeat1);
        taskRepository.saveTaskResult(heartbeat2);

        TaskResult latest = taskRepository.getLastHeartbeatResult();

        assertNotNull("Should return heartbeat", latest);
        assertEquals("Should return latest heartbeat", "hb2", latest.getId());
    }

    // ==================== getTaskResults() TYPE FILTERING TESTS ====================

    @Test
    public void getTaskResults_filtersByHeartbeatType() {
        TaskResult heartbeat = new TaskResult("hb", TaskResult.TYPE_HEARTBEAT,
                1000L, "Heartbeat");
        TaskResult cron = new TaskResult("cron", TaskResult.TYPE_CRON_JOB,
                2000L, "Cron");
        TaskResult manual = new TaskResult("manual", TaskResult.TYPE_MANUAL,
                3000L, "Manual");

        taskRepository.saveTaskResult(heartbeat);
        taskRepository.saveTaskResult(cron);
        taskRepository.saveTaskResult(manual);

        List<TaskResult> heartbeats = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should return only heartbeat results", 1, heartbeats.size());
        assertEquals("Should have correct ID", "hb", heartbeats.get(0).getId());
    }

    @Test
    public void getTaskResults_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            TaskResult result = new TaskResult("hb" + i, TaskResult.TYPE_HEARTBEAT,
                    (i + 1) * 1000L, "Heartbeat " + i);
            taskRepository.saveTaskResult(result);
        }

        List<TaskResult> limited = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 3);

        assertEquals("Should respect limit", 3, limited.size());
    }

    @Test
    public void getTaskResults_sortedByTimestampDescending() {
        TaskResult old = new TaskResult("old", TaskResult.TYPE_HEARTBEAT,
                1000L, "Old");
        TaskResult middle = new TaskResult("middle", TaskResult.TYPE_HEARTBEAT,
                2000L, "Middle");
        TaskResult newest = new TaskResult("newest", TaskResult.TYPE_HEARTBEAT,
                3000L, "Newest");

        taskRepository.saveTaskResult(old);
        taskRepository.saveTaskResult(middle);
        taskRepository.saveTaskResult(newest);

        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should return 3 results", 3, results.size());
        assertEquals("First should be newest", "newest", results.get(0).getId());
        assertEquals("Second should be middle", "middle", results.get(1).getId());
        assertEquals("Third should be old", "old", results.get(2).getId());
    }

    // ==================== METADATA TESTS ====================

    @Test
    public void saveHeartbeatResult_preservesHealthyMetadata() {
        TaskResult result = new TaskResult("test", TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(), "Test result");
        result.putMetadata("healthy", "true");
        result.putMetadata("status", "success");

        taskRepository.saveTaskResult(result);

        TaskResult retrieved = taskRepository.getLastHeartbeatResult();
        assertNotNull("Should retrieve result", retrieved);
        assertEquals("healthy metadata should match", "true", retrieved.getMetadataValue("healthy"));
        assertEquals("status metadata should match", "success", retrieved.getMetadataValue("status"));
    }

    @Test
    public void saveHeartbeatResult_preservesErrorMetadata() {
        TaskResult result = new TaskResult("test", TaskResult.TYPE_HEARTBEAT,
                System.currentTimeMillis(), "Failed");
        result.putMetadata("healthy", "false");
        result.putMetadata("status", "failed");
        result.putMetadata("error", "timeout");

        taskRepository.saveTaskResult(result);

        TaskResult retrieved = taskRepository.getLastHeartbeatResult();
        assertNotNull("Should retrieve result", retrieved);
        assertEquals("healthy should be false", "false", retrieved.getMetadataValue("healthy"));
        assertEquals("error should match", "timeout", retrieved.getMetadataValue("error"));
    }

    // ==================== OVERWRITE TESTS ====================

    @Test
    public void saveTaskResult_overwritesExistingId() {
        TaskResult result1 = new TaskResult("test", TaskResult.TYPE_HEARTBEAT,
                1000L, "First");
        result1.putMetadata("healthy", "true");
        taskRepository.saveTaskResult(result1);

        TaskResult result2 = new TaskResult("test", TaskResult.TYPE_HEARTBEAT,
                2000L, "Second");
        result2.putMetadata("healthy", "false");
        taskRepository.saveTaskResult(result2);

        List<TaskResult> results = taskRepository.getTaskResults(TaskResult.TYPE_HEARTBEAT, 10);

        assertEquals("Should have only 1 result with this ID", 1, results.size());
        assertEquals("Should have latest content", "Second", results.get(0).getContent());
        assertEquals("Should have latest metadata", "false", results.get(0).getMetadataValue("healthy"));
    }

    // ==================== TASK TYPE CONSTANTS TESTS ====================

    @Test
    public void taskTypeConstants_haveExpectedValues() {
        assertEquals("TYPE_HEARTBEAT should be 1", 1, TaskResult.TYPE_HEARTBEAT);
        assertEquals("TYPE_CRON_JOB should be 2", 2, TaskResult.TYPE_CRON_JOB);
        assertEquals("TYPE_MANUAL should be 3", 3, TaskResult.TYPE_MANUAL);
    }

    @Test
    public void typeToString_convertsCorrectly() {
        assertEquals("HEARTBEAT", TaskResult.typeToString(TaskResult.TYPE_HEARTBEAT));
        assertEquals("CRON_JOB", TaskResult.typeToString(TaskResult.TYPE_CRON_JOB));
        assertEquals("MANUAL", TaskResult.typeToString(TaskResult.TYPE_MANUAL));
        assertEquals("UNKNOWN(99)", TaskResult.typeToString(99));
    }
}
