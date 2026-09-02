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
 * A world as the simulation sees it (D6, D8, D20, M7): its difficulty curve, effects for the
 * {@code WORLD} layer, flags, spawn weights per obstacle kind, the patterns it streams, its
 * ambience and — for the Void — its rule cycles.
 *
 * @param id the world id
 * @param curve the difficulty curve
 * @param effects effects pushed into the {@code WORLD} layer
 * @param flags rules the world activates
 * @param spawnWeights positive weight per obstacle kind
 * @param patterns the set pieces the world draws, already resolved (M7); empty for a world that
 *     only spawns from its table
 * @param ambient the wind, the darkness and the cosmetic flashes (M7)
 * @param ruleCycles the rule shifts, or {@code null} for a world whose rules never change (M7)
 */
public record WorldSpec(String id, CurveSpec curve, List<StatModifier> effects, RuleSet flags,
        Map<ObstacleKind, Integer> spawnWeights, List<PatternSpec> patterns, AmbientSpec ambient,
        RuleCycleSpec ruleCycles) {

    /** Green Fields: the classic curve, pipe gates only, still air, no patterns. */
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
     * @param patterns the patterns
     * @param ambient the ambience
     * @param ruleCycles the rule cycles or {@code null}
     */
    public WorldSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(ambient, "ambient");
        effects = List.copyOf(effects);
        EnumMap<ObstacleKind, Integer> weights = new EnumMap<>(ObstacleKind.class);
        weights.putAll(spawnWeights);
        spawnWeights = Collections.unmodifiableMap(weights);
        patterns = List.copyOf(patterns);
    }

    /**
     * A world with no patterns, still air and no rule cycles — the M1 shape, kept for the seams
     * and the tests that only need a curve and a table.
     *
     * @param id the world id
     * @param curve the difficulty curve
     * @param effects world effects
     * @param flags world flags
     * @param spawnWeights spawn weights
     */
    public WorldSpec(String id, CurveSpec curve, List<StatModifier> effects, RuleSet flags,
            Map<ObstacleKind, Integer> spawnWeights) {
        this(id, curve, effects, flags, spawnWeights, List.of(), AmbientSpec.NONE, null);
    }

    /**
     * Returns a copy with another curve (tests).
     *
     * @param newCurve the curve
     * @return the copy
     */
    public WorldSpec withCurve(CurveSpec newCurve) {
        return new WorldSpec(id, newCurve, effects, flags, spawnWeights, patterns, ambient,
                ruleCycles);
    }

    /**
     * Returns a copy with other flags (tests).
     *
     * @param newFlags the flags
     * @return the copy
     */
    public WorldSpec withFlags(RuleSet newFlags) {
        return new WorldSpec(id, curve, effects, newFlags, spawnWeights, patterns, ambient,
                ruleCycles);
    }

    /**
     * Returns a copy with other patterns (tests).
     *
     * @param newPatterns the patterns
     * @return the copy
     */
    public WorldSpec withPatterns(List<PatternSpec> newPatterns) {
        return new WorldSpec(id, curve, effects, flags, spawnWeights, newPatterns, ambient,
                ruleCycles);
    }

    /**
     * Returns a copy with another ambience (tests).
     *
     * @param newAmbient the ambience
     * @return the copy
     */
    public WorldSpec withAmbient(AmbientSpec newAmbient) {
        return new WorldSpec(id, curve, effects, flags, spawnWeights, patterns, newAmbient,
                ruleCycles);
    }

    /**
     * Returns a copy with other rule cycles (tests).
     *
     * @param newCycles the cycles, or {@code null} for none
     * @return the copy
     */
    public WorldSpec withRuleCycles(RuleCycleSpec newCycles) {
        return new WorldSpec(id, curve, effects, flags, spawnWeights, patterns, ambient,
                newCycles);
    }

    /**
     * Whether the world has anything beyond a curve and a table: patterns, an active ambience or
     * rule cycles. A world that answers {@code false} plays — and hashes — like an M1 world.
     *
     * @return {@code true} when the M7 systems have work to do
     */
    public boolean hasWorldEffects() {
        return !patterns.isEmpty() || ambient.isActive() || ruleCycles != null;
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
        h = 31 * h + patterns.hashCode();
        h = 31 * h + ambient.hashCode();
        h = 31 * h + (ruleCycles == null ? 0 : ruleCycles.hashCode());
        return h;
    }
}
