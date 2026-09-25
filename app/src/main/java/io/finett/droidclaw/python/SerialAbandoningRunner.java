package io.finett.droidclaw.python;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs tasks serially, each on a fresh single-use daemon thread, and
 * <b>abandons</b> (rather than pretends to cancel) a task that exceeds its
 * timeout.
 *
 * <p>Containment for issue #150: the previous shared
 * {@code newSingleThreadExecutor()} meant that one wedged (uninterruptible)
 * Python run blocked every later run forever, because CPython code executed
 * via Chaquopy never observes the Java interrupt flag. This runner instead:
 * <ul>
 *   <li>Serializes runs while they are healthy (a {@link Semaphore} with one
 *       permit), which keeps the {@code __import__} hook install/uninstall and
 *       {@code sys.stdout}/{@code sys.stderr} swapping in the shared
 *       {@code __main__} namespace race-free between well-behaved runs.</li>
 *   <li>On timeout, best-effort interrupts the worker thread, releases the
 *       permit and lets the <b>next</b> run proceed on a brand-new thread.
 *       The timed-out task keeps running in the background; it cannot be
 *       stopped.</li>
 * </ul>
 *
 * <p><b>Documented containment limitation:</b> an abandoned run may still be
 * executing while a newer run is active. Both share the same CPython
 * interpreter, so the abandoned run can still write to the (now current)
 * {@code sys.stdout}/{@code sys.stderr} and, when it eventually finishes, its
 * cleanup may restore stale stream/hook state. This is accepted for the quick
 * fix; real isolation (a killable separate process) is tracked in #150.
 */
class SerialAbandoningRunner {

    private final Semaphore healthySlot = new Semaphore(1, true);
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final AtomicInteger abandonedCount = new AtomicInteger();
    private final String threadNamePrefix;

    SerialAbandoningRunner(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    /**
     * Runs {@code task} on a fresh daemon thread and waits up to the timeout.
     *
     * <p>Runs are serialized while healthy. If the task exceeds the timeout, a
     * {@link TimeoutException} is thrown, the task is <b>abandoned</b> (it
     * keeps running; the interrupt sent to it is a no-op for CPython code) and
     * the slot is released so the next call proceeds on a new thread.
     */
    <T> T run(Callable<T> task, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        healthySlot.acquire();
        try {
            FutureTask<T> futureTask = new FutureTask<>(task);
            Thread worker = new Thread(futureTask,
                    threadNamePrefix + "-" + threadSeq.incrementAndGet());
            worker.setDaemon(true);
            worker.start();
            try {
                return futureTask.get(timeout, unit);
            } catch (TimeoutException e) {
                // Best-effort interrupt: harmless, but CPython code running
                // inside Chaquopy does not observe the Java interrupt flag,
                // so the task is effectively abandoned, not cancelled.
                futureTask.cancel(true);
                abandonedCount.incrementAndGet();
                throw e;
            }
        } finally {
            // Always release the slot -- on timeout the abandoned thread keeps
            // running, but the next run must not be blocked behind it (#150).
            healthySlot.release();
        }
    }

    int getAbandonedCount() {
        return abandonedCount.get();
    }
}
