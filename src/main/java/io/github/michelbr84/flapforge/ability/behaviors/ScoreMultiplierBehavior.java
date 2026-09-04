package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import java.util.List;

/**
 * {@code score_multiplier} (ACTIVE, ECONOMY) — D9: 300 ticks of {@code SCORE_MULT × 2}.
 *
 * <p>No code: {@code SCORE_MULT} is authored in {@code effects} and lives in the {@code ABILITY}
 * layer while the duration runs, so every gate scored inside the window pays double and E1's
 * {@code coinsPerPoint} turns that into coins at the end of the run. The behaviour exists so the
 * registry can map the id and so the contract is written down next to its siblings.
 */
public final class ScoreMultiplierBehavior implements AbilityBehavior {

    /** The behaviour reads no level parameter. */
    public static final List<ParamSpec> PARAMS = List.of();
}
