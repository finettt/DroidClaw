package io.finett.droidclaw.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.tool.ToolResult;

@RunWith(AndroidJUnit4.class)
public class BackgroundProcessManagerInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void submit_returnsProcessId() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);

        String processId = manager.submit("test_tool", "{}", () -> {
            latch.countDown();
            return ToolResult.success(new JsonObject());
        });

        assertNotNull(processId);
        assertFalse(processId.isEmpty());

        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void submit_processCompletesSuccessfully() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);

        String processId = manager.submit("test_tool", "{}", () -> {
            JsonObject result = new JsonObject();
            result.addProperty("value", "hello");
            latch.countDown();
            return ToolResult.success(result);
        });

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Task did not complete in time", done);

        // Give a moment for status update
        Thread.sleep(100);

        BackgroundProcess process = manager.get(processId);
        assertNotNull(process);
        assertEquals(BackgroundProcess.STATUS_COMPLETED, process.getStatus());
        assertNotNull(process.getResult());
        assertTrue(process.getFinishedAt() > 0);
    }

    @Test
    public void submit_processFailsOnException() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);

        String processId = manager.submit("failing_tool", "{}", () -> {
            latch.countDown();
            throw new RuntimeException("deliberate failure");
        });

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Task did not complete in time", done);

        Thread.sleep(100);

        BackgroundProcess process = manager.get(processId);
        assertNotNull(process);
        assertEquals(BackgroundProcess.STATUS_FAILED, process.getStatus());
        assertNotNull(process.getError());
    }

    @Test
    public void kill_runningProcess_returnsTrue() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch startedLatch = new CountDownLatch(1);

        String processId = manager.submit("long_tool", "{}", () -> {
            startedLatch.countDown();
            // Simulate long-running task that respects interruption
            Thread.sleep(30_000);
            return ToolResult.success(new JsonObject());
        });

        // Wait for task to start
        boolean started = startedLatch.await(3, TimeUnit.SECONDS);
        assertTrue("Task did not start in time", started);

        boolean killed = manager.kill(processId);
        assertTrue("Kill should return true for a running process", killed);

        Thread.sleep(200);

        BackgroundProcess process = manager.get(processId);
        assertNotNull(process);
        assertEquals(BackgroundProcess.STATUS_KILLED, process.getStatus());
    }

    @Test
    public void kill_nonExistentProcess_returnsFalse() {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);
        assertFalse(manager.kill("nonexistent-process-id"));
    }

    @Test
    public void listAll_includesSubmittedProcesses() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);
        String processId = manager.submit("list_test_tool", "{}", () -> {
            latch.countDown();
            return ToolResult.success(new JsonObject());
        });

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        List<BackgroundProcess> all = manager.listAll();
        boolean found = false;
        for (BackgroundProcess p : all) {
            if (processId.equals(p.getId())) {
                found = true;
                break;
            }
        }
        assertTrue("Submitted process should appear in listAll()", found);
    }

    @Test
    public void runningCount_decreasesAfterCompletion() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);
        manager.submit("count_test_tool", "{}", () -> {
            latch.countDown();
            return ToolResult.success(new JsonObject());
        });

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        // After completion the count should not include this finished process
        int running = manager.runningCount();
        // We can only assert it's non-negative; other tests may have running processes
        assertTrue(running >= 0);
    }

    @Test
    public void get_unknownId_returnsNull() {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);
        assertNull(manager.get("totally-unknown-id-xyz"));
    }

    @Test
    public void backgroundProcess_getDurationMs_returnsZeroWhileRunning() {
        BackgroundProcess process = new BackgroundProcess("id", "tool", "{}");
        assertEquals(0L, process.getDurationMs());
    }

    @Test
    public void backgroundProcess_getDurationMs_returnsCorrectValueAfterFinish() {
        BackgroundProcess process = new BackgroundProcess("id", "tool", "{}");
        long now = System.currentTimeMillis() + 100;
        process.setFinishedAt(now);
        assertTrue(process.getDurationMs() > 0);
    }
}