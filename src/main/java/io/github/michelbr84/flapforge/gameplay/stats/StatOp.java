package io.github.michelbr84.flapforge.gameplay.stats;

/**
 * How a {@link StatModifier} combines into a stat (D8). The pipeline is
 * {@code clamp((base + ΣFLAT_ADD) × (1 + ΣPERCENT_ADD) × ΠMULTIPLY)}, which is commutative within
 * and across operations, so the order modifiers are applied in never matters.
 */
public enum StatOp {
    /** Added to the base before any scaling. */
    FLAT_ADD,
    /** Summed with the other percentages, then applied once as {@code 1 + Σ}. */
    PERCENT_ADD,
    /** Multiplied together with the other multipliers. */
    MULTIPLY
}
