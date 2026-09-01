package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A world as the simulation sees it (D6, D20): its difficulty curve, effects for the
 * {@code WORLD} layer, flags and spawn weights per obstacle kind.
 *
 * @param id the world id
 * @param curve the difficulty curve
 * @param effects effects pushed into the {@code WORLD} layer
 * @param flags rules the world activates
 * @param spawnWeights positive weight per obstacle kind
 */
public record WorldSpec(String id, CurveSpec curve, List<StatModifier> effects, RuleSet flags,
        Map<ObstacleKind, Integer> spawnWeights) {

    /** Green Fields: the classic curve, pipe gates only. */
    public static final WorldSpec GREEN_FIELDS = new WorldSpec("green_fields", CurveSpec.CLASSIC,
            List.of(), RuleSet.EMPTY, Map.of(ObstacleKind.PIPE_GATE, 100));

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param id the world id
     * @param curve the difficulty curve
     * @param effects world effects
     * @param flags world flags
     * @param spawnWeights spawn weights
     */
    public WorldSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(flags, "flags");
        effects = List.copyOf(effects);
        EnumMap<ObstacleKind, Integer> weights = new EnumMap<>(ObstacleKind.class);
        weights.putAll(spawnWeights);
        spawnWeights = Collections.unmodifiableMap(weights);
    }

    /**
     * Returns a copy with another curve (tests).
     *
     * @param newCurve the curve
     * @return the copy
     */
    public WorldSpec withCurve(CurveSpec newCurve) {
        return new WorldSpec(id, newCurve, effects, flags, spawnWeights);
    }

    /**
     * Returns a copy with other flags (tests).
     *
     * @param newFlags the flags
     * @return the copy
     */
    public WorldSpec withFlags(RuleSet newFlags) {
        return new WorldSpec(id, curve, effects, newFlags, spawnWeights);
    }

    /**
     * Specified hash: {@code spawnWeights} is enum-keyed, so it is hashed by walking
     * {@link ObstacleKind#values()} in ordinal order instead of relying on
     * {@link Enum#hashCode()}'s identity hash.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = id.hashCode();
        h = 31 * h + curve.hashCode();
        h = 31 * h + effects.hashCode();
        h = 31 * h + flags.hashCode();
        for (ObstacleKind kind : ObstacleKind.values()) {
            Integer weight = spawnWeights.get(kind);
            h = 31 * h + (weight == null ? 0 : weight);
        }
        return h;
    }
}
