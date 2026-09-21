package io.finett.droidclaw.workflow;

/**
 * The host capabilities a workflow is validated and run against.
 *
 * <p>This is an interface so that parsing, validation, graph construction and
 * template resolution are all testable on the plain JVM with no Android
 * dependencies. Production supplies an implementation backed by
 * {@code ToolRegistry} and {@code SettingsManager}.
 */
public interface WorkflowEnvironment {

    /** Whether {@code ToolRegistry} currently exposes a tool with this name. */
    boolean hasTool(String toolName);

    /**
     * Whether {@code providerId/modelId} resolves to a configured model.
     * The reference is split on the <em>first</em> {@code /} only, because a
     * model id may itself contain slashes.
     */
    boolean hasModel(String providerId, String modelId);

    /** Global {@code AgentConfig.maxIterations}, used when a node sets no {@code max_turns}. */
    int defaultMaxTurns();

    /** Whether {@code requireApproval} is on globally, for the {@code inherit} policy. */
    boolean globalRequireApproval();
}
