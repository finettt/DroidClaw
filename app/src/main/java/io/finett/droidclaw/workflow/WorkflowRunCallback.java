package io.finett.droidclaw.workflow;

import java.util.Map;

/**
 * Callbacks for monitoring a workflow run. WorkflowRunner delivers these on
 * the main looper. Delivery from a worker is posted and can follow run() returning.
 */
public interface WorkflowRunCallback {

    /** Called when a node begins execution. */
    void onNodeStart(String key);

    /** Called when a node reaches a terminal state. */
    void onNodeComplete(String key, WorkflowNodeStatus status);

    /** Progress message for the UI or logs. */
    void onProgress(String message);

    /** The workflow finished successfully. */
    void onComplete(String finalOutput, Map<String, TemplateResolver.NodeResult> results);

    /** The workflow failed or was cancelled. */
    void onError(String error);
}