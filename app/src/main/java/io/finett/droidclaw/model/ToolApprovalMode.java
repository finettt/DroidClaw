package io.finett.droidclaw.model;

/**
 * Per-tool approval override mode.
 *
 * <ul>
 *   <li>{@link #DEFAULT} — follows the global {@code requireApproval} toggle</li>
 *   <li>{@link #ALWAYS_APPROVE} — auto-approve, no user prompt</li>
 *   <li>{@link #ALWAYS_REJECT} — auto-reject, no user prompt (tool blocked)</li>
 * </ul>
 */
public enum ToolApprovalMode {
    DEFAULT,
    ALWAYS_APPROVE,
    ALWAYS_REJECT
}
