package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * A bird effect that grows with every passed gate (D8, {@code BirdDef.rampEffects}), re-evaluated
 * into the {@code BIRD_RAMP} layer on each {@code GatePassed}.
 *
 * <p>The ramp amount is {@code perGate × gates} capped at {@code max} (towards zero: a negative
 * {@code perGate} is floored at a negative {@code max}). For {@code FLAT_ADD} and
 * {@code PERCENT_ADD} the amount is the modifier value; for {@code MULTIPLY} the value is
 * {@code 1 + amount} so a ramp of zero is a no-op.
 *
 * @param stat the stat affected
 * @param op how the ramp combines
 * @param perGate the growth per gate
 * @param max the cap of the ramp amount
 */
public record RampEffect(StatId stat, StatOp op, double perGate, double max) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the ramp combines
     * @param perGate the growth per gate
     * @param max the cap
     */
    public RampEffect {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
    }

    /**
     * Ramp amount after a number of gates.
     *
     * @param gates gates passed
     * @return {@code perGate × gates} capped at {@code max}
     */
    public double amountAt(int gates) {
        double amount = perGate * gates;
        return perGate >= 0 ? Math.min(amount, max) : Math.max(amount, max);
    }

    /**
     * Modifier to push into the {@code BIRD_RAMP} layer after a number of gates.
     *
     * @param gates gates passed
     * @param source the origin label
     * @return the modifier
     */
    public StatModifier at(int gates, String source) {
        double amount = amountAt(gates);
        double value = op == StatOp.MULTIPLY ? 1 + amount : amount;
        return new StatModifier(stat, op, value, source);
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
        h = 31 * h + Double.hashCode(perGate);
        return 31 * h + Double.hashCode(max);
    }
}
