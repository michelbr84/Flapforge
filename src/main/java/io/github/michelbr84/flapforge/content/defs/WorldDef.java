package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One world of {@code worlds.json} (§4).
 *
 * <p>M4 ships this as a <em>stub with its final unlock and reward blocks</em> (E19): the id, the
 * order, the curve, the unlock condition, the palette, the ambience, the spawn weights and the
 * whole {@link #boss} block — including {@code boss.reward}, which is what chains the worlds
 * together in the unlock graph. What M7 adds in place is {@link #patterns} and
 * {@link #ruleCycles} (and M8 the {@link #music}); until {@code patterns.json} exists the
 * validator resolves pattern ids only when the registry is there, and
 * {@code GameContent.playable} reports every world but Green Fields as not yet playable.
 *
 * @param id the world id
 * @param order the position in the world chain, 1-based and unique
 * @param curve the id of the difficulty curve the world runs on
 * @param unlock the condition that unlocks {@code world:<id>}
 * @param palette the colours the world is drawn with
 * @param style the parallax background style
 * @param effects stat modifiers applied for the whole run in the {@code WORLD} layer
 * @param flags rule flags the world turns on
 * @param spawnWeights how often each obstacle family is drawn
 * @param patterns the authored pattern ids the world streams (M7)
 * @param ambient darkness, wind and the cosmetic lightning period
 * @param ruleCycles the Void's rule shifts, or {@code null} (M7)
 * @param boss the world boss and its reward
 * @param music the procedural music settings (M8), or {@code null}
 * @param sfxSet the synthesised sound set (E31.g)
 */
public record WorldDef(String id, int order, String curve, UnlockConditionDef unlock,
        WorldPaletteDef palette, String style, List<StatModifierDef> effects, List<RuleFlag> flags,
        Map<ObstacleKind, Integer> spawnWeights, List<String> patterns, AmbientDef ambient,
        RuleCyclesDef ruleCycles, BossDef boss, MusicDef music, SfxSet sfxSet) {

    /** Namespace of the unlockable id of a world. */
    public static final String NAMESPACE = "world:";

    /**
     * Copies the collections and checks the required fields.
     *
     * @throws NullPointerException when the id, the curve or the unlock block is missing
     * @throws IllegalArgumentException when the order or a spawn weight is out of range
     */
    public WorldDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(unlock, "unlock");
        if (order < 1) {
            throw new IllegalArgumentException("world.order must be at least 1: " + order);
        }
        effects = List.copyOf(effects);
        flags = List.copyOf(flags);
        EnumMap<ObstacleKind, Integer> weights = new EnumMap<>(ObstacleKind.class);
        for (Map.Entry<ObstacleKind, Integer> e : spawnWeights.entrySet()) {
            if (e.getValue() == null || e.getValue() < 0) {
                throw new IllegalArgumentException(
                        "spawnWeights." + e.getKey() + " must not be negative");
            }
            weights.put(e.getKey(), e.getValue());
        }
        spawnWeights = Collections.unmodifiableMap(weights);
        patterns = List.copyOf(patterns);
    }

    /**
     * The namespaced unlockable id, {@code world:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }
}
