package io.finett.droidclaw.workflow;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps UI notifications separate from the runner's blocking work. */
final class MainThreadWorkflowRunCallback implements WorkflowRunCallback {
    private final WorkflowRunCallback delegate;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MainThreadWorkflowRunCallback(WorkflowRunCallback delegate) {
        this.delegate = delegate;
    }

    static WorkflowRunCallback wrap(WorkflowRunCallback callback) {
        return callback == null ? null : new MainThreadWorkflowRunCallback(callback);
    }

    private void dispatch(Runnable notification) {
        if (Looper.myLooper() == handler.getLooper()) {
            notification.run();
        } else {
            handler.post(notification);
        }
    }

    @Override public void onNodeStart(String key) {
        dispatch(() -> delegate.onNodeStart(key));
    }

    @Override public void onNodeComplete(String key, WorkflowNodeStatus status) {
        dispatch(() -> delegate.onNodeComplete(key, status));
    }

    @Override public void onProgress(String message) {
        dispatch(() -> delegate.onProgress(message));
    }

    @Override public void onComplete(String output, Map<String, TemplateResolver.NodeResult> results) {
        Map<String, TemplateResolver.NodeResult> snapshot =
                Collections.unmodifiableMap(new LinkedHashMap<>(results));
        dispatch(() -> delegate.onComplete(output, snapshot));
    }

    @Override public void onError(String error) {
        dispatch(() -> delegate.onError(error));
    }
}
