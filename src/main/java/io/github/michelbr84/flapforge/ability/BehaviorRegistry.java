package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.ability.behaviors.CoinMagnetBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.DashBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.DoubleFlapBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.EmergencyRecoveryBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.InvulnerabilityBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.ScoreMultiplierBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.ShieldBehavior;
import io.github.michelbr84.flapforge.ability.behaviors.SlowTimeBehavior;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code AbilityDef.behavior} to implementation (D9), plus the parameter contract of each
 * behaviour ({@link ParamSpec}).
 *
 * <p>It is the reason an unknown behaviour id is a content error rather than a run that silently
 * does nothing: {@code ContentValidator} asks this registry whether the id exists and which level
 * parameters the code actually reads, so {@code abilities.json} and
 * {@code ability/behaviors/*} can never drift apart. A behaviour is created fresh per equipped
 * ability per run, so per-run state may live in its fields.
 *
 * <p>Insertion order is preserved, and nothing iterates the map during a tick, so the registry
 * cannot make a run depend on hash order (D12).
 */
public final class BehaviorRegistry {

    /** Behaviour id of the shield passive. */
    public static final String SHIELD = "shield";
    /** Behaviour id of the double flap. */
    public static final String DOUBLE_FLAP = "double_flap";
    /** Behaviour id of the dash. */
    public static final String DASH = "dash";
    /** Behaviour id of slow time. */
    public static final String SLOW_TIME = "slow_time";
    /** Behaviour id of the emergency recovery passive. */
    public static final String EMERGENCY_RECOVERY = "emergency_recovery";
    /** Behaviour id of the coin magnet passive. */
    public static final String COIN_MAGNET = "coin_magnet";
    /** Behaviour id of the score multiplier. */
    public static final String SCORE_MULTIPLIER = "score_multiplier";
    /** Behaviour id of invulnerability. */
    public static final String INVULNERABILITY = "invulnerability";

    /** The eight behaviours the game ships (D9). */
    public static final BehaviorRegistry DEFAULT = shipped();

    /** One registered behaviour: how to build it and what it reads. */
    private record Entry(Supplier<AbilityBehavior> factory, List<ParamSpec> params) {
    }

    private final Map<String, Entry> entries;

    private BehaviorRegistry(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    private static BehaviorRegistry shipped() {
        Map<String, Entry> map = new LinkedHashMap<>();
        map.put(SHIELD, new Entry(ShieldBehavior::new, ShieldBehavior.PARAMS));
        map.put(DOUBLE_FLAP, new Entry(DoubleFlapBehavior::new, DoubleFlapBehavior.PARAMS));
        map.put(DASH, new Entry(DashBehavior::new, DashBehavior.PARAMS));
        map.put(SLOW_TIME, new Entry(SlowTimeBehavior::new, SlowTimeBehavior.PARAMS));
        map.put(EMERGENCY_RECOVERY,
                new Entry(EmergencyRecoveryBehavior::new, EmergencyRecoveryBehavior.PARAMS));
        map.put(COIN_MAGNET, new Entry(CoinMagnetBehavior::new, CoinMagnetBehavior.PARAMS));
        map.put(SCORE_MULTIPLIER,
                new Entry(ScoreMultiplierBehavior::new, ScoreMultiplierBehavior.PARAMS));
        map.put(INVULNERABILITY,
                new Entry(InvulnerabilityBehavior::new, InvulnerabilityBehavior.PARAMS));
        return new BehaviorRegistry(map);
    }

    /**
     * Builds a registry from explicit entries (tests and future content packs).
     *
     * @param factories behaviour id to factory
     * @param params behaviour id to the parameters it reads
     * @return the registry
     */
    public static BehaviorRegistry of(Map<String, Supplier<AbilityBehavior>> factories,
            Map<String, List<ParamSpec>> params) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<AbilityBehavior>> e : factories.entrySet()) {
            List<ParamSpec> specs = params.getOrDefault(e.getKey(), List.of());
            map.put(e.getKey(), new Entry(e.getValue(), List.copyOf(specs)));
        }
        return new BehaviorRegistry(map);
    }

    /**
     * Whether a behaviour id is implemented.
     *
     * @param behaviorId the id from {@code abilities.json}
     * @return {@code true} when {@link #create(String)} would succeed
     */
    public boolean contains(String behaviorId) {
        return behaviorId != null && entries.containsKey(behaviorId);
    }

    /**
     * Creates a fresh behaviour.
     *
     * @param behaviorId the id from {@code abilities.json}
     * @return a new instance
     * @throws IllegalStateException when the id is unknown
     */
    public AbilityBehavior create(String behaviorId) {
        Entry entry = entries.get(behaviorId);
        if (entry == null) {
            throw new IllegalStateException("Unknown ability behavior '" + behaviorId + "'");
        }
        return entry.factory().get();
    }

    /**
     * The level parameters a behaviour reads.
     *
     * @param behaviorId the id from {@code abilities.json}
     * @return the specs, empty when the behaviour reads none or the id is unknown
     */
    public List<ParamSpec> params(String behaviorId) {
        Entry entry = entries.get(behaviorId);
        return entry == null ? List.of() : entry.params();
    }

    /**
     * The registered behaviour ids, in registration order.
     *
     * @return the ids
     */
    public List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    /**
     * How many behaviours are registered.
     *
     * @return the count
     */
    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "BehaviorRegistry" + new ArrayList<>(entries.keySet());
    }
}
