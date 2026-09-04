package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.AbilityContext;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;

/**
 * {@code coin_magnet} (PASSIVE, ECONOMY) — D9: {@code MAGNET_RADIUS +90}.
 *
 * <p>The base radius is authored in {@code effects}; the levels add to it from here, because an
 * ability's {@code effects} list is written once for every level while {@code params} are
 * per-level. The extra radius is contributed at equip time and stays for the whole run, exactly
 * like the authored one — the difference is only where the number came from.
 *
 * <p>The pull itself is {@code Coin.update}: coins inside {@code MAGNET_RADIUS} accelerate
 * towards the bird. Nothing here runs per tick, so a magnet costs the simulation nothing.
 */
public final class CoinMagnetBehavior implements AbilityBehavior {

    /** Level parameter: radius added on top of the authored {@code effects} value, in px. */
    public static final String EXTRA_RADIUS = "extraRadius";

    /** What the behaviour reads from {@code abilities.json}. */
    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.up(EXTRA_RADIUS, 0, 110));

    @Override
    public void onEquip(AbilityContext ctx) {
        double extra = ctx.param(EXTRA_RADIUS, 0);
        if (extra > 0) {
            ctx.addRunEffect(StatModifier.flat(StatId.MAGNET_RADIUS, extra, ctx.source()));
        }
    }
}
