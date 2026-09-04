package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.AbilityContext;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import java.util.List;

/**
 * {@code shield} (PASSIVE, DEFENSIVE) — D9.
 *
 * <p>The charge itself is data: {@code effects: SHIELD_CHARGES +1} in the {@code ABILITY} layer,
 * which is why the same shield works with no ability equipped when an upgrade node supplies the
 * charge. All this behaviour does is configure the run's {@link ShieldSystem} with the level's
 * numbers: how long an absorb keeps the bird invulnerable (45 ticks, 60 at level 3) and, from
 * level 2, how many passed gates give a spent charge back.
 *
 * <p>The ghost-until-clear part of the absorb belongs to the simulation, not here: it applies to
 * every absorb, ability or not, because a bird that survives inside a pipe would otherwise die to
 * the same overlap on the next tick.
 */
public final class ShieldBehavior implements AbilityBehavior {

    /** Level parameter: invulnerability ticks granted by an absorb. */
    public static final String INVULN_TICKS = "invulnTicks";
    /** Level parameter: passed gates that give one charge back; {@code 0} never. */
    public static final String REGEN_EVERY_GATES = "regenEveryGates";

    /** What the behaviour reads from {@code abilities.json}. */
    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.up(INVULN_TICKS, 1, 240),
            ParamSpec.free(REGEN_EVERY_GATES, 0, 100));

    @Override
    public void onEquip(AbilityContext ctx) {
        ctx.shield().configure(
                ctx.intParam(INVULN_TICKS, ShieldSystem.DEFAULT_INVULN_TICKS),
                ctx.intParam(REGEN_EVERY_GATES, 0));
    }
}
