package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * One authored entry of an effect list (D8): {@code {stat, op, value}}. Every layer of the stat
 * pipeline — bird, tier, world, upgrade, modifier — is written this way.
 *
 * @param stat the stat affected
 * @param op how the value combines
 * @param value the value ({@code MULTIPLY} uses the factor itself)
 */
public record StatModifierDef(StatId stat, StatOp op, double value) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the value combines
     * @param value the value
     */
    public StatModifierDef {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
    }

    /**
     * The simulation seam record.
     *
     * @param source the origin label pushed into the stat breakdown
     * @return the modifier
     */
    public StatModifier toModifier(String source) {
        return new StatModifier(stat, op, value, source);
    }
}
