package io.github.michelbr84.flapforge.gameplay.stats;

import java.util.Objects;

/**
 * One contribution to a stat (D8).
 *
 * @param stat the stat affected
 * @param op how the value combines
 * @param value the operand ({@code +px} for {@link StatOp#FLAT_ADD}, a fraction for
 *     {@link StatOp#PERCENT_ADD}, a factor for {@link StatOp#MULTIPLY})
 * @param source a human-readable origin (content id or system name) shown by the stat breakdown
 */
public record StatModifier(StatId stat, StatOp op, double value, String source) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the value combines
     * @param value the operand
     * @param source the origin label
     */
    public StatModifier {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(source, "source");
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Modifier value must be finite: " + value);
        }
    }

    /**
     * Convenience factory for a flat addition.
     *
     * @param stat the stat
     * @param value the amount to add
     * @param source the origin label
     * @return the modifier
     */
    public static StatModifier flat(StatId stat, double value, String source) {
        return new StatModifier(stat, StatOp.FLAT_ADD, value, source);
    }

    /**
     * Convenience factory for a percentage addition.
     *
     * @param stat the stat
     * @param value the fraction to add (0.1 = +10 %)
     * @param source the origin label
     * @return the modifier
     */
    public static StatModifier percent(StatId stat, double value, String source) {
        return new StatModifier(stat, StatOp.PERCENT_ADD, value, source);
    }

    /**
     * Convenience factory for a multiplier.
     *
     * @param stat the stat
     * @param value the factor
     * @param source the origin label
     * @return the modifier
     */
    public static StatModifier multiply(StatId stat, double value, String source) {
        return new StatModifier(stat, StatOp.MULTIPLY, value, source);
    }

    /**
     * Specified hash: {@link Enum#hashCode()} is an identity hash, so the record's generated one
     * differs between JVMs. Every seam record the simulation carries hashes its enums by ordinal
     * instead, so a future registry or cache keyed by them iterates the same way everywhere.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = stat.ordinal();
        h = 31 * h + op.ordinal();
        h = 31 * h + Double.hashCode(value);
        return 31 * h + source.hashCode();
    }
}
