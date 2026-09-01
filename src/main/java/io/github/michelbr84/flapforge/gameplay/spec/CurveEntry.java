package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * One line of a difficulty curve (D20): {@code value = clamp(base + perGate × gates, min, max)}
 * applied with {@code op}.
 *
 * @param stat the stat affected
 * @param op how the value combines (a {@code MULTIPLY} entry uses {@code base 1.0})
 * @param base the value at gate 0
 * @param perGate the growth per gate
 * @param min the lower clamp of the value
 * @param max the upper clamp of the value
 */
public record CurveEntry(StatId stat, StatOp op, double base, double perGate, double min,
        double max) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the value combines
     * @param base the value at gate 0
     * @param perGate the growth per gate
     * @param min the lower clamp
     * @param max the upper clamp
     */
    public CurveEntry {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
        if (min > max) {
            throw new IllegalArgumentException("Curve min > max for " + stat + ": " + min + " > " + max);
        }
    }

    /**
     * Value of the entry after a number of gates.
     *
     * @param gates gates passed
     * @return the clamped value
     */
    public double valueAt(int gates) {
        return MathUtil.clamp(base + perGate * gates, min, max);
    }

    /**
     * Modifier for a number of gates.
     *
     * @param gates gates passed
     * @param source the origin label
     * @return the modifier
     */
    public StatModifier at(int gates, String source) {
        return new StatModifier(stat, op, valueAt(gates), source);
    }

    /**
     * Specified hash (enums by ordinal, not by identity), like every seam record.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = stat.ordinal();
        h = 31 * h + op.ordinal();
        h = 31 * h + Double.hashCode(base);
        h = 31 * h + Double.hashCode(perGate);
        h = 31 * h + Double.hashCode(min);
        return 31 * h + Double.hashCode(max);
    }
}
