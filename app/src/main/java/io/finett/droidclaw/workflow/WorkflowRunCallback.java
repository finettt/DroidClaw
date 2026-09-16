package io.finett.droidclaw.workflow;

import java.util.Map;

/**
 * Callbacks for monitoring a workflow run. All methods are called on the
 * thread that drives the run (typically a background thread).
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