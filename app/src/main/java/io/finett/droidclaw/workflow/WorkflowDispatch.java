package io.finett.droidclaw.workflow;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Owns the process-wide worker pool for blocking workflow tool calls. */
public final class WorkflowDispatch {
    // Shared across AgentLoops, including short-lived workflow nodes. Cached workers
    // expire when idle, rather than leaving a permanent thread behind for each loop.
    private static final Executor EXECUTOR = Executors.newCachedThreadPool(task ->
            new Thread(task, "workflow-dispatch"));

    private WorkflowDispatch() {}

    public static Executor executor() {
        return EXECUTOR;
    }
}
