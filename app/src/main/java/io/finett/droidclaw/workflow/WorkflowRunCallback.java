package io.finett.droidclaw.workflow;

import java.util.Map;

/**
 * Callbacks for monitoring a workflow run. WorkflowRunner delivers these on
 * the main looper. Delivery from a worker is posted and can follow run() returning.
 */
public interface WorkflowRunCallback {

    void onNodeStart(String key);

    void onNodeComplete(String key, WorkflowNodeStatus status);

    void onProgress(String message);

    void onComplete(String finalOutput, Map<String, TemplateResolver.NodeResult> results);

    /** The workflow failed or was cancelled. */
    void onError(String error);
}
