package io.finett.droidclaw.python;

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Tests for the #150 containment: a wedged (uncancellable) run must not
 * block subsequent runs.
 */
public class SerialAbandoningRunnerTest {

    @Test
    public void testBlockedFirstRun_doesNotPreventSecondRun() throws Exception {
        SerialAbandoningRunner runner = new SerialAbandoningRunner("test");
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch neverReleased = new CountDownLatch(1);
        final AtomicBoolean firstStillRunning = new AtomicBoolean(true);

        // First run: simulates uninterruptible CPython code (ignores interrupt).
        Callable<String> wedged = () -> {
            firstStarted.countDown();
            // Ignore interrupts, like Chaquopy-executed CPython code does.
            while (neverReleased.getCount() > 0) {
                try {
                    neverReleased.await();
                } catch (InterruptedException ignored) {
                    // swallow -- simulate code that never observes interrupts
                }
            }
            firstStillRunning.set(false);
            return "first";
        };

        try {
            runner.run(wedged, 200, TimeUnit.MILLISECONDS);
            fail("Expected TimeoutException for the wedged run");
        } catch (TimeoutException expected) {
            // abandoned, as designed
        }

        assertTrue("First run should have started",
                firstStarted.await(2, TimeUnit.SECONDS));
        assertTrue("First run must still be wedged in the background",
                firstStillRunning.get());
        assertEquals(1, runner.getAbandonedCount());

        // Second run must proceed on a fresh thread despite the wedged first run.
        String result = runner.run(() -> "second", 5, TimeUnit.SECONDS);
        assertEquals("second", result);

        // Cleanup: release the wedged thread.
        neverReleased.countDown();
    }

    @Test
    public void testHealthyRuns_executeSequentiallyAndReturnResults() throws Exception {
        SerialAbandoningRunner runner = new SerialAbandoningRunner("test");
        assertEquals("a", runner.run(() -> "a", 5, TimeUnit.SECONDS));
        assertEquals("b", runner.run(() -> "b", 5, TimeUnit.SECONDS));
        assertEquals(0, runner.getAbandonedCount());
    }

    @Test
    public void testEachRunUsesAFreshThread() throws Exception {
        SerialAbandoningRunner runner = new SerialAbandoningRunner("test");
        String t1 = runner.run(() -> Thread.currentThread().getName(), 5, TimeUnit.SECONDS);
        String t2 = runner.run(() -> Thread.currentThread().getName(), 5, TimeUnit.SECONDS);
        assertNotEquals("Each run must get its own single-use thread", t1, t2);
        assertTrue(t1.startsWith("test-"));
    }

    @Test
    public void testTaskException_propagatesAsExecutionException() throws Exception {
        SerialAbandoningRunner runner = new SerialAbandoningRunner("test");
        try {
            runner.run(() -> { throw new IllegalStateException("boom"); },
                    5, TimeUnit.SECONDS);
            fail("Expected ExecutionException");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        // A failed (but finished) run must not consume the slot.
        assertEquals("ok", runner.run(() -> "ok", 5, TimeUnit.SECONDS));
    }
}
