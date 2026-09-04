package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * One line of a difficulty curve in {@code difficulty.json} (D20):
 * {@code value = clamp(base + perGate × gates, min, max)}, applied with {@code op}.
 *
 * @param stat the stat affected
 * @param op how the value combines (a {@code MULTIPLY} line uses {@code base 1.0})
 * @param base the value at gate 0
 * @param perGate the growth per gate
 * @param min the lower clamp of the value
 * @param max the upper clamp of the value
 */
public record CurveEntryDef(StatId stat, StatOp op, double base, double perGate, double min,
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
    public CurveEntryDef {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
        if (min > max) {
            throw new IllegalArgumentException("curve min > max for " + stat + ": " + min + " > "
                    + max);
        }
    }

    /**
     * The simulation seam record.
     *
     * @return the curve entry
     */
    public CurveEntry toEntry() {
        return new CurveEntry(stat, op, base, perGate, min, max);
    }
}
