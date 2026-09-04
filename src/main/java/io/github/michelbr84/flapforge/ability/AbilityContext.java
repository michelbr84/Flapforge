package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.List;
import java.util.Objects;

/**
 * What a behaviour sees when one of its hooks runs (D9): the bird, the tick context, the resolved
 * stats and rules, the run counters, its own level parameters and counters, and the two things it
 * may ask the run for — invulnerability and a fact on the tick report.
 *
 * <p>One instance is owned by the {@link AbilityManager} and rebound before every hook call
 * ({@link #bind}) instead of being allocated per call: a tick with three equipped abilities
 * allocates nothing, which keeps the simulation's per-tick cost flat and its behaviour identical
 * whatever the loadout is.
 */
public final class AbilityContext {

    private final AbilityHost host;
    private final AbilityManager manager;
    private AbilityInstance instance;
    private SimContext sim;
    private List<TickFact> facts;

    /**
     * Creates the context of one manager.
     *
     * @param host the run the abilities live in
     * @param manager the owner, which collects the run effects added through this context
     */
    AbilityContext(AbilityHost host, AbilityManager manager) {
        this.host = Objects.requireNonNull(host, "host");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    /**
     * Rebinds the context to the ability and the tick whose hook is about to run.
     *
     * @param instance the ability being called
     * @param sim the tick context, or {@code null} outside a tick (run start)
     * @param facts where facts are appended, or {@code null} when the caller collects none
     */
    void bind(AbilityInstance instance, SimContext sim, List<TickFact> facts) {
        this.instance = instance;
        this.sim = sim;
        this.facts = facts;
    }

    /**
     * The bird.
     *
     * @return the bird
     */
    public Bird bird() {
        return host.bird();
    }

    /**
     * The tick context (world clock scale, random streams).
     *
     * @return the context, or {@code null} at run start
     */
    public SimContext sim() {
        return sim;
    }

    /**
     * The resolved stats.
     *
     * @return the sheet
     */
    public StatSheet stats() {
        return host.stats();
    }

    /**
     * The active rules.
     *
     * @return the rules
     */
    public RuleSet rules() {
        return host.rules();
    }

    /**
     * The shield charges of the run.
     *
     * @return the system
     */
    public ShieldSystem shield() {
        return host.shield();
    }

    /**
     * The revives of the run.
     *
     * @return the system
     */
    public ReviveSystem revive() {
        return host.revive();
    }

    /**
     * The ability whose hook is running.
     *
     * @return the instance
     */
    public AbilityInstance ability() {
        return instance;
    }

    /**
     * The owned level of the ability.
     *
     * @return the level
     */
    public int level() {
        return instance.level();
    }

    /**
     * A level parameter of the ability.
     *
     * @param key the parameter name
     * @param fallback the value to use when the level does not declare it
     * @return the value
     */
    public double param(String key, double fallback) {
        return instance.param(key, fallback);
    }

    /**
     * A level parameter of the ability, truncated to an int.
     *
     * @param key the parameter name
     * @param fallback the value to use when the level does not declare it
     * @return the value
     */
    public int intParam(String key, int fallback) {
        return instance.intParam(key, fallback);
    }

    /**
     * Gates passed so far.
     *
     * @return the count
     */
    public int gatesPassed() {
        return host.gatesPassed();
    }

    /**
     * Points scored so far.
     *
     * @return the points
     */
    public double points() {
        return host.points();
    }

    /**
     * Value of the coins picked up so far.
     *
     * @return the value
     */
    public int coinsCollected() {
        return host.coinsCollected();
    }

    /**
     * The tick being processed.
     *
     * @return the tick
     */
    public int tick() {
        return host.tick();
    }

    /**
     * Grants invulnerability ticks (the longest grant wins).
     *
     * @param ticks the number of ticks
     */
    public void grantIFrames(int ticks) {
        host.grantIFrames(ticks);
    }

    /**
     * Makes the bird ignore lethal hits from the hazard it is inside until it is clear of it —
     * the rule a shield absorb follows, and what a held-line burst asks for when it releases
     * inside a column. It covers that one hazard, never a second one that arrives later.
     */
    public void ghostUntilClear() {
        host.ghostUntilClear();
    }

    /**
     * Whether a lethal hit would currently be ignored.
     *
     * @return {@code true} when the bird is invulnerable
     */
    public boolean isInvulnerable() {
        return host.isInvulnerable();
    }

    /**
     * Appends a fact to the tick report.
     *
     * @param fact the fact
     */
    public void emit(TickFact fact) {
        if (facts != null) {
            facts.add(fact);
        }
    }

    /**
     * Contributes a modifier to the {@code ABILITY} layer for the rest of the run — the way a
     * passive scales with its level, since {@code effects} are authored once for every level.
     *
     * <p>Call it from {@link AbilityBehavior#onEquip} only: the shield and revive systems read
     * their charges before the first hook runs, so a {@code SHIELD_CHARGES} or {@code REVIVES}
     * contribution added here would never reach them.
     *
     * @param modifier the modifier
     */
    public void addRunEffect(StatModifier modifier) {
        manager.addRunEffect(modifier);
    }

    /**
     * The label ability modifiers carry in a stat breakdown: {@code ability:<id>}.
     *
     * @return the source label
     */
    public String source() {
        return AbilityManager.SOURCE_PREFIX + instance.id();
    }
}
