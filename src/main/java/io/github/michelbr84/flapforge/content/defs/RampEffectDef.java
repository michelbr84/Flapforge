package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * A bird effect that grows with every passed gate (D8, {@code BirdDef.rampEffects}), re-evaluated
 * into the {@code BIRD_RAMP} layer on each gate.
 *
 * @param stat the stat affected
 * @param op how the ramp combines
 * @param perGate the growth per gate
 * @param max the cap of the accumulated amount
 */
public record RampEffectDef(StatId stat, StatOp op, double perGate, double max) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the ramp combines
     * @param perGate the growth per gate
     * @param max the cap
     */
    public RampEffectDef {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
    }

    /**
     * The simulation seam record.
     *
     * @return the ramp effect
     */
    public RampEffect toRampEffect() {
        return new RampEffect(stat, op, perGate, max);
    }
}
