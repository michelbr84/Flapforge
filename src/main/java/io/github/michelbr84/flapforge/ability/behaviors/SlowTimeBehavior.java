package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import java.util.List;

/**
 * {@code slow_time} (ACTIVE, TEMPO) — D9: 90 ticks of {@code TIME_SCALE × 0.5}.
 *
 * <p>It has no code. {@code TIME_SCALE} is authored in {@code effects} and pushed into the
 * {@code ABILITY} layer while the duration runs; {@code SimContext.worldDt} then scales the
 * scroll, the obstacle phases, the pickups and the streaming — and nothing else. The tick length
 * and the bird integration are deliberately outside it (D8), so a flap under slow time still
 * rises exactly 42 px in 13 ticks and the muscle memory the whole game is built on survives the
 * ability. The behaviour exists so the registry has an entry to map the id to, and so this
 * contract has a home.
 */
public final class SlowTimeBehavior implements AbilityBehavior {

    /** The behaviour reads no level parameter. */
    public static final List<ParamSpec> PARAMS = List.of();
}
