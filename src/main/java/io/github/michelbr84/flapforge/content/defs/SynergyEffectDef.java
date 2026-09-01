package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.spec.SynergyEffect;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * A bird effect that scales with the total of owned upgrade levels (D8,
 * {@code BirdDef.synergyEffects}), resolved once at run start into the {@code BIRD_SYNERGY}
 * layer. M1 binds and validates it; M4 wires the layer through {@link #toSynergyEffect()}.
 *
 * @param stat the stat affected
 * @param op how the effect combines
 * @param perUpgradeLevel the growth per owned upgrade level
 * @param max the cap of the accumulated amount
 */
public record SynergyEffectDef(StatId stat, StatOp op, double perUpgradeLevel, double max) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the effect combines
     * @param perUpgradeLevel the growth per level
     * @param max the cap
     */
    public SynergyEffectDef {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
    }

    /**
     * The simulation seam record.
     *
     * @return the synergy effect
     */
    public SynergyEffect toSynergyEffect() {
        return new SynergyEffect(stat, op, perUpgradeLevel, max);
    }
}
