package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.AbilityContext;
import io.github.michelbr84.flapforge.ability.AbilityInstance;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.List;

/**
 * {@code double_flap} (ACTIVE, MOVEMENT) — D9: "zero the downward velocity, then
 * {@code vy = −FLAP_VELOCITY × 1.5}; two charges, one back every five gates".
 *
 * <p>Writing the bird's velocity is the sanctioned kind of ability effect (E24): the strength of
 * a <em>flap</em> is a stat, but a one-off impulse is not, and expressing it as a stat would make
 * every later flap stronger too.
 *
 * <p>The impulse is a plain set, never an add: a bird falling at the terminal 1500 px/s and one
 * hovering both leave the activation at exactly {@code −FLAP_VELOCITY × 1.5}, so the ability is
 * worth the same at the bottom of a dive as at the top of one — the reason it is the panic button
 * the default loadout ships with.
 *
 * <p>The ceiling gate that refuses an ordinary flap at {@code y ≤ 32} applies here too, through
 * {@link #canActivate}: the ability is a stronger flap, and a flap the physics would refuse must
 * not become a legal one just because it was bought. Refusing before the bookkeeping means the
 * charge is not spent either, and the screen answers the press the way it answers an ability that
 * is not ready. Without the gate the kick pushed the bird about 95 px above the playfield, where
 * nothing clamps it and a {@code LETHAL_CEILING} tier kills it outright. The charges themselves
 * are generic bookkeeping in {@link AbilityInstance}.
 */
public final class DoubleFlapBehavior implements AbilityBehavior {

    /** Level parameter: the flap velocity factor. */
    public static final String FLAP_MULTIPLIER = "flapMultiplier";

    /** Default factor when the level does not declare one (D9). */
    public static final double DEFAULT_FLAP_MULTIPLIER = 1.5;

    /** What the behaviour reads from {@code abilities.json}. */
    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.up(FLAP_MULTIPLIER, 1, 3),
            ParamSpec.up(AbilityInstance.PARAM_CHARGES, 1, 5),
            ParamSpec.free(AbilityInstance.PARAM_RECHARGE_EVERY_GATES, 0, 50));

    @Override
    public boolean canActivate(AbilityContext ctx) {
        return BirdPhysics.canFlap(ctx.bird());
    }

    @Override
    public void onActivate(AbilityContext ctx) {
        Bird bird = ctx.bird();
        double flap = ctx.stats().resolve(StatId.FLAP_VELOCITY);
        bird.setVy(-flap * ctx.param(FLAP_MULTIPLIER, DEFAULT_FLAP_MULTIPLIER));
    }
}
