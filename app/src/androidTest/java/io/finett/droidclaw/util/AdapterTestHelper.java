package io.finett.droidclaw.util;

import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AdapterTestHelper {

    private static final long DEFAULT_TIMEOUT_SECONDS = 2;

        public static <T, VH extends RecyclerView.ViewHolder> void submitListAndWait(
            ListAdapter<T, VH> adapter,
            List<T> list) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        adapter.submitList(list, latch::countDown);
        latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

        public static <T, VH extends RecyclerView.ViewHolder> void submitListAndWait(
            ListAdapter<T, VH> adapter,
            List<T> list,
            long timeoutSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        adapter.submitList(list, latch::countDown);
        latch.await(timeoutSeconds, TimeUnit.SECONDS);
    }
}