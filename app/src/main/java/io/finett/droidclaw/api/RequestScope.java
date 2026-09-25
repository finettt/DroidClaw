package io.finett.droidclaw.api;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;

/**
 * Tracks the in-flight OkHttp {@link Call}s that belong to a single agent run so
 * that cancelling the run only aborts its own requests, never unrelated traffic on
 * the shared {@link okhttp3.OkHttpClient} (issue #144).
 *
 * <p>One scope is created per {@link io.finett.droidclaw.agent.AgentLoop} run and is
 * passed down to every {@code LlmApiService.send*} call the run issues. Cancelling
 * the scope cancels exactly those calls; calls registered after cancellation are
 * cancelled immediately, so a lost race with an in-flight enqueue cannot leak a
 * live request.</p>
 *
 * <p>Forward compatibility: mid-run steering can cancel only the current in-flight
 * call (via {@link Call#cancel()}) and re-issue a new request within the same
 * scope, since {@link #cancel()} is reserved for aborting the whole run.</p>
 *
 * <p>Thread-safe.</p>
 */
public class RequestScope {

    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    /**
     * Register a call as belonging to this scope. If the scope was already
     * cancelled, the call is cancelled immediately instead of being tracked.
     */
    public void register(Call call) {
        if (call == null) {
            return;
        }
        if (cancelled) {
            call.cancel();
            return;
        }
        calls.add(call);
        // Close the race where cancel() ran between the check above and the add.
        if (cancelled) {
            calls.remove(call);
            call.cancel();
        }
    }

    /**
     * Remove a call from the scope, typically once its callback has fired and the
     * call can no longer be usefully cancelled.
     */
    public void unregister(Call call) {
        if (call != null) {
            calls.remove(call);
        }
    }

    /**
     * Cancel this scope: aborts every registered call and marks the scope so that
     * any call registered afterwards is cancelled on arrival. Idempotent.
     */
    public void cancel() {
        cancelled = true;
        for (Call call : calls) {
            calls.remove(call);
            call.cancel();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
