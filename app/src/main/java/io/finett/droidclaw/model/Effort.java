package io.finett.droidclaw.model;

import java.util.Locale;

/**
 * Reasoning effort for an agent run. The same value sets both the reasoning
 * parameter sent to the API (for reasoning-capable models) and the agent's turn
 * budget: how many iterations the loop runs before it stops.
 *
 * <p>Sentinel meaning: a turn budget of zero or less, or {@link #MAX}, means the
 * run has no turn cap. The API wire token is separate from the persisted name for
 * {@link #MEDIUM} because the wire format uses {@code medium} while the setting
 * itself is stored as {@code med}.
 */
public enum Effort {
    LOW("low", 8),
    MEDIUM("med", "medium", 20),
    HIGH("high", 40),
    XHIGH("xhigh", 80),
    MAX("max", 0);

    private final String name;
    private final String wire;
    private final int turnBudget;

    /** Compact form for the two-value constructor (MAX has no wire budget). */
    private Effort(String name, int turnBudget) {
        this.name = name;
        this.wire = name;
        this.turnBudget = turnBudget;
    }

    private Effort(String name, String wire, int turnBudget) {
        this.name = name;
        this.wire = wire;
        this.turnBudget = turnBudget;
    }

    /** Canonical persisted name (the value written to settings). */
    public String persistedName() {
        return name;
    }

    /** API wire token for the reasoning parameter (OpenAI {@code reasoning_effort}, Anthropic {@code output_config.effort}). */
    public String wire() {
        return wire;
    }

    /**
     * Turn budget for this level. A value of zero means unlimited (no turn cap);
     * only {@link #MAX} uses that sentinel here.
     */
    public int turnBudget() {
        return turnBudget;
    }

    /** True when this level imposes no turn cap. */
    public boolean isUnlimited() {
        return this == MAX || turnBudget <= 0;
    }

    /** The default level used for fresh installs. */
    public static Effort getDefault() {
        return MEDIUM;
    }

    /**
     * Parse a persisted or user-facing effort name. Tolerates {@code med} and
     * {@code medium} as the same level. Unknown or blank input is a hard failure,
     * never a silent default, so misconfiguration surfaces instead of being ignored.
     */
    public static Effort fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("effort value is null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "low":
                return LOW;
            case "med":
            case "medium":
                return MEDIUM;
            case "high":
                return HIGH;
            case "xhigh":
                return XHIGH;
            case "max":
                return MAX;
            default:
                throw new IllegalArgumentException("unknown effort: '" + value + "'");
        }
    }

    /**
     * Map a legacy {@code maxIterations} integer to an effort level, used when
     * migrating older settings to the effort model. Bounds are inclusive at the
     * low end: 8 or fewer is Low, 20 or fewer is Medium, and so on, anything above
     * 80 (including unbounded) resolves to Max.
     */
    public static Effort fromMaxIterations(int maxIterations) {
        if (maxIterations <= 8) return LOW;
        if (maxIterations <= 20) return MEDIUM;
        if (maxIterations <= 40) return HIGH;
        if (maxIterations <= 80) return XHIGH;
        return MAX;
    }
}
