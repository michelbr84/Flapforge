package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.AbilityContext;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import java.util.List;

/**
 * {@code emergency_recovery} (PASSIVE, DEFENSIVE + REVIVE) — D9, "this is Revive".
 *
 * <p>The charge is data ({@code effects: REVIVES +1}), so the run's {@link ReviveSystem} owns the
 * mechanic and a bare {@code REVIVES > 0} from an upgrade node revives on its own. What the
 * ability adds is the part that makes a revive survivable rather than symbolic: the auto-flap
 * kick instead of a zeroed velocity, and 90 invulnerability ticks instead of 60 — long enough to
 * fly out of the column that killed the bird.
 *
 * <p>Both are configured once, at equip time, so the consume path stays a single implementation
 * (D9: "no two parallel revive mechanisms").
 */
public final class EmergencyRecoveryBehavior implements AbilityBehavior {

    /** Level parameter: invulnerability ticks granted by the revive. */
    public static final String INVULN_TICKS = "invulnTicks";
    /** Level parameter: the auto-flap kick as a factor of {@code FLAP_VELOCITY}. */
    public static final String KICK_MULTIPLIER = "kickMultiplier";

    /** Invulnerability the ability grants on a revive (D9). */
    public static final int ABILITY_INVULN_TICKS = 90;

    /** What the behaviour reads from {@code abilities.json}. */
    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.up(INVULN_TICKS, 60, 240),
            ParamSpec.up(KICK_MULTIPLIER, 0.5, 3));

    @Override
    public void onEquip(AbilityContext ctx) {
        ctx.revive().configure(
                ctx.intParam(INVULN_TICKS, ABILITY_INVULN_TICKS),
                ctx.param(KICK_MULTIPLIER, 1));
    }
}
