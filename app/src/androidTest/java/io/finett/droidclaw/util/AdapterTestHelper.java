package io.finett.droidclaw.util;

import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for testing ListAdapter subclasses.
 * Provides utilities to handle async submitList operations in tests.
 */
public class AdapterTestHelper {

    private static final long DEFAULT_TIMEOUT_SECONDS = 2;

    /**
     * Submits a list to a ListAdapter and waits for the async diffing to complete.
     * This ensures that tests can make assertions on the adapter state after the
     * list has been fully processed.
     *
     * @param adapter The ListAdapter to submit the list to
     * @param list    The list to submit
     * @param <T>     The type of items in the list
     * @param <VH>    The ViewHolder type
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public static <T, VH extends RecyclerView.ViewHolder> void submitListAndWait(
            ListAdapter<T, VH> adapter,
            List<T> list) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        adapter.submitList(list, latch::countDown);
        latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Submits a list to a ListAdapter and waits for the async diffing to complete
     * with a custom timeout.
     *
     * @param adapter The ListAdapter to submit the list to
     * @param list    The list to submit
     * @param timeoutSeconds The timeout in seconds to wait for completion
     * @param <T>     The type of items in the list
     * @param <VH>    The ViewHolder type
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public static <T, VH extends RecyclerView.ViewHolder> void submitListAndWait(
            ListAdapter<T, VH> adapter,
            List<T> list,
            long timeoutSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        adapter.submitList(list, latch::countDown);
        latch.await(timeoutSeconds, TimeUnit.SECONDS);
    }
}