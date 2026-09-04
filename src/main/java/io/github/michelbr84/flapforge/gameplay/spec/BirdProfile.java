package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the simulation needs to know about a bird (D8, D30): the seam between
 * {@code BirdDef} (content) and {@link io.github.michelbr84.flapforge.gameplay.Simulation}.
 *
 * @param id the bird id
 * @param baseStats base values; stats not listed use their default
 * @param hitbox the hitbox geometry
 * @param effects innate effects pushed into the {@code BIRD} layer
 * @param rampEffects effects re-evaluated on every gate into {@code BIRD_RAMP}
 * @param synergyEffects effects resolved once at run start from the total of owned upgrade levels
 *     into {@code BIRD_SYNERGY} (D8, M4)
 * @param passiveSlots number of passive ability slots
 */
public record BirdProfile(String id, Map<StatId, Double> baseStats, HitboxSpec hitbox,
        List<StatModifier> effects, List<RampEffect> rampEffects,
        List<SynergyEffect> synergyEffects, int passiveSlots) {

    /** Forgewing: the upstream feel ({@code 1800 / 405 / 1500}, hitbox 33×31 at −17/−12). */
    public static final BirdProfile CLASSIC = new BirdProfile("classic",
            Map.of(StatId.GRAVITY, 1800.0, StatId.FLAP_VELOCITY, 405.0,
                    StatId.MAX_FALL_SPEED, 1500.0),
            HitboxSpec.CLASSIC, List.of(), List.of(), List.of(), 2);

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param id the bird id
     * @param baseStats base values
     * @param hitbox the hitbox geometry
     * @param effects innate effects
     * @param rampEffects ramp effects
     * @param synergyEffects synergy effects
     * @param passiveSlots passive slots
     */
    public BirdProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hitbox, "hitbox");
        EnumMap<StatId, Double> stats = new EnumMap<>(StatId.class);
        stats.putAll(baseStats);
        baseStats = Collections.unmodifiableMap(stats);
        effects = List.copyOf(effects);
        rampEffects = List.copyOf(rampEffects);
        synergyEffects = List.copyOf(synergyEffects);
        if (passiveSlots < 0) {
            throw new IllegalArgumentException("passiveSlots must not be negative");
        }
    }

    /**
     * Returns a copy with different base stats (tests: the 450 fall clamp case).
     *
     * @param stat the stat to override
     * @param value the new base value
     * @return the copy
     */
    public BirdProfile withBase(StatId stat, double value) {
        EnumMap<StatId, Double> stats = new EnumMap<>(StatId.class);
        stats.putAll(baseStats);
        stats.put(stat, value);
        return new BirdProfile(id, stats, hitbox, effects, rampEffects, synergyEffects,
                passiveSlots);
    }

    /**
     * Specified hash: {@code baseStats} is enum-keyed, and {@code EnumMap.hashCode()} sums entry
     * hashes built from {@link Enum#hashCode()}, which is an identity hash and therefore differs
     * from JVM to JVM. Walking {@link StatId#values()} in ordinal order gives the same number
     * everywhere, which is what the cross-OS determinism guarantee needs the moment a profile
     * becomes a map key.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = id.hashCode();
        for (StatId stat : StatId.values()) {
            Double value = baseStats.get(stat);
            h = 31 * h + (value == null ? 0 : Double.hashCode(value));
        }
        h = 31 * h + hitbox.hashCode();
        h = 31 * h + effects.hashCode();
        h = 31 * h + rampEffects.hashCode();
        h = 31 * h + synergyEffects.hashCode();
        return 31 * h + passiveSlots;
    }
}
