package io.finett.droidclaw.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.service.BackgroundProcess;
import io.finett.droidclaw.service.BackgroundProcessManager;
import io.finett.droidclaw.tool.impl.KillBackgroundProcessTool;
import io.finett.droidclaw.tool.impl.ListBackgroundProcessesTool;

@RunWith(AndroidJUnit4.class)
public class BackgroundProcessToolsInstrumentedTest {

    private Context context;
    private KillBackgroundProcessTool killTool;
    private ListBackgroundProcessesTool listTool;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        killTool = new KillBackgroundProcessTool(context);
        listTool = new ListBackgroundProcessesTool(context);
    }

    // ==================== KillBackgroundProcessTool ====================

    @Test
    public void killTool_missingProcessId_returnsError() {
        JsonObject args = new JsonObject();
        ToolResult result = killTool.execute(args);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    public void killTool_emptyProcessId_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("process_id", "");
        ToolResult result = killTool.execute(args);
        assertFalse(result.isSuccess());
    }

    @Test
    public void killTool_unknownProcessId_returnsError() {
        JsonObject args = new JsonObject();
        args.addProperty("process_id", "nonexistent-abc-123");
        ToolResult result = killTool.execute(args);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("No background process found"));
    }

    @Test
    public void killTool_completedProcess_returnsNotRunning() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch latch = new CountDownLatch(1);
        String processId = manager.submit("done_tool", "{}", () -> {
            latch.countDown();
            return ToolResult.success(new JsonObject());
        });

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(150);

        JsonObject args = new JsonObject();
        args.addProperty("process_id", processId);
        ToolResult result = killTool.execute(args);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertNotNull(content);
        assertTrue(content.contains("\"killed\":false"));
    }

    @Test
    public void killTool_runningProcess_killsIt() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch startLatch = new CountDownLatch(1);
        String processId = manager.submit("slow_tool", "{}", () -> {
            startLatch.countDown();
            Thread.sleep(30_000);
            return ToolResult.success(new JsonObject());
        });

        startLatch.await(3, TimeUnit.SECONDS);

        JsonObject args = new JsonObject();
        args.addProperty("process_id", processId);
        ToolResult result = killTool.execute(args);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertNotNull(content);
        assertTrue(content.contains("\"killed\":true"));
    }

    @Test
    public void killTool_requiresApproval() {
        assertTrue(killTool.requiresApproval());
    }

    @Test
    public void killTool_name() {
        assertEquals("kill_background_process", killTool.getName());
    }

    // ==================== ListBackgroundProcessesTool ====================

    @Test
    public void listTool_noArgs_returnsAllProcesses() {
        JsonObject args = new JsonObject();
        ToolResult result = listTool.execute(args);
        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertNotNull(content);
        assertTrue(content.contains("\"processes\""));
        assertTrue(content.contains("\"total\""));
    }

    @Test
    public void listTool_filterRunning_returnsOnlyRunning() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch startLatch = new CountDownLatch(1);
        String processId = manager.submit("running_tool", "{}", () -> {
            startLatch.countDown();
            Thread.sleep(20_000);
            return ToolResult.success(new JsonObject());
        });

        startLatch.await(3, TimeUnit.SECONDS);

        JsonObject args = new JsonObject();
        args.addProperty("status", "running");
        ToolResult result = listTool.execute(args);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertNotNull(content);
        assertTrue(content.contains(processId));

        // Cleanup
        manager.kill(processId);
    }

    @Test
    public void listTool_filterCompleted_doesNotIncludeRunning() throws InterruptedException {
        BackgroundProcessManager manager = BackgroundProcessManager.getInstance(context);

        CountDownLatch startLatch = new CountDownLatch(1);
        String runningId = manager.submit("still_running", "{}", () -> {
            startLatch.countDown();
            Thread.sleep(20_000);
            return ToolResult.success(new JsonObject());
        });

        startLatch.await(3, TimeUnit.SECONDS);

        JsonObject args = new JsonObject();
        args.addProperty("status", "completed");
        ToolResult result = listTool.execute(args);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        // The still-running process should NOT appear in completed filter
        assertFalse("Running process should not appear in completed filter",
                content.contains(runningId));

        // Cleanup
        manager.kill(runningId);
    }

    @Test
    public void listTool_doesNotRequireApproval() {
        assertFalse(listTool.requiresApproval());
    }

    @Test
    public void listTool_name() {
        assertEquals("list_background_processes", listTool.getName());
    }

    @Test
    public void listTool_nullArgs_doesNotCrash() {
        ToolResult result = listTool.execute(null);
        assertTrue(result.isSuccess());
    }
}