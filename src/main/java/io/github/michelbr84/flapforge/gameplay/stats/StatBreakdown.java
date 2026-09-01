package io.github.michelbr84.flapforge.gameplay.stats;

import java.util.List;

/**
 * Explanation of one resolved stat for the selection UI (D8, M4): every contributing modifier
 * and the intermediate sums of the pipeline
 * {@code clamp((base + flatSum) × (1 + percentSum) × multiplyProduct)}.
 *
 * @param stat the stat
 * @param base the base value (from the bird, or the stat default)
 * @param contributions every modifier touching the stat, in {@link Layer} order
 * @param flatSum the sum of {@link StatOp#FLAT_ADD} values
 * @param percentSum the sum of {@link StatOp#PERCENT_ADD} values
 * @param multiplyProduct the product of {@link StatOp#MULTIPLY} values
 * @param unclamped the pipeline result before clamping (and before rule zeroing)
 * @param zeroedByRule {@code true} when an active rule forces the stat to zero
 * @param value the final resolved value
 */
public record StatBreakdown(StatId stat, double base, List<EffectStack.Entry> contributions,
        double flatSum, double percentSum, double multiplyProduct, double unclamped,
        boolean zeroedByRule, double value) {

    /**
     * Copies the contribution list.
     *
     * @param stat the stat
     * @param base the base value
     * @param contributions the contributing modifiers
     * @param flatSum the flat sum
     * @param percentSum the percent sum
     * @param multiplyProduct the multiplier product
     * @param unclamped the unclamped result
     * @param zeroedByRule whether a rule zeroes the stat
     * @param value the final value
     */
    public StatBreakdown {
        contributions = List.copyOf(contributions);
    }

    /**
     * Tells whether the clamp changed the value.
     *
     * @return {@code true} when {@code value != unclamped} and no rule zeroed it
     */
    public boolean clamped() {
        return !zeroedByRule && value != unclamped;
    }
}
